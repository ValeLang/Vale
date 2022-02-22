package net.verdagon.vale.scout

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{Collector, Err, FileCoordinate, FileCoordinateMap, Interner, Ok, RangeS, vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

class ScoutErrorHumanizerTests extends FunSuite with Matchers {
  private def compile(code: String): ProgramS = {
    Parser.runParser(code) match {
      case Err(err) => fail(err.toString)
      case Ok(program0) => {
        new Scout(GlobalOptions.test(), new Interner())
            .scoutProgram(FileCoordinate.test, program0) match {
          case Err(e) => vfail(e.toString)
          case Ok(t) => t
        }
      }
    }
  }

  private def compileForError(code: String): ICompileErrorS = {
    Parser.runParser(code) match {
      case Err(err) => fail(err.toString)
      case Ok(program0) => {
        new Scout(GlobalOptions.test(), new Interner())
            .scoutProgram(FileCoordinate.test, program0) match {
          case Err(e) => e
          case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
        }
      }
    }
  }

  test("Should require identifying runes") {
    val error =
      compileForError(
        """
          |func do(callable) infer-ret {callable()}
          |""".stripMargin)
    error match {
      case LightFunctionMustHaveParamTypes(_, 0) =>
    }
  }

  test("Humanize errors") {
    val codeMap = FileCoordinateMap.test("blah blah blah\nblah blah blah")

    vassert(ScoutErrorHumanizer.humanize(codeMap,
      VariableNameAlreadyExists(RangeS.testZero, CodeVarNameS("Spaceship")))
      .nonEmpty)
    vassert(ScoutErrorHumanizer.humanize(codeMap,
      InterfaceMethodNeedsSelf(RangeS.testZero))
      .nonEmpty)
    vassert(ScoutErrorHumanizer.humanize(codeMap,
      ForgotSetKeywordError(RangeS.testZero))
      .nonEmpty)
    vassert(ScoutErrorHumanizer.humanize(codeMap,
      CantUseThatLocalName(RangeS.testZero, "set"))
      .nonEmpty)
    vassert(ScoutErrorHumanizer.humanize(codeMap,
      ExternHasBody(RangeS.testZero))
      .nonEmpty)
    vassert(ScoutErrorHumanizer.humanize(codeMap,
      CantInitializeIndividualElementsOfRuntimeSizedArray(RangeS.testZero))
      .nonEmpty)
    vassert(ScoutErrorHumanizer.humanize(codeMap,
      LightFunctionMustHaveParamTypes(RangeS.testZero, 0))
      .nonEmpty)

  }
}
