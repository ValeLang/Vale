package dev.vale.postparsing

import dev.vale.parsing.ast.{FinalP, LoadAsBorrowP, MutableP, UseP}
import dev.vale.postparsing.patterns.{AtomSP, CaptureS}
import dev.vale.postparsing.rules.{LiteralSR, MaybeCoercingLookupSR, MutabilityLiteralSL, RuneUsage}
import dev.vale.solver.IncompleteSolve
import dev.vale._
import org.scalatest._

class AfterRegionsErrorTests extends FunSuite with Matchers with Collector {

  private def compile(code: String, interner: Interner = new Interner()): ProgramS = {
    val compile = PostParserTestCompilation.test(code, interner)
    compile.getScoutput() match {
      case Err(e) => {
        val codeMap = compile.getCodeMap().getOrDie()
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

  test("Reports when non-kind interface in impl") {
    val err = compileForError(
      """
        |struct Moo {}
        |interface IMoo {}
        |impl &IMoo for Moo;
        |""".stripMargin)
    err match {
      case CantOwnershipInterfaceInImpl(_) =>
    }
  }

  test("Reports when non-kind struct in impl") {
    val err = compileForError(
      """
        |struct Moo {}
        |interface IMoo {}
        |impl IMoo for &Moo;
        |""".stripMargin)
    err match {
      case CantOwnershipStructInImpl(_) =>
    }
  }

  test("Test one-anonymous-param lambda identifying runes") {
    val bork = compile(
      """
        |exported func main() int {do((_) => { true })}
        |""".stripMargin)

    val main = bork.lookupFunction("main")
    // We dont support regions yet, so scout should filter them out.
    main.genericParams.size shouldEqual 0
    val lambda = Collector.onlyOf(main.body, classOf[FunctionSE])
    lambda.function.genericParams.size shouldEqual 1

    // The above vale code seems to work even if we dont disambiguate lambda function
    // instantiations with different identifying runes.
    // We still need to decide whether we want to or not.
    // See also "Test two instantiations of anonymous-param lambda"
    vimpl()
  }
}