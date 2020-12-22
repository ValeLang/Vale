package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.von.{VonBool, VonInt}
import org.scalatest.{FunSuite, Matchers}

class ConjunctionTests extends FunSuite with Matchers {
  test("And") {
    val compile = Compilation("fn main() bool export {true and true}")
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

  test("Or") {
    val compile = Compilation("fn main() bool export {true or false}")
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

  test("And short-circuiting") {
    val compile = Compilation(
      """
        |fn printAndFalse() bool { print("bork!"); = false; }
        |fn main() bool export {printAndFalse() and printAndFalse()}
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "bork!"
  }

  test("Or short-circuiting") {
    val compile = Compilation(
      """
        |fn printAndTrue() bool { print("bork!"); = true; }
        |fn main() bool export {printAndTrue() or printAndTrue()}
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "bork!"
  }
}
