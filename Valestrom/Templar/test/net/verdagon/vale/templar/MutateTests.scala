package net.verdagon.vale.templar

import net.verdagon.vale._
import net.verdagon.vale.astronomer.{Astronomer, FunctionNameA, GlobalFunctionFamilyNameA, ProgramA}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{CodeLocationS, ProgramS, RangeS, Scout}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, WrongNumberOfArguments}
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List
import scala.io.Source

class MutateTests extends FunSuite with Matchers {
  // TODO: pull all of the templar specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }

  test("Test mutating a local var") {
    val compile = TemplarCompilation("fn main() {a! = 3; set a = 4; }")
    val temputs = compile.getTemputs();
    val main = temputs.lookupFunction("main")
    main.only({ case Mutate2(LocalLookup2(_,ReferenceLocalVariable2(FullName2(_, CodeVarName2("a")), Varying, _), _, Varying), IntLiteral2(4)) => })

    val lookup = main.only({ case l @ LocalLookup2(range, localVariable, reference, variability) => l })
    val resultCoord = lookup.resultRegister.reference
    resultCoord shouldEqual Coord(Share, Readonly, Int2())
  }

  test("Test mutable member permission") {
    val compile =
      TemplarCompilation(
        """
          |struct Engine { fuel int; }
          |struct Spaceship { engine! Engine; }
          |fn main() {
          |  ship = Spaceship(Engine(10));
          |  set ship.engine = Engine(15);
          |}
          |""".stripMargin)
    val temputs = compile.getTemputs();
    val main = temputs.lookupFunction("main")

    val lookup = main.only({ case l @ ReferenceMemberLookup2(_, _, _, _, _, _) => l })
    val resultCoord = lookup.resultRegister.reference
    // See RMLRMO, it should result in the same type as the member.
    resultCoord match {
      case Coord(Own, Readwrite, StructRef2(_)) =>
      case x => vfail(x.toString)
    }
  }

  test("Local-set upcasts") {
    val compile = TemplarCompilation(
      """
        |interface IOption<T> rules(T Ref) { }
        |struct Some<T> rules(T Ref) { value T; }
        |impl<T> IOption<T> for Some<T>;
        |struct None<T> rules(T Ref) { }
        |impl<T> IOption<T> for None<T>;
        |
        |fn main() {
        |  m! IOption<int> = None<int>();
        |  set m = Some(6);
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main.only({
      case Mutate2(_, StructToInterfaceUpcast2(_, _)) =>
    })
  }

  test("Expr-set upcasts") {
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
        |  set m.weapon = Some(6);
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

  test("Reports when we try to mutate an imm struct") {
    val compile = TemplarCompilation(
      """
        |struct Vec3 { x float; y float; z float; }
        |fn main() int export {
        |  v = Vec3(3.0, 4.0, 5.0);
        |  set v.x = 10.0;
        |}
        |""".stripMargin)
    compile.getTemplarError() match {
      case CantMutateFinalMember(_, structRef2, memberName) => {
        structRef2.last match {
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
        |  set a = "blah";
        |}
        |""".stripMargin)
    compile.getTemplarError() match {
      case CouldntConvertForMutateT(_, Coord(Share, Readonly, Int2()), Coord(Share, Readonly, Str2())) =>
      case _ => vfail()
    }
  }

  test("Humanize errors") {
    val fireflyKind = StructRef2(FullName2(List(), CitizenName2("Firefly", List())))
    val fireflyCoord = Coord(Own,Readwrite,fireflyKind)
    val serenityKind = StructRef2(FullName2(List(), CitizenName2("Serenity", List())))
    val serenityCoord = Coord(Own,Readwrite,serenityKind)

    val filenamesAndSources = FileCoordinateMap.test("blah blah blah\nblah blah blah")

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
      CantImplStruct(
        RangeS.testZero, fireflyKind))
      .nonEmpty)
  }
}
