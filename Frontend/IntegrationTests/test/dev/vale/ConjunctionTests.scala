package dev.vale

import dev.vale.von.VonBool
import org.scalatest.{FunSuite, Matchers}

class ConjunctionTests extends FunSuite with Matchers {
  test("And") {
    val compile = RunCompilation.test("exported func main() bool { return true and true; }")
    compile.evalForKind(Vector()) match { case VonBool(true) => }
  }

  test("Or") {
    val compile = RunCompilation.test("exported func main() bool { return true or false; }")
    compile.evalForKind(Vector()) match { case VonBool(true) => }
  }

  test("And short-circuiting") {
    val compile = RunCompilation.test(
      """
        |func printAndFalse() bool { print("bork!"); return false; }
        |exported func main() bool { return printAndFalse() and printAndFalse(); }
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "bork!"
  }

  test("Or short-circuiting") {
    val compile = RunCompilation.test(
      """
        |func printAndTrue() bool { print("bork!"); return true; }
        |exported func main() bool { return printAndTrue() or printAndTrue(); }
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "bork!"
  }
}
