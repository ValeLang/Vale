package dev.vale

import dev.vale.typing.ast.TupleTE
import dev.vale.typing.types.IntT
import dev.vale.typing._
import dev.vale.von.{VonBool, VonInt}
import org.scalatest.{FunSuite, Matchers}

class TupleTests extends FunSuite with Matchers {
  test("Returning tuple from function and dotting it") {
    val compile = RunCompilation.test(
      """
        |func makeTup() (int, int) { return (2, 3); }
        |exported func main() int {
        |  return makeTup().1;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Tuple with two things") {
    val compile = RunCompilation.test( "exported func main() bool { return (9, true).1; }")
    compile.evalForKind(Vector()) match { case VonBool(true) => }
  }


  test("Tuple type") {
    val compile = RunCompilation.test(
      """
        |func moo(a (int, int)) int { return a.1; }
        |
        |exported func main() int {
        |  return moo((3, 4));
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  // todo: indexing into it with a variable, to get a union type
}
