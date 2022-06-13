package dev.vale.postparsing

import dev.vale.{Err, FileCoordinateMap, Interner, Ok, RangeS, StrI, vassert, vfail}
import dev.vale.options.GlobalOptions
import dev.vale.parsing._
import dev.vale.postparsing.patterns.AbstractSP
import dev.vale.postparsing.rules._
import org.scalatest.{FunSuite, Matchers}

class PostParserErrorHumanizerTests extends FunSuite with Matchers {

  private def compile(code: String): ProgramS = {
    val interner = new Interner()
    PostParserTestCompilation.test(code).getScoutput() match {
      case Err(e) => vfail(PostParserErrorHumanizer.humanize(FileCoordinateMap.test(interner, code), e))
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
    val interner = new Interner()
    val codeMap = FileCoordinateMap.test(interner, "blah blah blah\nblah blah blah")
    val tz = RangeS.testZero(interner)

    vassert(PostParserErrorHumanizer.humanize(codeMap,
      VariableNameAlreadyExists(tz, CodeVarNameS(interner.intern(StrI("Spaceship")))))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      InterfaceMethodNeedsSelf(tz))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      ForgotSetKeywordError(tz))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      CantUseThatLocalName(tz, "set"))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      ExternHasBody(tz))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      CantInitializeIndividualElementsOfRuntimeSizedArray(tz))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(codeMap,
      LightFunctionMustHaveParamTypes(tz, 0))
      .nonEmpty)

  }
}
