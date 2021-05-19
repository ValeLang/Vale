package net.verdagon.vale

import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class PrintTests extends FunSuite with Matchers {
  test("Println'ing an int") {
    val compile = Compilation.test(List("builtinexterns"),
      """
        |fn main() {
        |  println(6);
        |}
      """.stripMargin +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))

    compile.evalForStdout(Vector()) shouldEqual "6\n"
  }

  test("Println'ing a bool") {
    val compile = Compilation.test(List("builtinexterns"),
      """
        |fn main() {
        |  println(true);
        |}
      """.stripMargin +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))

    compile.evalForStdout(Vector()) shouldEqual "true\n"
  }
}
