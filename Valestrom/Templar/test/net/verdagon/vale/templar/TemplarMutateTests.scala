package net.verdagon.vale.templar

import net.verdagon.vale._
import net.verdagon.vale.astronomer.{Astronomer, ProgramA}
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{CodeNameS, FunctionNameS, GlobalFunctionFamilyNameS, ProgramS, Scout}
import net.verdagon.vale.templar.OverloadTemplar.{FindFunctionFailure, WrongNumberOfArguments}
import net.verdagon.vale.templar.ast.{ConstantIntTE, LocalLookupTE, MutateTE, ReferenceMemberLookupTE, SignatureT, StructToInterfaceUpcastTE}
import net.verdagon.vale.templar.env.ReferenceLocalVariableT
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, CodeVarNameT, FullNameT, FunctionNameT, RawArrayNameT, RuntimeSizedArrayNameT, StaticSizedArrayNameT}
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
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported func main() {a! = 3; set a = 4; }
        |""".stripMargin)
    val temputs = compile.expectTemputs();
    val main = temputs.lookupFunction("main")
    Collector.only(main, { case MutateTE(LocalLookupTE(_,ReferenceLocalVariableT(FullNameT(_,_, CodeVarNameT("a")), VaryingT, _)), ConstantIntTE(4, _)) => })

    val lookup = Collector.only(main, { case l @ LocalLookupTE(range, localVariable) => l })
    val resultCoord = lookup.result.reference
    resultCoord shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)
  }

  test("Test mutable member permission") {
    val compile =
      TemplarTestCompilation.test(
        """
          |import v.builtins.tup.*;
          |struct Engine { fuel int; }
          |struct Spaceship { engine! Engine; }
          |exported func main() {
          |  ship = Spaceship(Engine(10));
          |  set ship.engine = Engine(15);
          |}
          |""".stripMargin)
    val temputs = compile.expectTemputs();
    val main = temputs.lookupFunction("main")

    val lookup = Collector.only(main, { case l @ ReferenceMemberLookupTE(_, _, _, _, _, _) => l })
    val resultCoord = lookup.result.reference
    // See RMLRMO, it should result in the same type as the member.
    resultCoord match {
      case CoordT(OwnT, ReadwriteT, StructTT(_)) =>
      case x => vfail(x.toString)
    }
  }

  test("Local-set upcasts") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface IXOption<T> where T Ref { }
        |struct XSome<T> where T Ref { value T; }
        |impl<T> IXOption<T> for XSome<T>;
        |struct XNone<T> where T Ref { }
        |impl<T> IXOption<T> for XNone<T>;
        |
        |exported func main() {
        |  m! IXOption<int> = XNone<int>();
        |  set m = XSome(6);
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case MutateTE(_, StructToInterfaceUpcastTE(_, _)) =>
    })
  }

  test("Expr-set upcasts") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |interface IXOption<T> where T Ref { }
        |struct XSome<T> where T Ref { value T; }
        |impl<T> IXOption<T> for XSome<T>;
        |struct XNone<T> where T Ref { }
        |impl<T> IXOption<T> for XNone<T>;
        |
        |struct Marine {
        |  weapon! IXOption<int>;
        |}
        |exported func main() {
        |  m = Marine(XNone<int>());
        |  set m.weapon = XSome(6);
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case MutateTE(_, StructToInterfaceUpcastTE(_, _)) =>
    })
  }

  test("Reports when we try to mutate an imm struct") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Vec3 imm { x float; y float; z float; }
        |exported func main() int {
        |  v = Vec3(3.0, 4.0, 5.0);
        |  set v.x = 10.0;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantMutateFinalMember(_, structTT, memberName)) => {
        structTT match {
          case StructTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT("Vec3"), Vector()))) =>
        }
        memberName.last match {
          case CodeVarNameT("x") =>
        }
      }
    }
  }

  test("Reports when we try to mutate a final member in a struct") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |struct Vec3 { x float; y float; z float; }
        |exported func main() int {
        |  v = Vec3(3.0, 4.0, 5.0);
        |  set v.x = 10.0;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantMutateFinalMember(_, structTT, memberName)) => {
        structTT match {
          case StructTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT("Vec3"), Vector()))) =>
        }
        memberName.last match {
          case CodeVarNameT("x") =>
        }
      }
    }
  }

  test("Can mutate an element in a runtime-sized array") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.arrays.*;
        |exported func main() int {
        |  arr = Array<mut, int>(3);
        |  arr!.push(0);
        |  arr!.push(1);
        |  arr!.push(2);
        |  set arr[1] = 10;
        |  ret 73;
        |}
        |""".stripMargin)
    compile.expectTemputs()
  }

  test("Reports when we try to mutate an element in an imm static-sized array") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import ifunction.ifunction1.*;
        |exported func main() int {
        |  arr = #[#10]({_});
        |  set arr[4] = 10;
        |  ret 73;
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantMutateFinalElement(_, arrRef2)) => {
        arrRef2.kind match {
          case StaticSizedArrayTT(10,ImmutableT,FinalT,CoordT(ShareT,ReadonlyT,IntT(_))) =>
        }
      }
    }
  }

  test("Reports when we try to mutate a local variable with wrong type") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |exported func main() {
        |  a! = 5;
        |  set a = "blah";
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CouldntConvertForMutateT(_, CoordT(ShareT, ReadonlyT, IntT.i32), CoordT(ShareT, ReadonlyT, StrT()))) =>
      case _ => vfail()
    }
  }

  test("Reports when we try to override a non-interface") {
    val compile = TemplarTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |func bork(a int impl int) {}
        |exported func main() {
        |  bork(7);
        |}
        |""".stripMargin)
    compile.getTemputs() match {
      case Err(CantImplNonInterface(_, IntT(32)) )=>
      case _ => vfail()
    }
  }

  test("Humanize errors") {
    val fireflyKind = StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector.empty, CitizenNameT(CitizenTemplateNameT("Firefly"), Vector.empty)))
    val fireflyCoord = CoordT(OwnT,ReadwriteT,fireflyKind)
    val serenityKind = StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector.empty, CitizenNameT(CitizenTemplateNameT("Serenity"), Vector.empty)))
    val serenityCoord = CoordT(OwnT,ReadwriteT,serenityKind)

    val filenamesAndSources = FileCoordinateMap.test("blah blah blah\nblah blah blah")

    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindTypeT(RangeS.testZero, "Spaceship")).nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CouldntFindFunctionToCallT(
        RangeS.testZero,
        FindFunctionFailure(CodeNameS(""), Vector.empty, Map())))
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
      CantReconcileBranchesResults(
        RangeS.testZero,
        fireflyCoord,
        serenityCoord))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantUseUnstackifiedLocal(
        RangeS.testZero,
        CodeVarNameT("firefly")))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      FunctionAlreadyExists(
        RangeS.testZero,
        RangeS.testZero,
        SignatureT(FullNameT(PackageCoordinate.TEST_TLD, Vector.empty, FunctionNameT("myFunc", Vector.empty, Vector.empty)))))
      .nonEmpty)
    vassert(TemplarErrorHumanizer.humanize(false, filenamesAndSources,
      CantMutateFinalMember(
        RangeS.testZero,
        serenityKind,
        FullNameT(PackageCoordinate.TEST_TLD, Vector.empty, CodeVarNameT("bork"))))
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
  }
}
