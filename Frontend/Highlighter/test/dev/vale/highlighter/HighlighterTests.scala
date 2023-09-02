package dev.vale.highlighter

import Spanner._
import dev.vale.lexing.RangeL
import dev.vale.{Err, FileCoordinateMap, IPackageResolver, Interner, Keywords, Ok, PackageCoordinate}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.Parser
import dev.vale.parsing._
import org.scalatest._

import scala.collection.immutable.Map

class HighlighterTests extends FunSuite with Matchers {
  private def highlight(code: String): String = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val opts = GlobalOptions(true, true, true, true, true)
    val codeMap = FileCoordinateMap.test(interner, Vector(code))
    ParseAndExplore.parseAndExploreAndCollect(
      interner,
      keywords,
      opts,
      new Parser(interner, keywords, opts),
      Vector(PackageCoordinate.TEST_TLD(interner, keywords)),
      new IPackageResolver[Map[String, String]]() {
        override def resolve(packageCoord: PackageCoordinate): Option[Map[String, String]] = {
          // For testing the parser, we dont want it to fetch things with import statements
          Some(codeMap.resolve(packageCoord).getOrElse(Map("" -> "")))
        }
      }) match {
      case Err(err) => fail(err.toString)
      case Ok(accumulator) => {
        val file = accumulator.buildArray().head._2
        val span = Spanner.forProgram(file)
        Highlighter.toHTML(code, span, file.commentsRanges.map(x => RangeL(x.begin, x.end)).toVector)
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
