package dev.vale.highlighter

import Spanner._
import dev.vale.{Err, Ok}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.Parser
import dev.vale.parsing._
import dev.vale.Err
import org.scalatest.{FunSuite, Matchers}

class HighlighterTests extends FunSuite with Matchers {
  private def highlight(code: String): String = {
    new Parser(GlobalOptions(true, true, true, true)).
        runParserForProgramAndCommentRanges(code) match {
      case Err(err) => fail(err.toString)
      case Ok((program0, commentRanges)) => {
        Highlighter.toHTML(code, Spanner.forProgram(program0), commentRanges)
      }
    }
  }

  test("Highlighter simple function") {
    val code =
      """
        |func main() {
        |  3
        |}
        |""".stripMargin
    highlight(code) shouldEqual
      """<span class="Prog"><br /><span class="Fn">func <span class="FnName">main</span><span class="Params">()</span> <span class="Block">&#123;<br />  <span class="Num">3</span><br />&#125;</span></span><br /></span>"""
  }

  test("Highlighter with comments") {
    val code =
      """
        |func main(
        | // hello
        |) {
        |  3//bork
        |}
        |""".stripMargin
    highlight(code) shouldEqual
      """<span class="Prog"><br /><span class="Fn">func <span class="FnName">main</span><span class="Params">(<br /> <span class="Comment">// hello</span><br />)</span> <span class="Block">&#123;<br />  <span class="Num">3<span class="Comment">//bork</span></span><br />&#125;</span></span><br /></span>"""
  }
}
