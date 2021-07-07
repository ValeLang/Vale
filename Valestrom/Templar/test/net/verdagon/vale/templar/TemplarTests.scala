package net.verdagon.vale.templar

import net.verdagon.vale.parser.{CombinatorParsers, FileP, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier}
import net.verdagon.vale.scout.{CodeLocationS, CodeVarNameS, ICompileErrorS, ProgramS, RangeS, Scout, VariableNameAlreadyExists}
import net.verdagon.vale.templar.env.ReferenceLocalVariableT
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
      case FunctionHeaderT(simpleName("main"),List(UserFunction2),Nil, CoordT(ShareT, ReadonlyT, IntT.i32), _) => true
    })
    main.only({ case ConstantIntTE(3, _) => true })
  }

  test("Hardcoding negative numbers") {
    val compile = TemplarTestCompilation.test("fn main() int export {-3}")
    val main = compile.expectTemputs().lookupFunction("main")
    main.only({ case ConstantIntTE(-3, _) => true })
  }

  test("Tests panic return type") {
    val compile = TemplarTestCompilation.test(
      """
        |fn main() infer-ret export {
        |  __panic();
        |  a = 42;
        |  = a;
        |}
    """.stripMargin)
    val main = compile.expectTemputs().lookupFunction("main")
    vassert(main.header.returnType.kind == NeverT())
  }

  test("Taking an argument and returning it") {
    val compile = TemplarTestCompilation.test("fn main(a int) int {a}")
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").onlyOf(classOf[ParameterT]).tyype == CoordT(ShareT, ReadonlyT, IntT.i32)
    val lookup = temputs.lookupFunction("main").allOf(classOf[LocalLookupTE]).head;
    lookup.localVariable.id.last shouldEqual CodeVarNameT("a")
    lookup.reference shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
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
    main.only({ case ConstantIntTE(2, _) => true })
    main.only({ case ConstantIntTE(3, _) => true })
    main.only({
      case FunctionCallTE(
        functionName("+"),
        List(
          ConstantIntTE(2, _),
          ConstantIntTE(3, _))) =>
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
        case LetNormalTE(ReferenceLocalVariableT(FullNameT(_, _, CodeVarNameT("b")), _, tyype), _) => tyype
      })
    tyype.ownership shouldEqual ConstraintT
    tyype.permission shouldEqual ReadonlyT
  }

  test("Recursion") {
    val compile = TemplarTestCompilation.test("fn main() int export{main()}")
    val temputs = compile.expectTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupFunction("main").header.returnType shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
  }

  test("Simple lambda") {
    val compile = TemplarTestCompilation.test("fn main() int export {{7}()}")
    val temputs = compile.expectTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupFunction("__call").header.returnType shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
    temputs.lookupFunction("main").header.returnType shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
  }

//  test("Infer") {
//    val compile =
//      TemplarTestCompilation.test(
//        """
//          |struct Ship { }
//          |fn moo<T>(a &!T) &!T { a }
//          |fn main() export {
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
        .only({ case ParameterT(_, None, CoordT(ShareT, ReadonlyT, IntT.i32)) => })

    temputs.lookupLambdaIn("main").header.returnType shouldEqual
        CoordT(ShareT, ReadonlyT, IntT.i32)
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
    lambda.only({ case ParameterT(CodeVarNameT("a"), None, CoordT(ShareT, ReadonlyT, IntT.i32)) => {} })
    // Check the name is right
    vassert(temputs.nameIsLambdaIn(lambda.header.fullName, "main"))

    val main = temputs.lookupFunction("main");
    main.only({ case FunctionCallTE(callee, _) if temputs.nameIsLambdaIn(callee.fullName, "main") => })
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
        types shouldEqual Set(CoordT(ShareT, ReadonlyT, IntT.i32), CoordT(ShareT, ReadonlyT, BoolT()))
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

    temputs.functions.collect({ case x @ functionName("do") => x }).head.header.returnType shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
  }

  test("Calls destructor on local var") {
    val compile = TemplarTestCompilation.test(
      """struct Muta { }
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
    main.only({ case FunctionCallTE(functionName(CallTemplar.MUT_DESTRUCTOR_NAME), _) => })
    main.all({ case FunctionCallTE(_, _) => }).size shouldEqual 2
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
        |fn main() export {
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
      case StructDefinitionT(
      simpleName("MyStruct"),
      _,
      false,
      MutableT,
      List(StructMemberT(CodeVarNameT("a"), FinalT, ReferenceMemberTypeT(CoordT(ShareT, ReadonlyT, IntT.i32)))),
      false) =>
    }).get
    // Check there's a constructor
    temputs.lookupFunction("MyStruct").only({
      case FunctionHeaderT(
      simpleName("MyStruct"),
      _,
      List(ParameterT(CodeVarNameT("a"), None, CoordT(ShareT, ReadonlyT, IntT.i32))),
      CoordT(OwnT,ReadwriteT, StructRefT(simpleName("MyStruct"))),
      _) =>
    })
    val main = temputs.lookupFunction("main")
    // Check that we call the constructor
    main.only({
      case FunctionCallTE(
        PrototypeT(simpleName("MyStruct"), _),
        List(ConstantIntTE(7, _))) =>
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
        case id @ InterfaceDefinitionT(simpleName("MyInterface"), _, false, MutableT, Nil) => id
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
        |interface MyOption<T> rules(T Ref) { }
        |fn main(a MyOption<int>) { }
      """.stripMargin)
    val temputs = compile.expectTemputs()

    temputs.lookupInterface(
      InterfaceRefT(
        FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MyOption", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))))
    vassert(temputs.lookupFunction("main").header.params.head.tyype ==
        CoordT(OwnT,ReadwriteT,InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MyOption", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))))))))

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
        InterfaceRefT(
          FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MyOption", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))))

    val struct = temputs.lookupStruct(StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MySome", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))));

    temputs.lookupImpl(struct.getRef, interface.getRef)
  }

  test("Tests single expression and single statement functions' returns") {
    val compile = TemplarTestCompilation.test(
      """
        |struct MyThing { value int; }
        |fn moo() MyThing { MyThing(4) }
        |fn main() export { moo(); }
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val moo = temputs.lookupFunction("moo")
    moo.header.returnType match {
      case CoordT(OwnT,ReadwriteT,StructRefT(simpleName("MyThing"))) =>
    }
    val main = temputs.lookupFunction("main")
    main.header.returnType match {
      case CoordT(ShareT, ReadonlyT, VoidT()) =>
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

    temputs.lookupStruct(StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MySome", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32)))))));

    val constructor = temputs.lookupFunction("MySome")
    constructor.header match {
      case
        FunctionHeaderT(
        simpleName("MySome"),
        _,
        _,
        CoordT(OwnT,ReadwriteT,StructRefT(FullNameT(_, Nil, CitizenNameT("MySome", List(CoordTemplata(CoordT(ShareT, ReadonlyT, IntT.i32))))))),
        _) =>
    }

    temputs.lookupFunction("main").only({
      case FunctionCallTE(functionName("MySome"), _) =>
    })
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = TemplarTestCompilation.test(readCodeFromResource("programs/virtuals/upcasting.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")

    main.only({ case ReferenceLocalVariableT(FullNameT(_,_,CodeVarNameT("x")),FinalT,CoordT(OwnT,ReadwriteT,InterfaceRefT(simpleName("MyInterface")))) => })

    val upcast = main.onlyOf(classOf[StructToInterfaceUpcastTE])
    vassert(upcast.resultRegister.reference == CoordT(OwnT,ReadwriteT,InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MyInterface", Nil)))))
    vassert(upcast.innerExpr.resultRegister.reference == CoordT(OwnT,ReadwriteT,StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("MyStruct", Nil)))))
  }

  test("Tests calling a virtual function") {
    val compile = TemplarTestCompilation.test(readCodeFromResource("programs/virtuals/calling.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case up @ StructToInterfaceUpcastTE(innerExpr, InterfaceRefT(simpleName("Car"))) => {
        innerExpr.resultRegister.only({
          case StructRefT(simpleName("Toyota")) =>
        })
        vassert(up.resultRegister.reference.kind == InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("Car", Nil))))
      }
    })
  }

  test("Tests calling a virtual function through a borrow ref") {
    val compile = TemplarTestCompilation.test(readCodeFromResource("programs/virtuals/callingThroughBorrow.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case f @ FunctionCallTE(PrototypeT(simpleName("doCivicDance"),CoordT(ShareT,ReadonlyT,IntT.i32)), _) => {
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
      case DestroyTE(_, _, _) =>
    }).size shouldEqual 0

    // Make sure there's a destroy in its destructor though.
    val destructor =
        temputs.functions.find(_.header.fullName.last.isInstanceOf[ImmConcreteDestructorNameT]).get
    destructor.only({
      case DestroyTE(_, StructRefT(FullNameT(_, _, CitizenNameT("Vec3i", _))), _) =>
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
      case DestroyTE(_, _, _) =>
    }).size shouldEqual 0

    main.only({
      case ReferenceMemberLookupTE(_,
        SoftLoadTE(LocalLookupTE(_, _, CoordT(_,_,StructRefT(_)), FinalT), ConstraintT, ReadonlyT),
        FullNameT(_, List(CitizenNameT("Vec3i",Nil)),CodeVarNameT("x")),CoordT(ShareT,ReadonlyT,IntT.i32),ReadonlyT,FinalT) =>
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
      case FunctionT(header @ functionName("doThing"), _, _) if header.getAbstractInterface != None => true
    }).get
  }

  test("Tests a foreach for a linked list") {
    val compile = TemplarTestCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/foreachlinkedlist.vale"))
    val temputs = compile.expectTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case f @ FunctionCallTE(functionName("forEach"), _) => f
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
        |  patternTiles Array<imm, final, Vec2>;
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
    main.only({ case ReturnTE(_) => })
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
    main.all({ case ReturnTE(_) => }).size shouldEqual 2
    main.only({ case ConstantIntTE(7, _) => })
    main.only({ case ConstantIntTE(9, _) => })
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
      main.all({ case fpc @ FunctionCallTE(PrototypeT(FullNameT(_, Nil, FunctionNameT("destructor",List(CoordTemplata(CoordT(OwnT,ReadwriteT,StructRefT(simpleName("Marine"))))), _)), _),_) => fpc })
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
      case Err(NonReadonlyReferenceFoundInPureFunctionParameter(_, CodeVarNameT("ship"))) =>
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
      case Err(CantUseUnstackifiedLocal(_, CodeVarNameT("newWeapon"))) =>
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
      case Err(CantUseUnstackifiedLocal(_, CodeVarNameT("newWeapon"))) =>
    }
  }

  test("Reports when moving from inside a while") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Marine {
        |  ammo int;
        |}
        |
        |fn main() int export {
        |  m = Marine(7);
        |  while (false) {
        |    drop(m);
        |  }
        |  = 42;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantUnstackifyOutsideLocalFromInsideWhile(_, CodeVarNameT("m"))) =>
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
      case Err(CannotSubscriptT(_, StructRefT(FullNameT(_, _, CitizenNameT("Weapon", Nil))))) =>
    }
  }

  test("Reports when two functions with same signature") {
    val compile = TemplarTestCompilation.test(
      """
        |fn moo() int export { 1337 }
        |fn moo() int export { 1448 }
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(FunctionAlreadyExists(_, _, SignatureT(FullNameT(_, Nil, FunctionNameT("moo", Nil, Nil))))) =>
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
          |fn main() export {
          |  arr = &AFunction1<int>((_){ true });
          |}
          |""".stripMargin)

    compile.getTemputs() match {
      case Err(LambdaReturnDoesntMatchInterfaceConstructor(_)) =>
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
          |  (base).drop(); // Destroys base.
          |  printShipBase(&ship);
          |}
          |""".stripMargin)

    compile.expectTemputs()
  }

  test("Humanize errors") {
    val fireflyKind = StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("Firefly", Nil)))
    val fireflyCoord = CoordT(OwnT,ReadwriteT,fireflyKind)
    val serenityKind = StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("Serenity", Nil)))
    val serenityCoord = CoordT(OwnT,ReadwriteT,serenityKind)
    val ispaceshipKind = InterfaceRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("ISpaceship", Nil)))
    val ispaceshipCoord = CoordT(OwnT,ReadwriteT,ispaceshipKind)
    val unrelatedKind = StructRefT(FullNameT(PackageCoordinate.TEST_TLD, Nil, CitizenNameT("Spoon", Nil)))
    val unrelatedCoord = CoordT(OwnT,ReadwriteT,unrelatedKind)

    val filenamesAndSources = FileCoordinateMap.test("blah blah blah\nblah blah blah")

    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindTypeT(RangeS.testZero, "Spaceship")).nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindFunctionToCallT(
        RangeS.testZero,
        ScoutExpectedFunctionFailure(
          CodeTypeNameA("someFunc"),
          Nil,
          Map(),
          Map(),
          Map()))).nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindFunctionToCallT(
        RangeS.testZero,
        ScoutExpectedFunctionFailure(GlobalFunctionFamilyNameA(""), Nil, Map(), Map(), Map())))
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
      FunctionAlreadyExists(
        RangeS.testZero,
        RangeS.testZero,
        SignatureT(FullNameT(PackageCoordinate.TEST_TLD, Nil, FunctionNameT("myFunc", Nil, Nil)))))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantMutateFinalMember(
        RangeS.testZero,
        serenityKind.fullName,
        FullNameT(PackageCoordinate.TEST_TLD, Nil, CodeVarNameT("bork"))))
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
