package net.verdagon.vale.templar

import net.verdagon.vale.parser.{ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier}
import net.verdagon.vale.scout.{CodeNameS, CodeRuneS, CodeVarNameS, FunctionNameS, GlobalFunctionFamilyNameS, ICompileErrorS, ProgramS, Scout, TopLevelCitizenDeclarationNameS, VariableNameAlreadyExists}
import net.verdagon.vale.templar.env.ReferenceLocalVariableT
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale._
import net.verdagon.vale.astronomer.{Astronomer, AstronomerCompilation}
import net.verdagon.vale.solver.{FailedSolve, RuleError, Step}
import net.verdagon.vale.templar.OverloadTemplar.{FindFunctionFailure, SpecificParamDoesntSend, WrongNumberOfArguments}
import net.verdagon.vale.templar.ast.{ConstantIntTE, DestroyTE, DiscardTE, FunctionCallTE, FunctionHeaderT, FunctionT, KindExportT, LetAndLendTE, LetNormalTE, LocalLookupTE, ParameterT, PrototypeT, ReferenceExpressionTE, ReferenceMemberLookupTE, ReturnTE, SignatureT, SoftLoadTE, StructToInterfaceUpcastTE, UserFunctionT, referenceExprResultKind, referenceExprResultStructName}
import net.verdagon.von.{JsonSyntax, VonPrinter}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.infer.KindIsNotConcrete
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, CodeVarNameT, FreeNameT, FullNameT, FunctionNameT, FunctionTemplateNameT}
//import net.verdagon.vale.templar.infer.NotEnoughToSolveError
import org.scalatest.{FunSuite, Matchers, _}

import scala.collection.immutable.List
import scala.io.Source

class TemplarTests extends FunSuite with Matchers {
  // TODO: pull all of the templar specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }

  test("do not submit") {
    vfail()
    // 1. add an assert that all function bodies end in ret
  }

  test("Simple program returning an int, inferred") {
    val compile =
      TemplarTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |fn main() infer-ret { ret 3; }
          |""".stripMargin)
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionHeaderT(simpleName("main"),Vector(UserFunctionT),Vector(), CoordT(ShareT, ReadonlyT, IntT.i32), _) => true
    })
    Collector.only(main, { case ConstantIntTE(3, _) => true })
  }

  test("Simple program returning an int, explicit") {
    // We had a bug once looking up "int" in the environment, hence this test.

    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() int { ret 3; }
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    main.header.returnType.kind shouldEqual IntT(32)
  }

  test("Hardcoding negative numbers") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() int export { ret -3; }
        |""".stripMargin)
    val main = compile.expectTemputs().lookupFunction("main")
    Collector.only(main, { case ConstantIntTE(-3, _) => true })
  }

  test("Simple local") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() infer-ret export {
        |  a = 42;
        |  ret a;
        |}
    """.stripMargin)
    val main = compile.expectTemputs().lookupFunction("main")
    vassert(main.header.returnType.kind == IntT(32))
  }

  test("Tests panic return type") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.panic.*;
        |fn main() infer-ret export {
        |  __vbi_panic();
        |  a = 42;
        |}
    """.stripMargin)
    val main = compile.expectTemputs().lookupFunction("main")
    vassert(main.header.returnType.kind == NeverT())
  }

  test("Taking an argument and returning it") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main(a int) int { ret a; }
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    Collector.onlyOf(temputs.lookupFunction("main"), classOf[ParameterT]).tyype == CoordT(ShareT, ReadonlyT, IntT.i32)
    val lookup = Collector.onlyOf(temputs.lookupFunction("main"), classOf[LocalLookupTE]);
    lookup.localVariable.id.last shouldEqual CodeVarNameT("a")
    lookup.reference shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
  }

  test("Tests adding two numbers") {
    val compile =
      TemplarTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |import v.builtins.arith.*;
          |fn main() int export { ret +(2, 3); }
          |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
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
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Moo export { hp int; }
        |fn main(moo *Moo) int export {
        |  ret moo.hp;
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
  }

  test("Simple struct instantiate") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Moo export { hp int; }
        |fn main() Moo export {
        |  ret Moo(42);
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
  }

  test("Call destructor") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Moo export { hp int; }
        |fn main() int export {
        |  ret Moo(42).hp;
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionCallTE(PrototypeT(FullNameT(_, _, FunctionNameT("drop", _, _)), _), _) =>
    })
  }

  test("Custom destructor") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Moo #!DeriveStructDrop export { hp int; }
        |fn drop(self ^Moo) {
        |  (_) = self;
        |}
        |fn main() int export {
        |  ret Moo(42).hp;
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case FunctionCallTE(PrototypeT(FullNameT(_, _, FunctionNameT("drop", _, _)), _), _) =>
    })
  }

  test("Make constraint reference") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Moo {}
        |fn main() void export {
        |  m = Moo();
        |  b = *m;
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    val tyype =
      Collector.only(main.body, {
        case LetNormalTE(ReferenceLocalVariableT(FullNameT(_, _, CodeVarNameT("b")), _, tyype), _) => tyype
      })
    tyype.ownership shouldEqual PointerT
    tyype.permission shouldEqual ReadonlyT
  }

  test("Recursion") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() int export{ ret main(); }
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupFunction("main").header.returnType shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
  }

  test("Simple lambda") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() int export { ret { 7; }(); }
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupFunction("__call").header.returnType shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
    temputs.lookupFunction("main").header.returnType shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
  }

  test("Lambda with one magic arg") {
    val compile =
      TemplarTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |fn main() int export { ret {_;}(3); }
          |""".stripMargin)
    val temputs = compile.expectTemputs()

    // Make sure it inferred the param type and return type correctly
    Collector.only(temputs.lookupLambdaIn("main"),
        { case ParameterT(_, None, CoordT(ShareT, ReadonlyT, IntT.i32)) => })

    temputs.lookupLambdaIn("main").header.returnType shouldEqual
        CoordT(ShareT, ReadonlyT, IntT.i32)
  }


  // Test that the lambda's arg is the right type, and the name is right
  test("Lambda with a type specified param") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arith.*;
        |fn main() int export {
        |  ret (a int){+(a,a);}(3);
        |}
        |""".stripMargin);
    val temputs = compile.expectTemputs()

    val lambda = temputs.lookupLambdaIn("main");

    // Check that the param type is right
    Collector.only(lambda, { case ParameterT(CodeVarNameT("a"), None, CoordT(ShareT, ReadonlyT, IntT.i32)) => {} })
    // Check the name is right
    vassert(temputs.nameIsLambdaIn(lambda.header.fullName, "main"))

    val main = temputs.lookupFunction("main");
    Collector.only(main, { case FunctionCallTE(callee, _) if temputs.nameIsLambdaIn(callee.fullName, "main") => })
  }

  test("Test overloads") {
    val compile = TemplarTestCompilation.test(Tests.loadExpected("programs/functions/overloads.vale"))
    val temputs = compile.expectTemputs()

    temputs.lookupFunction("main").header.returnType shouldEqual
      CoordT(ShareT, ReadonlyT, IntT.i32)
  }

  test("Test readonly UFCS") {
    val compile = TemplarTestCompilation.test(Tests.loadExpected("programs/ufcs.vale"))
    val temputs = compile.expectTemputs()
  }

  test("Test readwrite UFCS") {
    val compile = TemplarTestCompilation.test(Tests.loadExpected("programs/readwriteufcs.vale"))
    val temputs = compile.expectTemputs()
  }

  test("Test permission mismatch") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Engine { fuel int; }
        |struct Spaceship { engine Engine; }
        |fn getFuel(a *Engine) int { a.fuel }
        |fn main() int export {
        |  ship = Spaceship(Engine(42));
        |  ret getFuel(ship.engine);
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CouldntFindFunctionToCallT(_, _)) =>
    }
  }

  test("Report when imm struct has varying member") {
    // https://github.com/ValeLang/Vale/issues/131
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Spaceship imm {
        |  name! str;
        |  numWings int;
        |}
        |fn main() export {
        |  ship = Spaceship("Serenity", 2);
        |  println(ship.name);
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ImmStructCantHaveVaryingMember(_, _, _)) =>
    }
  }

  test("Test templates") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn ~<T>(a T, b T)T{ ret a; }
        |fn main() int export {true ~ false; 2 ~ 2; ret 3 ~ 3;}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    // Tests that we reuse existing stamps
    vassert(temputs.getAllUserFunctions.size == 3)
  }

  test("Test taking a callable param") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn do<F>(callable F) infer-ret { ret callable(); }
        |fn main() int export { ret do({ ret 3; }); }
      """.stripMargin)
    val temputs = compile.expectTemputs()

    temputs.functions.collect({ case x @ functionName("do") => x }).head.header.returnType shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
  }

  test("Calls destructor on local var") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Muta { }
        |
        |fn destructor(m ^Muta) {
        |  Muta() = m;
        |}
        |
        |fn main() export {
        |  a = Muta();
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    Collector.only(main, { case FunctionCallTE(PrototypeT(FullNameT(_, _, FunctionNameT("drop", _, _)), _), _) => })
    Collector.all(main, { case FunctionCallTE(_, _) => }).size shouldEqual 2
  }

  test("Stamps an interface template via a function return") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface MyInterface<X> rules(X Ref) { }
        |
        |struct SomeStruct<X> rules(X Ref) { x X; }
        |impl<X> MyInterface<X> for SomeStruct<X>;
        |
        |fn doAThing<T>(t T) SomeStruct<T> {
        |  ret SomeStruct<T>(t);
        |}
        |
        |fn main() export {
        |  doAThing(4);
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
  }

//  test("Constructor is stamped even without calling") {
//    val compile = RunCompilation.test(
//      """
//        |struct MyStruct imm {}
//        |fn wot(b: *MyStruct) int { ret 9; }
//      """.stripMargin)
//    val temputs = compile.expectTemputs()
//
//    temputs.lookupFunction("MyStruct")
//  }

  test("Reads a struct member") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct MyStruct { a int; }
        |fn main() int export { ms = MyStruct(7); ret ms.a; }
      """.stripMargin)
    val temputs = compile.expectTemputs()

    // Check the struct was made
    temputs.structs.collectFirst({
      case StructDefinitionT(
      simpleName("MyStruct"),
      _,
      false,
      MutableT,
      Vector(StructMemberT(CodeVarNameT("a"), FinalT, ReferenceMemberTypeT(CoordT(ShareT, ReadonlyT, IntT.i32)))),
      false) =>
    }).get
    // Check there's a constructor
    Collector.all(temputs.lookupFunction("MyStruct"), {
      case FunctionHeaderT(
      simpleName("MyStruct"),
      _,
      Vector(ParameterT(CodeVarNameT("a"), None, CoordT(ShareT, ReadonlyT, IntT.i32))),
      CoordT(OwnT,ReadwriteT, StructTT(simpleName("MyStruct"))),
      _) =>
    })
    val main = temputs.lookupFunction("main")
    // Check that we call the constructor
    Collector.only(main, {
      case FunctionCallTE(
        PrototypeT(simpleName("MyStruct"), _),
        Vector(ConstantIntTE(7, _))) =>
    })
  }

  test("Tests defining an interface and an implementing struct") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface MyInterface { }
        |struct MyStruct { }
        |impl MyInterface for MyStruct;
        |fn main(a MyStruct) {}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    val interfaceDef =
      temputs.interfaces.collectFirst({
        case id @ InterfaceDefinitionT(simpleName("MyInterface"), _, false, MutableT, Vector()) => id
      }).get

    val structDef =
      temputs.structs.collectFirst({
        case sd @ StructDefinitionT(simpleName("MyStruct"), _, false, MutableT, _, false) => sd
      }).get

    vassert(temputs.edges.exists(impl => {
      impl.struct == structDef.getRef && impl.interface == interfaceDef.getRef
    }))
  }

  test("Tests stamping an interface template from a function param") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface MyOption<T> rules(T Ref) { }
        |fn main(a MyOption<int>) { }
      """.stripMargin)
    val temputs = compile.expectTemputs()

    temputs.lookupInterface(
      InterfaceTT(
        FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MyOption"), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))))
    vassert(temputs.lookupFunction("main").header.params.head.tyype ==
        CoordT(OwnT,ReadwriteT,InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MyOption"), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))))))))

    // Can't run it because there's nothing implementing that interface >_>
  }

  test("Tests exporting function") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn moo() export { }
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val moo = temputs.lookupFunction("moo")
    val export = vassertOne(temputs.functionExports)
    `export`.prototype shouldEqual moo.header.toPrototype
  }

  test("Tests exporting struct") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Moo export { a int; }
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val moo = temputs.lookupStruct("Moo")
    val export = vassertOne(temputs.kindExports)
    `export`.tyype shouldEqual moo.getRef
  }

  test("Tests exporting interface") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface IMoo export { fn hi(virtual this *IMoo) void; }
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val moo = temputs.lookupInterface("IMoo")
    val export = vassertOne(temputs.kindExports)
    `export`.tyype shouldEqual moo.getRef
  }

  test("Tests stamping a struct and its implemented interface from a function param") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.panic.*;
        |import panicutils.*;
        |interface MyOption<T> rules(T Ref) imm { }
        |struct MySome<T> rules(T Ref) imm { value T; }
        |impl<T> MyOption<T> for MySome<T>;
        |fn moo(a MySome<int>) { }
        |fn main() export { moo(__pretend<MySome<int>>()); }
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    val interface =
      temputs.lookupInterface(
        InterfaceTT(
          FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MyOption"), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))))

    val struct = temputs.lookupStruct(StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MySome"), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))));

    temputs.lookupImpl(struct.getRef, interface.getRef)
  }

  test("Tests single expression and single statement functions' returns") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct MyThing { value int; }
        |fn moo() MyThing { ret MyThing(4) }
        |fn main() export { moo(); }
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val moo = temputs.lookupFunction("moo")
    moo.header.returnType match {
      case CoordT(OwnT,ReadwriteT,StructTT(simpleName("MyThing"))) =>
    }
    val main = temputs.lookupFunction("main")
    main.header.returnType match {
      case CoordT(ShareT, ReadonlyT, VoidT()) =>
    }
  }

  test("Tests calling a templated struct's constructor") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct MySome<T> rules(T Ref) { value T; }
        |fn main() int export {
        |  ret MySome<int>(4).value;
        |}
        |""".stripMargin
    )

    val temputs = compile.expectTemputs()

    temputs.lookupStruct(StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MySome"), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))));

    val constructor = temputs.lookupFunction("MySome")
    constructor.header match {
      case
        FunctionHeaderT(
        simpleName("MySome"),
        _,
        _,
        CoordT(OwnT,ReadwriteT,StructTT(FullNameT(_, Vector(), CitizenNameT(CitizenTemplateNameT("MySome"), Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))))))),
        _) =>
    }

    Collector.all(temputs.lookupFunction("main"), {
      case FunctionCallTE(functionName("MySome"), _) =>
    })
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = TemplarTestCompilation.test(readCodeFromResource("programs/virtuals/upcasting.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")

    Collector.only(main, { case LetNormalTE(ReferenceLocalVariableT(FullNameT(_,_,CodeVarNameT("x")),FinalT,CoordT(OwnT,ReadwriteT,InterfaceTT(simpleName("MyInterface")))), _) => })

    val upcast = Collector.onlyOf(main, classOf[StructToInterfaceUpcastTE])
    vassert(upcast.result.reference == CoordT(OwnT,ReadwriteT,InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MyInterface"), Vector())))))
    vassert(upcast.innerExpr.result.reference == CoordT(OwnT,ReadwriteT,StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("MyStruct"), Vector())))))
  }

  test("Tests calling a virtual function") {
    val compile = TemplarTestCompilation.test(readCodeFromResource("programs/virtuals/calling.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case up @ StructToInterfaceUpcastTE(innerExpr, InterfaceTT(simpleName("Car"))) => {
        Collector.only(innerExpr.result, {
          case StructTT(simpleName("Toyota")) =>
        })
        vassert(up.result.reference.kind == InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("Car"), Vector()))))
      }
    })
  }

  test("Tests calling a virtual function through a borrow ref") {
    val compile = TemplarTestCompilation.test(readCodeFromResource("programs/virtuals/callingThroughBorrow.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case f @ FunctionCallTE(PrototypeT(simpleName("doCivicDance"),CoordT(ShareT,ReadonlyT,IntT.i32)), _) => {
//        vassert(f.callable.paramTypes == Vector(Coord(Borrow,InterfaceRef2(simpleName("Car")))))
      }
    })
  }

  test("Tests calling a templated function with explicit template args") {
    // Tests putting MyOption<int> as the type of x.
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |fn moo<T> () rules(T Ref) { }
        |
        |fn main() export {
        |	moo<int>();
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  // See DSDCTD
  test("Tests destructuring shared doesnt compile to destroy") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |struct Vec3i imm {
        |  x int;
        |  y int;
        |  z int;
        |}
        |
        |fn main() int export {
        |	 Vec3i(x, y, z) = Vec3i(3, 4, 5);
        |  ret y;
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    Collector.all(temputs.lookupFunction("main"), {
      case DestroyTE(_, _, _) =>
    }).size shouldEqual 0

    // Make sure there's a destroy in its destructor though.
    val destructor =
      vassertOne(
        temputs.functions.collect({
          case f if (f.header.fullName.last match { case FreeNameT(_, _) => true case _ => false }) => f
        }))

    Collector.only(destructor, { case DestroyTE(referenceExprResultStructName("Vec3i"), _, _) => })
    Collector.all(destructor, { case DiscardTE(referenceExprResultKind(IntT(_))) => }).size shouldEqual 3
  }

  // See DSDCTD
  test("Tests destructuring borrow doesnt compile to destroy") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |struct Vec3i {
        |  x int;
        |  y int;
        |  z int;
        |}
        |
        |fn main() int export {
        |  v = Vec3i(3, 4, 5);
        |	 (x, y, z) = *v;
        |  ret y;
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")

    Collector.all(main, {
      case DestroyTE(_, _, _) =>
    }).size shouldEqual 0

    Collector.only(main, {
      case ReferenceMemberLookupTE(_,
        SoftLoadTE(LocalLookupTE(_, _, CoordT(_,_,StructTT(_)), FinalT), PointerT, ReadonlyT),
        FullNameT(_, Vector(CitizenNameT(CitizenTemplateNameT("Vec3i"),Vector())),CodeVarNameT("x")),CoordT(ShareT,ReadonlyT,IntT.i32),ReadonlyT,FinalT) =>
    })
  }

  test("Tests making a variable with a pattern") {
    // Tests putting MyOption<int> as the type of x.
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |interface MyOption<T> rules(T Ref) { }
        |
        |struct MySome<T> rules(T Ref) {}
        |impl<T> MyOption<T> for MySome<T>;
        |
        |fn doSomething(opt MyOption<int>) int {
        |  ret 9;
        |}
        |
        |fn main() int export {
        |	x MyOption<int> = MySome<int>();
        |	ret doSomething(x);
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Tests a linked list") {
    val compile = TemplarTestCompilation.test(
      Tests.loadExpected("programs/virtuals/ordinarylinkedlist.vale"))
    val temputs = compile.expectTemputs()
  }

  test("Test borrow ref") {
    val compile = TemplarTestCompilation.test(Tests.loadExpected("programs/borrowRef.vale"))
    val temputs = compile.expectTemputs()
  }

  test("Tests calling a function with an upcast") {
    val compile = TemplarTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |interface ISpaceship {}
          |struct Firefly {}
          |impl ISpaceship for Firefly;
          |fn launch(ship *ISpaceship) { }
          |fn main() {
          |  launch(*Firefly());
          |}
          |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case StructToInterfaceUpcastTE(_, InterfaceTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT("ISpaceship"), _)))) =>
    })
  }

  test("Tests calling a templated function with an upcast") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface ISpaceship<T> rules(T Ref) {}
        |struct Firefly<T> rules(T Ref) {}
        |impl<T> ISpaceship<T> for Firefly<T>;
        |fn launch<T>(ship *ISpaceship<T>) { }
        |fn main() {
        |  launch(*Firefly<int>());
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case StructToInterfaceUpcastTE(_, InterfaceTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT("ISpaceship"), _)))) =>
    })
  }

  test("Tests a templated linked list") {
    val compile = TemplarTestCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/templatedlinkedlist.vale"))
    val temputs = compile.expectTemputs()
  }

  test("Tests calling an abstract function") {
    val compile = TemplarTestCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/callingAbstract.vale"))
    val temputs = compile.expectTemputs()

    temputs.functions.collectFirst({
      case FunctionT(header @ functionName("doThing"), _) if header.getAbstractInterface != None => true
    }).get
  }

  test("Tests a foreach for a linked list") {
    val compile = TemplarTestCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/foreachlinkedlist.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case f @ FunctionCallTE(functionName("forEach"), _) => f
    })

  }

  // Make sure a ListNode struct made it out
  test("Templated imm struct") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct ListNode<T> rules(T Ref) imm {
        |  tail ListNode<T>;
        |}
        |fn main(a ListNode<int>) {}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Borrow-load member") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Bork {
        |  x int;
        |}
        |fn getX(bork &Bork) int { ret bork.x; }
        |struct List {
        |  array! Bork;
        |}
        |fn main() int export {
        |  l = List(Bork(0));
        |  ret getX(&l.array);
        |}
        """.stripMargin)

    val temputs = compile.expectTemputs()
    vpass()
  }

  test("Pointer-load member") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Bork {
        |  x int;
        |}
        |fn getX(bork *Bork) int { ret bork.x; }
        |struct List {
        |  array! Bork;
        |}
        |fn main() int export {
        |  l = List(Bork(0));
        |  ret getX(*l.array);
        |}
        """.stripMargin)

    val temputs = compile.expectTemputs()
    vpass()
  }

  test("fdsfsdf") {
    // This test is because we had a bug where &! still produced a *!.
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Bork { }
        |fn myFunc<F>(consumer &!F) void { }
        |fn main() {
        |  bork = Bork();
        |  myFunc(&!{ bork; });
        |}
        |
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Test Array of StructTemplata") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Vec2 imm {
        |  x float;
        |  y float;
        |}
        |struct Pattern imm {
        |  patternTiles Array<imm, Vec2>;
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Test array push, pop, len, capacity, drop") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arith.*;
        |import array.make.*;
        |import v.builtins.arrays.*;
        |import ifunction.ifunction1.*;
        |
        |fn main() void export {
        |  arr = Array<mut, int>(9);
        |  arr!.push(420);
        |  arr!.push(421);
        |  arr!.push(422);
        |  arr.len();
        |  arr.capacity();
        |  // implicit drop with pops
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Test MakeArray") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arith.*;
        |import array.make.*;
        |import v.builtins.arrays.*;
        |import ifunction.ifunction1.*;
        |
        |fn main() int export {
        |  a = MakeArray(11, {_});
        |  ret len(&a);
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Test return") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() int export {
        |  ret 7;
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.only(main, { case ReturnTE(_) => })
  }

  test("Test return from inside if") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.panic.*;
        |fn main() int export {
        |  if (true) {
        |    ret 7;
        |  } else {
        |    ret 9;
        |  }
        |  __vbi_panic();
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.all(main, { case ReturnTE(_) => }).size shouldEqual 2
    Collector.only(main, { case ConstantIntTE(7, _) => })
    Collector.only(main, { case ConstantIntTE(9, _) => })
  }

  test("Test return from inside if destroys locals") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Marine { hp int; }
        |fn main() int export {
        |  m = Marine(5);
        |  x =
        |    if (true) {
        |      ret 7;
        |    } else {
        |      = m.hp;
        |    };
        |  ret x;
        |}
        |""".stripMargin)// +
//        Tests.loadExpected("castutils/castutils.vale") +
//        Tests.loadExpected("printutils/printutils.vale"))
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    val destructorCalls =
      Collector.all(main, {
        case fpc @ FunctionCallTE(PrototypeT(FullNameT(_,Vector(CitizenNameT(CitizenTemplateNameT("Marine"),Vector())),FunctionNameT("drop",Vector(),Vector(CoordT(_,_,StructTT(simpleName("Marine")))))),_),_) => fpc
      })
    destructorCalls.size shouldEqual 2
  }

  test("Test complex interface") {
    val compile = TemplarTestCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/templatedinterface.vale"))
    val temputs = compile.expectTemputs()
  }

  test("Lambda is incompatible anonymous interface") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface AFunction1<P> rules(P Ref) {
        |  fn __call(virtual this *AFunction1<P>, a P) int;
        |}
        |fn main() export {
        |  arr = AFunction1<int>((_){ true });
        |}
        |""".stripMargin)

    compile.getTemputs() match {
      case Err(BodyResultDoesntMatch(_, _, _, _)) =>
      case Err(other) => vwat(TemplarErrorHumanizer.humanize(true, compile.getCodeMap().getOrDie(), other))
      case Ok(wat) => vwat(wat)
    }
  }
  test("Zero method anonymous interface") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface MyInterface {}
        |fn main() export {
        |  x = MyInterface();
        |}
        |""".stripMargin)
    compile.expectTemputs()
  }

  test("Lock weak member") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.opt.*;
        |import v.builtins.weak.*;
        |import v.builtins.logic.*;
        |import panicutils.*;
        |import printutils.*;
        |
        |struct Base {
        |  name str;
        |}
        |struct Spaceship {
        |  name str;
        |  origin **Base;
        |}
        |fn printShipBase(ship *Spaceship) {
        |  maybeOrigin = lock(ship.origin); «14»«15»
        |  if (not maybeOrigin.isEmpty()) { «16»
        |    o = maybeOrigin.get();
        |    println("Ship base: " + o.name);
        |  } else {
        |    println("Ship base unknown!");
        |  }
        |}
        |fn main() export {
        |  base = Base("Zion");
        |  ship = Spaceship("Neb", **base);
        |  printShipBase(*ship);
        |  (base).drop(); // Destroys base.
        |  printShipBase(*ship);
        |}
        |""".stripMargin)

    compile.expectTemputs()
  }

  test("Lambda inside template") {
    // This originally didn't work because both helperFunc<int> and helperFunc<Str>
    // made a closure struct called helperFunc:lam1, which collided.
    // This is what spurred paackage support.

    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import printutils.*;
        |
        |fn helperFunc<T>(x T) {
        |  { print(x); }();
        |}
        |fn main() export {
        |  helperFunc(4);
        |  helperFunc("bork");
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
  }


  test("Lambda inside different function with same name") {
    // This originally didn't work because both helperFunc(:Int) and helperFunc(:Str)
    // made a closure struct called helperFunc:lam1, which collided.
    // We need to disambiguate by parameters, not just template args.

    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import printutils.*;
        |
        |fn helperFunc(x int) {
        |  { print(x); }();
        |}
        |fn helperFunc(x str) {
        |  { print(x); }();
        |}
        |fn main() export {
        |  helperFunc(4);
        |  helperFunc("bork");
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Reports when exported function depends on non-exported param") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Firefly { }
        |fn moo(firefly *Firefly) export { }
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ExportedFunctionDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when exported function depends on non-exported return") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.panic.*;
        |import panicutils.*;
        |struct Firefly { }
        |fn moo() *Firefly export { __pretend<*Firefly>(); }
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ExportedFunctionDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when extern function depends on non-exported param") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Firefly { }
        |fn moo(firefly *Firefly) extern;
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ExternFunctionDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when extern function depends on non-exported return") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Firefly imm { }
        |fn moo() *Firefly extern;
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ExternFunctionDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when exported struct depends on non-exported member") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Firefly export imm {
        |  raza Raza;
        |}
        |struct Raza imm { }
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when exported RSA depends on non-exported element") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arrays.*;
        |import v.builtins.functor1.*;
        |export Array<imm, Raza> as RazaArray;
        |struct Raza imm { }
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Checks that we stored a borrowed temporary in a local") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Muta { }
        |fn doSomething(m *Muta, i int) {}
        |fn main() export {
        |  doSomething(*Muta(), 1)
        |}
      """.stripMargin)

    // Should be a temporary for this object
    Collector.onlyOf(
      compile.expectTemputs().lookupFunction("main"),
      classOf[LetAndLendTE]) match {
        case LetAndLendTE(_, _, PointerT) =>
      }
  }

  test("Reports when exported SSA depends on non-exported element") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arrays.*;
        |import v.builtins.functor1.*;
        |export [<imm> 5 * Raza] as RazaArray;
        |struct Raza imm { }
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when reading nonexistant local") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() int export {
        |  moo
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CouldntFindIdentifierToLoadT(_, CodeNameS("moo"))) =>
    }
  }

  test("Reports when RW param in pure func") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Spaceship { }
        |fn main(ship *!Spaceship) int pure {
        |  7
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(NonReadonlyReferenceFoundInPureFunctionParameter(_, CodeVarNameT("ship"))) =>
    }
  }

  test("Reports when mutating after moving") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Weapon {
        |  ammo! int;
        |}
        |struct Marine {
        |  weapon! Weapon;
        |}
        |
        |fn main() int export {
        |  m = Marine(Weapon(7));
        |  newWeapon = Weapon(10);
        |  set m.weapon = newWeapon;
        |  set newWeapon.ammo = 11;
        |  ret 42;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantUseUnstackifiedLocal(_, CodeVarNameT("newWeapon"))) =>
    }
  }

  test("Tests export struct twice") {
    // See MMEDT why this is an error
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Moo export { }
        |export Moo as Bork;
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(TypeExportedMultipleTimes(_, _, Vector(_, _))) =>
    }
  }

  test("Reports when reading after moving") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Weapon {
        |  ammo! int;
        |}
        |struct Marine {
        |  weapon! Weapon;
        |}
        |
        |fn main() int export {
        |  m = Marine(Weapon(7));
        |  newWeapon = Weapon(10);
        |  set m.weapon = newWeapon;
        |  println(newWeapon.ammo);
        |  ret 42;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantUseUnstackifiedLocal(_, CodeVarNameT("newWeapon"))) =>
    }
  }

  test("Reports when moving from inside a while") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Marine {
        |  ammo int;
        |}
        |
        |fn main() int export {
        |  m = Marine(7);
        |  while (false) {
        |    drop(m);
        |  }
        |  ret 42;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantUnstackifyOutsideLocalFromInsideWhile(_, CodeVarNameT("m"))) =>
    }
  }

  test("Cant subscript non-subscriptable type") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Weapon {
        |  ammo! int;
        |}
        |
        |fn main() int export {
        |  weapon = Weapon(10);
        |  ret weapon[42];
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CannotSubscriptT(_, StructTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT("Weapon"), Vector()))))) =>
    }
  }

  test("Reports when two functions with same signature") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn moo() int export { ret 1337; }
        |fn moo() int export { ret 1448; }
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(FunctionAlreadyExists(_, _, SignatureT(FullNameT(_, Vector(), FunctionNameT("moo", Vector(), Vector()))))) =>
    }
  }

  test("Reports when we give too many args") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn moo(a int, b bool, s str) int { a }
        |fn main() int export {
        |  moo(42, true, "hello", false)
        |}
        |""".stripMargin)
    compile.getTemputs() match {
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
    val fireflyKind = StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("Firefly"), Vector())))
    val fireflyCoord = CoordT(OwnT,ReadwriteT,fireflyKind)
    val serenityKind = StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("Serenity"), Vector())))
    val serenityCoord = CoordT(OwnT,ReadwriteT,serenityKind)
    val ispaceshipKind = InterfaceTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("ISpaceship"), Vector())))
    val ispaceshipCoord = CoordT(OwnT,ReadwriteT,ispaceshipKind)
    val unrelatedKind = StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("Spoon"), Vector())))
    val unrelatedCoord = CoordT(OwnT,ReadwriteT,unrelatedKind)
    val fireflySignature = ast.SignatureT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), FunctionNameT("myFunc", Vector(), Vector(fireflyCoord))))
    val fireflyExport = KindExportT(RangeS.testZero, fireflyKind, PackageCoordinate.TEST_TLD, "Firefly");
    val serenityExport = KindExportT(RangeS.testZero, fireflyKind, PackageCoordinate.TEST_TLD, "Serenity");

    val filenamesAndSources = FileCoordinateMap.test("blah blah blah\nblah blah blah")

    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindTypeT(RangeS.testZero, "Spaceship")).nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindFunctionToCallT(
        RangeS.testZero,
        FindFunctionFailure(
          CodeNameS("someFunc"),
          Vector(),
          Map()))).nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindFunctionToCallT(
        RangeS.testZero,
        FindFunctionFailure(CodeNameS(""), Vector(), Map())))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CannotSubscriptT(
        RangeS.testZero,
        fireflyKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindIdentifierToLoadT(
        RangeS.testZero,
        CodeNameS("spaceship")))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindMemberT(
        RangeS.testZero,
        "hp"))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      BodyResultDoesntMatch(
        RangeS.testZero,
        FunctionNameS("myFunc", CodeLocationS.testZero), fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntConvertForReturnT(
        RangeS.testZero,
        fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntConvertForMutateT(
        RangeS.testZero,
        fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntConvertForMutateT(
        RangeS.testZero,
        fireflyCoord, serenityCoord))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantMoveOutOfMemberT(
        RangeS.testZero,
        CodeVarNameT("hp")))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantUseUnstackifiedLocal(
        RangeS.testZero,
        CodeVarNameT("firefly")))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantUnstackifyOutsideLocalFromInsideWhile(
        RangeS.testZero,
        CodeVarNameT("firefly")))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      FunctionAlreadyExists(RangeS.testZero, RangeS.testZero, fireflySignature))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantMutateFinalMember(
        RangeS.testZero,
        serenityKind.fullName,
        FullNameT(PackageCoordinate.TEST_TLD, Vector(), CodeVarNameT("bork"))))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      LambdaReturnDoesntMatchInterfaceConstructor(
        RangeS.testZero))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      IfConditionIsntBoolean(
        RangeS.testZero, fireflyCoord))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      WhileConditionIsntBoolean(
        RangeS.testZero, fireflyCoord))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantImplNonInterface(
        RangeS.testZero, fireflyKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      ImmStructCantHaveVaryingMember(
        RangeS.testZero, TopLevelCitizenDeclarationNameS("SpaceshipSnapshot", RangeS.testZero), "fuel"))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantDowncastUnrelatedTypes(
        RangeS.testZero, ispaceshipKind, unrelatedKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantDowncastToInterface(
        RangeS.testZero, ispaceshipKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      ExportedFunctionDependedOnNonExportedKind(
        RangeS.testZero, PackageCoordinate.TEST_TLD, fireflySignature, fireflyKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      ExportedImmutableKindDependedOnNonExportedKind(
        RangeS.testZero, PackageCoordinate.TEST_TLD, serenityKind, fireflyKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      ExternFunctionDependedOnNonExportedKind(
        RangeS.testZero, PackageCoordinate.TEST_TLD, fireflySignature, fireflyKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      TypeExportedMultipleTimes(
        RangeS.testZero, PackageCoordinate.TEST_TLD, Vector(fireflyExport, serenityExport)))
      .nonEmpty)
//    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
//      NotEnoughToSolveError(
//        RangeS.testZero,
//        Map(
//          CodeRuneS("X") -> KindTemplata(fireflyKind)),
//        Vector(CodeRuneS("Y"))))
//      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      TemplarSolverError(
        RangeS.testZero,
        FailedSolve(
          Vector(
            Step(
              false,
              Vector(),
              Vector(),
              Map(
                CodeRuneS("X") -> KindTemplata(fireflyKind)))),
          Vector(),
          RuleError(KindIsNotConcrete(ispaceshipKind)))))
      .nonEmpty)
  }

  test("Report when downcasting between unrelated types") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.as.*;
        |import panicutils.*;
        |
        |interface ISpaceship { }
        |struct Spoon { }
        |
        |fn main() export {
        |  ship = __pretend<ISpaceship>();
        |  ship.as<Spoon>();
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantDowncastUnrelatedTypes(_, _, _)) =>
    }
  }

  test("Report when downcasting to interface") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.as.*;
        |import panicutils.*;
        |
        |interface ISuper { }
        |interface ISub { }
        |impl ISuper for ISub;
        |
        |fn main() export {
        |  ship = __pretend<ISuper>();
        |  ship.as<ISub>();
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantDowncastToInterface(_, _)) =>
    }
  }

  test("Report when multiple types in array") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() int export {
        |  arr = [][true, 42];
        |  ret arr.1;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ArrayElementsHaveDifferentTypes(_, types)) => {
        types shouldEqual Set(CoordT(ShareT, ReadonlyT, IntT.i32), CoordT(ShareT, ReadonlyT, BoolT()))
      }
    }
  }

  test("Report when num elements mismatch") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Spaceship imm {
        |  name! str;
        |  numWings int;
        |}
        |fn main() bool export {
        |  arr = [4][true, false, false];
        |  ret arr.0;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(InitializedWrongNumberOfElements(_, 4, 3)) =>
    }
  }

  test("Report when abstract method defined outside open interface") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.panic.*;
        |interface IBlah { }
        |fn bork(virtual moo *IBlah) abstract;
        |fn main() export {
        |  bork(__vbi_panic());
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(AbstractMethodOutsideOpenInterface(_)) =>
    }
  }

  test("Reports when ownership doesnt match") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |struct Firefly {}
        |fn getFuel(self *Firefly) int { ret 7; }
        |
        |fn main() int export {
        |  f = Firefly();
        |  ret (&f).getFuel();
        |}
        |""".stripMargin
    )
    compile.getTemputs() match {
      case Err(CouldntFindFunctionToCallT(range, fff)) => {
        fff.name match { case CodeNameS("getFuel") => }
        fff.rejectedCalleeToReason.size shouldEqual 1
        val reason = fff.rejectedCalleeToReason.head._2
        reason match { case SpecificParamDoesntSend(0, _, _) => }
      }
    }
  }
}
