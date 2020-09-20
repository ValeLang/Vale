package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class StringTests extends FunSuite with Matchers {
  test("Simple string") {
    val compile = Compilation(
      """
        |fn main() {
        |  "sprogwoggle"
        |}
      """.stripMargin, false)

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").only({ case StrLiteral2("sprogwoggle") => })

    compile.evalForReferend(Vector()) shouldEqual VonStr("sprogwoggle")
  }

  test("String with escapes") {
    val compile = Compilation(
      """
        |fn main() {
        |  "sprog\nwoggle"
        |}
        |""".stripMargin, false)

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").only({ case StrLiteral2("sprog\nwoggle") => })

    compile.evalForReferend(Vector()) shouldEqual VonStr("sprog\nwoggle")
  }

  test("String with hex escape") {
    val compile = Compilation(
      """
        |fn main() {
        |  "sprog\u001bwoggle"
        |}
        |""".stripMargin, false)

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").only({
      case StrLiteral2(x) => {
        x shouldEqual "sprog\u001bwoggle"
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonStr("sprog\u001bwoggle")
  }


  test("String length") {
    val compile = Compilation(Samples.get("programs/strings/strlen.vale"))

    compile.evalForReferend(Vector()) shouldEqual VonInt(11)
  }
}
