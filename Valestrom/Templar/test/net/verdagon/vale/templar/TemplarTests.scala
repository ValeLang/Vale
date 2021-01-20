package net.verdagon.vale.templar

import net.verdagon.vale.parser.{CombinatorParsers, FileP, ParseErrorHumanizer, ParseFailure, ParseSuccess, Parser}
import net.verdagon.vale.scout.{CodeLocationS, CodeVarNameS, ProgramS, RangeS, Scout, VariableNameAlreadyExists}
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale._
import net.verdagon.vale.astronomer.{Astronomer, FunctionNameA, GlobalFunctionFamilyNameA, IFunctionDeclarationNameA, ProgramA}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, WrongNumberOfArguments}
import org.scalatest.{FunSuite, Matchers, _}

import scala.collection.immutable.List
import scala.io.Source

object TemplarCompilation {
  def multiple(code: List[String]): TemplarCompilation = {
    new TemplarCompilation(code.zipWithIndex.map({ case (code, index) => (index + ".vale", code) }))
  }
  def apply(code: String): TemplarCompilation = {
    new TemplarCompilation(List(("in.vale", code)))
  }
}

class TemplarCompilation(var filenamesAndSources: List[(String, String)]) {
  filenamesAndSources = filenamesAndSources :+ ("builtins/builtinexterns.vale", Samples.get("builtins/builtinexterns.vale"))

  var parsedsCache: Option[List[FileP]] = None
  var scoutputCache: Option[ProgramS] = None
  var astroutsCache: Option[ProgramA] = None
  var hinputsCache: Option[Hinputs] = None

  def getParseds(): List[FileP] = {
    parsedsCache match {
      case Some(parseds) => parseds
      case None => {
        parsedsCache =
          Some(
            filenamesAndSources.zipWithIndex.map({ case ((filename, source), fileIndex) =>
              Parser.runParserForProgramAndCommentRanges(source) match {
                case ParseFailure(err) => {
                  vwat(ParseErrorHumanizer.humanize(filenamesAndSources, fileIndex, err))
                }
                case ParseSuccess((program0, _)) => {
                  program0
                }
              }
            }))
        parsedsCache.get
      }
    }
  }

  def getScoutput(): ProgramS = {
    scoutputCache match {
      case Some(scoutput) => scoutput
      case None => {
        val scoutput =
          Scout.scoutProgram(getParseds()) match {
            case Err(e) => vfail(e.toString)
            case Ok(p) => p
          }
        scoutputCache = Some(scoutput)
        scoutput
      }
    }
  }

  def getAstrouts(): ProgramA = {
    astroutsCache match {
      case Some(astrouts) => astrouts
      case None => {
        Astronomer.runAstronomer(getScoutput()) match {
          case Right(err) => vfail(err.toString)
          case Left(astrouts) => {
            astroutsCache = Some(astrouts)
            astrouts
          }
        }
      }
    }
  }

  def getTemputs(): Hinputs = {
    hinputsCache match {
      case Some(temputs) => temputs
      case None => {
        val hamuts =
          new Templar(println, true, new NullProfiler(), true).evaluate(getAstrouts()) match {
            case Ok(t) => t
            case Err(e) => vfail(TemplarErrorHumanizer.humanize(true, filenamesAndSources, e))
          }
        hinputsCache = Some(hamuts)
        hamuts
      }
    }
  }


  def getTemplarError(): ICompileErrorT = {
    hinputsCache match {
      case Some(temputs) => vfail()
      case None => {

        val debugOut = (string: String) => { println(string) }

        new Templar(debugOut, true, new NullProfiler(), false).evaluate(getAstrouts()) match {
          case Ok(t) => vfail("Accidentally successfully compiled:\n" + t.toString)
          case Err(e) => e
        }
      }
    }
  }
}

class TemplarTests extends FunSuite with Matchers {
  // TODO: pull all of the templar specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }


  test("Simple program returning an int") {
    val compile = TemplarCompilation("fn main() infer-ret {3}")
    val temputs = compile.getTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case FunctionHeader2(simpleName("main"),List(UserFunction2),List(), Coord(Share, Int2()), _) => true
    })
    main.only({ case IntLiteral2(3) => true })
  }

  test("Hardcoding negative numbers") {
    val compile = TemplarCompilation("fn main() int export {-3}")
    val main = compile.getTemputs().lookupFunction("main")
    main.only({ case IntLiteral2(-3) => true })
  }

  test("Taking an argument and returning it") {
    val compile = TemplarCompilation("fn main(a int) int {a}")
    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").onlyOf(classOf[Parameter2]).tyype == Coord(Share, Int2())
    val lookup = temputs.lookupFunction("main").allOf(classOf[LocalLookup2]).head;
    lookup.localVariable.id.last shouldEqual CodeVarName2("a")
    lookup.reference shouldEqual Coord(Share, Int2())
  }

  test("Tests adding two numbers") {
    val compile = TemplarCompilation("fn main() int export { +(2, 3) }")
    val temputs = compile.getTemputs()
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

  test("Recursion") {
    val compile = TemplarCompilation("fn main() int export{main()}")
    val temputs = compile.getTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupFunction("main").header.returnType shouldEqual Coord(Share, Int2())
  }

  test("Simple lambda") {
    val compile = TemplarCompilation("fn main() int export {{7}()}")
    val temputs = compile.getTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupFunction("__call").header.returnType shouldEqual Coord(Share, Int2())
    temputs.lookupFunction("main").header.returnType shouldEqual Coord(Share, Int2())
  }

  test("Lambda with one magic arg") {
    val compile = TemplarCompilation("fn main() int export {{_}(3)}")
    val temputs = compile.getTemputs()

    // Make sure it inferred the param type and return type correctly
    temputs.lookupLambdaIn("main")
        .only({ case Parameter2(_, None, Coord(Share, Int2())) => })

    temputs.lookupLambdaIn("main").header.returnType shouldEqual
        Coord(Share, Int2())
  }


  // Test that the lambda's arg is the right type, and the name is right
  test("Lambda with a type specified param") {
    val compile = TemplarCompilation("fn main() int export {(a int){+(a,a)}(3)}");
    val temputs = compile.getTemputs()

    val lambda = temputs.lookupLambdaIn("main");

    // Check that the param type is right
    lambda.only({ case Parameter2(CodeVarName2("a"), None, Coord(Share, Int2())) => {} })
    // Check the name is right
    vassert(temputs.nameIsLambdaIn(lambda.header.fullName, "main"))

    val main = temputs.lookupFunction("main");
    main.only({ case FunctionCall2(callee, _) if temputs.nameIsLambdaIn(callee.fullName, "main") => })
  }

  test("Test overloads") {
    val compile = TemplarCompilation(Samples.get("programs/functions/overloads.vale"))
    val temputs = compile.getTemputs()

    temputs.lookupFunction("main").header.returnType shouldEqual
        Coord(Share, Int2())
  }

  test("Test templates") {
    val compile = TemplarCompilation(
      """
        |fn ~<T>(a T, b T)T{a}
        |fn main() int export {true ~ false; 2 ~ 2; = 3 ~ 3;}
      """.stripMargin)
    val temputs = compile.getTemputs()

    // Tests that we reuse existing stamps
    vassert(temputs.getAllUserFunctions.size == 3)
  }

  test("Test mutating a local var") {
    val compile = TemplarCompilation("fn main() {a! = 3; mut a = 4; }")
    val temputs = compile.getTemputs();
    val main = temputs.lookupFunction("main")
    main.only({ case Mutate2(LocalLookup2(_,ReferenceLocalVariable2(FullName2(_, CodeVarName2("a")), Varying, _), _, Varying), IntLiteral2(4)) => })
  }

  test("Test taking a callable param") {
    val compile = TemplarCompilation(
      """
        |fn do(callable) infer-ret {callable()}
        |fn main() int export {do({ 3 })}
      """.stripMargin)
    val temputs = compile.getTemputs()

    temputs.functions.collect({ case x @ functionName("do") => x }).head.header.returnType shouldEqual Coord(Share, Int2())
  }

  test("Calls destructor on local var") {
    val compile = TemplarCompilation(
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

    val main = compile.getTemputs().lookupFunction("main")
    main.only({ case FunctionCall2(functionName(CallTemplar.MUT_DESTRUCTOR_NAME), _) => })
    main.all({ case FunctionCall2(_, _) => }).size shouldEqual 2
  }

  test("Stamps an interface template via a function return") {
    val compile = TemplarCompilation(
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
    val temputs = compile.getTemputs()
  }
//
//  test("Constructor is stamped even without calling") {
//    val compile = Compilation(
//      """
//        |struct MyStruct imm {}
//        |fn wot(b: *MyStruct) int { 9 }
//      """.stripMargin)
//    val temputs = compile.getTemputs()
//
//    temputs.lookupFunction("MyStruct")
//  }

  test("Reads a struct member") {
    val compile = TemplarCompilation(
      """
        |struct MyStruct { a int; }
        |fn main() int export { ms = MyStruct(7); = ms.a; }
      """.stripMargin)
    val temputs = compile.getTemputs()

    // Check the struct was made
    temputs.structs.collectFirst({
      case StructDefinition2(
      simpleName("MyStruct"),
      _,
      false,
      Mutable,
      List(StructMember2(CodeVarName2("a"), Final, ReferenceMemberType2(Coord(Share, Int2())))),
      false) =>
    }).get
    // Check there's a constructor
    temputs.lookupFunction("MyStruct").only({
      case FunctionHeader2(
      simpleName("MyStruct"),
      _,
      List(Parameter2(CodeVarName2("a"), None, Coord(Share, Int2()))),
      Coord(Own, StructRef2(simpleName("MyStruct"))),
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
    val compile = TemplarCompilation(
      """
        |interface MyInterface { }
        |struct MyStruct { }
        |impl MyInterface for MyStruct;
        |fn main(a MyStruct) {}
      """.stripMargin)
    val temputs = compile.getTemputs()

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
    val compile = TemplarCompilation(
      """
        |interface MyOption<T> rules(T Ref) { }
        |fn main(a MyOption<int>) { }
      """.stripMargin)
    val temputs = compile.getTemputs()

    temputs.lookupInterface(
      InterfaceRef2(
        FullName2(List(), CitizenName2("MyOption", List(CoordTemplata(Coord(Share, Int2())))))))
    vassert(temputs.lookupFunction("main").header.params.head.tyype ==
        Coord(Own,InterfaceRef2(FullName2(List(), CitizenName2("MyOption", List(CoordTemplata(Coord(Share, Int2()))))))))

    // Can't run it because there's nothing implementing that interface >_>
  }

  test("Tests exporting function") {
    val compile = TemplarCompilation(
      """
        |fn moo() export { }
        |""".stripMargin)
    val temputs = compile.getTemputs()
    val moo = temputs.lookupFunction("moo")
    moo.header.isExport shouldEqual true
  }

  test("Tests exporting struct") {
    val compile = TemplarCompilation(
      """
        |struct Moo export { a int; }
        |""".stripMargin)
    val temputs = compile.getTemputs()
    val moo = temputs.lookupStruct("Moo")
    moo.attributes.contains(Export2) shouldEqual true
  }

  test("Tests exporting interface") {
    val compile = TemplarCompilation(
      """
        |interface IMoo export { fn hi(virtual this &IMoo) void; }
        |""".stripMargin)
    val temputs = compile.getTemputs()
    val moo = temputs.lookupInterface("IMoo")
    moo.attributes.contains(Export2) shouldEqual true
  }

  test("Tests stamping a struct and its implemented interface from a function param") {
    val compile = TemplarCompilation(
      """
        |interface MyOption<T> imm rules(T Ref) { }
        |struct MySome<T> imm rules(T Ref) { value T; }
        |impl<T> MyOption<T> for MySome<T>;
        |fn moo(a MySome<int>) export { }
        |""".stripMargin)
    val temputs = compile.getTemputs()

    val interface =
      temputs.lookupInterface(
        InterfaceRef2(
          FullName2(List(), CitizenName2("MyOption", List(CoordTemplata(Coord(Share, Int2())))))))

    val struct = temputs.lookupStruct(StructRef2(FullName2(List(), CitizenName2("MySome", List(CoordTemplata(Coord(Share, Int2())))))));

    temputs.lookupImpl(struct.getRef, interface.getRef)
  }

  test("Tests single expression and single statement functions' returns") {
    val compile = TemplarCompilation(
      """
        |struct MyThing { value int; }
        |fn moo() MyThing { MyThing(4) }
        |fn main() { moo(); }
      """.stripMargin)

    val temputs = compile.getTemputs()
    val moo = temputs.lookupFunction("moo")
    moo.header.returnType match {
      case Coord(Own,StructRef2(simpleName("MyThing"))) =>
    }
    val main = temputs.lookupFunction("main")
    main.header.returnType match {
      case Coord(Share, Void2()) =>
    }
  }

  test("Tests calling a templated struct's constructor") {
    val compile = TemplarCompilation(
      """
        |struct MySome<T> rules(T Ref) { value T; }
        |fn main() int export {
        |  MySome<int>(4).value
        |}
        |""".stripMargin
    )

    val temputs = compile.getTemputs()

    temputs.lookupStruct(StructRef2(FullName2(List(), CitizenName2("MySome", List(CoordTemplata(Coord(Share, Int2())))))));

    val constructor = temputs.lookupFunction("MySome")
    constructor.header match {
      case
        FunctionHeader2(
        simpleName("MySome"),
        _,
        _,
        Coord(Own, StructRef2(FullName2(List(), CitizenName2("MySome", List(CoordTemplata(Coord(Share, Int2()))))))),
        _) =>
    }

    temputs.lookupFunction("main").only({
      case FunctionCall2(functionName("MySome"), _) =>
    })
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = TemplarCompilation(readCodeFromResource("programs/virtuals/upcasting.vale"))
    val temputs = compile.getTemputs()

    val main = temputs.lookupFunction("main")

    main.only({ case ReferenceLocalVariable2(FullName2(_,CodeVarName2("x")),Final,Coord(Own,InterfaceRef2(simpleName("MyInterface")))) => })

    val upcast = main.onlyOf(classOf[StructToInterfaceUpcast2])
    vassert(upcast.resultRegister.reference == Coord(Own, InterfaceRef2(FullName2(List(), CitizenName2("MyInterface", List())))))
    vassert(upcast.innerExpr.resultRegister.reference == Coord(Own, StructRef2(FullName2(List(), CitizenName2("MyStruct", List())))))
  }

  test("Tests calling a virtual function") {
    val compile = TemplarCompilation(readCodeFromResource("programs/virtuals/calling.vale"))
    val temputs = compile.getTemputs()

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
    val compile = TemplarCompilation(readCodeFromResource("programs/virtuals/callingThroughBorrow.vale"))
    val temputs = compile.getTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case f @ FunctionCall2(Prototype2(simpleName("doCivicDance"),Coord(Share,Int2())), _) => {
//        vassert(f.callable.paramTypes == List(Coord(Borrow,InterfaceRef2(simpleName("Car")))))
      }
    })
  }

  test("Tests calling a templated function with explicit template args") {
    // Tests putting MyOption<int> as the type of x.
    val compile = TemplarCompilation(
      """
        |fn moo<T> () rules(T Ref) { }
        |
        |fn main() {
        |	moo<int>();
        |}
      """.stripMargin)
    val temputs = compile.getTemputs()
  }

  // See DSDCTD
  test("Tests destructuring shared doesnt compile to destroy") {
    val compile = TemplarCompilation(
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
    val temputs = compile.getTemputs()

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
    val compile = TemplarCompilation(
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
    val temputs = compile.getTemputs()

    val main = temputs.lookupFunction("main")

    main.all({
      case Destroy2(_, _, _) =>
    }).size shouldEqual 0

    main.only({
      case ReferenceMemberLookup2(_,
        SoftLoad2(LocalLookup2(_, _, Coord(_,StructRef2(_)), Final), Borrow),
        FullName2(List(CitizenName2("Vec3i",List())),CodeVarName2("x")),Coord(Share,Int2()),Final, Share) =>
    })
  }

  test("Tests making a variable with a pattern") {
    // Tests putting MyOption<int> as the type of x.
    val compile = TemplarCompilation(
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
    val temputs = compile.getTemputs()
  }

  test("Tests a linked list") {
    val compile = TemplarCompilation(
      Samples.get("programs/virtuals/ordinarylinkedlist.vale") +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))
    val temputs = compile.getTemputs()
  }

  test("Tests a templated linked list") {
    val compile = TemplarCompilation(
      Samples.get("programs/genericvirtuals/templatedlinkedlist.vale") +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))
    val temputs = compile.getTemputs()
  }

  test("Tests calling an abstract function") {
    val compile = TemplarCompilation(
      Samples.get("programs/genericvirtuals/callingAbstract.vale") +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))
    val temputs = compile.getTemputs()

    temputs.functions.collectFirst({
      case Function2(header @ functionName("doThing"), _, _) if header.getAbstractInterface != None => true
    }).get
  }

  test("Tests a foreach for a linked list") {
    val compile = TemplarCompilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("programs/genericvirtuals/foreachlinkedlist.vale"))
    val temputs = compile.getTemputs()

    val main = temputs.lookupFunction("main")
    main.only({
      case f @ FunctionCall2(functionName("forEach"), _) => f
    })
  }

  // Make sure a ListNode struct made it out
  test("Templated imm struct") {
    val compile = TemplarCompilation(
      """
        |struct ListNode<T> imm rules(T Ref) {
        |  tail ListNode<T>;
        |}
        |fn main(a ListNode<int>) {}
      """.stripMargin)
    val temputs = compile.getTemputs()
  }


  test("Test Array of StructTemplata") {
    val compile = TemplarCompilation(
      """
        |struct Vec2 imm {
        |  x float;
        |  y float;
        |}
        |struct Pattern imm {
        |  patternTiles Array<imm, Vec2>;
        |}
      """.stripMargin)
    val temputs = compile.getTemputs()
  }

  test("Test array length") {
    val compile = TemplarCompilation(
      """
        |fn main() int export {
        |  a = Array<mut, int>(11, &IFunction1<imm, int, int>({_}));
        |  = len(&a);
        |}
      """.stripMargin)
    val temputs = compile.getTemputs()
  }

  test("Test return") {
    val compile = TemplarCompilation(
      """
        |fn main() int export {
        |  ret 7;
        |}
      """.stripMargin)
    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main.only({ case Return2(_) => })
  }

  test("Test return from inside if") {
    val compile = TemplarCompilation(
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
    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main.all({ case Return2(_) => }).size shouldEqual 2
    main.only({ case IntLiteral2(7) => })
    main.only({ case IntLiteral2(9) => })
  }

  test("Test return from inside if destroys locals") {
    val compile = TemplarCompilation(
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
        |""".stripMargin +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))
    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    val destructorCalls =
      main.all({ case fpc @ FunctionCall2(Prototype2(FullName2(List(), FunctionName2("destructor",List(CoordTemplata(Coord(Own,StructRef2(simpleName("Marine"))))), _)), _),_) => fpc })
    destructorCalls.size shouldEqual 2
  }

  test("Test complex interface") {
    val compile = TemplarCompilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("programs/genericvirtuals/templatedinterface.vale"))
    val temputs = compile.getTemputs()
  }

  test("Local-mut upcasts") {
    val compile = TemplarCompilation(
      """
        |interface IOption<T> rules(T Ref) { }
        |struct Some<T> rules(T Ref) { value T; }
        |impl<T> IOption<T> for Some<T>;
        |struct None<T> rules(T Ref) { }
        |impl<T> IOption<T> for None<T>;
        |
        |fn main() {
        |  m IOption<int> = None<int>();
        |  mut m = Some(6);
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main.only({
      case Mutate2(_, StructToInterfaceUpcast2(_, _)) =>
    })
  }

  test("Expr-mut upcasts") {
    val compile = TemplarCompilation(
      """
        |interface IOption<T> rules(T Ref) { }
        |struct Some<T> rules(T Ref) { value T; }
        |impl<T> IOption<T> for Some<T>;
        |struct None<T> rules(T Ref) { }
        |impl<T> IOption<T> for None<T>;
        |
        |struct Marine {
        |  weapon! IOption<int>;
        |}
        |fn main() {
        |  m = Marine(None<int>());
        |  mut m.weapon = Some(6);
        |}
      """.stripMargin +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main.only({
      case Mutate2(_, StructToInterfaceUpcast2(_, _)) =>
    })
  }

  test("Lambda inside template") {
    // This originally didn't work because both helperFunc<int> and helperFunc<Str>
    // made a closure struct called helperFunc:lam1, which collided.
    // This is what spurred namespace support.

    val compile = TemplarCompilation(
      """fn helperFunc<T>(x T) {
        |  { print(x); }();
        |}
        |fn main() {
        |  helperFunc(4);
        |  helperFunc("bork");
        |}
        |""".stripMargin +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))
    val temputs = compile.getTemputs()
  }


  test("Lambda inside different function with same name") {
    // This originally didn't work because both helperFunc(:Int) and helperFunc(:Str)
    // made a closure struct called helperFunc:lam1, which collided.
    // We need to disambiguate by parameters, not just template args.

    val compile = TemplarCompilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
      """fn helperFunc(x int) {
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
    val temputs = compile.getTemputs()
  }

  test("Reports when reading nonexistant local") {
    val compile = TemplarCompilation(
      """fn main() int export {
        |  moo
        |}
        |""".stripMargin)
    compile.getTemplarError() match {
      case CouldntFindIdentifierToLoadT(_, "moo") =>
    }
  }

  test("Reports when mutating after moving") {
    val compile = TemplarCompilation(
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
        |  mut m.weapon = newWeapon;
        |  mut newWeapon.ammo = 11;
        |  = 42;
        |}
        |""".stripMargin)
    compile.getTemplarError() match {
      case CantUseUnstackifiedLocal(_, CodeVarName2("newWeapon")) =>
    }
  }

  test("Reports when reading after moving") {
    val compile = TemplarCompilation(
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
        |  mut m.weapon = newWeapon;
        |  println(newWeapon.ammo);
        |  = 42;
        |}
        |""".stripMargin)
    compile.getTemplarError() match {
      case CantUseUnstackifiedLocal(_, CodeVarName2("newWeapon")) =>
    }
  }

  test("Cant subscript non-subscriptable type") {
    val compile = TemplarCompilation(
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
    compile.getTemplarError() match {
      case CannotSubscriptT(_, StructRef2(FullName2(_, CitizenName2("Weapon", List())))) =>
    }
  }

  test("Reports when two functions with same signature") {
    val compile = TemplarCompilation(
      """
        |fn moo() int export { 1337 }
        |fn moo() int export { 1448 }
        |""".stripMargin)
    compile.getTemplarError() match {
      case FunctionAlreadyExists(_, _, Signature2(FullName2(List(), FunctionName2("moo", List(), List())))) =>
    }
  }

  test("Reports when we give too many args") {
    val compile = TemplarCompilation(
      """
        |fn moo(a int, b bool, s str) int { a }
        |fn main() int export {
        |  moo(42, true, "hello", false)
        |}
        |""".stripMargin)
    compile.getTemplarError() match {
      //      case WrongNumberOfArguments(_, _) =>
      case CouldntFindFunctionToCallT(_, seff) => {
        vassert(seff.rejectedReasonByBanner.size == 1)
        seff.rejectedReasonByBanner.head._2 match {
          case WrongNumberOfArguments(4, 3) =>
        }
      }
    }
  }

  test("Reports when we try to mutate an imm struct") {
    val compile = TemplarCompilation(
      """
        |struct Vec3 { x float; y float; z float; }
        |fn main() int export {
        |  v = Vec3(3.0, 4.0, 5.0);
        |  mut v.x = 10.0;
        |}
        |""".stripMargin)
    compile.getTemplarError() match {
      case CantMutateFinalMember(_, structRef2, memberName) => {
        structRef2.fullName.last match {
          case CitizenName2("Vec3", List()) =>
        }
        memberName.last match {
          case CodeVarName2("x") =>
        }
      }
    }
  }

  test("Reports when we try to mutate a local variable with wrong type") {
    val compile = TemplarCompilation(
      """
        |fn main() {
        |  a! = 5;
        |  mut a = "blah";
        |}
        |""".stripMargin)
    compile.getTemplarError() match {
      case CouldntConvertForMutateT(_, Coord(Share, Int2()), Coord(Share, Str2())) =>
      case _ => vfail()
    }
  }

  test("Lambda is compatible anonymous interface") {
    val compile = TemplarCompilation(
        """
          |interface AFunction1<P> rules(P Ref) {
          |  fn __call(virtual this &AFunction1<P>, a P) int;
          |}
          |fn main() {
          |  arr = &AFunction1<int>((_){ true });
          |}
          |""".stripMargin)

    compile.getTemplarError() match {
      case LambdaReturnDoesntMatchInterfaceConstructor(_) =>
    }
  }

  test("Lock weak member") {
    val compile = TemplarCompilation.multiple(
      List(
        Samples.get("libraries/opt.vale"),
        Samples.get("libraries/printutils.vale"),
        Samples.get("libraries/castutils.vale"),
        """
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
          |""".stripMargin))

    compile.getTemputs()
  }

  test("Humanize errors") {
    val fireflyKind = StructRef2(FullName2(List(), CitizenName2("Firefly", List())))
    val fireflyCoord = Coord(Own, fireflyKind)
    val serenityKind = StructRef2(FullName2(List(), CitizenName2("Serenity", List())))
    val serenityCoord = Coord(Own, serenityKind)

    val filenamesAndSources = List(("file.vale", "blah blah blah\nblah blah blah"))

    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindTypeT(RangeS.testZero, "Spaceship")).nonEmpty)
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
        FunctionNameA("myFunc", CodeLocationS.zero), fireflyCoord, serenityCoord))
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
        RangeS(CodeLocationS(0, 10), CodeLocationS(0, 15)),
        Signature2(FullName2(List(), FunctionName2("myFunc", List(), List())))))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantMutateFinalMember(
        RangeS.testZero,
        serenityKind,
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
      CantImplStruct(
        RangeS.testZero, fireflyKind))
      .nonEmpty)
  }
}
