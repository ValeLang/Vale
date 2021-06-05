package net.verdagon.vale

import net.verdagon.von.{VonBool, VonInt}
import org.scalatest.{FunSuite, Matchers}

class ConjunctionTests extends FunSuite with Matchers {
  test("And") {
    val compile = RunCompilation.test("fn main() bool export {true and true}")
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

  test("Or") {
    val compile = RunCompilation.test("fn main() bool export {true or false}")
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

  test("And short-circuiting") {
    val compile = RunCompilation.test(
      """
        |fn printAndFalse() bool { print("bork!"); = false; }
        |fn main() bool export {printAndFalse() and printAndFalse()}
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "bork!"
  }

  test("Or short-circuiting") {
    val compile = RunCompilation.test(
      """
        |fn printAndTrue() bool { print("bork!"); = true; }
        |fn main() bool export {printAndTrue() or printAndTrue()}
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "bork!"
  }
}
