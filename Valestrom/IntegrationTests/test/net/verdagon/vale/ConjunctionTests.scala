package net.verdagon.vale

import net.verdagon.von.{VonBool, VonInt}
import org.scalatest.{FunSuite, Matchers}

class ConjunctionTests extends FunSuite with Matchers {
  test("And") {
    val compile = RunCompilation.test("fn main() bool export { ret true and true; }")
    compile.evalForKind(Vector()) shouldEqual VonBool(true)
  }

  test("Or") {
    val compile = RunCompilation.test("fn main() bool export { ret true or false; }")
    compile.evalForKind(Vector()) shouldEqual VonBool(true)
  }

  test("And short-circuiting") {
    val compile = RunCompilation.test(
      """
        |fn printAndFalse() bool { print("bork!"); ret false; }
        |fn main() bool export { ret printAndFalse() and printAndFalse(); }
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "bork!"
  }

  test("Or short-circuiting") {
    val compile = RunCompilation.test(
      """
        |fn printAndTrue() bool { print("bork!"); ret true; }
        |fn main() bool export { ret printAndTrue() or printAndTrue(); }
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "bork!"
  }
}
