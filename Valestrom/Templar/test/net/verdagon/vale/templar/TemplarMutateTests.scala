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

class TemplarMutateTests extends FunSuite with Matchers {
  // TODO: pull all of the templar specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }

  test("Test mutating a local var") {
    val compile = TemplarTestCompilation.test("fn main() {a! = 3; set a = 4; }")
    val temputs = compile.expectTemputs();
    val main = temputs.lookupFunction("main")
    main.only({ case Mutate2(LocalLookup2(_,ReferenceLocalVariable2(FullName2(_, CodeVarName2("a")), Varying, _), _, Varying), IntLiteral2(4)) => })

    val lookup = main.only({ case l @ LocalLookup2(range, localVariable, reference, variability) => l })
    val resultCoord = lookup.resultRegister.reference
    resultCoord shouldEqual Coord(Share, Readonly, Int2())
  }

  test("Test mutable member permission") {
    val compile =
      TemplarTestCompilation.test(
        """
          |struct Engine { fuel int; }
          |struct Spaceship { engine! Engine; }
          |fn main() {
          |  ship = Spaceship(Engine(10));
          |  set ship.engine = Engine(15);
          |}
          |""".stripMargin)
    val temputs = compile.expectTemputs();
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
    val compile = TemplarTestCompilation.test(
      """
        |interface IXOption<T> rules(T Ref) { }
        |struct XSome<T> rules(T Ref) { value T; }
        |impl<T> IXOption<T> for XSome<T>;
        |struct XNone<T> rules(T Ref) { }
        |impl<T> IXOption<T> for XNone<T>;
        |
        |fn main() {
        |  m! IXOption<int> = XNone<int>();
        |  set m = XSome(6);
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    main.only({
      case Mutate2(_, StructToInterfaceUpcast2(_, _)) =>
    })
  }

  test("Expr-set upcasts") {
    val compile = TemplarTestCompilation.test(
      """
        |interface IXOption<T> rules(T Ref) { }
        |struct XSome<T> rules(T Ref) { value T; }
        |impl<T> IXOption<T> for XSome<T>;
        |struct XNone<T> rules(T Ref) { }
        |impl<T> IXOption<T> for XNone<T>;
        |
        |struct Marine {
        |  weapon! IXOption<int>;
        |}
        |fn main() {
        |  m = Marine(XNone<int>());
        |  set m.weapon = XSome(6);
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    main.only({
      case Mutate2(_, StructToInterfaceUpcast2(_, _)) =>
    })
  }

  test("Reports when we try to mutate an imm struct") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Vec3 imm { x float; y float; z float; }
        |fn main() int export {
        |  v = Vec3(3.0, 4.0, 5.0);
        |  set v.x = 10.0;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantMutateFinalMember(_, structRef2, memberName)) => {
        structRef2.last match {
          case CitizenName2("Vec3", List()) =>
        }
        memberName.last match {
          case CodeVarName2("x") =>
        }
      }
    }
  }

  test("Reports when we try to mutate a final member in a struct") {
    val compile = TemplarTestCompilation.test(
      """
        |struct Vec3 { x float; y float; z float; }
        |fn main() int export {
        |  v = Vec3(3.0, 4.0, 5.0);
        |  set v.x = 10.0;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantMutateFinalMember(_, structRef2, memberName)) => {
        structRef2.last match {
          case CitizenName2("Vec3", List()) =>
        }
        memberName.last match {
          case CodeVarName2("x") =>
        }
      }
    }
  }

  test("Reports when we try to mutate an element in a runtime-sized array of finals") {
    val compile = TemplarTestCompilation.test(
      """
        |fn main() int export {
        |  arr = [*](10, {_});
        |  set arr[4] = 10;
        |  ret 73;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantMutateFinalElement(_, arrRef2)) => {
        arrRef2.last match {
          case UnknownSizeArrayName2(RawArrayName2(Mutable,_)) =>
        }
      }
    }
  }

  test("Reports when we try to mutate an element in a static-sized array of finals") {
    val compile = TemplarTestCompilation.test(
      """
        |fn main() int export {
        |  arr = [10]({_});
        |  set arr[4] = 10;
        |  ret 73;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantMutateFinalElement(_, arrRef2)) => {
        arrRef2.last match {
          case KnownSizeArrayName2(10,RawArrayName2(Mutable,_)) =>
        }
      }
    }
  }

  test("Reports when we try to mutate a local variable with wrong type") {
    val compile = TemplarTestCompilation.test(
      """
        |fn main() {
        |  a! = 5;
        |  set a = "blah";
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CouldntConvertForMutateT(_, Coord(Share, Readonly, Int2()), Coord(Share, Readonly, Str2()))) =>
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
      CantImplStruct(
        RangeS.testZero, fireflyKind))
      .nonEmpty)
  }
}
