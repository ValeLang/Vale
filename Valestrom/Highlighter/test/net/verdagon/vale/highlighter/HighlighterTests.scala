package net.verdagon.vale.highlighter
import net.verdagon.vale.highlighter.Spanner._
import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class HighlighterTests extends FunSuite with Matchers {
  private def highlight(code: String): String = {
    VParser.runParser(code) match {
      case VParser.NoSuccess(msg, input) => {
        fail();
      }
      case VParser.Success((program0, commentRanges), rest) => {
        if (!rest.atEnd) {
          vfail(rest.pos.longString)
        }
        Highlighter.toHTML(code, Spanner.forProgram(program0), commentRanges)
      }
    }
  }

  test("Highlighter simple function") {
    val code =
      """
        |fn main() {
        |  3
        |}
        |""".stripMargin
    highlight(code) shouldEqual
      """<span class="Prog"><br /><span class="Fn">fn <span class="FnName">main</span><span class="Params">()</span> <span class="Block">&#123;<br />  <span class="Num">3</span><br />&#125;</span></span><br /></span>"""
  }

  test("Highlighter with comments") {
    val code =
      """
        |fn main(
        | // hello
        |) {
        |  3//bork
        |}
        |""".stripMargin
    highlight(code) shouldEqual
      """<span class="Prog"><br /><span class="Fn">fn <span class="FnName">main</span><span class="Params">(<br /> <span class="Comment">// hello</span><br />)</span> <span class="Block">&#123;<br />  <span class="Num">3<span class="Comment">//bork</span></span><br />&#125;</span></span><br /></span>"""
  }
}
