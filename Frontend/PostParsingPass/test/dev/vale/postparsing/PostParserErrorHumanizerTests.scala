package dev.vale.postparsing

import dev.vale.{CodeLocationS, Err, FileCoordinateMap, Interner, Ok, RangeS, SourceCodeUtils, StrI, vassert, vfail}
import dev.vale.options.GlobalOptions
import dev.vale.parsing._
import dev.vale.postparsing.rules._
import org.scalatest.{FunSuite, Matchers}

class PostParserErrorHumanizerTests extends FunSuite with Matchers {

  private def compile(code: String): ProgramS = {
    val interner = new Interner()
    PostParserTestCompilation.test(code).getScoutput() match {
      case Err(e) => {
        val codeMap = FileCoordinateMap.test(interner, code)
        vfail(
          PostParserErrorHumanizer.humanize(
            SourceCodeUtils.humanizePos(codeMap, _),
            SourceCodeUtils.linesBetween(codeMap, _, _),
            SourceCodeUtils.lineRangeContaining(codeMap, _),
            SourceCodeUtils.lineContaining(codeMap, _),
            e))
      }
      case Ok(t) => t.expectOne()
    }
  }

  private def compileForError(code: String): ICompileErrorS = {
    PostParserTestCompilation.test(code).getScoutput() match {
      case Err(e) => e
      case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
    }
  }

  test("Humanize errors") {
    val interner = new Interner()
    val codeMap = FileCoordinateMap.test(interner, "blah blah blah\nblah blah blah")
    val tz = RangeS.testZero(interner)

    val humanizePos = (x: CodeLocationS) => SourceCodeUtils.humanizePos(codeMap, x)
    val linesBetween = (x: CodeLocationS, y: CodeLocationS) => SourceCodeUtils.linesBetween(codeMap, x, y)
    val lineRangeContaining = (x: CodeLocationS) => SourceCodeUtils.lineRangeContaining(codeMap, x)
    val lineContaining = (x: CodeLocationS) => SourceCodeUtils.lineContaining(codeMap, x)

    vassert(PostParserErrorHumanizer.humanize(humanizePos, linesBetween, lineRangeContaining, lineContaining,
      VariableNameAlreadyExists(tz, CodeVarNameS(interner.intern(StrI("Spaceship")))))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(humanizePos, linesBetween, lineRangeContaining, lineContaining,
      InterfaceMethodNeedsSelf(tz))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(humanizePos, linesBetween, lineRangeContaining, lineContaining,
      ForgotSetKeywordError(tz))
      .nonEmpty)
    vassert(PostParserErrorHumanizer.humanize(humanizePos, linesBetween, lineRangeContaining, lineContaining,
      ExternHasBody(tz))
      .nonEmpty)
  }
}
