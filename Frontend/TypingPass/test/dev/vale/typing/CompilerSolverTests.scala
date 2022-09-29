package dev.vale.typing

import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.expression.CallCompiler
import dev.vale.{CodeLocationS, Collector, Err, FileCoordinate, FileCoordinateMap, PackageCoordinate, RangeS, vassert, vfail}
import dev.vale.typing.types._
import dev.vale._
import dev.vale.postparsing._
import dev.vale.postparsing.rules.{CoordComponentsSR, IRulexSR, KindComponentsSR, RuneUsage}
import dev.vale.solver.RuleError
import OverloadResolver.{FindFunctionFailure, InferFailure, SpecificParamDoesntSend, WrongNumberOfArguments}
import dev.vale.Collector.ProgramWithExpect
import dev.vale.postparsing._
import dev.vale.solver.{FailedSolve, IncompleteSolve, RuleError, SolverConflict, Step}
import dev.vale.typing.ast.{ConstantIntTE, FunctionCallTE, KindExportT, PrototypeT, SignatureT}
import dev.vale.typing.infer.{ITypingPassSolverError, KindIsNotConcrete, SendingNonCitizen}
import dev.vale.typing.names.{BuildingFunctionNameWithClosuredsT, CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionBoundNameT, FunctionBoundTemplateNameT, FunctionNameT, FunctionTemplateNameT, InterfaceNameT, InterfaceTemplateNameT, PlaceholderNameT, PlaceholderTemplateNameT, StructNameT, StructTemplateNameT}
import dev.vale.typing.templata._
import dev.vale.typing.ast._
import dev.vale.typing.templata._
import dev.vale.typing.types._
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest.{FunSuite, Matchers}

import scala.io.Source

class CompilerSolverTests extends FunSuite with Matchers {
  // TODO: pull all of the typingpass specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }


  test("Test simple generic function") {
    val compile = CompilerTestCompilation.test(
      """
        |func bork<T>(a T) T { return a; }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    vassert(coutputs.getAllUserFunctions.size == 1)
  }

  test("Test lacking drop function") {
    val compile = CompilerTestCompilation.test(
      """
        |func bork<T>(a T) { }
      """.stripMargin)
    compile.getCompilerOutputs().expectErr() match {
      case CouldntFindFunctionToCallT(_, FindFunctionFailure(CodeNameS(StrI("drop")), _, _)) =>
    }
  }

  test("Test having drop function concept function") {
    val compile = CompilerTestCompilation.test(
      """
        |func bork<T>(a T) where func drop(T)void { }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val bork = coutputs.lookupFunction("bork")

    // Only identifying template arg coord should be of PlaceholderT(0)
    bork.header.fullName.last.templateArgs match {
      case Vector(CoordTemplata(CoordT(OwnT,PlaceholderT(FullNameT(_, _, PlaceholderNameT(PlaceholderTemplateNameT(0)))))))
      =>
    }

    // Make sure it calls drop, and that it has the right placeholders
    bork.body shouldHave {
      case FunctionCallTE(
        PrototypeT(
          FullNameT(
            _,
            _,
            FunctionBoundNameT(
              FunctionBoundTemplateNameT(StrI("drop"),_),
              Vector(),
              Vector(
                CoordT(
                  OwnT,
                  PlaceholderT(
                    FullNameT(
                      _,
                      Vector(FunctionTemplateNameT(StrI("bork"),_)),
                      PlaceholderNameT(PlaceholderTemplateNameT(0)))))))),
          CoordT(ShareT,VoidT())),
        _) =>
    }
  }

  test("Test calling a generic function with a concept function") {
    val compile = CompilerTestCompilation.test(
      """
        |func moo(x int) { }
        |
        |func bork<T>(a T) T where func moo(T)void { a }
        |
        |exported func main() {
        |  bork(3);
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    main shouldHave {
      case FunctionCallTE(
        PrototypeT(
          FullNameT(_,
            _,
            FunctionNameT(
              FunctionTemplateNameT(StrI("bork"), _),
              Vector(CoordTemplata(CoordT(ShareT,IntT(32)))),
              Vector(CoordT(ShareT,IntT(32))))),
          CoordT(ShareT,IntT(32))),
        Vector(ConstantIntTE(IntegerTemplata(3),32))) =>
    }
  }

  test("Test rune type in generic param") {
    val compile = CompilerTestCompilation.test(
      """
        |func bork<I Int>() int { I }
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("bork")
    main.header.fullName.last.templateArgs match {
      case Vector(PlaceholderTemplata(_, IntegerTemplataType())) =>
    }
  }

  test("Test single parameter function") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Functor1<F Prot = func(P1)R> imm
        |where P1 Ref, R Ref { }
        |
        |func __call<F Prot = func(P1)R>(self &Functor1<F>, param1 P1) R
        |where P1 Ref, R Ref {
        |  F(param1)
        |}
        |
        |exported func main() int {
        |  Functor1({_})(4)
        |}
      """.stripMargin)

  }

  test("Test calling a generic function with a drop concept function") {
    val compile = CompilerTestCompilation.test(
      """
        |func bork<T>(a T) where func drop(T)void {
        |}
        |
        |struct Mork {}
        |
        |exported func main() {
        |  bork(Mork());
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val bork = coutputs.lookupFunction("main")
    val prototype =
      bork.body match {
        case BlockTE(
            ReturnTE(
              ConsecutorTE(
                Vector(FunctionCallTE(prototype, _),
                VoidLiteralTE())))) => prototype
      }
    prototype match {
      case PrototypeT(
          FullNameT(
            _,_,
            FunctionNameT(
              FunctionTemplateNameT(StrI("bork"), _),
              Vector(CoordTemplata(templateArgCoord)),
              Vector(arg))),
          CoordT(ShareT,VoidT())) => {

        templateArgCoord match {
          case CoordT(
              OwnT,
              StructTT(
                FullNameT(_,_,
                  StructNameT(StructTemplateNameT(StrI("Mork")),Vector())))) =>
        }

        vassert(arg == templateArgCoord)
      }
    }
  }

  test("Humanize errors") {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val tz = List(RangeS.testZero(interner))
    val testPackageCoord = PackageCoordinate.TEST_TLD(interner, keywords)

    val fireflyKind = StructTT(FullNameT(testPackageCoord, Vector(), StructNameT(StructTemplateNameT(StrI("Firefly")), Vector())))
    val fireflyCoord = CoordT(OwnT,fireflyKind)
    val serenityKind = StructTT(FullNameT(testPackageCoord, Vector(), StructNameT(StructTemplateNameT(StrI("Serenity")), Vector())))
    val serenityCoord = CoordT(OwnT,serenityKind)
    val ispaceshipKind = InterfaceTT(FullNameT(testPackageCoord, Vector(), InterfaceNameT(InterfaceTemplateNameT(StrI("ISpaceship")), Vector())))
    val ispaceshipCoord = CoordT(OwnT,ispaceshipKind)
    val unrelatedKind = StructTT(FullNameT(testPackageCoord, Vector(), StructNameT(StructTemplateNameT(StrI("Spoon")), Vector())))
    val unrelatedCoord = CoordT(OwnT,unrelatedKind)
    val fireflySignature = SignatureT(FullNameT(testPackageCoord, Vector(), interner.intern(FunctionNameT(interner.intern(FunctionTemplateNameT(interner.intern(StrI("myFunc")), tz.head.begin)), Vector(), Vector(fireflyCoord)))))
    val fireflyExport = KindExportT(tz.head, fireflyKind, testPackageCoord, interner.intern(StrI("Firefly")));
    val serenityExport = KindExportT(tz.head, fireflyKind, testPackageCoord, interner.intern(StrI("Serenity")));

    val codeStr = "Hello I am A large piece Of code [that has An error]"
    val filenamesAndSources = FileCoordinateMap.test(interner, codeStr)
    def makeLoc(pos: Int) = CodeLocationS(FileCoordinate.test(interner), pos)
    def makeRange(begin: Int, end: Int) = RangeS(makeLoc(begin), makeLoc(end))

    val unsolvedRules =
      Vector(
        CoordComponentsSR(
          makeRange(0, codeStr.length),
          RuneUsage(makeRange(6, 7), CodeRuneS(interner.intern(StrI("I")))),
          RuneUsage(makeRange(11, 12), CodeRuneS(interner.intern(StrI("A")))),
          RuneUsage(makeRange(33, 52), ImplicitRuneS(LocationInDenizen(Vector(7))))),
        KindComponentsSR(
          makeRange(33, 52),
          RuneUsage(makeRange(33, 52), ImplicitRuneS(LocationInDenizen(Vector(7)))),
          RuneUsage(makeRange(43, 45), CodeRuneS(interner.intern(StrI("An"))))))

    vassert(CompilerErrorHumanizer.humanize(false, filenamesAndSources,
      TypingPassSolverError(
        tz,
        FailedCompilerSolve(
          Vector(
            Step[IRulexSR, IRuneS, ITemplata[ITemplataType]](
              false,
              Vector(),
              Vector(),
              Map(
                CodeRuneS(interner.intern(StrI("A"))) -> OwnershipTemplata(OwnT)))).toStream,
          unsolvedRules,
          RuleError(KindIsNotConcrete(ispaceshipKind)))))
      .nonEmpty)

    val errorText =
      CompilerErrorHumanizer.humanize(false, filenamesAndSources,
        TypingPassSolverError(
          tz,
          IncompleteCompilerSolve(
            Vector(
              Step[IRulexSR, IRuneS, ITemplata[ITemplataType]](
                false,
                Vector(),
                Vector(),
                Map(
                  CodeRuneS(interner.intern(StrI("A"))) -> OwnershipTemplata(OwnT)))).toStream,
            unsolvedRules,
            Set(
              CodeRuneS(interner.intern(StrI("I"))),
              CodeRuneS(interner.intern(StrI("Of"))),
              CodeRuneS(interner.intern(StrI("An"))),
              ImplicitRuneS(LocationInDenizen(Vector(7)))),
            Map())))
    println(errorText)
    vassert(errorText.nonEmpty)
    vassert(errorText.contains("\n           ^ A: own"))
    vassert(errorText.contains("\n      ^ I: (unknown)"))
    vassert(errorText.contains("\n                                 ^^^^^^^^^^^^^^^^^^^ _7: (unknown)"))
  }

  test("Simple int rule") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |exported func main() int where N Int = 3 {
        |  return N;
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), { case ConstantIntTE(IntegerTemplata(3), 32) => })
  }

  test("Equals transitive") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |exported func main() int where N Int = 3, M Int = N {
        |  return M;
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), { case ConstantIntTE(IntegerTemplata(3), 32) => })
  }

  test("OneOf") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |exported func main() int where N Int = any(2, 3, 4), N = 3 {
        |  return N;
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), { case ConstantIntTE(IntegerTemplata(3), 32) => })
  }

  test("Components") {
    val compile = CompilerTestCompilation.test(
      """
        |exported struct MyStruct { }
        |exported func main() X
        |where
        |  MyStruct = Ref[O Ownership, K Kind],
        |  X Ref = Ref[borrow, K]
        |{
        |  return &MyStruct();
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    coutputs.lookupFunction("main").header.returnType match {
      case CoordT(BorrowT, StructTT(_)) =>
    }
  }

  test("Prototype rule, call via rune") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |func moo(i int, b bool) str { return "hello"; }
        |exported func main() str
        |where mooFunc Prot = func moo(int, bool)str
        |{
        |  return (mooFunc)(5, true);
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case FunctionCallTE(PrototypeT(simpleName("moo"), _), _) =>
    })
  }

  test("Prototype rule, call directly") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |func moo(i int, b bool) str { return "hello"; }
        |exported func main() str
        |where func moo(int, bool)str
        |{
        |  return moo(5, true);
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case FunctionCallTE(PrototypeT(simpleName("moo"), _), _) =>
    })
  }

  test("Send struct to struct") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |struct MyStruct {}
        |func moo(m MyStruct) { }
        |exported func main() {
        |  moo(MyStruct())
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Send struct to interface") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |struct MyStruct {}
        |interface MyInterface {}
        |impl MyInterface for MyStruct;
        |func moo(m MyInterface) { }
        |exported func main() {
        |  moo(MyStruct())
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Assume most specific generic param") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |struct MyStruct {}
        |interface MyInterface {}
        |impl MyInterface for MyStruct;
        |func moo<T>(m T) where func drop(T)void { }
        |exported func main() {
        |  moo(MyStruct())
        |}
        |""".stripMargin
    )

    val coutputs = compile.expectCompilerOutputs()
    val arg =
      coutputs.lookupFunction("main").body shouldHave {
        case FunctionCallTE(_, Vector(arg)) => arg
      }
    arg.result.reference match {
      case CoordT(_, StructTT(_)) =>
    }
  }

  test("Assume most specific common ancestor") {
    val compile = CompilerTestCompilation.test(
      """
        |interface IShip {}
        |struct Firefly {}
        |impl IShip for Firefly;
        |struct Serenity {}
        |impl IShip for Serenity;
        |func moo<T>(a T, b T) where func drop(T)void { }
        |exported func main() {
        |  moo(Firefly(), Serenity())
        |}
        |""".stripMargin
    )

    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupFunction("moo")
    val main = coutputs.lookupFunction("main")
    main.body shouldHave {
      case FunctionCallTE(prototype, Vector(_, _)) => {
        prototype.fullName.last.templateArgs.head match {
          case CoordTemplata(CoordT(_, InterfaceTT(_))) =>
        }
      }
    }
    Collector.all(main, {
      case UpcastTE(_, _, _) =>
    }).size shouldEqual 2
  }

  test("Descendant satisfying call") {
    val compile = CompilerTestCompilation.test(
      """
        |interface IShip<T> where T Ref {}
        |struct Firefly<T> where T Ref {}
        |impl<T> IShip<T> for Firefly<T>;
        |func moo<T>(a IShip<T>) { }
        |exported func main() {
        |  moo(Firefly<int>())
        |}
        |""".stripMargin
    )

    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupFunction("moo")
    moo.header.params.head.tyype match {
      case CoordT(_, InterfaceTT(FullNameT(_, _, CitizenNameT(_, Vector(CoordTemplata(CoordT(_, PlaceholderT(FullNameT(_,_,PlaceholderNameT(PlaceholderTemplateNameT(0))))))))))) =>
    }
    val main = coutputs.lookupFunction("main")
    main.body shouldHave {
      case FunctionCallTE(
        PrototypeT(FullNameT(_,_, FunctionNameT(FunctionTemplateNameT(StrI("moo"), _), _, _)), _),
        Vector(
          UpcastTE(
            _,
            InterfaceTT(FullNameT(_,_,InterfaceNameT(InterfaceTemplateNameT(StrI("IShip")),Vector(CoordTemplata(CoordT(ShareT,IntT(32))))))),
            _))) =>
    }
  }

  test("Reports incomplete solve") {
    val interner = new Interner()
    val compile = CompilerTestCompilation.test(
      """
        |
        |exported func main() int where N Int {
        |  M
        |}
        |""".stripMargin,
      interner)
    compile.getCompilerOutputs() match {
      case Err(TypingPassSolverError(_,IncompleteCompilerSolve(_,Vector(),unsolved, _))) => {
        unsolved shouldEqual Set(CodeRuneS(interner.intern(interner.intern(StrI("N")))))
      }
    }
  }


  test("Stamps an interface template via a function return") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |interface MyInterface<X Ref> { }
        |
        |struct SomeStruct<X Ref> where func drop(X)void { x X; }
        |impl<X> MyInterface<X> for SomeStruct<X> where func drop(X)void;
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

  test("Pointer becomes share if kind is immutable") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |
        |struct SomeStruct imm { i int; }
        |
        |func bork(x &SomeStruct) int {
        |  return x.i;
        |}
        |
        |exported func main() int {
        |  return bork(SomeStruct(7));
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    coutputs.lookupFunction("bork").header.params.head.tyype.ownership shouldEqual ShareT
  }

  test("Detects conflict between types") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |struct ShipA {}
        |struct ShipB {}
        |exported func main() where N Kind = ShipA, N Kind = ShipB {
        |}
        |""".stripMargin
    )
    compile.getCompilerOutputs() match {
      case Err(TypingPassSolverError(_, FailedCompilerSolve(_, _, SolverConflict(_, KindTemplata(StructTT(FullNameT(_,_,StructNameT(StructTemplateNameT(StrI("ShipA")),_)))), KindTemplata(StructTT(FullNameT(_,_,StructNameT(StructTemplateNameT(StrI("ShipB")),_)))))))) =>
      case Err(TypingPassSolverError(_, FailedCompilerSolve(_, _, SolverConflict(_, KindTemplata(StructTT(FullNameT(_,_,StructNameT(StructTemplateNameT(StrI("ShipB")),_)))), KindTemplata(StructTT(FullNameT(_,_,StructNameT(StructTemplateNameT(StrI("ShipA")),_)))))))) =>
      case other => vfail(other)
    }
  }

  test("Can match KindTemplataType() against StructEnvEntry / StructTemplata") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |#!DeriveStructDrop
        |struct SomeStruct<T>
        |{ x T; }
        |
        |func bork<X, Z>() Z
        |where X Kind = SomeStruct<int>, X = SomeStruct<Z> {
        |  return 9;
        |}
        |
        |exported func main() int {
        |  return bork();
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    coutputs.lookupFunction("bork").header.fullName.last.templateArgs.last shouldEqual CoordTemplata(CoordT(ShareT, IntT(32)))
  }

  test("Can destructure and assemble static sized array") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |import v.builtins.arrays.*;
        |import v.builtins.drop.*;
        |
        |func swap<T>(x [#2]T) [#2]T {
        |  [a, b] = x;
        |  return [#][b, a];
        |}
        |
        |exported func main() int {
        |  return swap([#][5, 7]).0;
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()

    val swap = coutputs.lookupFunction("swap")
    swap.header.fullName.last.templateArgs.last match {
      case CoordTemplata(CoordT(OwnT,PlaceholderT(FullNameT(_,Vector(FunctionTemplateNameT(StrI("swap"),_)),PlaceholderNameT(PlaceholderTemplateNameT(0)))))) =>
    }

    val main = coutputs.lookupFunction("main")
    val call =
      Collector.only(main, {
        case call @ FunctionCallTE(PrototypeT(FullNameT(_, _, FunctionNameT(FunctionTemplateNameT(StrI("swap"), _), _, _)), _), _) => call
      })
    call.callable.fullName.last.templateArgs.last match {
      case CoordTemplata(CoordT(ShareT, IntT(32))) =>
    }
  }
}
