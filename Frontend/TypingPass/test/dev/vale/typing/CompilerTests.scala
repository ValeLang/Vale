package dev.vale.typing

import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.infer.{KindIsNotConcrete, OwnershipDidntMatch}
import dev.vale._
import dev.vale.parsing.ParseErrorHumanizer
import dev.vale.postparsing.PostParser
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.highertyping.{FunctionA, HigherTypingCompilation}
import dev.vale.solver.RuleError
import OverloadResolver.{FindFunctionFailure, InferFailure, SpecificParamDoesntSend, WrongNumberOfArguments}
import dev.vale.Collector.ProgramWithExpect
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.solver.{FailedSolve, RuleError, Step}
import dev.vale.typing.ast.{ConstantIntTE, DestroyTE, DiscardTE, FunctionCallTE, FunctionDefinitionT, FunctionHeaderT, KindExportT, LetAndLendTE, LetNormalTE, LocalLookupTE, ParameterT, PrototypeT, ReferenceMemberLookupTE, ReturnTE, SignatureT, SoftLoadTE, UserFunctionT, referenceExprResultKind, referenceExprResultStructName}
import dev.vale.typing.names.{BuildingFunctionNameWithClosuredsT, CitizenNameT, CitizenTemplateNameT, CodeVarNameT, ExportNameT, ExportTemplateNameT, FunctionNameT, FunctionTemplateNameT, IdT, InterfaceNameT, InterfaceTemplateNameT, KindPlaceholderNameT, KindPlaceholderTemplateNameT, StructNameT, StructTemplateNameT}
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.typing.ast._
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest._

import scala.collection.immutable.List
import scala.io.Source

class CompilerTests extends FunSuite with Matchers {
  // TODO: pull all of the typingpass specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }

  test("Simple program returning an int, explicit") {
    // We had a bug once looking up "int" in the environment, hence this test.

    val compile = CompilerTestCompilation.test(
      """
        |func main() int { return 3; }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    main.header.returnType.kind shouldEqual IntT(32)
  }

  test("Hardcoding negative numbers") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() int { return -3; }
        |""".stripMargin)
    val main = compile.expectCompilerOutputs().lookupFunction("main")
    Collector.only(main, { case ConstantIntTE(IntegerTemplataT(-3), _) => true })
  }

  test("Simple local") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() int {
        |  a = 42;
        |  return a;
        |}
    """.stripMargin)
    val main = compile.expectCompilerOutputs().lookupFunction("main")
    vassert(main.header.returnType.kind == IntT(32))
  }

  test("Tests panic return type") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |exported func main() int {
        |  x = { __vbi_panic() }();
        |}
        """.stripMargin)
    val main = compile.expectCompilerOutputs().lookupFunction("main")
    main shouldHave {
      case LetNormalTE(
        ReferenceLocalVariableT(_,_,CoordT(ShareT,_,NeverT(false))),
        _) =>
    }
  }

  test("Taking an argument and returning it") {
    val compile = CompilerTestCompilation.test(
      """
        |func main(a int) int { return a; }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    Collector.onlyOf(coutputs.lookupFunction("main"), classOf[ParameterT]).tyype == CoordT(ShareT, GlobalRegionT(), IntT.i32)
    val lookup = Collector.onlyOf(coutputs.lookupFunction("main"), classOf[LocalLookupTE]);
    lookup.localVariable.name match { case CodeVarNameT(StrI("a")) => }
    lookup.localVariable.coord match { case CoordT(ShareT, _, IntT.i32) => }
  }

  test("Tests adding two numbers") {
    val compile =
      CompilerTestCompilation.test(
        """
          |import v.builtins.arith.*;
          |exported func main() int { return +(2, 3); }
          |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, { case ConstantIntTE(IntegerTemplataT(2), _) => true })
    Collector.only(main, { case ConstantIntTE(IntegerTemplataT(3), _) => true })
    Collector.only(main, {
      case FunctionCallTE(
        functionName("+"),
        Vector(
          ConstantIntTE(IntegerTemplataT(2), _),
          ConstantIntTE(IntegerTemplataT(3), _))) =>
    })
  }

  test("Simple struct read") {
    val compile = CompilerTestCompilation.test(
      """
        |exported struct Moo { hp int; }
        |exported func main(moo &Moo) int {
        |  return moo.hp;
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
  }

  test("Make array and dot it") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.arrays.*;
        |exported func main() int {
        |  [#]int(6, 60, 103).2
        |}
        |""".stripMargin)
    compile.expectCompilerOutputs()
  }

  test("Simple struct instantiate") {
    val compile = CompilerTestCompilation.test(
      """
        |exported struct Moo { hp int; }
        |exported func main() Moo {
        |  return Moo(42);
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
  }

  test("Call destructor") {
    val compile = CompilerTestCompilation.test(
      """
        |exported struct Moo { hp int; }
        |exported func main() int {
        |  return Moo(42).hp;
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionCallTE(PrototypeT(IdT(_, _, FunctionNameT(FunctionTemplateNameT(StrI("drop"), _), _, _)), _), _) =>
    })
  }

  test("Custom destructor") {
    val compile = CompilerTestCompilation.test(
      """
        |#!DeriveStructDrop
        |exported struct Moo { hp int; }
        |func drop(self ^Moo) {
        |  [_] = self;
        |}
        |exported func main() int {
        |  return Moo(42).hp;
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionCallTE(PrototypeT(IdT(_, _, FunctionNameT(FunctionTemplateNameT(StrI("drop"), _), _, _)), _), _) =>
    })
  }

  test("Make constraint reference") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Moo {}
        |exported func main() void {
        |  m = Moo();
        |  b = &m;
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    val tyype =
      Collector.only(main.body, {
        case LetNormalTE(ReferenceLocalVariableT(CodeVarNameT(StrI("b")), _, tyype), _) => tyype
      })
    tyype.ownership shouldEqual BorrowT
  }



  test("Recursion") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() int { return main(); }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Make sure it inferred the param type and return type correctly
    coutputs.lookupFunction("main").header.returnType shouldEqual CoordT(ShareT, GlobalRegionT(), IntT.i32)
  }

  test("Test overloads") {
    val compile = CompilerTestCompilation.test(Tests.loadExpected("programs/functions/overloads.vale"))
    val coutputs = compile.expectCompilerOutputs()

    coutputs.lookupFunction("main").header.returnType shouldEqual
      CoordT(ShareT, GlobalRegionT(), IntT.i32)
  }

  test("Test readonly UFCS") {
    val compile = CompilerTestCompilation.test(Tests.loadExpected("programs/ufcs.vale"))
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test readwrite UFCS") {
    val compile = CompilerTestCompilation.test(Tests.loadExpected("programs/readwriteufcs.vale"))
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test templates") {
    val compile = CompilerTestCompilation.test(
      """
        |func bork<T>(a T) T { return a; }
        |exported func main() int { bork(true); bork(2); bork(3) }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Tests that there's only two functions, because we have generics not templates
    vassert(coutputs.getAllUserFunctions.size == 2)
  }

  test("Test taking a callable param") {
    val compile = CompilerTestCompilation.test(
      """
        |func do<F>(callable F) int
        |where func(&F)int, func drop(F)void
        |{
        |  return callable();
        |}
        |exported func main() int { return do({ return 3; }); }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    coutputs.functions.collect({ case x @ functionName("do") => x }).head.header.returnType shouldEqual CoordT(ShareT, GlobalRegionT(), IntT.i32)
  }

  test("Simple struct") {
    val compile = CompilerTestCompilation.test(
      """
        |#!DeriveStructDrop
        |struct MyStruct { a int; }
        |exported func main() {
        |  ms = MyStruct(7);
        |  [_] = ms;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Check the struct was made
    coutputs.structs.collectFirst({
      case StructDefinitionT(
        simpleName("MyStruct"),
        StructTT(simpleName("MyStruct")),
        _,
        false,
        MutabilityTemplataT(MutableT),
        Vector(NormalStructMemberT(CodeVarNameT(StrI("a")), FinalT, ReferenceMemberTypeT((CoordT(ShareT, _,IntT.i32))))),
        false,
        _,
        _) =>
    }).get
    // Check there's a constructor
    Collector.all(coutputs.lookupFunction("MyStruct"), {
      case FunctionHeaderT(
        simpleName("MyStruct"),
        _,
        Vector(ParameterT(CodeVarNameT(StrI("a")), None, _, CoordT(ShareT, _,IntT.i32))),
        CoordT(OwnT, _,StructTT(simpleName("MyStruct"))),
        _) =>
    })
    val main = coutputs.lookupFunction("main")
    // Check that we call the constructor
    Collector.only(main, {
      case FunctionCallTE(
        PrototypeT(simpleName("MyStruct"), _),
        Vector(ConstantIntTE(IntegerTemplataT(7), _))) =>
    })
  }

  test("Calls destructor on local var") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Muta { }
        |
        |func destructor(m ^Muta) {
        |  Muta[ ] = m;
        |}
        |
        |exported func main() {
        |  a = Muta();
        |}
      """.stripMargin)

    val main = compile.expectCompilerOutputs().lookupFunction("main")
    Collector.only(main, { case FunctionCallTE(PrototypeT(IdT(_, _, FunctionNameT(FunctionTemplateNameT(StrI("drop"), _), _, _)), _), _) => })
    Collector.all(main, { case FunctionCallTE(_, _) => }).size shouldEqual 2
  }

  test("Tests defining an empty interface and an implementing struct") {
    val compile = CompilerTestCompilation.test(
      """
        |sealed interface MyInterface { }
        |struct MyStruct { }
        |impl MyInterface for MyStruct;
        |func main(a MyStruct) {}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val interfaceDef =
      vassertOne(coutputs.interfaces.collectFirst({
        case id @ InterfaceDefinitionT(simpleName("MyInterface"), _, _, _, false, MutabilityTemplataT(MutableT), _, _, Vector()) => id
      }))

    val structDef =
      vassertOne(coutputs.structs.collectFirst({
        case sd @ StructDefinitionT(simpleName("MyStruct"), _, _, false, MutabilityTemplataT(MutableT), _, false, _, _) => sd
      }))

    vassert(coutputs.interfaceToSubCitizenToEdge.flatMap(_._2.values).exists(impl => {
      impl.subCitizen.id == structDef.instantiatedCitizen.id &&
        impl.superInterface == interfaceDef.instantiatedCitizen.id
    }))
  }

  test("Tests defining a non-empty interface and an implementing struct") {
    val compile = CompilerTestCompilation.test(
      """
        |exported sealed interface MyInterface {
        |  func bork(virtual self &MyInterface);
        |}
        |exported struct MyStruct { }
        |impl MyInterface for MyStruct;
        |func bork(self &MyStruct) {}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val (interfaceDef, methods) =
      vassertOne(coutputs.interfaces.collectFirst({
        case id @ InterfaceDefinitionT(simpleName("MyInterface"), _, _, _, false, MutabilityTemplataT(MutableT), _, _, methods) => (id, methods)
      }))
    vassertSome(methods.collectFirst({
      case (f @ PrototypeT(simpleName("bork"), _), _) => f
    }))

    val structDef =
      vassertOne(coutputs.structs.collectFirst({
        case sd @ StructDefinitionT(simpleName("MyStruct"), _, _, false, MutabilityTemplataT(MutableT), _, false, _, _) => sd
      }))

    vassert(coutputs.interfaceToSubCitizenToEdge.values.flatMap(_.values).exists(impl => {
      impl.subCitizen.id == structDef.instantiatedCitizen.id &&
        impl.superInterface == interfaceDef.instantiatedCitizen.id
    }))
  }

  test("Stamps an interface template via a function return") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |sealed interface MyInterface<X Ref> where func drop(X)void { }
        |
        |struct SomeStruct<X Ref> where func drop(X)void { x X; }
        |impl<X> MyInterface<X> for SomeStruct<X>;
        |
        |func doAThing<T>(t T) SomeStruct<T>
        |where func drop(T)void {
        |  return SomeStruct<T>(t);
        |}
        |
        |exported func main() {
        |  doAThing(4);
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
  }

//  test("Constructor is stamped even without calling") {
//    val compile = RunCompilation.test(
//      """
//        |struct MyStruct imm {}
//        |func wot(b: *MyStruct) int { return 9; }
//      """.stripMargin)
//    val coutputs = compile.expectCompilerOutputs()
//
//    coutputs.lookupFunction("MyStruct")
//  }

  test("Reads a struct member") {
    val compile = CompilerTestCompilation.test(
      """
        |#!DeriveStructDrop
        |struct MyStruct { a int; }
        |exported func main() int {
        |  ms = MyStruct(7);
        |  x = ms.a;
        |  [_] = ms;
        |  return x;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    // check for the member access
    main shouldHave {
      case ReferenceMemberLookupTE(_,
        SoftLoadTE(_,BorrowT),
        CodeVarNameT(StrI("a")),
        CoordT(ShareT,_,IntT(32)),
        FinalT) =>
    }
  }

  test("Automatically drops struct") {
    val compile = CompilerTestCompilation.test(
      """
        |struct MyStruct { a int; }
        |exported func main() int {
        |  ms = MyStruct(7);
        |  return ms.a;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    // check for the call to drop
    main shouldHave {
      case FunctionCallTE(
        PrototypeT(
          IdT(_,
            Vector(StructTemplateNameT(StrI("MyStruct"))),
            FunctionNameT(
              FunctionTemplateNameT(StrI("drop"),_),
              Vector(),
              Vector(CoordT(OwnT,_,StructTT(IdT(_,_,StructNameT(StructTemplateNameT(StrI("MyStruct")),Vector()))))))),
          CoordT(ShareT,_,VoidT())), _) =>
    }
  }

  test("Tests stamping an interface template from a function param") {
    val compile = CompilerTestCompilation.test(
      """
        |interface MyOption<T Ref> { }
        |func main(a &MyOption<int>) { }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner
    val keywords = compile.keywords

    coutputs.lookupInterfaceByTemplateName(
      interner.intern(
        InterfaceTemplateNameT(interner.intern(StrI("MyOption")))))
    coutputs.lookupFunction("main").header.params.head.tyype shouldEqual
        CoordT(
          BorrowT,
          GlobalRegionT(),
          interner.intern(
            InterfaceTT(IdT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), interner.intern(InterfaceNameT(interner.intern(InterfaceTemplateNameT(interner.intern(StrI("MyOption")))), Vector(CoordTemplataT(CoordT(ShareT, GlobalRegionT(), IntT.i32)))))))))

    // Can't run it because there's nothing implementing that interface >_>
  }

  test("Reports mismatched return type when expecting void") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() { 73 }
        |""".stripMargin)
    compile.getCompilerOutputs().expectErr() match {
      case BodyResultDoesntMatch(_,
        FunctionNameS(StrI("main"),_),
        CoordT(ShareT,_,VoidT()),
        CoordT(ShareT,_,IntT(_))) =>
    }
  }

  test("Tests exporting function") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func moo() { }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupFunction("moo")
    val export = vassertOne(coutputs.functionExports)
    `export`.prototype shouldEqual moo.header.toPrototype
  }

  test("Tests exporting struct") {
    val compile = CompilerTestCompilation.test(
      """
        |exported struct Moo { a int; }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupStruct("Moo")
    val export = vassertOne(coutputs.kindExports)
    `export`.tyype shouldEqual moo.instantiatedCitizen
  }

  test("Tests exporting interface") {
    val compile = CompilerTestCompilation.test(
      """
        |exported sealed interface IMoo { func hi(virtual this &IMoo) void; }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupInterface("IMoo")
    val export = vassertOne(coutputs.kindExports)
    `export`.tyype shouldEqual moo.instantiatedInterface
  }

  test("Tests single expression and single statement functions' returns") {
    val compile = CompilerTestCompilation.test(
      """
        |struct MyThing { value int; }
        |func moo() MyThing { return MyThing(4); }
        |exported func main() { moo(); }
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupFunction("moo")
    moo.header.returnType match {
      case CoordT(OwnT,_, StructTT(simpleName("MyThing"))) =>
    }
    val main = coutputs.lookupFunction("main")
    main.header.returnType match {
      case CoordT(ShareT, _, VoidT()) =>
    }
  }

  test("Tests calling a templated struct's constructor") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.drop.*;
        |struct MySome<T Ref> where func drop(T)void { value T; }
        |exported func main() int {
        |  return MySome<int>(4).value;
        |}
        |""".stripMargin
    )

    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner
    val keywords = compile.keywords

    coutputs.lookupStructByTemplateName(
      interner.intern(StructTemplateNameT(interner.intern(StrI("MySome")))))

    val constructor = coutputs.lookupFunction("MySome")
    constructor.header match {
      case FunctionHeaderT(
        IdT(_,
          _,
          FunctionNameT(
            FunctionTemplateNameT(StrI("MySome"), _),
            Vector(CoordTemplataT(CoordT(OwnT, _,KindPlaceholderT(IdT(_,_,KindPlaceholderNameT(KindPlaceholderTemplateNameT(0, CodeRuneS(StrI("T"))))))))),
            Vector(CoordT(OwnT,_,KindPlaceholderT(IdT(_,_,KindPlaceholderNameT(KindPlaceholderTemplateNameT(0, _)))))))),
        Vector(),
        Vector(
          ParameterT(
            CodeVarNameT(StrI("value")),
            None,
            _,
            CoordT(OwnT,_,KindPlaceholderT(IdT(_,_,KindPlaceholderNameT(KindPlaceholderTemplateNameT(0, _))))))),
        CoordT(
          OwnT,
          _,
          StructTT(
            IdT(_,
              _,
              StructNameT(
                StructTemplateNameT(StrI("MySome")),
                Vector(
                  CoordTemplataT(CoordT(OwnT, _,KindPlaceholderT(IdT(_,_,KindPlaceholderNameT(KindPlaceholderTemplateNameT(0, _))))))))))),
        Some(_)) =>
    }

    Collector.all(coutputs.lookupFunction("main"), {
      case FunctionCallTE(functionName("MySome"), _) =>
    })
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = CompilerTestCompilation.test(readCodeFromResource("programs/virtuals/upcasting.vale"))
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")

    Collector.only(main, { case LetNormalTE(ReferenceLocalVariableT(CodeVarNameT(StrI("x")),FinalT,CoordT(OwnT,_, InterfaceTT(simpleName("MyInterface")))), _) => })

    val upcast = Collector.onlyOf(main, classOf[UpcastTE])
    upcast.result.coord match { case CoordT(OwnT,_, InterfaceTT(IdT(x, Vector(), InterfaceNameT(InterfaceTemplateNameT(StrI("MyInterface")), Vector())))) => vassert(x.isTest) }
    upcast.innerExpr.result.coord match { case CoordT(OwnT,_, StructTT(IdT(x, Vector(), StructNameT(StructTemplateNameT(StrI("MyStruct")), Vector())))) => vassert(x.isTest) }
  }

  test("Tests calling a virtual function") {
    val compile = CompilerTestCompilation.test(readCodeFromResource("programs/virtuals/calling.vale"))
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case up @ UpcastTE(innerExpr, InterfaceTT(simpleName("Car")), _) => {
        Collector.only(innerExpr.result, {
          case StructTT(simpleName("Toyota")) =>
        })
        up.result.coord.kind match { case InterfaceTT(IdT(x, Vector(), InterfaceNameT(InterfaceTemplateNameT(StrI("Car")), Vector()))) => vassert(x.isTest) }
      }
    })
  }

  test("Tests upcasting has the right stuff") {
    val compile = CompilerTestCompilation.test(readCodeFromResource("programs/virtuals/calling.vale"))
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    val up @ UpcastTE(innerExpr, _, implName) =
      Collector.only(main, { case up @ UpcastTE(_, InterfaceTT(simpleName("Car")), _) => up})

    Collector.only(innerExpr.result, {
      case StructTT(simpleName("Toyota")) =>
    })
    up.result.coord.kind match { case InterfaceTT(IdT(x, Vector(), InterfaceNameT(InterfaceTemplateNameT(StrI("Car")), Vector()))) => vassert(x.isTest) }

    val impl = coutputs.lookupEdge(implName)
    vassert(impl.subCitizen.id == up.innerExpr.result.coord.kind.expectCitizen().id)
    vassert(impl.superInterface == up.result.coord.kind.expectCitizen().id)

//    freePrototype.fullName.last.parameters.head shouldEqual up.result.reference
  }

  test("Tests calling a virtual function through a borrow ref") {
    val compile = CompilerTestCompilation.test(readCodeFromResource("programs/virtuals/callingThroughBorrow.vale"))
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case f @ FunctionCallTE(PrototypeT(simpleName("doCivicDance"),CoordT(ShareT,_, IntT.i32)), _) => {
//        vassert(f.callable.paramTypes == Vector(Coord(Borrow,InterfaceRef2(simpleName("Car")))))
      }
    })
  }

  test("Tests calling a templated function with explicit template args") {
    // Tests putting MyOption<int> as the type of x.
    val compile = CompilerTestCompilation.test(
      """
        |
        |func moo<T> () where T Ref { }
        |
        |exported func main() {
        |	moo<int>();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  // See DSDCTD
  test("Tests destructuring borrow doesnt compile to destroy") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |struct Vec3i {
        |  x int;
        |  y int;
        |  z int;
        |}
        |
        |exported func main() int {
        |  v = Vec3i(3, 4, 5);
        |	 [x, y, z] = &v;
        |  return y;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")

    Collector.all(main, {
      case DestroyTE(_, _, _) =>
    }).size shouldEqual 0

    Collector.only(main, {
      case ReferenceMemberLookupTE(_,
        SoftLoadTE(LocalLookupTE(_,ReferenceLocalVariableT(_,FinalT,CoordT(_,_,StructTT(_)))),BorrowT),
        CodeVarNameT(StrI("x")),CoordT(ShareT,_,IntT.i32),FinalT) =>
    })
  }

  test("Tests making a variable with a pattern") {
    // Tests putting MyOption<int> as the type of x.
    val compile = CompilerTestCompilation.test(
      """
        |
        |sealed interface MyOption<T> where T Ref { }
        |
        |struct MySome<T> where T Ref {}
        |impl<T> MyOption<T> for MySome<T>;
        |
        |func doSomething(opt MyOption<int>) int {
        |  return 9;
        |}
        |
        |exported func main() int {
        |	x MyOption<int> = MySome<int>();
        |	return doSomething(x);
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Tests a linked list") {
    val compile = CompilerTestCompilation.test(
      Tests.loadExpected("programs/virtuals/ordinarylinkedlist.vale"))
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test borrow ref") {
    val compile = CompilerTestCompilation.test(Tests.loadExpected("programs/borrowRef.vale"))
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Tests calling a function with an upcast") {
    val compile = CompilerTestCompilation.test(
        """
          |interface ISpaceship {}
          |struct Firefly {}
          |impl ISpaceship for Firefly;
          |func launch(ship &ISpaceship) { }
          |func main() {
          |  launch(&Firefly());
          |}
          |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case UpcastTE(
        _,
        InterfaceTT(IdT(_, _, InterfaceNameT(InterfaceTemplateNameT(StrI("ISpaceship")), _))),
        _) =>
    })
  }

  test("Tests calling a templated function with an upcast") {
    val compile = CompilerTestCompilation.test(
      """
        |interface ISpaceship<T> where T Ref {}
        |struct Firefly<T> where T Ref {}
        |impl<T> ISpaceship<T> for Firefly<T>;
        |func launch<T>(ship &ISpaceship<T>) { }
        |func main() {
        |  launch(&Firefly<int>());
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case UpcastTE(
        _,
        InterfaceTT(IdT(_, _, InterfaceNameT(InterfaceTemplateNameT(StrI("ISpaceship")), _))),
        _) =>
    })
  }


  test("Tests upcast with generics has the right stuff") {
    val compile = CompilerTestCompilation.test(
      """
        |interface ISpaceship<T> where T Ref {}
        |struct Firefly<T> where T Ref {}
        |impl<T> ISpaceship<T> for Firefly<T>;
        |func launch<T>(ship &ISpaceship<T>) { }
        |func main() {
        |  launch(&Firefly<int>());
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case UpcastTE(
      _,
      InterfaceTT(IdT(_, _, InterfaceNameT(InterfaceTemplateNameT(StrI("ISpaceship")), _))),
      _) =>
    })
  }

  test("Tests a templated linked list") {
    val compile = CompilerTestCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/templatedlinkedlist.vale"))
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Tests a foreach for a linked list") {
    val compile = CompilerTestCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/foreachlinkedlist.vale"))
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case f @ FunctionCallTE(functionName("forEach"), _) => f
    })
  }

  test("Test return from inside if destroys locals") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Marine { hp int; }
        |exported func main() int {
        |  m = Marine(5);
        |  x =
        |    if (true) {
        |      return 7;
        |    } else {
        |      m.hp
        |    };
        |  return x;
        |}
        |""".stripMargin)// +
    //        Tests.loadExpected("castutils/castutils.vale") +
    //        Tests.loadExpected("printutils/printutils.vale"))
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    val destructorCalls =
      Collector.all(main, {
        case fpc @ FunctionCallTE(PrototypeT(IdT(_,Vector(StructTemplateNameT(StrI("Marine"))),FunctionNameT(FunctionTemplateNameT(StrI("drop"),_),Vector(),Vector(CoordT(OwnT,_, StructTT(IdT(_,Vector(),StructNameT(StructTemplateNameT(StrI("Marine")),Vector()))))))),_),_) => fpc
      })
    destructorCalls.size shouldEqual 2
  }

  test("Recursive struct") {
    val compile = CompilerTestCompilation.test(
      """
        |struct ListNode imm {
        |  tail ListNode;
        |}
        |func main(a ListNode) {}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Recursive struct with Opt") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.opt.*;
        |struct ListNode {
        |  tail Opt<ListNode>;
        |}
        |func main(a ListNode) {}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  // Make sure a ListNode struct made it out
  test("Templated imm struct") {
    val compile = CompilerTestCompilation.test(
      """
        |struct ListNode<T Ref> imm {
        |  tail ListNode<T>;
        |}
        |func main(a ListNode<int>) {}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Borrow-load member") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Bork {
        |  x int;
        |}
        |func getX(bork &Bork) int { return bork.x; }
        |struct List {
        |  array! Bork;
        |}
        |exported func main() int {
        |  l = List(Bork(0));
        |  return getX(&l.array);
        |}
        """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    vpass()
  }

  test("Test Vector of StructTemplata") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.arrays.*;
        |import v.builtins.functor1.*;
        |import v.builtins.drop.*;
        |import v.builtins.panic.*;
        |
        |struct Vec2 imm {
        |  x float;
        |  y float;
        |}
        |struct Pattern imm {
        |  patternTiles []<imm>Vec2;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }


  test("If branches returns never and struct") {
    // We had a bug where it couldn't reconcile never and struct.

    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |
        |exported struct Moo {}
        |exported func main() Moo {
        |  if true {
        |    Moo()
        |  } else {
        |    panic("Error in CreateDir");
        |  }
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test return") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() int {
        |  return 7;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, { case ReturnTE(_) => })
  }

  test("Test return from inside if") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |exported func main() int {
        |  if (true) {
        |    return 7;
        |  } else {
        |    return 9;
        |  }
        |  __vbi_panic();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.all(main, { case ReturnTE(_) => }).size shouldEqual 2
    Collector.only(main, { case ConstantIntTE(IntegerTemplataT(7), _) => })
    Collector.only(main, { case ConstantIntTE(IntegerTemplataT(9), _) => })
  }

  test("Zero method anonymous interface") {
    val compile = CompilerTestCompilation.test(
      """
        |interface MyInterface {}
        |exported func main() {
        |  x = MyInterface();
        |}
        |""".stripMargin)
    compile.expectCompilerOutputs()
  }

  test("Reports when exported function depends on non-exported param") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Firefly { }
        |exported func moo(firefly &Firefly) { }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedFunctionDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when exported function depends on non-exported return") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |import panicutils.*;
        |struct Firefly { }
        |exported func moo() &Firefly { __pretend<&Firefly>() }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedFunctionDependedOnNonExportedKind(_, _, _, _)) =>
      case _ => compile.expectCompilerOutputs(); vfail()
    }
  }

  test("Reports when extern function depends on non-exported param") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Firefly { }
        |extern func moo(firefly &Firefly);
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExternFunctionDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when extern function depends on non-exported return") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Firefly imm { }
        |extern func moo() &Firefly;
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExternFunctionDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when exported struct depends on non-exported member") {
    val compile = CompilerTestCompilation.test(
      """
        |exported struct Firefly imm {
        |  raza Raza;
        |}
        |struct Raza imm { }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }



  test("Checks that we stored a borrowed temporary in a local") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Muta { }
        |func doSomething(m &Muta, i int) {}
        |exported func main() {
        |  doSomething(&Muta(), 1)
        |}
      """.stripMargin)

    // Should be a temporary for this object
    Collector.onlyOf(
      compile.expectCompilerOutputs().lookupFunction("main"),
      classOf[LetAndLendTE]) match {
        case LetAndLendTE(_, _, BorrowT) =>
      }
  }

  test("Reports when reading nonexistant local") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() int {
        |  moo
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(CouldntFindIdentifierToLoadT(_, CodeNameS(StrI("moo")))) =>
    }
  }

  test("Reports when mutating after moving") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Weapon {
        |  ammo! int;
        |}
        |struct Marine {
        |  weapon! Weapon;
        |}
        |
        |exported func main() int {
        |  m = Marine(Weapon(7));
        |  newWeapon = Weapon(10);
        |  set m.weapon = newWeapon;
        |  set newWeapon.ammo = 11;
        |  return 42;
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(CantUseUnstackifiedLocal(_, CodeVarNameT(StrI("newWeapon")))) =>
    }
  }

  test("Tests export struct twice") {
    // See MMEDT why this is an error
    val compile = CompilerTestCompilation.test(
      """
        |exported struct Moo { }
        |export Moo as Bork;
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(TypeExportedMultipleTimes(_, _, Vector(_, _))) =>
    }
  }

  test("Reports when reading after moving") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Weapon {
        |  ammo! int;
        |}
        |struct Marine {
        |  weapon! Weapon;
        |}
        |
        |exported func main() int {
        |  m = Marine(Weapon(7));
        |  newWeapon = Weapon(10);
        |  set m.weapon = newWeapon;
        |  println(newWeapon.ammo);
        |  return 42;
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(CantUseUnstackifiedLocal(_, CodeVarNameT(StrI("newWeapon")))) =>
    }
  }

  test("Reports when moving from inside a while") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Marine {
        |  ammo int;
        |}
        |
        |exported func main() int {
        |  m = Marine(7);
        |  while (false) {
        |    drop(m);
        |  }
        |  return 42;
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(CantUnstackifyOutsideLocalFromInsideWhile(_, CodeVarNameT(StrI("m")))) =>
    }
  }

  test("Cant subscript non-subscriptable type") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Weapon {
        |  ammo! int;
        |}
        |
        |exported func main() int {
        |  weapon = Weapon(10);
        |  return weapon[42];
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(CannotSubscriptT(_, StructTT(IdT(_, _, StructNameT(StructTemplateNameT(StrI("Weapon")), Vector()))))) =>
    }
  }

  test("Reports when we give too many args") {
    val compile = CompilerTestCompilation.test(
      """
        |func moo(a int, b bool, s str) int { a }
        |exported func main() int {
        |  moo(42, true, "hello", false)
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      // Err(     case WrongNumberOfArguments(_, _)) =>
      case Err(CouldntFindFunctionToCallT(_, fff)) => {
        vassert(fff.rejectedCalleeToReason.size == 1)
        fff.rejectedCalleeToReason.head._2 match {
          case WrongNumberOfArguments(4, 3) =>
        }
      }
    }
  }

  test("Humanize errors") {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val testPackageCoord = PackageCoordinate.TEST_TLD(interner, keywords)
    val tz = List(RangeS.testZero(interner))
    val tzCodeLoc = CodeLocationS.testZero(interner)
    val funcTemplateName = FunctionTemplateNameT(interner.intern(StrI("main")), tzCodeLoc)
    val funcTemplateId = IdT(testPackageCoord, Vector(), funcTemplateName)
    val funcName = IdT(testPackageCoord, Vector(), FunctionNameT(FunctionTemplateNameT(interner.intern(StrI("main")), tzCodeLoc), Vector(), Vector()))
    val regionName = funcTemplateId.addStep(interner.intern(KindPlaceholderNameT(interner.intern(KindPlaceholderTemplateNameT(0, DenizenDefaultRegionRuneS(FunctionNameS(funcTemplateName.humanName, funcTemplateName.codeLocation)))))))
    val region = GlobalRegionT()

    val fireflyKind = StructTT(IdT(testPackageCoord, Vector(), StructNameT(StructTemplateNameT(StrI("Firefly")), Vector())))
    val fireflyCoord = CoordT(OwnT,region,fireflyKind)
    val serenityKind = StructTT(IdT(testPackageCoord, Vector(), StructNameT(StructTemplateNameT(StrI("Serenity")), Vector())))
    val serenityCoord = CoordT(OwnT,region,serenityKind)
    val ispaceshipKind = InterfaceTT(IdT(testPackageCoord, Vector(), InterfaceNameT(InterfaceTemplateNameT(StrI("ISpaceship")), Vector())))
    val ispaceshipCoord = CoordT(OwnT,region,ispaceshipKind)
    val unrelatedKind = StructTT(IdT(testPackageCoord, Vector(), StructNameT(StructTemplateNameT(StrI("Spoon")), Vector())))
    val unrelatedCoord = CoordT(OwnT,region,unrelatedKind)
    val fireflyTemplateName = IdT(testPackageCoord, Vector(), interner.intern(FunctionTemplateNameT(interner.intern(StrI("myFunc")), tz.head.begin)))
    val fireflySignature = ast.SignatureT(IdT(testPackageCoord, Vector(), interner.intern(FunctionNameT(interner.intern(FunctionTemplateNameT(interner.intern(StrI("myFunc")), tz.head.begin)), Vector(), Vector(fireflyCoord)))))
    val fireflyExportId = IdT(testPackageCoord, Vector(), interner.intern(ExportNameT(ExportTemplateNameT(tz.head.begin))))
    val fireflyExport = KindExportT(tz.head, fireflyKind, fireflyExportId, interner.intern(StrI("Firefly")));
    val serenityExportId = IdT(testPackageCoord, Vector(), interner.intern(ExportNameT(ExportTemplateNameT(tz.head.begin))))
    val serenityExport = KindExportT(tz.head, fireflyKind, serenityExportId, interner.intern(StrI("Serenity")));

    val filenamesAndSources = FileCoordinateMap.test(interner, "blah blah blah\nblah blah blah")

    val humanizePos = (x: CodeLocationS) => SourceCodeUtils.humanizePos(filenamesAndSources, x)
    val linesBetween = (x: CodeLocationS, y: CodeLocationS) => SourceCodeUtils.linesBetween(filenamesAndSources, x, y)
    val lineRangeContaining = (x: CodeLocationS) => SourceCodeUtils.lineRangeContaining(filenamesAndSources, x)
    val lineContaining = (x: CodeLocationS) => SourceCodeUtils.lineContaining(filenamesAndSources, x)

    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CouldntFindTypeT(tz, CodeNameS(interner.intern(StrI("Spaceship"))))).nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CouldntFindFunctionToCallT(
        tz,
        FindFunctionFailure(
          CodeNameS(StrI("someFunc")),
          Vector(),
          Map()))).nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CouldntFindFunctionToCallT(
        tz,
        FindFunctionFailure(CodeNameS(interner.intern(StrI(""))), Vector(), Map())))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CannotSubscriptT(
        tz,
        fireflyKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CouldntFindIdentifierToLoadT(
        tz,
        CodeNameS(StrI("spaceship"))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CouldntFindMemberT(
        tz,
        "hp"))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      BodyResultDoesntMatch(
        tz,
        FunctionNameS(StrI("myFunc"), CodeLocationS.testZero(interner)), fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CouldntConvertForReturnT(
        tz,
        fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CouldntConvertForMutateT(
        tz,
        fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CouldntConvertForMutateT(
        tz,
        fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CantMoveOutOfMemberT(
        tz,
        CodeVarNameT(StrI("hp"))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CantUseUnstackifiedLocal(
        tz,
        CodeVarNameT(StrI("firefly"))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CantUnstackifyOutsideLocalFromInsideWhile(
        tz,
        CodeVarNameT(StrI("firefly"))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      FunctionAlreadyExists(tz.head, tz.head, fireflySignature.id))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CantMutateFinalMember(
        tz,
        serenityKind,
        CodeVarNameT(StrI("bork"))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      LambdaReturnDoesntMatchInterfaceConstructor(
        tz))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      IfConditionIsntBoolean(
        tz, fireflyCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      WhileConditionIsntBoolean(
        tz, fireflyCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CantImplNonInterface(
        tz, KindTemplataT(fireflyKind)))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      ImmStructCantHaveVaryingMember(
        tz, TopLevelStructDeclarationNameS(interner.intern(StrI("SpaceshipSnapshot")), tz.head), "fuel"))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CantDowncastUnrelatedTypes(
        tz, ispaceshipKind, unrelatedKind, Vector()))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      CantDowncastToInterface(
        tz, ispaceshipKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      ExportedFunctionDependedOnNonExportedKind(
        tz, PackageCoordinate.TEST_TLD(interner, keywords), fireflySignature, fireflyKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      ExportedImmutableKindDependedOnNonExportedKind(
        tz, PackageCoordinate.TEST_TLD(interner, keywords), serenityKind, fireflyKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      ExternFunctionDependedOnNonExportedKind(
        tz, PackageCoordinate.TEST_TLD(interner, keywords), fireflySignature, fireflyKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      TypeExportedMultipleTimes(
        tz, PackageCoordinate.TEST_TLD(interner, keywords), Vector(fireflyExport, serenityExport)))
      .nonEmpty)
//    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
//      NotEnoughToSolveError(
//        tz,
//        Map(
//          CodeRuneS(StrI("X")) -> KindTemplata(fireflyKind)),
//        Vector(CodeRuneS(StrI("Y")))))
//      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, humanizePos, linesBetween, lineRangeContaining, lineContaining,
      TypingPassSolverError(
        tz,
        FailedCompilerSolve(
          Vector(
            Step[IRulexSR, IRuneS, ITemplataT[ITemplataType]](
              false,
              Vector(),
              Vector(),
              Map(
                CodeRuneS(StrI("X")) -> KindTemplataT(fireflyKind)))).toStream,
          Vector(),
          RuleError(KindIsNotConcrete(ispaceshipKind)))))
      .nonEmpty)
  }

  test("Report when multiple types in array") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() int {
        |  arr = [#](true, 42);
        |  return arr.1;
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ArrayElementsHaveDifferentTypes(_, types)) => {
        types shouldEqual Set(CoordT(ShareT, GlobalRegionT(), IntT.i32), CoordT(ShareT, GlobalRegionT(), BoolT()))
      }
    }
  }

  test("Report when abstract method defined outside open interface") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |interface IBlah { }
        |abstract func bork(virtual moo &IBlah);
        |exported func main() {
        |  bork(__vbi_panic());
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(AbstractMethodOutsideOpenInterface(_)) =>
    }
  }

  test("Report when imm struct has varying member") {
    // https://github.com/ValeLang/Vale/issues/131
    val compile = CompilerTestCompilation.test(
      """
        |struct Spaceship imm {
        |  name! str;
        |  numWings int;
        |}
        |exported func main() {
        |  ship = Spaceship("Serenity", 2);
        |  println(ship.name);
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ImmStructCantHaveVaryingMember(_, _, _)) =>
    }
  }

  test("Report imm mut mismatch for generic type") {
    val compile = CompilerTestCompilation.test(
      """
        |struct MyImmContainer<T Ref> imm
        |where func drop(T)void { value T; }
        |struct MyMutStruct { }
        |exported func main() { x = MyImmContainer<MyMutStruct>(MyMutStruct()); }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ImmStructCantHaveMutableMember(_, _, _)) =>
    }
  }

  test("Tests stamping a struct and its implemented interface from a function param") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |import v.builtins.drop.*;
        |import panicutils.*;
        |sealed interface MyOption<T Ref> where func drop(T)void { }
        |struct MySome<T Ref> where func drop(T)void { value T; }
        |impl<T> MyOption<T> for MySome<T> where func drop(T)void;
        |func moo(a MySome<int>) { }
        |exported func main() { moo(__pretend<MySome<int>>()); }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner
    val keywords = compile.keywords

    val interface =
      coutputs.lookupInterfaceByTemplateName(
        interner.intern(InterfaceTemplateNameT(interner.intern(StrI("MyOption")))))

    val struct =
      coutputs.lookupStructByTemplateName(
        interner.intern(StructTemplateNameT(interner.intern(StrI("MySome")))))

    coutputs.lookupImpl(struct.instantiatedCitizen.id, interface.instantiatedInterface.id)
  }

  test("Report when imm contains varying member") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Spaceship imm {
        |  name! str;
        |  numWings int;
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ImmStructCantHaveVaryingMember(_,TopLevelStructDeclarationNameS(StrI("Spaceship"),_),"name")) =>
    }
  }

  test("Reports when ownership doesnt match") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |struct Firefly {}
        |func getFuel(self &Firefly) int { return 7; }
        |
        |exported func main() int {
        |  f = Firefly();
        |  return (f).getFuel();
        |}
        |""".stripMargin
    )
    compile.getCompilerOutputs() match {
      case Err(CouldntFindFunctionToCallT(range, fff)) => {
        fff.name match { case CodeNameS(StrI("getFuel")) => }
        fff.rejectedCalleeToReason.size shouldEqual 1
        val reason = fff.rejectedCalleeToReason.head._2
        reason match {
          case InferFailure(FailedCompilerSolve(_,_,RuleError(OwnershipDidntMatch(CoordT(OwnT,_, _),BorrowT)))) =>
          //          case SpecificParamDoesntSend(0, _, _) =>
          case other => vfail(other)
        }
      }
    }
  }

  test("Test imm array") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |import v.builtins.drop.*;
        |export #[]int as ImmArrInt;
        |exported func main(arr #[]int) {
        |  __vbi_panic();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    main.header.params.head.tyype.kind match { case contentsRuntimeSizedArrayTT(MutabilityTemplataT(ImmutableT), _) => }
  }


  test("Tests calling an abstract function") {
    val compile = CompilerTestCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/callingAbstract.vale"))
    val coutputs = compile.expectCompilerOutputs()

    coutputs.functions.collectFirst({
      case FunctionDefinitionT(header @ functionName("doThing"), _, _, _) if header.getAbstractInterface != None => true
    }).get
  }

  test("Test struct default generic argument in type") {
    val compile = CompilerTestCompilation.test(
      """
        |struct MyHashSet<K Ref, H Int = 5> { }
        |struct MyStruct {
        |  x MyHashSet<bool>();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupStruct("MyStruct")
    val tyype = Collector.only(moo, { case ReferenceMemberTypeT(c) => c })
    tyype match {
      case CoordT(
      OwnT,
      _,
      StructTT(
      IdT(_,_,
      StructNameT(
      StructTemplateNameT(StrI("MyHashSet")),
      Vector(
      CoordTemplataT(CoordT(ShareT,_,BoolT())),
      IntegerTemplataT(5)))))) =>
    }
  }

  test("Lock weak member") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.opt.*;
        |import v.builtins.weak.*;
        |import v.builtins.logic.*;
        |import v.builtins.drop.*;
        |import panicutils.*;
        |import printutils.*;
        |
        |struct Base {
        |  name str;
        |}
        |struct Spaceship {
        |  name str;
        |  origin &&Base;
        |}
        |func printShipBase(ship &Spaceship) {
        |  maybeOrigin = lock(ship.origin); «14»«15»
        |  if (not maybeOrigin.isEmpty()) { «16»
        |    o = maybeOrigin.get();
        |    println("Ship base: " + o.name);
        |  } else {
        |    println("Ship base unknown!");
        |  }
        |}
        |exported func main() {
        |  base = Base("Zion");
        |  ship = Spaceship("Neb", &&base);
        |  printShipBase(&ship);
        |  (base).drop(); // Destroys base.
        |  printShipBase(&ship);
        |}
        |""".stripMargin)

    compile.expectCompilerOutputs()
  }

  test("Failure to resolve a Prot rule's function doesnt halt") {
    // In the below example, it should disqualify the first foo() because T = bool
    // and there exists no moo(bool). Instead, we saw the Prot rule throw and halt
    // compilation.

    // Instead, we need to bubble up that failure to find the right function, so
    // it disqualifies the candidate and goes with the other one.

    CompilerTestCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |func moo(a str) { }
        |func foo<T>(f T) void where func drop(T)void, func moo(str)void { }
        |func foo<T>(f T) void where func drop(T)void, func moo(bool)void { }
        |func main() { foo("hello"); }
        |""".stripMargin).expectCompilerOutputs()
  }

  // See DSDCTD
  test("Tests destructuring shared doesnt compile to destroy") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |struct Vec3i imm {
        |  x int;
        |  y int;
        |  z int;
        |}
        |
        |exported func main() int {
        |	 Vec3i[x, y, z] = Vec3i(3, 4, 5);
        |  return y;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    Collector.all(coutputs.lookupFunction("main"), {
      case DestroyTE(_, _, _) =>
    }).size shouldEqual 0

//    // Make sure there's a destroy in its destructor though.
//    val destructor =
//      vassertOne(
//        coutputs.functions.collect({
//          case f if (f.header.fullName.last match { case FreeNameT(_, _, _) => true case _ => false }) => f
//        }))
//
//    Collector.only(destructor, { case DestroyTE(referenceExprResultStructName(StrI("Vec3i")), _, _) => })
//    Collector.all(destructor, { case DiscardTE(referenceExprResultKind(IntT(_))) => }).size shouldEqual 3
  }


  test("Generates free function for imm struct") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Vec3i imm {
        |  x int;
        |  y int;
        |  z int;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

//    // Make sure there's a destroy in its destructor though.
//    val freeFunc =
//      vassertOne(
//        coutputs.functions.collect({
//          case f if (f.header.fullName.last match { case FreeNameT(_, _, _) => true case _ => false }) => f
//        }))
//
//    Collector.only(freeFunc, { case DestroyTE(referenceExprResultStructName(StrI("Vec3i")), _, _) => })
//    Collector.all(freeFunc, { case DiscardTE(referenceExprResultKind(IntT(_))) => }).size shouldEqual 3
  }

  test("Reports when exported SSA depends on non-exported element") {
    val compile = CompilerTestCompilation.test(
      """
        |export [#5]<imm>Raza as RazaArray;
        |struct Raza imm { }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when exported RSA depends on non-exported element") {
    val compile = CompilerTestCompilation.test(
      """
        |export []<imm>Raza as RazaArray;
        |struct Raza imm { }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Imm generic can contain imm thing") {
    val compile = CompilerTestCompilation.test(
      """
        |struct MyImmContainer<T Ref imm> imm
        |where func drop(T)void { value T; }
        |struct MyMutStruct { }
        |exported func main() { x = MyImmContainer<MyMutStruct>(MyMutStruct()); }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test MakeArray") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |import v.builtins.arith.*;
        |import array.make.*;
        |import v.builtins.arrays.*;
        |import v.builtins.drop.*;
        |
        |exported func main() int {
        |  a = MakeArray<int>(11, {_});
        |  return len(&a);
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test array push, pop, len, capacity, drop") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.arrays.*;
        |import v.builtins.drop.*;
        |
        |exported func main() void {
        |  arr = Array<mut, int>(9);
        |  arr.push(420);
        |  arr.push(421);
        |  arr.push(422);
        |  arr.len();
        |  arr.capacity();
        |  // implicit drop with pops
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Upcast generic") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |interface IShip {}
        |
        |struct Raza { fuel int; }
        |impl IShip for Raza;
        |
        |func doUpcast<T>(x T) IShip
        |where implements(T, IShip) {
        |  i IShip = x;
        |  return i;
        |}
        |
        |exported func main() {
        |  doUpcast(Raza(42));
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val doUpcast = coutputs.lookupFunction("doUpcast")
    Collector.only(doUpcast, {
      case UpcastTE(sourceExpr, targetSuperKind, _) => {
        sourceExpr.result.coord.kind match {
          case KindPlaceholderT(_) =>
        }
        targetSuperKind match {
          case InterfaceTT(IdT(_, Vector(),InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")),Vector()))) =>
        }
      }
    })
  }

  test("Downcast function, RRBFS") {
    // Here we had something interesting happen: the complex solve had a race with the thing that
    // populates identifying runes.
    // Populating identifying runes only happens after the solver has done as much as it possibly
    // can... but the solver sometimes takes a leap (as part of CSALR, SMCMST) to figure out the best type
    // to meet some requirements.
    // The solution was to make it only do that leap when solving call sites.
    // See RRBFS.
    val compile = CompilerTestCompilation.test(
      """
        |
        |#!DeriveInterfaceDrop
        |sealed interface Result<OkType Ref, ErrType Ref> { }
        |
        |#!DeriveStructDrop
        |struct Ok<OkType Ref, ErrType Ref> { value OkType; }
        |
        |impl<OkType, ErrType> Result<OkType, ErrType> for Ok<OkType, ErrType>;
        |
        |#!DeriveStructDrop
        |struct Err<OkType Ref, ErrType Ref> { value ErrType; }
        |
        |impl<OkType, ErrType> Result<OkType, ErrType> for Err<OkType, ErrType>;
        |
        |
        |extern("vale_as_subtype")
        |func as<SubType Ref, SuperType Ref>(left &SuperType) Result<&SubType, &SuperType>
        |where implements(SubType, SuperType);
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val asFunc =
      vassertOne(
        coutputs.functions.filter({
          case FunctionDefinitionT(FunctionHeaderT(IdT(_, _, FunctionNameT(FunctionTemplateNameT(StrI("as"), _), _, Vector(CoordT(BorrowT, _, _)))), _, _, _, _), _, _, _) => true
          case _ => false
        }))
    val as = Collector.only(asFunc, { case as@AsSubtypeTE(_, _, _, _, _, _, _, _) => as })
    val AsSubtypeTE(sourceExpr, targetSubtype, resultOptType, okConstructor, errConstructor, _, _, _) = as
    sourceExpr.result.coord match {
      case CoordT(BorrowT,_, KindPlaceholderT(IdT(_,Vector(FunctionTemplateNameT(StrI("as"),_)),KindPlaceholderNameT(KindPlaceholderTemplateNameT(1, _))))) =>
      //case CoordT(BorrowT, InterfaceTT(FullNameT(_, Vector(), InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")), Vector())))) =>
    }
    targetSubtype.kind match {
      case KindPlaceholderT(IdT(_,Vector(FunctionTemplateNameT(StrI("as"),_)),KindPlaceholderNameT(KindPlaceholderTemplateNameT(0, _)))) =>
      case StructTT(IdT(_, Vector(), StructNameT(StructTemplateNameT(StrI("Raza")), Vector()))) =>
    }
    val (firstGenericArg, secondGenericArg) =
      resultOptType match {
        case CoordT(
        OwnT,
        _,
        InterfaceTT(
        IdT(
        _, Vector(),
        InterfaceNameT(
        InterfaceTemplateNameT(StrI("Result")),
        Vector(firstGenericArg, secondGenericArg))))) => (firstGenericArg, secondGenericArg)
      }
    // They should both be pointers, since we dont really do borrows in structs yet
    firstGenericArg match {
      case CoordTemplataT(
      CoordT(
      BorrowT,
      _,
      KindPlaceholderT(
      IdT(_,Vector(FunctionTemplateNameT(StrI("as"),_)),KindPlaceholderNameT(KindPlaceholderTemplateNameT(0, _)))))) =>
    }
    secondGenericArg match {
      case CoordTemplataT(
      CoordT(
      BorrowT,
      _,
      KindPlaceholderT(IdT(_,Vector(FunctionTemplateNameT(StrI("as"),_)),KindPlaceholderNameT(KindPlaceholderTemplateNameT(1, _)))))) =>
    }
    vassert(okConstructor.paramTypes.head == targetSubtype)
    vassert(errConstructor.paramTypes.head == sourceExpr.result.coord)
  }

  test("Downcast with as") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.as.*;
        |import v.builtins.logic.*;
        |import v.builtins.drop.*;
        |
        |interface IShip {}
        |
        |struct Raza { fuel int; }
        |impl IShip for Raza;
        |
        |exported func main() {
        |  ship IShip = Raza(42);
        |  ship.as<Raza>();
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    {
      val mainFunc = coutputs.lookupFunction("main")
      val (asPrototype, asArg) =
        Collector.only(mainFunc, {
          case FunctionCallTE(
          prototype @ PrototypeT(IdT(_,Vector(),FunctionNameT(FunctionTemplateNameT(StrI("as"),_),_,_)), _),
          Vector(arg)) => {
            (prototype, arg)
          }
        })
      val (asPrototypeTemplateArgs, asPrototypeParams, asPrototypeReturn) =
        asPrototype match {
          case PrototypeT(IdT(_,Vector(),FunctionNameT(_, templateArgs, params)), retuurn) => {
            (templateArgs, params, retuurn)
          }
        }

      asPrototypeTemplateArgs match {
        case Vector(CoordTemplataT(CoordT(OwnT, _, StructTT(IdT(_,Vector(),StructNameT(StructTemplateNameT(StrI("Raza")),Vector()))))), CoordTemplataT(CoordT(OwnT, _, InterfaceTT(IdT(_,Vector(),InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")),Vector())))))) =>
      }

      asPrototypeParams match {
        case Vector(CoordT(BorrowT,_, InterfaceTT(IdT(_,Vector(),InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")),Vector()))))) =>
      }

      asPrototypeReturn match {
        case CoordT(
        OwnT,
        _,
        InterfaceTT(
        IdT(
        _,
        Vector(),
        InterfaceNameT(
        InterfaceTemplateNameT(StrI("Result")),
        Vector(
        CoordTemplataT(CoordT(BorrowT,_,StructTT(IdT(_,Vector(),StructNameT(StructTemplateNameT(StrI("Raza")),Vector()))))),
        CoordTemplataT(CoordT(BorrowT,_,InterfaceTT(IdT(_,Vector(),InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")),Vector())))))))))) =>
      }

      asArg.result.coord match {
        case CoordT(BorrowT,_, InterfaceTT(IdT(_,Vector(),InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")),Vector())))) =>
      }
    }

    {
      val asFunc =
        vassertOne(
          coutputs.functions.filter({
            case FunctionDefinitionT(FunctionHeaderT(IdT(_, _, FunctionNameT(FunctionTemplateNameT(StrI("as"), _), _, Vector(CoordT(BorrowT, _,_)))), _, _, _, _), _, _, _) => true
            case _ => false
          }))
      val as = Collector.only(asFunc, { case as@AsSubtypeTE(_, _, _, _, _, _, _, _) => as })
      val AsSubtypeTE(sourceExpr, targetSubtype, resultOptType, okConstructor, errConstructor, _, _, _) = as
      sourceExpr.result.coord match {
        case CoordT(BorrowT,_, KindPlaceholderT(IdT(_,Vector(FunctionTemplateNameT(StrI("as"),_)),KindPlaceholderNameT(KindPlaceholderTemplateNameT(1, _))))) =>
        //case CoordT(BorrowT, InterfaceTT(FullNameT(_, Vector(), InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")), Vector())))) =>
      }
      targetSubtype.kind match {
        case KindPlaceholderT(IdT(_,Vector(FunctionTemplateNameT(StrI("as"),_)),KindPlaceholderNameT(KindPlaceholderTemplateNameT(0, _)))) =>
        case StructTT(IdT(_, Vector(), StructNameT(StructTemplateNameT(StrI("Raza")), Vector()))) =>
      }
      val (firstGenericArg, secondGenericArg) =
        resultOptType match {
          case CoordT(
          OwnT,
          _,
          InterfaceTT(
          IdT(
          _, Vector(),
          InterfaceNameT(
          InterfaceTemplateNameT(StrI("Result")),
          Vector(firstGenericArg, secondGenericArg))))) => (firstGenericArg, secondGenericArg)
        }
      // They should both be pointers, since we dont really do borrows in structs yet
      firstGenericArg match {
        case CoordTemplataT(
        CoordT(
        BorrowT,
        _,
        KindPlaceholderT(
        IdT(_,Vector(FunctionTemplateNameT(StrI("as"),_)),KindPlaceholderNameT(KindPlaceholderTemplateNameT(0, _)))))) =>
      }
      secondGenericArg match {
        case CoordTemplataT(
        CoordT(
        BorrowT,
        _,
        KindPlaceholderT(IdT(_,Vector(FunctionTemplateNameT(StrI("as"),_)),KindPlaceholderNameT(KindPlaceholderTemplateNameT(1, _)))))) =>
      }
      vassert(okConstructor.paramTypes.head == targetSubtype)
      vassert(errConstructor.paramTypes.head == sourceExpr.result.coord)
    }
  }

  test("Closure using parent function's bound") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.arith.*;
        |
        |func genFunc<T>(a &T) T
        |where func +(&T, &T)T {
        |  { a + a }()
        |}
        |exported func main() int {
        |  genFunc(7)
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }
}
