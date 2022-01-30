package net.verdagon.vale.templar

import net.verdagon.vale._
import net.verdagon.vale.scout._
import net.verdagon.vale.scout.rules.{CoordComponentsSR, KindComponentsSR, RuneUsage}
import net.verdagon.vale.solver.{FailedSolve, IncompleteSolve, RuleError, SolverConflict, Step}
import net.verdagon.vale.templar.OverloadTemplar.{FindFunctionFailure, InferFailure, SpecificParamDoesntSend, WrongNumberOfArguments}
import net.verdagon.vale.templar.ast.{ConstantIntTE, FunctionCallTE, KindExportT, PrototypeT, SignatureT, StructToInterfaceUpcastTE}
import net.verdagon.vale.templar.env.ReferenceLocalVariableT
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.infer.{KindIsNotConcrete, SendingNonCitizen}
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionNameT}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
//import net.verdagon.vale.templar.infer.NotEnoughToSolveError
import org.scalatest.{FunSuite, Matchers}

import scala.io.Source

class TemplarSolverTests extends FunSuite with Matchers {
  // TODO: pull all of the templar specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
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
    val fireflySignature = SignatureT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), FunctionNameT("myFunc", Vector(), Vector(fireflyCoord))))
    val fireflyExport = KindExportT(RangeS.testZero, fireflyKind, PackageCoordinate.TEST_TLD, "Firefly");
    val serenityExport = KindExportT(RangeS.testZero, fireflyKind, PackageCoordinate.TEST_TLD, "Serenity");

    val codeStr = "Hello I am A large piece Of code [that has An error]"
    val filenamesAndSources = FileCoordinateMap.test(codeStr)
    def makeLoc(pos: Int) = CodeLocationS(FileCoordinate.test, pos)
    def makeRange(begin: Int, end: Int) = RangeS(makeLoc(begin), makeLoc(end))

    val unsolvedRules =
      Vector(
        CoordComponentsSR(
          makeRange(0, codeStr.length),
          RuneUsage(makeRange(6, 7), CodeRuneS("I")),
          RuneUsage(makeRange(11, 12), CodeRuneS("A")),
          RuneUsage(makeRange(25, 27), CodeRuneS("Of")),
          RuneUsage(makeRange(33, 52), ImplicitRuneS(LocationInDenizen(Vector(7))))),
        KindComponentsSR(
          makeRange(33, 52),
          RuneUsage(makeRange(33, 52), ImplicitRuneS(LocationInDenizen(Vector(7)))),
          RuneUsage(makeRange(43, 45), CodeRuneS("An"))))

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
                CodeRuneS("A") -> OwnershipTemplata(OwnT)))),
          unsolvedRules,
          RuleError(KindIsNotConcrete(ispaceshipKind)))))
      .nonEmpty)

    val errorText =
      TemplarErrorHumanizer.humanize(false, filenamesAndSources,
        TemplarSolverError(
          RangeS.testZero,
          IncompleteSolve(
            Vector(
              Step(
                false,
                Vector(),
                Vector(),
                Map(
                  CodeRuneS("A") -> OwnershipTemplata(OwnT)))),
            unsolvedRules,
            Set(
              CodeRuneS("I"),
              CodeRuneS("Of"),
              CodeRuneS("An"),
              ImplicitRuneS(LocationInDenizen(Vector(7)))))))
    println(errorText)
    vassert(errorText.nonEmpty)
    vassert(errorText.contains("\n           ^ A: own"))
    vassert(errorText.contains("\n      ^ I: (unknown)"))
    vassert(errorText.contains("\n                                 ^^^^^^^^^^^^^^^^^^^ _7: (unknown)"))
  }

  test("Simple int rule") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() rules(N int = 3) int export {
        |  ret N;
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), { case ConstantIntTE(3, 32) => })
  }

  test("Equals transitive") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() rules(N int = 3, M int = N) int export {
        |  ret M;
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), { case ConstantIntTE(3, 32) => })
  }

  test("OneOf") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() rules(N int = 2 | 3 | 4, N = 3) int export {
        |  ret N;
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), { case ConstantIntTE(3, 32) => })
  }

  test("Components") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct MyStruct export { }
        |fn main()
        |rules(
        |  MyStruct = T Ref(O Ownership, P Permission, K Kind),
        |  X Ref(ptr, ro, K))
        |X export {
        |  ret *MyStruct();
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").header.returnType match {
      case CoordT(PointerT, ReadonlyT, StructTT(_)) =>
    }
  }

  test("Prototype rule") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn moo(i int, b bool) str { ret "hello"; }
        |fn main()
        |rules(
        |  mooFunc Prot("moo", (int, bool), _))
        |str export {
        |  ret (mooFunc)(5, true);
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case FunctionCallTE(PrototypeT(simpleName("moo"), _), _) =>
    })
  }

  test("Send struct to struct") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct MyStruct {}
        |fn moo(m MyStruct) { }
        |fn main() export {
        |  moo(MyStruct())
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
  }

  test("Send struct to interface") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct MyStruct {}
        |interface MyInterface {}
        |impl MyInterface for MyStruct;
        |fn moo(m MyInterface) { }
        |fn main() export {
        |  moo(MyStruct())
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
  }

  test("Assume most specific generic param") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct MyStruct {}
        |interface MyInterface {}
        |impl MyInterface for MyStruct;
        |fn moo<T>(m T) { }
        |fn main() export {
        |  moo(MyStruct())
        |}
        |""".stripMargin
    )

    val temputs = compile.expectTemputs()
    temputs.lookupFunction("moo").header.params.head.tyype match {
      case CoordT(_, _, StructTT(_)) =>
    }
  }

  test("Assume most specific common ancestor") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface IShip {}
        |struct Firefly {}
        |impl IShip for Firefly;
        |struct Serenity {}
        |impl IShip for Serenity;
        |fn moo<T>(a T, b T) { }
        |fn main() export {
        |  moo(Firefly(), Serenity())
        |}
        |""".stripMargin
    )

    val temputs = compile.expectTemputs()
    val moo = temputs.lookupFunction("moo")
    moo.header.params.head.tyype match {
      case CoordT(_, _, InterfaceTT(_)) =>
    }
    val main = temputs.lookupFunction("main")
    Collector.all(main, {
      case StructToInterfaceUpcastTE(_, _) =>
    }).size shouldEqual 2
  }

  test("Descendant satisfying call") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface IShip<T> rules(T Ref) {}
        |struct Firefly<T> rules(T Ref) {}
        |impl<T> IShip<T> for Firefly<T>;
        |fn moo<T>(a IShip<T>) { }
        |fn main() export {
        |  moo(Firefly<int>())
        |}
        |""".stripMargin
    )

    val temputs = compile.expectTemputs()
    val moo = temputs.lookupFunction("moo")
    moo.header.params.head.tyype match {
      case CoordT(_, _, InterfaceTT(FullNameT(_, _, CitizenNameT(_, Vector(CoordTemplata(CoordT(_, _, IntT(_)))))))) =>
    }
  }

  test("Reports incomplete solve") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |fn main() rules(N int) int export {
        |  M
        |}
        |""".stripMargin
    )
    compile.getTemputs() match {
      case Err(TemplarSolverError(_,IncompleteSolve(_,Vector(),unsolved))) => {
        unsolved shouldEqual Set(CodeRuneS("N"))
      }
    }
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

  test("Pointer becomes share if kind is immutable") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |struct SomeStruct imm { i int; }
        |
        |fn bork(x *SomeStruct) int {
        |  ret x.i;
        |}
        |
        |fn main() int export {
        |  ret bork(SomeStruct(7));
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("bork").header.params.head.tyype.ownership shouldEqual ShareT
  }

  test("Detects conflict between types") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct ShipA {}
        |struct ShipB {}
        |fn main() rules(N Kind = ShipA, N Kind = ShipB) export {
        |}
        |""".stripMargin
    )
    compile.getTemputs() match {
      case Err(TemplarSolverError(_, FailedSolve(_, _, SolverConflict(_, KindTemplata(StructTT(FullNameT(_,_,CitizenNameT(CitizenTemplateNameT("ShipA"),_)))), KindTemplata(StructTT(FullNameT(_,_,CitizenNameT(CitizenTemplateNameT("ShipB"),_)))))))) =>
      case Err(TemplarSolverError(_, FailedSolve(_, _, SolverConflict(_, KindTemplata(StructTT(FullNameT(_,_,CitizenNameT(CitizenTemplateNameT("ShipB"),_)))), KindTemplata(StructTT(FullNameT(_,_,CitizenNameT(CitizenTemplateNameT("ShipA"),_)))))))) =>
      case other => vfail(other)
    }
  }

  test("Can match KindTemplataType against StructEnvEntry / StructTemplata") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |struct SomeStruct<T> { x T; }
        |
        |fn bork<X, Z>() Z
        |rules(X Kind = SomeStruct<int>, X = SomeStruct<Z>) {
        |  ret 9;
        |}
        |
        |fn main() int export {
        |  ret bork();
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("bork").header.fullName.last.templateArgs.last shouldEqual CoordTemplata(CoordT(ShareT, ReadonlyT, IntT(32)))
  }

  test("Can turn a borrow coord into an owning coord") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |struct SomeStruct { }
        |
        |fn bork<T>(x T) ^T {
        |  ret SomeStruct();
        |}
        |
        |fn main() export {
        |  bork(SomeStruct());
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("bork").header.fullName.last.templateArgs.last match {
      case CoordTemplata(CoordT(OwnT, _, _)) =>
    }
  }

  test("Can destructure and assemble tuple") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |fn swap<T, Y>(x [T, Y]) [Y, T] {
        |  (a, b) = x;
        |  ret [b, a];
        |}
        |
        |fn main() bool export {
        |  ret swap([5, true]).0;
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("swap").header.fullName.last.templateArgs.last match {
      case CoordTemplata(CoordT(ShareT, ReadonlyT, BoolT())) =>
    }
  }

  test("Can destructure and assemble static sized array") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arrays.*;
        |
        |fn swap<N, T>(x [N * T]) [N * T] {
        |  (a, b) = x;
        |  ret [][b, a];
        |}
        |
        |fn main() int export {
        |  ret swap([][5, 7]).0;
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("swap").header.fullName.last.templateArgs.last match {
      case CoordTemplata(CoordT(ShareT, ReadonlyT, IntT(32))) =>
    }
  }

  test("Impl rule") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |
        |interface IShip {
        |  fn getFuel(virtual self &IShip) int;
        |}
        |struct Firefly {}
        |fn getFuel(self &Firefly impl IShip) int { ret 7; }
        |impl IShip for Firefly;
        |
        |fn genericGetFuel<T>(x T) int
        |rules(implements(T, IShip)) {
        |  ret x.getFuel();
        |}
        |
        |fn main() int export {
        |  ret genericGetFuel(Firefly());
        |}
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("genericGetFuel").header.fullName.last.templateArgs.last match {
      case CoordTemplata(CoordT(_,_,StructTT(FullNameT(_,_,CitizenNameT(CitizenTemplateNameT("Firefly"),_))))) =>
    }
  }

  test("Prototype rule to get return type") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.panic.*;
        |
        |fn moo(i int, b bool) str { ret "hello"; }
        |
        |fn main()
        |rules(
        |  mooFunc Prot("moo", (int, bool), R Ref))
        |R export {
        |  __vbi_panic();
        |}
        |
        |""".stripMargin
    )
    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").header.returnType match {
      case CoordT(_,_,StrT()) =>
    }
  }

  test("Detects sending non-citizen to citizen") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface MyInterface {}
        |fn moo<T>(a T)
        |rules(implements(T, MyInterface))
        |{ }
        |fn main() export {
        |  moo(7);
        |}
        |""".stripMargin
    )
    compile.getTemputs() match {
      case Err(CouldntFindFunctionToCallT(range, fff)) => {
        fff.rejectedCalleeToReason.values.head match {
          case InferFailure(reason) => {
            reason match {
              case FailedSolve(_, _, RuleError(SendingNonCitizen(IntT(32)))) =>
              case other => vfail(other)
            }
          }
        }
      }
    }
  }
}
