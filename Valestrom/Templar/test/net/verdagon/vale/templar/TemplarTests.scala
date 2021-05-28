package net.verdagon.vale.templar

import net.verdagon.vale.parser.{CombinatorParsers, FileP, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier}
import net.verdagon.vale.scout.{CodeLocationS, CodeVarNameS, ICompileErrorS, ProgramS, RangeS, Scout, VariableNameAlreadyExists}
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale._
import net.verdagon.vale.astronomer.{Astronomer, AstronomerCompilation, CodeTypeNameA, CodeVarNameA, FunctionNameA, GlobalFunctionFamilyNameA, ICompileErrorA, IFunctionDeclarationNameA, ProgramA, TopLevelCitizenDeclarationNameA}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, WrongNumberOfArguments}
import net.verdagon.von.{JsonSyntax, VonPrinter}
import net.verdagon.vale.templar.expression.CallTemplar
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


  test("Simple program returning an int") {
    val compile = TemplarTestCompilation.test("fn main() infer-ret {3}")
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case FunctionHeader2(simpleName("main"),List(UserFunction2),List(), Coord(Share, Readonly, Int2()), _) => true
    })
    main.only({ case IntLiteral2(3) => true })
  }

  test("Hardcoding negative numbers") {
    val compile = TemplarTestCompilation.test("fn main() int export {-3}")
    val main = compile.expectTemputs().lookupFunction("main")
    main.only({ case IntLiteral2(-3) => true })
  }

  test("Taking an argument and returning it") {
    val compile = TemplarTestCompilation.test("fn main(a int) int {a}")
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").onlyOf(classOf[Parameter2]).tyype == Coord(Share, Readonly, Int2())
    val lookup = temputs.lookupFunction("main").allOf(classOf[LocalLookup2]).head;
    lookup.localVariable.id.last shouldEqual CodeVarName2("a")
    lookup.reference shouldEqual Coord(Share, Readonly, Int2())
  }

  test("Tests adding two numbers") {
    val compile =
      TemplarTestCompilation.test(
        """
          |import v.builtins.arith.*;
          |fn main() int export { +(2, 3) }
          |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    main.only({ case IntLiteral2(2) => true })
    main.only({ case IntLiteral2(3) => true })
    main.only({
      case FunctionCall2(
        functionName("+"),
        List(
          IntLiteral2(2),
          IntLiteral2(3))) =>
    })
  }

  test("Constraint reference") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Moo {}
        |fn main() void export {
        |  m = Moo();
        |  b = &m;
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    val tyype =
      main.body.only({
        case LetNormal2(ReferenceLocalVariable2(FullName2(_, CodeVarName2("b")), _, tyype), _) => tyype
      })
    tyype.ownership shouldEqual Constraint
    tyype.permission shouldEqual Readonly
  }

  test("Recursion") {
    val compile = TemplarTestCompilation.test("fn main() int export{main()}")
    val temputs = compile.expectTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupFunction("main").header.returnType shouldEqual Coord(Share, Readonly, Int2())
  }

  test("Simple lambda") {
    val compile = TemplarTestCompilation.test("fn main() int export {{7}()}")
    val temputs = compile.expectTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupFunction("__call").header.returnType shouldEqual Coord(Share, Readonly, Int2())
    temputs.lookupFunction("main").header.returnType shouldEqual Coord(Share, Readonly, Int2())
  }

//  test("Infer") {
//    val compile =
//      TemplarTestCompilation.test(
//        """
//          |struct Ship { }
//          |fn moo<T>(a &!T) &!T { a }
//          |fn main() {
//          |  s = Ship();
//          |  t = moo(&s);
//          |}
//          |""".stripMargin)
//    val temputs = compile.expectTemputs()
//  }

  test("Lambda with one magic arg") {
    val compile = TemplarTestCompilation.test("fn main() int export {{_}(3)}")
    val temputs = compile.expectTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupLambdaIn("main")
        .only({ case Parameter2(_, None, Coord(Share, Readonly, Int2())) => })

    temputs.lookupLambdaIn("main").header.returnType shouldEqual
        Coord(Share, Readonly, Int2())
  }


  // Test that the lambda's arg is the right type, and the name is right
  test("Lambda with a type specified param") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.arith.*;
        |fn main() int export {
        |  (a int){+(a,a)}(3)
        |}
        |""".stripMargin);
    val temputs = compile.expectTemputs()

    val lambda = temputs.lookupLambdaIn("main");

    // Check that the param type is right
    lambda.only({ case Parameter2(CodeVarName2("a"), None, Coord(Share, Readonly, Int2())) => {} })
    // Check the name is right
    vassert(temputs.nameIsLambdaIn(lambda.header.fullName, "main"))

    val main = temputs.lookupFunction("main");
    main.only({ case FunctionCall2(callee, _) if temputs.nameIsLambdaIn(callee.fullName, "main") => })
  }

  test("Test overloads") {
    val compile = TemplarTestCompilation.test(Tests.loadExpected("programs/functions/overloads.vale"))
    val temputs = compile.expectTemputs()

    temputs.lookupFunction("main").header.returnType shouldEqual
      Coord(Share, Readonly, Int2())
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
        |struct Engine { fuel int; }
        |struct Spaceship { engine Engine; }
        |fn getFuel(a &Engine) int { a.fuel }
        |fn main() int export {
        |  ship = Spaceship(Engine(42));
        |  = getFuel(ship.engine);
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

  test("Report when downcasting between unrelated types") {
    val compile = TemplarTestCompilation.test(
      """
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
        |fn main() int export {
        |  arr = [][true, 42];
        |  = arr.1;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(ArrayElementsHaveDifferentTypes(_, types)) => {
        types shouldEqual Set(Coord(Share, Readonly, Int2()), Coord(Share, Readonly, Bool2()))
      }
    }
  }

  test("Report when num elements mismatch") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Spaceship imm {
        |  name! str;
        |  numWings int;
        |}
        |fn main() bool export {
        |  arr = [4][true, false, false];
        |  = arr.0;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(InitializedWrongNumberOfElements(_, 4, 3)) =>
    }
  }

  test("Report when changing final local") {
    // https://github.com/ValeLang/Vale/issues/128
    val compile = TemplarTestCompilation.test(
      """
        |fn main() export {
        |  x = "world!";
        |  set x = "changed";
        |  println(x); // => changed
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantMutateFinalLocal(_, _)) =>
    }
  }

  test("Test templates") {
    val compile = TemplarTestCompilation.test(
      """
        |fn ~<T>(a T, b T)T{a}
        |fn main() int export {true ~ false; 2 ~ 2; = 3 ~ 3;}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    // Tests that we reuse existing stamps
    vassert(temputs.getAllUserFunctions.size == 3)
  }

  test("Test taking a callable param") {
    val compile = TemplarTestCompilation.test(
      """
        |fn do(callable) infer-ret {callable()}
        |fn main() int export {do({ 3 })}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    temputs.functions.collect({ case x @ functionName("do") => x }).head.header.returnType shouldEqual Coord(Share, Readonly, Int2())
  }

  test("Calls destructor on local var") {
    val compile = TemplarTestCompilation.test(
      """struct Muta { }
        |
        |fn destructor(m ^Muta) {
        |  Muta() = m;
        |}
        |
        |fn main() {
        |  a = Muta();
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    main.only({ case FunctionCall2(functionName(CallTemplar.MUT_DESTRUCTOR_NAME), _) => })
    main.all({ case FunctionCall2(_, _) => }).size shouldEqual 2
  }

  test("Stamps an interface template via a function return") {
    val compile = TemplarTestCompilation.test(
      """
        |interface MyInterface<X> rules(X Ref) { }
        |
        |struct SomeStruct<X> rules(X Ref) { x X; }
        |impl<X> MyInterface<X> for SomeStruct<X>;
        |
        |fn doAThing<T>(t T) SomeStruct<T> {
        |  SomeStruct<T>(t)
        |}
        |
        |fn main() {
        |  doAThing(4);
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
  }
//
//  test("Constructor is stamped even without calling") {
//    val compile = RunCompilation.test(
//      """
//        |struct MyStruct imm {}
//        |fn wot(b: *MyStruct) int { 9 }
//      """.stripMargin)
//    val temputs = compile.expectTemputs()
//
//    temputs.lookupFunction("MyStruct")
//  }

  test("Reads a struct member") {
    val compile = TemplarTestCompilation.test(
      """
        |struct MyStruct { a int; }
        |fn main() int export { ms = MyStruct(7); = ms.a; }
      """.stripMargin)
    val temputs = compile.expectTemputs()

    // Check the struct was made
    temputs.structs.collectFirst({
      case StructDefinition2(
      simpleName("MyStruct"),
      _,
      false,
      Mutable,
      List(StructMember2(CodeVarName2("a"), Final, ReferenceMemberType2(Coord(Share, Readonly, Int2())))),
      false) =>
    }).get
    // Check there's a constructor
    temputs.lookupFunction("MyStruct").only({
      case FunctionHeader2(
      simpleName("MyStruct"),
      _,
      List(Parameter2(CodeVarName2("a"), None, Coord(Share, Readonly, Int2()))),
      Coord(Own,Readwrite, StructRef2(simpleName("MyStruct"))),
      _) =>
    })
    val main = temputs.lookupFunction("main")
    // Check that we call the constructor
    main.only({
      case FunctionCall2(
        Prototype2(simpleName("MyStruct"), _),
        List(IntLiteral2(7))) =>
    })
  }

  test("Tests defining an interface and an implementing struct") {
    val compile = TemplarTestCompilation.test(
      """
        |interface MyInterface { }
        |struct MyStruct { }
        |impl MyInterface for MyStruct;
        |fn main(a MyStruct) {}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    val interfaceDef =
      temputs.interfaces.collectFirst({
        case id @ InterfaceDefinition2(simpleName("MyInterface"), _, false, Mutable, List()) => id
      }).get

    val structDef =
      temputs.structs.collectFirst({
        case sd @ StructDefinition2(simpleName("MyStruct"), _, false, Mutable, _, false) => sd
      }).get

    vassert(temputs.edges.exists(impl => {
      impl.struct == structDef.getRef && impl.interface == interfaceDef.getRef
    }))
  }

  test("Tests stamping an interface template from a function param") {
    val compile = TemplarTestCompilation.test(
      """
        |interface MyOption<T> rules(T Ref) { }
        |fn main(a MyOption<int>) { }
      """.stripMargin)
    val temputs = compile.expectTemputs()

    temputs.lookupInterface(
      InterfaceRef2(
        FullName2(List(), CitizenName2("MyOption", List(CoordTemplata(Coord(Share, Readonly, Int2())))))))
    vassert(temputs.lookupFunction("main").header.params.head.tyype ==
        Coord(Own,Readwrite,InterfaceRef2(FullName2(List(), CitizenName2("MyOption", List(CoordTemplata(Coord(Share, Readonly, Int2()))))))))

    // Can't run it because there's nothing implementing that interface >_>
  }

  test("Tests exporting function") {
    val compile = TemplarTestCompilation.test(
      """
        |fn moo() export { }
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val moo = temputs.lookupFunction("moo")
    moo.header.isExport shouldEqual true
  }

  test("Tests exporting struct") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Moo export { a int; }
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val moo = temputs.lookupStruct("Moo")
    moo.attributes.exists({ case Export2(_) => true }) shouldEqual true
  }

  test("Tests exporting interface") {
    val compile = TemplarTestCompilation.test(
      """
        |interface IMoo export { fn hi(virtual this &IMoo) void; }
        |""".stripMargin)
    val temputs = compile.expectTemputs()
    val moo = temputs.lookupInterface("IMoo")
    moo.attributes.exists({ case Export2(_) => true }) shouldEqual true
  }

  test("Tests stamping a struct and its implemented interface from a function param") {
    val compile = TemplarTestCompilation.test(
      """
        |interface MyOption<T> imm rules(T Ref) { }
        |struct MySome<T> imm rules(T Ref) { value T; }
        |impl<T> MyOption<T> for MySome<T>;
        |fn moo(a MySome<int>) export { }
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    val interface =
      temputs.lookupInterface(
        InterfaceRef2(
          FullName2(List(), CitizenName2("MyOption", List(CoordTemplata(Coord(Share, Readonly, Int2())))))))

    val struct = temputs.lookupStruct(StructRef2(FullName2(List(), CitizenName2("MySome", List(CoordTemplata(Coord(Share, Readonly, Int2())))))));

    temputs.lookupImpl(struct.getRef, interface.getRef)
  }

  test("Tests single expression and single statement functions' returns") {
    val compile = TemplarTestCompilation.test(
      """
        |struct MyThing { value int; }
        |fn moo() MyThing { MyThing(4) }
        |fn main() { moo(); }
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val moo = temputs.lookupFunction("moo")
    moo.header.returnType match {
      case Coord(Own,Readwrite,StructRef2(simpleName("MyThing"))) =>
    }
    val main = temputs.lookupFunction("main")
    main.header.returnType match {
      case Coord(Share, Readonly, Void2()) =>
    }
  }

  test("Tests calling a templated struct's constructor") {
    val compile = TemplarTestCompilation.test(
      """
        |struct MySome<T> rules(T Ref) { value T; }
        |fn main() int export {
        |  MySome<int>(4).value
        |}
        |""".stripMargin
    )

    val temputs = compile.expectTemputs()

    temputs.lookupStruct(StructRef2(FullName2(List(), CitizenName2("MySome", List(CoordTemplata(Coord(Share, Readonly, Int2())))))));

    val constructor = temputs.lookupFunction("MySome")
    constructor.header match {
      case
        FunctionHeader2(
        simpleName("MySome"),
        _,
        _,
        Coord(Own,Readwrite,StructRef2(FullName2(List(), CitizenName2("MySome", List(CoordTemplata(Coord(Share, Readonly, Int2()))))))),
        _) =>
    }

    temputs.lookupFunction("main").only({
      case FunctionCall2(functionName("MySome"), _) =>
    })
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = TemplarTestCompilation.test(readCodeFromResource("programs/virtuals/upcasting.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")

    main.only({ case ReferenceLocalVariable2(FullName2(_,CodeVarName2("x")),Final,Coord(Own,Readwrite,InterfaceRef2(simpleName("MyInterface")))) => })

    val upcast = main.onlyOf(classOf[StructToInterfaceUpcast2])
    vassert(upcast.resultRegister.reference == Coord(Own,Readwrite,InterfaceRef2(FullName2(List(), CitizenName2("MyInterface", List())))))
    vassert(upcast.innerExpr.resultRegister.reference == Coord(Own,Readwrite,StructRef2(FullName2(List(), CitizenName2("MyStruct", List())))))
  }

  test("Tests calling a virtual function") {
    val compile = TemplarTestCompilation.test(readCodeFromResource("programs/virtuals/calling.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case up @ StructToInterfaceUpcast2(innerExpr, InterfaceRef2(simpleName("Car"))) => {
        innerExpr.resultRegister.only({
          case StructRef2(simpleName("Toyota")) =>
        })
        vassert(up.resultRegister.reference.referend == InterfaceRef2(FullName2(List(), CitizenName2("Car", List()))))
      }
    })
  }

  test("Tests calling a virtual function through a borrow ref") {
    val compile = TemplarTestCompilation.test(readCodeFromResource("programs/virtuals/callingThroughBorrow.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case f @ FunctionCall2(Prototype2(simpleName("doCivicDance"),Coord(Share,Readonly,Int2())), _) => {
//        vassert(f.callable.paramTypes == List(Coord(Borrow,InterfaceRef2(simpleName("Car")))))
      }
    })
  }

  test("Tests calling a templated function with explicit template args") {
    // Tests putting MyOption<int> as the type of x.
    val compile = TemplarTestCompilation.test(
      """
        |fn moo<T> () rules(T Ref) { }
        |
        |fn main() {
        |	moo<int>();
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  // See DSDCTD
  test("Tests destructuring shared doesnt compile to destroy") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Vec3i imm {
        |  x int;
        |  y int;
        |  z int;
        |}
        |
        |fn main() int export {
        |	 Vec3i(x, y, z) = Vec3i(3, 4, 5);
        |  = y;
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    temputs.lookupFunction("main").all({
      case Destroy2(_, _, _) =>
    }).size shouldEqual 0

    // Make sure there's a destroy in its destructor though.
    val destructor =
        temputs.functions.find(_.header.fullName.last.isInstanceOf[ImmConcreteDestructorName2]).get
    destructor.only({
      case Destroy2(_, StructRef2(FullName2(_, CitizenName2("Vec3i", _))), _) =>
    })
  }

  // See DSDCTD
  test("Tests destructuring borrow doesnt compile to destroy") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Vec3i {
        |  x int;
        |  y int;
        |  z int;
        |}
        |
        |fn main() int export {
        |  v = Vec3i(3, 4, 5);
        |	 (x, y, z) = &v;
        |  = y;
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")

    main.all({
      case Destroy2(_, _, _) =>
    }).size shouldEqual 0

    main.only({
      case ReferenceMemberLookup2(_,
        SoftLoad2(LocalLookup2(_, _, Coord(_,_,StructRef2(_)), Final), Constraint, Readonly),
        FullName2(List(CitizenName2("Vec3i",List())),CodeVarName2("x")),Coord(Share,Readonly,Int2()),Readonly,Final) =>
    })
  }

  test("Tests making a variable with a pattern") {
    // Tests putting MyOption<int> as the type of x.
    val compile = TemplarTestCompilation.test(
      """
        |interface MyOption<T> rules(T Ref) { }
        |
        |struct MySome<T> rules(T Ref) {}
        |impl<T> MyOption<T> for MySome<T>;
        |
        |fn doSomething(opt MyOption<int>) int {
        |  = 9;
        |}
        |
        |fn main() int export {
        |	x MyOption<int> = MySome<int>();
        |	= doSomething(x);
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Tests a linked list") {
    val compile = TemplarTestCompilation.test(
      Tests.loadExpected("programs/virtuals/ordinarylinkedlist.vale"))
    val temputs = compile.expectTemputs()
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
      case Function2(header @ functionName("doThing"), _, _) if header.getAbstractInterface != None => true
    }).get
  }

  test("Tests a foreach for a linked list") {
    val compile = TemplarTestCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/foreachlinkedlist.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case f @ FunctionCall2(functionName("forEach"), _) => f
    })

  }

  // Make sure a ListNode struct made it out
  test("Templated imm struct") {
    val compile = TemplarTestCompilation.test(
      """
        |struct ListNode<T> imm rules(T Ref) {
        |  tail ListNode<T>;
        |}
        |fn main(a ListNode<int>) {}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }


  test("Test Array of StructTemplata") {
    val compile = TemplarTestCompilation.test(
      """
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

  test("Test array length") {
    val compile = TemplarTestCompilation.test(
      """
        |import array.make.*;
        |
        |fn main() int export {
        |  a = MakeArray(11, {_});
        |  = len(&a);
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Test return") {
    val compile = TemplarTestCompilation.test(
      """
        |fn main() int export {
        |  ret 7;
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    main.only({ case Return2(_) => })
  }

  test("Test return from inside if") {
    val compile = TemplarTestCompilation.test(
      """
        |fn main() int export {
        |  if (true) {
        |    ret 7;
        |  } else {
        |    ret 9;
        |  }
        |  = __panic();
        |}
      """.stripMargin)
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    main.all({ case Return2(_) => }).size shouldEqual 2
    main.only({ case IntLiteral2(7) => })
    main.only({ case IntLiteral2(9) => })
  }

  test("Test return from inside if destroys locals") {
    val compile = TemplarTestCompilation.test(
      """struct Marine { hp int; }
        |fn main() int export {
        |  m = Marine(5);
        |  x =
        |    if (true) {
        |      ret 7;
        |    } else {
        |      = m.hp;
        |    };
        |  = x;
        |}
        |""".stripMargin)// +
//        Tests.loadExpected("castutils/castutils.vale") +
//        Tests.loadExpected("printutils/printutils.vale"))
    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    val destructorCalls =
      main.all({ case fpc @ FunctionCall2(Prototype2(FullName2(List(), FunctionName2("destructor",List(CoordTemplata(Coord(Own,Readwrite,StructRef2(simpleName("Marine"))))), _)), _),_) => fpc })
    destructorCalls.size shouldEqual 2
  }

  test("Test complex interface") {
    val compile = TemplarTestCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/templatedinterface.vale"))
    val temputs = compile.expectTemputs()
  }

  test("Lambda inside template") {
    // This originally didn't work because both helperFunc<int> and helperFunc<Str>
    // made a closure struct called helperFunc:lam1, which collided.
    // This is what spurred paackage support.

    val compile = TemplarTestCompilation.test(
      """
        |import printutils.*;
        |
        |fn helperFunc<T>(x T) {
        |  { print(x); }();
        |}
        |fn main() {
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
        |import printutils.*;
        |
        |fn helperFunc(x int) {
        |  { print(x); }();
        |}
        |fn helperFunc(x str) {
        |  { print(x); }();
        |}
        |fn main() {
        |  helperFunc(4);
        |  helperFunc("bork");
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()
  }

  test("Reports when reading nonexistant local") {
    val compile = TemplarTestCompilation.test(
      """fn main() int export {
        |  moo
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CouldntFindIdentifierToLoadT(_, "moo")) =>
    }
  }

  test("Reports when RW param in pure func") {
    val compile = TemplarTestCompilation.test(
      """struct Spaceship { }
        |fn main(ship &!Spaceship) int pure {
        |  7
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(NonReadonlyReferenceFoundInPureFunctionParameter(_, CodeVarName2("ship"))) =>
    }
  }

  test("Reports when mutating after moving") {
    val compile = TemplarTestCompilation.test(
      """
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
        |  = 42;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantUseUnstackifiedLocal(_, CodeVarName2("newWeapon"))) =>
    }
  }

  test("Reports when reading after moving") {
    val compile = TemplarTestCompilation.test(
      """
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
        |  = 42;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantUseUnstackifiedLocal(_, CodeVarName2("newWeapon"))) =>
    }
  }

  test("Cant subscript non-subscriptable type") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Weapon {
        |  ammo! int;
        |}
        |
        |fn main() int export {
        |  weapon = Weapon(10);
        |  = weapon[42];
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CannotSubscriptT(_, StructRef2(FullName2(_, CitizenName2("Weapon", List()))))) =>
    }
  }

  test("Reports when two functions with same signature") {
    val compile = TemplarTestCompilation.test(
      """
        |fn moo() int export { 1337 }
        |fn moo() int export { 1448 }
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(FunctionAlreadyExists(_, _, Signature2(FullName2(List(), FunctionName2("moo", List(), List()))))) =>
    }
  }

  test("Reports when we give too many args") {
    val compile = TemplarTestCompilation.test(
      """
        |fn moo(a int, b bool, s str) int { a }
        |fn main() int export {
        |  moo(42, true, "hello", false)
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      // Err(     case WrongNumberOfArguments(_, _)) =>
      case Err(CouldntFindFunctionToCallT(_, seff)) => {
        vassert(seff.rejectedReasonByBanner.size == 1)
        seff.rejectedReasonByBanner.head._2 match {
          case WrongNumberOfArguments(4, 3) =>
        }
      }
    }
  }

  test("Lambda is compatible anonymous interface") {
    val compile = TemplarTestCompilation.test(
        """
          |interface AFunction1<P> rules(P Ref) {
          |  fn __call(virtual this &AFunction1<P>, a P) int;
          |}
          |fn main() {
          |  arr = &AFunction1<int>((_){ true });
          |}
          |""".stripMargin)

    compile.getTemputs() match {
      case Err(LambdaReturnDoesntMatchInterfaceConstructor(_)) =>
    }
  }

  test("Reports when mutating a tuple") {
    val compile = TemplarTestCompilation.test(
      """
        |fn main() export {
        |  t2 = [5, true, "V"];
        |  set t2.1 = false;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantMutateFinalMember(_, _, _)) =>
    }

    val compile2 = TemplarTestCompilation.test(
      """
        |fn main() export {
        |  t2 = [5, true, "V"];
        |  set t2[1] = false;
        |}
        |""".stripMargin)
    compile2.getTemputs() match {
      case Err(CantMutateFinalMember(_, _, _)) =>
    }
  }

  test("Lock weak member") {
    val compile = TemplarTestCompilation.test(
        """
          |import v.builtins.opt.*;
          |import v.builtins.logic.*;
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
          |fn printShipBase(ship &Spaceship) {
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
          |  ship = Spaceship("Neb", &&base);
          |  printShipBase(&ship);
          |  base^.drop(); // Destroys base.
          |  printShipBase(&ship);
          |}
          |""".stripMargin)

    compile.expectTemputs()
  }

  test("Humanize errors") {
    val fireflyKind = StructRef2(FullName2(List(), CitizenName2("Firefly", List())))
    val fireflyCoord = Coord(Own,Readwrite,fireflyKind)
    val serenityKind = StructRef2(FullName2(List(), CitizenName2("Serenity", List())))
    val serenityCoord = Coord(Own,Readwrite,serenityKind)
    val ispaceshipKind = InterfaceRef2(FullName2(List(), CitizenName2("ISpaceship", List())))
    val ispaceshipCoord = Coord(Own,Readwrite,ispaceshipKind)
    val unrelatedKind = StructRef2(FullName2(List(), CitizenName2("Spoon", List())))
    val unrelatedCoord = Coord(Own,Readwrite,unrelatedKind)

    val filenamesAndSources = FileCoordinateMap.test("blah blah blah\nblah blah blah")

    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindTypeT(RangeS.testZero, "Spaceship")).nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindFunctionToCallT(
        RangeS.testZero,
        ScoutExpectedFunctionFailure(
          CodeTypeNameA("someFunc"),
          List(),
          Map(),
          Map(),
          Map()))).nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindFunctionToCallT(
        RangeS.testZero,
        ScoutExpectedFunctionFailure(GlobalFunctionFamilyNameA(""), List(), Map(), Map(), Map())))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CannotSubscriptT(
        RangeS.testZero,
        fireflyKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindIdentifierToLoadT(
        RangeS.testZero,
        "spaceship"))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindMemberT(
        RangeS.testZero,
        "hp"))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      BodyResultDoesntMatch(
        RangeS.testZero,
        FunctionNameA("myFunc", CodeLocationS.testZero), fireflyCoord, serenityCoord))
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
        CodeVarName2("hp")))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantUseUnstackifiedLocal(
        RangeS.testZero,
        CodeVarName2("firefly")))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      FunctionAlreadyExists(
        RangeS.testZero,
        RangeS.testZero,
        Signature2(FullName2(List(), FunctionName2("myFunc", List(), List())))))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantMutateFinalMember(
        RangeS.testZero,
        serenityKind.fullName,
        FullName2(List(), CodeVarName2("bork"))))
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
      CantMutateFinalLocal(
        RangeS.testZero, CodeVarNameA("x")))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantImplStruct(
        RangeS.testZero, fireflyKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      ImmStructCantHaveVaryingMember(
        RangeS.testZero, TopLevelCitizenDeclarationNameA("SpaceshipSnapshot", CodeLocationS.testZero), "fuel"))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantDowncastUnrelatedTypes(
        RangeS.testZero, ispaceshipKind, unrelatedKind))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantDowncastToInterface(
        RangeS.testZero, ispaceshipKind))
      .nonEmpty)
  }
}
