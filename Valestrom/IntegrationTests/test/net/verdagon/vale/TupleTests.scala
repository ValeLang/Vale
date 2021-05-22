package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types.Int2
import net.verdagon.von.{VonBool, VonInt, VonObject}
import org.scalatest.{FunSuite, Matchers}

class TupleTests extends FunSuite with Matchers {
  test("Returning tuple from function and dotting it") {
    val compile = RunCompilation.test(
      """
        |fn makeArray() infer-ret { [2, 3, 4, 5, 6] }
        |fn main() int export {
        |  makeArray().3
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Simple tuple with one int") {
    val compile = RunCompilation.test( "fn main() int export { [9].0 }")

    val temputs = compile.expectTemputs()
    temputs.lookupFunction("main").header.returnType.referend shouldEqual Int2()
    // Funny story, theres no such thing as a one element tuple! It becomes a one element array.
    temputs.lookupFunction("main").only({ case TupleE2(_, _, _) => })

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Tuple with two things") {
    val compile = RunCompilation.test( "fn main() bool export { [9, true].1 }")
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }


  test("Tuple type") {
    val compile = RunCompilation.test(
      """
        |fn moo(a [int, int]) int { a.1 }
        |
        |fn main() int {
        |  moo([3, 4])
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  // todo: indexing into it with a variable, to get a union type
}
