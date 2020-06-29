package net.verdagon.vale

import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class PrintTests extends FunSuite with Matchers {
  test("Println'ing an int") {
    val compile = new Compilation(
      """
        |fn main() {
        |  println(6);
        |}
      """.stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "6\n"
  }
}
