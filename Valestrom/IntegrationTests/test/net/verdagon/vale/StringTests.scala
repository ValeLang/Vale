package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class StringTests extends FunSuite with Matchers {
  test("Simple string") {
    val compile = Compilation.test(List("builtinexterns"),
      """
        |fn main() str {
        |  "sprogwoggle"
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").only({ case StrLiteral2("sprogwoggle") => })

    compile.evalForReferend(Vector()) shouldEqual VonStr("sprogwoggle")
  }

  test("String with escapes") {
    val compile = Compilation.test(List("builtinexterns"),
      """
        |fn main() str {
        |  "sprog\nwoggle"
        |}
        |""".stripMargin)

    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").only({ case StrLiteral2("sprog\nwoggle") => })

    compile.evalForReferend(Vector()) shouldEqual VonStr("sprog\nwoggle")
  }

  test("String with hex escape") {
    val compile = Compilation.test(List("builtinexterns"),
      """
        |fn main() str {
        |  "sprog\u001bwoggle"
        |}
        |""".stripMargin)

    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").only({
      case StrLiteral2(x) => {
        x shouldEqual "sprog\u001bwoggle"
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonStr("sprog\u001bwoggle")
  }

  test("String length") {
    val compile = Compilation.test(List("builtinexterns"), Samples.get("programs/strings/strlen.vale"))

    compile.evalForReferend(Vector()) shouldEqual VonInt(11)
  }

  test("String interpolate") {
    val compile = Compilation.test(List("builtinexterns"),
      "fn +(s str, i int) str { s + str(i) }\n" +
      "fn ns(i int) int { i }\n" +
      "fn main() str { \"\"\"bl\"{ns(4)}rg\"\"\" }")

    compile.evalForReferend(Vector()) shouldEqual VonStr("bl\"4rg")
  }

  test("Slice a slice") {
    val compile = Compilation.test(List("builtinexterns"),
      List(
        Samples.get("builtins/strings.vale"),
        Samples.get("builtins/castutils.vale"),
        Samples.get("libraries/opt.vale"),
        Samples.get("libraries/utils.vale"),
        Samples.get("libraries/printutils.vale"),
        """
          |fn main() int {
          |  "hello".slice().slice(1, 4).len()
          |}
          |""".stripMargin))

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }
}
