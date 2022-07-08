package dev.vale.typing

import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.infer.KindIsNotConcrete
import dev.vale.{CodeLocationS, Collector, Err, FileCoordinateMap, Ok, PackageCoordinate, RangeS, Tests, vassert, vassertOne, vpass, vwat}
import dev.vale.parsing.ParseErrorHumanizer
import dev.vale.postparsing.PostParser
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale._
import dev.vale.highertyping.HigherTypingCompilation
import dev.vale.solver.RuleError
import OverloadResolver.{FindFunctionFailure, SpecificParamDoesntSend, WrongNumberOfArguments}
import dev.vale.postparsing.{CodeNameS, CodeRuneS, FunctionNameS, TopLevelCitizenDeclarationNameS}
import dev.vale.solver.{FailedSolve, RuleError, Step}
import dev.vale.typing.ast.{ConstantIntTE, DestroyTE, DiscardTE, FunctionCallTE, FunctionHeaderT, FunctionT, KindExportT, LetAndLendTE, LetNormalTE, LocalLookupTE, ParameterT, PrototypeT, ReferenceMemberLookupTE, ReturnTE, SignatureT, SoftLoadTE, StructToInterfaceUpcastTE, UserFunctionT, referenceExprResultKind, referenceExprResultStructName}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, CodeVarNameT, FreeNameT, FullNameT, FunctionNameT}
import dev.vale.typing.templata.{CoordTemplata, KindTemplata, functionName, simpleName}
import dev.vale.typing.types.{BoolT, BorrowT, CoordT, FinalT, IntT, InterfaceDefinitionT, InterfaceTT, MutableT, NeverT, OwnT, ReferenceMemberTypeT, ShareT, StructDefinitionT, StructMemberT, StructTT, VoidT}
import dev.vale.typing.ast._
import dev.vale.typing.names.CitizenTemplateNameT
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest.{FunSuite, Matchers, _}

import scala.collection.immutable.List
import scala.io.Source

class CompilerTests extends FunSuite with Matchers {
  // TODO: pull all of the typingpass specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }

  test("Simple program returning an int, inferred") {
    val compile =
      CompilerTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |func main() infer-return { return 3; }
          |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionHeaderT(simpleName("main"),Vector(UserFunctionT),Vector(), CoordT(ShareT, IntT.i32), _) => true
    })
    Collector.only(main, { case ConstantIntTE(3, _) => true })
  }

  test("Simple program returning an int, explicit") {
    // We had a bug once looking up "int" in the environment, hence this test.

    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |func main() int { return 3; }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    main.header.returnType.kind shouldEqual IntT(32)
  }

  test("Hardcoding negative numbers") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported func main() int { return -3; }
        |""".stripMargin)
    val main = compile.expectCompilerOutputs().lookupFunction("main")
    Collector.only(main, { case ConstantIntTE(-3, _) => true })
  }

  test("Simple local") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported func main() infer-return {
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
        |import v.builtins.tup.*;
        |import v.builtins.panic.*;
        |exported func main() infer-return {
        |  __vbi_panic();
        |  a = 42;
        |}
    """.stripMargin)
    val main = compile.expectCompilerOutputs().lookupFunction("main")
    vassert(main.header.returnType.kind == NeverT(false))
  }

  test("Taking an argument and returning it") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |func main(a int) int { return a; }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    Collector.onlyOf(coutputs.lookupFunction("main"), classOf[ParameterT]).tyype == CoordT(ShareT, IntT.i32)
    val lookup = Collector.onlyOf(coutputs.lookupFunction("main"), classOf[LocalLookupTE]);
    lookup.localVariable.id.last match { case CodeVarNameT(StrI("a")) => }
    lookup.localVariable.reference match { case CoordT(ShareT, IntT.i32) => }
  }

  test("Tests adding two numbers") {
    val compile =
      CompilerTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |import v.builtins.arith.*;
          |exported func main() int { return +(2, 3); }
          |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, { case ConstantIntTE(2, _) => true })
    Collector.only(main, { case ConstantIntTE(3, _) => true })
    Collector.only(main, {
      case FunctionCallTE(
        functionName("+"),
        Vector(
          ConstantIntTE(2, _),
          ConstantIntTE(3, _))) =>
    })
  }

  test("Simple struct read") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported struct Moo { hp int; }
        |exported func main(moo &Moo) int {
        |  return moo.hp;
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
  }

  test("Simple struct instantiate") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
        |exported struct Moo { hp int; }
        |exported func main() int {
        |  return Moo(42).hp;
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionCallTE(PrototypeT(FullNameT(_, _, FunctionNameT(StrI("drop"), _, _)), _), _) =>
    })
  }

  test("Custom destructor") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
      case FunctionCallTE(PrototypeT(FullNameT(_, _, FunctionNameT(StrI("drop"), _, _)), _), _) =>
    })
  }

  test("Make constraint reference") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        case LetNormalTE(ReferenceLocalVariableT(FullNameT(_, _, CodeVarNameT(StrI("b"))), _, tyype), _) => tyype
      })
    tyype.ownership shouldEqual BorrowT
  }



  test("Recursion") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported func main() int { return main(); }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Make sure it inferred the param type and return type correctly
    coutputs.lookupFunction("main").header.returnType shouldEqual CoordT(ShareT, IntT.i32)
  }

  test("Simple lambda") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported func main() int { return { 7 }(); }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Make sure it inferred the param type and return type correctly
    coutputs.lookupFunction("__call").header.returnType shouldEqual CoordT(ShareT, IntT.i32)
    coutputs.lookupFunction("main").header.returnType shouldEqual CoordT(ShareT, IntT.i32)
  }

  test("Lambda with one magic arg") {
    val compile =
      CompilerTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |exported func main() int { return {_}(3); }
          |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Make sure it inferred the param type and return type correctly
    Collector.only(coutputs.lookupLambdaIn("main"),
        { case ParameterT(_, None, CoordT(ShareT, IntT.i32)) => })

    coutputs.lookupLambdaIn("main").header.returnType shouldEqual
        CoordT(ShareT, IntT.i32)
  }


  // Test that the lambda's arg is the right type, and the name is right
  test("Lambda with a type specified param") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arith.*;
        |exported func main() int {
        |  return (a int) => {+(a,a)}(3);
        |}
        |""".stripMargin);
    val coutputs = compile.expectCompilerOutputs()

    val lambda = coutputs.lookupLambdaIn("main");

    // Check that the param type is right
    Collector.only(lambda, { case ParameterT(CodeVarNameT(StrI("a")), None, CoordT(ShareT, IntT.i32)) => {} })
    // Check the name is right
    vassert(coutputs.nameIsLambdaIn(lambda.header.fullName, "main"))

    val main = coutputs.lookupFunction("main");
    Collector.only(main, { case FunctionCallTE(callee, _) if coutputs.nameIsLambdaIn(callee.fullName, "main") => })
  }

  test("Test overloads") {
    val compile = CompilerTestCompilation.test(Tests.loadExpected("programs/functions/overloads.vale"))
    val coutputs = compile.expectCompilerOutputs()

    coutputs.lookupFunction("main").header.returnType shouldEqual
      CoordT(ShareT, IntT.i32)
  }

  test("Test readonly UFCS") {
    val compile = CompilerTestCompilation.test(Tests.loadExpected("programs/ufcs.vale"))
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test readwrite UFCS") {
    val compile = CompilerTestCompilation.test(Tests.loadExpected("programs/readwriteufcs.vale"))
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Report when imm struct has varying member") {
    // https://github.com/ValeLang/Vale/issues/131
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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

  test("Test templates") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |func bork<T>(a T, b T)T{ return a; }
        |exported func main() int {true bork false; 2 bork 2; return 3 bork 3;}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Tests that we reuse existing stamps
    vassert(coutputs.getAllUserFunctions.size == 3)
  }

  test("Test taking a callable param") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |func do<F>(callable F) infer-return { return callable(); }
        |exported func main() int { return do({ return 3; }); }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    coutputs.functions.collect({ case x @ functionName("do") => x }).head.header.returnType shouldEqual CoordT(ShareT, IntT.i32)
  }

  test("Calls destructor on local var") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
    Collector.only(main, { case FunctionCallTE(PrototypeT(FullNameT(_, _, FunctionNameT(StrI("drop"), _, _)), _), _) => })
    Collector.all(main, { case FunctionCallTE(_, _) => }).size shouldEqual 2
  }

  test("Stamps an interface template via a function return") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface MyInterface<X> where X Ref { }
        |
        |struct SomeStruct<X> where X Ref { x X; }
        |impl<X> MyInterface<X> for SomeStruct<X>;
        |
        |func doAThing<T>(t T) SomeStruct<T> {
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
        |import v.builtins.tup.*;
        |struct MyStruct { a int; }
        |exported func main() int { ms = MyStruct(7); return ms.a; }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Check the struct was made
    coutputs.structs.collectFirst({
      case StructDefinitionT(
      simpleName("MyStruct"),
      _,
      _,
      false,
      MutableT,
      Vector(StructMemberT(CodeVarNameT(StrI("a")), FinalT, ReferenceMemberTypeT(CoordT(ShareT, IntT.i32)))),
      false) =>
    }).get
    // Check there's a constructor
    Collector.all(coutputs.lookupFunction("MyStruct"), {
      case FunctionHeaderT(
      simpleName("MyStruct"),
      _,
      Vector(ParameterT(CodeVarNameT(StrI("a")), None, CoordT(ShareT, IntT.i32))),
      CoordT(OwnT, StructTT(simpleName("MyStruct"))),
      _) =>
    })
    val main = coutputs.lookupFunction("main")
    // Check that we call the constructor
    Collector.only(main, {
      case FunctionCallTE(
        PrototypeT(simpleName("MyStruct"), _),
        Vector(ConstantIntTE(7, _))) =>
    })
  }

  test("Tests defining a non-empty interface and an implementing struct") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported interface MyInterface {
        |  func bork(virtual self &MyInterface);
        |}
        |exported struct MyStruct { }
        |impl MyInterface for MyStruct;
        |func bork(self &MyStruct) {}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val (interfaceDef, method) =
      vassertOne(coutputs.interfaces.collectFirst({
        case id @ InterfaceDefinitionT(simpleName("MyInterface"), _, _, false, MutableT, Vector(method)) => (id, method)
      }))

    val structDef =
      vassertOne(coutputs.structs.collectFirst({
        case sd @ StructDefinitionT(simpleName("MyStruct"), _, _, false, MutableT, _, false) => sd
      }))

    vassert(coutputs.edges.exists(impl => {
      impl.struct == structDef.getRef && impl.interface == interfaceDef.getRef
    }))
  }

  test("Tests defining an empty interface and an implementing struct") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface MyInterface { }
        |struct MyStruct { }
        |impl MyInterface for MyStruct;
        |func main(a MyStruct) {}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val interfaceDef =
      vassertOne(coutputs.interfaces.collectFirst({
        case id @ InterfaceDefinitionT(simpleName("MyInterface"), _, _, false, MutableT, Vector()) => id
      }))

    val structDef =
      vassertOne(coutputs.structs.collectFirst({
        case sd @ StructDefinitionT(simpleName("MyStruct"), _, _, false, MutableT, _, false) => sd
      }))

    vassert(coutputs.edges.exists(impl => {
      impl.struct == structDef.getRef && impl.interface == interfaceDef.getRef
    }))
  }

  test("Tests stamping an interface template from a function param") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface MyOption<T> where T Ref { }
        |func main(a MyOption<int>) { }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner
    val keywords = compile.keywords

    coutputs.lookupInterface(
      interner.intern(
        InterfaceTT(
          FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), interner.intern(CitizenNameT(interner.intern(CitizenTemplateNameT(interner.intern(StrI("MyOption")))), Vector(CoordTemplata(CoordT(ShareT, IntT.i32)))))))))
    vassert(coutputs.lookupFunction("main").header.params.head.tyype ==
        CoordT(OwnT,
          interner.intern(
            InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), interner.intern(CitizenNameT(interner.intern(CitizenTemplateNameT(interner.intern(StrI("MyOption")))), Vector(CoordTemplata(CoordT(ShareT, IntT.i32))))))))))

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
        CoordT(ShareT,VoidT()),
        CoordT(ShareT,IntT(_))) =>
    }
  }

  test("Tests exporting function") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
        |exported struct Moo { a int; }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupStruct("Moo")
    val export = vassertOne(coutputs.kindExports)
    `export`.tyype shouldEqual moo.getRef
  }

  test("Tests exporting interface") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported interface IMoo { func hi(virtual this &IMoo) void; }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupInterface("IMoo")
    val export = vassertOne(coutputs.kindExports)
    `export`.tyype shouldEqual moo.getRef
  }

  test("Tests stamping a struct and its implemented interface from a function param") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.panic.*;
        |import panicutils.*;
        |interface MyOption<T> imm where T Ref { }
        |struct MySome<T> imm where T Ref { value T; }
        |impl<T> MyOption<T> for MySome<T>;
        |func moo(a MySome<int>) { }
        |exported func main() { moo(__pretend<MySome<int>>()); }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner
    val keywords = compile.keywords

    val interface =
      coutputs.lookupInterface(
        interner.intern(
          InterfaceTT(
            FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), interner.intern(CitizenNameT(interner.intern(CitizenTemplateNameT(interner.intern(StrI("MyOption")))), Vector(CoordTemplata(CoordT(ShareT, IntT.i32)))))))))

    val struct =
      coutputs.lookupStruct(
        interner.intern(
          StructTT(FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), interner.intern(CitizenNameT(interner.intern(CitizenTemplateNameT(interner.intern(StrI("MySome")))), Vector(CoordTemplata(CoordT(ShareT, IntT.i32)))))))))

    coutputs.lookupImpl(struct.getRef, interface.getRef)
  }

  test("Tests single expression and single statement functions' returns") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct MyThing { value int; }
        |func moo() MyThing { return MyThing(4); }
        |exported func main() { moo(); }
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupFunction("moo")
    moo.header.returnType match {
      case CoordT(OwnT,StructTT(simpleName("MyThing"))) =>
    }
    val main = coutputs.lookupFunction("main")
    main.header.returnType match {
      case CoordT(ShareT, VoidT()) =>
    }
  }

  test("Tests calling a templated struct's constructor") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct MySome<T> where T Ref { value T; }
        |exported func main() int {
        |  return MySome<int>(4).value;
        |}
        |""".stripMargin
    )

    val coutputs = compile.expectCompilerOutputs()
    val interner = compile.interner
    val keywords = compile.keywords

    coutputs.lookupStruct(
      interner.intern(
        StructTT(FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), interner.intern(CitizenNameT(interner.intern(CitizenTemplateNameT(interner.intern(StrI("MySome")))), Vector(CoordTemplata(CoordT(ShareT, IntT.i32)))))))))

    val constructor = coutputs.lookupFunction("MySome")
    constructor.header match {
      case
        FunctionHeaderT(
        simpleName("MySome"),
        _,
        _,
        CoordT(OwnT,StructTT(FullNameT(_, Vector(), CitizenNameT(CitizenTemplateNameT(StrI("MySome")), Vector(CoordTemplata(CoordT(ShareT, IntT.i32))))))),
        _) =>
    }

    Collector.all(coutputs.lookupFunction("main"), {
      case FunctionCallTE(functionName("MySome"), _) =>
    })
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = CompilerTestCompilation.test(readCodeFromResource("programs/virtuals/upcasting.vale"))
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")

    Collector.only(main, { case LetNormalTE(ReferenceLocalVariableT(FullNameT(_,_,CodeVarNameT(StrI("x"))),FinalT,CoordT(OwnT,InterfaceTT(simpleName("MyInterface")))), _) => })

    val upcast = Collector.onlyOf(main, classOf[StructToInterfaceUpcastTE])
    upcast.result.reference match { case CoordT(OwnT,InterfaceTT(FullNameT(x, Vector(), CitizenNameT(CitizenTemplateNameT(StrI("MyInterface")), Vector())))) => vassert(x.isTest) }
    upcast.innerExpr.result.reference match { case CoordT(OwnT,StructTT(FullNameT(x, Vector(), CitizenNameT(CitizenTemplateNameT(StrI("MyStruct")), Vector())))) => vassert(x.isTest) }
  }

  test("Tests calling a virtual function") {
    val compile = CompilerTestCompilation.test(readCodeFromResource("programs/virtuals/calling.vale"))
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case up @ StructToInterfaceUpcastTE(innerExpr, InterfaceTT(simpleName("Car"))) => {
        Collector.only(innerExpr.result, {
          case StructTT(simpleName("Toyota")) =>
        })
        up.result.reference.kind match { case InterfaceTT(FullNameT(x, Vector(), CitizenNameT(CitizenTemplateNameT(StrI("Car")), Vector()))) => vassert(x.isTest) }
      }
    })
  }

  test("Tests calling a virtual function through a borrow ref") {
    val compile = CompilerTestCompilation.test(readCodeFromResource("programs/virtuals/callingThroughBorrow.vale"))
    val coutputs = compile.expectCompilerOutputs()

    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case f @ FunctionCallTE(PrototypeT(simpleName("doCivicDance"),CoordT(ShareT,IntT.i32)), _) => {
//        vassert(f.callable.paramTypes == Vector(Coord(Borrow,InterfaceRef2(simpleName("Car")))))
      }
    })
  }

  test("Tests calling a templated function with explicit template args") {
    // Tests putting MyOption<int> as the type of x.
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
  test("Tests destructuring shared doesnt compile to destroy") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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

    // Make sure there's a destroy in its destructor though.
    val destructor =
      vassertOne(
        coutputs.functions.collect({
          case f if (f.header.fullName.last match { case FreeNameT(_, _) => true case _ => false }) => f
        }))

    Collector.only(destructor, { case DestroyTE(referenceExprResultStructName(StrI("Vec3i")), _, _) => })
    Collector.all(destructor, { case DiscardTE(referenceExprResultKind(IntT(_))) => }).size shouldEqual 3
  }

  // See DSDCTD
  test("Tests destructuring borrow doesnt compile to destroy") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        SoftLoadTE(LocalLookupTE(_, ReferenceLocalVariableT(_, FinalT, CoordT(_,StructTT(_)))), BorrowT),
        FullNameT(_, Vector(CitizenNameT(CitizenTemplateNameT(StrI("Vec3i")),Vector())),CodeVarNameT(StrI("x"))),CoordT(ShareT,IntT.i32),FinalT) =>
    })
  }

  test("Tests making a variable with a pattern") {
    // Tests putting MyOption<int> as the type of x.
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |interface MyOption<T> where T Ref { }
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
          |import v.builtins.tup.*;
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
      case StructToInterfaceUpcastTE(_, InterfaceTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT(StrI("ISpaceship")), _)))) =>
    })
  }

  test("Tests calling a templated function with an upcast") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
      case StructToInterfaceUpcastTE(_, InterfaceTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT(StrI("ISpaceship")), _)))) =>
    })
  }

  test("Tests a templated linked list") {
    val compile = CompilerTestCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/templatedlinkedlist.vale"))
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Tests calling an abstract function") {
    val compile = CompilerTestCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/callingAbstract.vale"))
    val coutputs = compile.expectCompilerOutputs()

    coutputs.functions.collectFirst({
      case FunctionT(header @ functionName("doThing"), _) if header.getAbstractInterface != None => true
    }).get
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

  // Make sure a ListNode struct made it out
  test("Templated imm struct") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct ListNode<T> imm where T Ref {
        |  tail ListNode<T>;
        |}
        |func main(a ListNode<int>) {}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Borrow-load member") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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

  test("Pointer-load member") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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

  test("fdsfsdf") {
    // This test is because we had a bug where & still produced a *!.
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Bork { }
        |func myFunc<F>(consumer &F) void { }
        |func main() {
        |  bork = Bork();
        |  myFunc(&{ bork });
        |}
        |
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test imm array") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |import v.builtins.drop.*;
        |import v.builtins.arrays.*;
        |import v.builtins.functor1.*;
        |export #[]int as ImmArrInt;
        |exported func main(arr #[]int) {
        |  __vbi_panic();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    main.header.params.head.tyype.kind match { case RuntimeSizedArrayTT(ImmutableT, _) => }
  }


  test("Test Array of StructTemplata") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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

  test("Test array push, pop, len, capacity, drop") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arith.*;
        |import array.make.*;
        |import v.builtins.arrays.*;
        |import v.builtins.drop.*;
        |import ifunction.ifunction1.*;
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

  test("Test MakeArray") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arith.*;
        |import array.make.*;
        |import v.builtins.arrays.*;
        |import v.builtins.drop.*;
        |import ifunction.ifunction1.*;
        |
        |exported func main() int {
        |  a = MakeArray(11, {_});
        |  return len(&a);
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test return") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
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
    Collector.only(main, { case ConstantIntTE(7, _) => })
    Collector.only(main, { case ConstantIntTE(9, _) => })
  }

  test("Test return from inside if destroys locals") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        case fpc @ FunctionCallTE(PrototypeT(FullNameT(_,Vector(CitizenNameT(CitizenTemplateNameT(StrI("Marine")),Vector())),FunctionNameT(StrI("drop"),Vector(),Vector(CoordT(_,StructTT(simpleName("Marine")))))),_),_) => fpc
      })
    destructorCalls.size shouldEqual 2
  }

  test("Test complex interface") {
    val compile = CompilerTestCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/templatedinterface.vale"))
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Lambda is incompatible anonymous interface") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface AFunction1<P> where P Ref {
        |  func __call(virtual this &AFunction1<P>, a P) int;
        |}
        |exported func main() {
        |  arr = AFunction1<int>((_) => { true });
        |}
        |""".stripMargin)

    compile.getCompilerOutputs() match {
      case Err(BodyResultDoesntMatch(_, _, _, _)) =>
      case Err(other) => vwat(CompilerErrorHumanizer.humanize(true, compile.getCodeMap().getOrDie(), other))
      case Ok(wat) => vwat(wat)
    }
  }
  test("Zero method anonymous interface") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface MyInterface {}
        |exported func main() {
        |  x = MyInterface();
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
        |func moo(a str) { }
        |func foo<T>(f T) void where func moo(str)void { }
        |func foo<T>(f T) void where func moo(bool)void { }
        |func main() { foo("hello"); }
        |""".stripMargin).expectCompilerOutputs()
  }




  test("Lock weak member") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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

  test("Lambda inside template") {
    // This originally didn't work because both helperFunc<int> and helperFunc<Str>
    // made a closure struct called helperFunc:lam1, which collided.
    // This is what spurred paackage support.

    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import printutils.*;
        |
        |func helperFunc<T>(x T) {
        |  { print(x); }();
        |}
        |exported func main() {
        |  helperFunc(4);
        |  helperFunc("bork");
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }


  test("Lambda inside different function with same name") {
    // This originally didn't work because both helperFunc(:Int) and helperFunc(:Str)
    // made a closure struct called helperFunc:lam1, which collided.
    // We need to disambiguate by parameters, not just template args.

    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import printutils.*;
        |
        |func helperFunc(x int) {
        |  { print(x); }();
        |}
        |func helperFunc(x str) {
        |  { print(x); }();
        |}
        |exported func main() {
        |  helperFunc(4);
        |  helperFunc("bork");
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Reports when exported function depends on non-exported param") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
        |import v.builtins.panic.*;
        |import panicutils.*;
        |struct Firefly { }
        |exported func moo() &Firefly { __pretend<&Firefly>() }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedFunctionDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when extern function depends on non-exported param") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
        |exported struct Firefly imm {
        |  raza Raza;
        |}
        |struct Raza imm { }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when exported RSA depends on non-exported element") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arrays.*;
        |import v.builtins.functor1.*;
        |export []<imm>Raza as RazaArray;
        |struct Raza imm { }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Checks that we stored a borrowed temporary in a local") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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

  test("Reports when exported SSA depends on non-exported element") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arrays.*;
        |import v.builtins.functor1.*;
        |export [#5]<imm>Raza as RazaArray;
        |struct Raza imm { }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when reading nonexistant local") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
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
        |import v.builtins.tup.*;
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
      case Err(CannotSubscriptT(_, StructTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT(StrI("Weapon")), Vector()))))) =>
    }
  }

  test("Reports when two functions with same signature") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported func moo() int { return 1337; }
        |exported func moo() int { return 1448; }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(FunctionAlreadyExists(_, _, SignatureT(FullNameT(_, Vector(), FunctionNameT(StrI("moo"), Vector(), Vector()))))) =>
    }
  }

  test("Reports when we give too many args") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
    val tz = RangeS.testZero(interner)

    val fireflyKind = StructTT(FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), CitizenNameT(CitizenTemplateNameT(StrI("Firefly")), Vector())))
    val fireflyCoord = CoordT(OwnT,fireflyKind)
    val serenityKind = StructTT(FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), CitizenNameT(CitizenTemplateNameT(StrI("Serenity")), Vector())))
    val serenityCoord = CoordT(OwnT,serenityKind)
    val ispaceshipKind = InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), CitizenNameT(CitizenTemplateNameT(StrI("ISpaceship")), Vector())))
    val ispaceshipCoord = CoordT(OwnT,ispaceshipKind)
    val unrelatedKind = StructTT(FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), CitizenNameT(CitizenTemplateNameT(StrI("Spoon")), Vector())))
    val unrelatedCoord = CoordT(OwnT,unrelatedKind)
    val fireflySignature = ast.SignatureT(FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), FunctionNameT(StrI("myFunc"), Vector(), Vector(fireflyCoord))))
    val fireflyExport = KindExportT(tz, fireflyKind, PackageCoordinate.TEST_TLD(interner, keywords), interner.intern(StrI("Firefly")));
    val serenityExport = KindExportT(tz, fireflyKind, PackageCoordinate.TEST_TLD(interner, keywords), interner.intern(StrI("Serenity")));

    val filenamesAndSources = FileCoordinateMap.test(interner, "blah blah blah\nblah blah blah")

    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindTypeT(tz, "Spaceship")).nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindFunctionToCallT(
        tz,
        FindFunctionFailure(
          CodeNameS(StrI("someFunc")),
          Vector(),
          Map()))).nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindFunctionToCallT(
        tz,
        FindFunctionFailure(CodeNameS(interner.intern(StrI(""))), Vector(), Map())))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CannotSubscriptT(
        tz,
        fireflyKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindIdentifierToLoadT(
        tz,
        CodeNameS(StrI("spaceship"))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindMemberT(
        tz,
        "hp"))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      BodyResultDoesntMatch(
        tz,
        FunctionNameS(StrI("myFunc"), CodeLocationS.testZero(interner)), fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntConvertForReturnT(
        tz,
        fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntConvertForMutateT(
        tz,
        fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntConvertForMutateT(
        tz,
        fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CantMoveOutOfMemberT(
        tz,
        CodeVarNameT(StrI("hp"))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CantUseUnstackifiedLocal(
        tz,
        CodeVarNameT(StrI("firefly"))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CantUnstackifyOutsideLocalFromInsideWhile(
        tz,
        CodeVarNameT(StrI("firefly"))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      FunctionAlreadyExists(tz, tz, fireflySignature))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CantMutateFinalMember(
        tz,
        serenityKind,
        FullNameT(PackageCoordinate.TEST_TLD(interner, keywords), Vector(), CodeVarNameT(StrI("bork")))))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      LambdaReturnDoesntMatchInterfaceConstructor(
        tz))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      IfConditionIsntBoolean(
        tz, fireflyCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      WhileConditionIsntBoolean(
        tz, fireflyCoord))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CantImplNonInterface(
        tz, fireflyKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      ImmStructCantHaveVaryingMember(
        tz, TopLevelCitizenDeclarationNameS(interner.intern(StrI("SpaceshipSnapshot")), tz), "fuel"))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CantDowncastUnrelatedTypes(
        tz, ispaceshipKind, unrelatedKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      CantDowncastToInterface(
        tz, ispaceshipKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      ExportedFunctionDependedOnNonExportedKind(
        tz, PackageCoordinate.TEST_TLD(interner, keywords), fireflySignature, fireflyKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      ExportedImmutableKindDependedOnNonExportedKind(
        tz, PackageCoordinate.TEST_TLD(interner, keywords), serenityKind, fireflyKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      ExternFunctionDependedOnNonExportedKind(
        tz, PackageCoordinate.TEST_TLD(interner, keywords), fireflySignature, fireflyKind))
      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      TypeExportedMultipleTimes(
        tz, PackageCoordinate.TEST_TLD(interner, keywords), Vector(fireflyExport, serenityExport)))
      .nonEmpty)
//    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
//      NotEnoughToSolveError(
//        tz,
//        Map(
//          CodeRuneS(StrI("X")) -> KindTemplata(fireflyKind)),
//        Vector(CodeRuneS(StrI("Y")))))
//      .nonEmpty)
    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      TypingPassSolverError(
        tz,
        FailedSolve(
          Vector(
            Step(
              false,
              Vector(),
              Vector(),
              Map(
                CodeRuneS(StrI("X")) -> KindTemplata(fireflyKind)))),
          Vector(),
          RuleError(KindIsNotConcrete(ispaceshipKind)))))
      .nonEmpty)
  }

  test("Report when downcasting between unrelated types") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.as.*;
        |import panicutils.*;
        |
        |interface ISpaceship { }
        |struct Spoon { }
        |
        |exported func main() {
        |  ship = __pretend<ISpaceship>();
        |  ship.as<Spoon>();
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(CantDowncastUnrelatedTypes(_, _, _)) =>
    }
  }

  test("Report when downcasting to interface") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.as.*;
        |import panicutils.*;
        |
        |interface ISuper { }
        |interface ISub { }
        |impl ISuper for ISub;
        |
        |exported func main() {
        |  ship = __pretend<ISuper>();
        |  ship.as<ISub>();
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(CantDowncastToInterface(_, _)) =>
    }
  }

  test("Report when multiple types in array") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported func main() int {
        |  arr = [#][true, 42];
        |  return arr.1;
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ArrayElementsHaveDifferentTypes(_, types)) => {
        types shouldEqual Set(CoordT(ShareT, IntT.i32), CoordT(ShareT, BoolT()))
      }
    }
  }

  test("Report when num elements mismatch") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Spaceship imm {
        |  name! str;
        |  numWings int;
        |}
        |exported func main() bool {
        |  arr = [#4][true, false, false];
        |  return arr.0;
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(InitializedWrongNumberOfElements(_, 4, 3)) =>
    }
  }

  test("Report when abstract method defined outside open interface") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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

  test("Reports when ownership doesnt match") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
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
        reason match { case SpecificParamDoesntSend(0, _, _) => }
      }
    }
  }
}
