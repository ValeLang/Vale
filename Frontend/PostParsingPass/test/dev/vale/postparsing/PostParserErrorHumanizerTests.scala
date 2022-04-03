package dev.vale.postparsing

import dev.vale.{Err, FileCoordinateMap, Ok, RangeS, vassert, vfail}
import dev.vale.options.GlobalOptions
import dev.vale.parsing._
import dev.vale.postparsing.patterns.AbstractSP
import dev.vale.postparsing.rules._
import dev.vale.Err
import org.scalatest.{FunSuite, Matchers}

class PostParserErrorHumanizerTests extends FunSuite with Matchers {

  private def compile(code: String): ProgramS = {
    PostParserTestCompilation.test(code).getScoutput() match {
      case Err(e) => vfail(PostParserErrorHumanizer.humanize(FileCoordinateMap.test(code), e))
      case Ok(t) => t.expectOne()
    }
  }

  private def compileForError(code: String): ICompileErrorS = {
    PostParserTestCompilation.test(code).getScoutput() match {
      case Err(e) => e
      case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
    }
  }

  test("Should require identifying runes") {
    val error =
      compileForError(
        """
          |func do(callable) infer-return {callable()}
          |""".stripMargin)
    error match {
      case LightFunctionMustHaveParamTypes(_, 0) =>
    }
  }

  test("Humanize errors") {
    val codeMap = FileCoordinateMap.test("blah blah blah\nblah blah blah")

    vassert(PostParserErrorHumanizer.humanize(codeMap,
      VariableNameAlreadyExists(RangeS.testZero, CodeVarNameS("Spaceship")))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      InterfaceMethodNeedsSelf(RangeS.testZero))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      ForgotSetKeywordError(RangeS.testZero))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      CantUseThatLocalName(RangeS.testZero, "set"))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      ExternHasBody(RangeS.testZero))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      CantInitializeIndividualElementsOfRuntimeSizedArray(RangeS.testZero))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      LightFunctionMustHaveParamTypes(RangeS.testZero, 0))
      .nonEmpty)

  }
}
