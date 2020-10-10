package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class StringTests extends FunSuite with Matchers {
  test("Simple string") {
    val compile = Compilation(
      """
        |fn main() str {
        |  "sprogwoggle"
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").only({ case StrLiteral2("sprogwoggle") => })

    compile.evalForReferend(Vector()) shouldEqual VonStr("sprogwoggle")
  }

  test("String with escapes") {
    val compile = Compilation(
      """
        |fn main() str {
        |  "sprog\nwoggle"
        |}
        |""".stripMargin)

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").only({ case StrLiteral2("sprog\nwoggle") => })

    compile.evalForReferend(Vector()) shouldEqual VonStr("sprog\nwoggle")
  }

  test("String with hex escape") {
    val compile = Compilation(
      """
        |fn main() str {
        |  "sprog\u001bwoggle"
        |}
        |""".stripMargin)

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

  // Intentional failure 2020.09.26
  test("Slice a slice") {
    val compile = Compilation(Samples.get("programs/strings/complex/strlen.vale"))

    compile.evalForReferend(Vector()) shouldEqual VonInt(11)
  }
}
