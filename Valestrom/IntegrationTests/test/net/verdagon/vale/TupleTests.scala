package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types.Int2
import net.verdagon.von.{VonBool, VonInt}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class TupleTests extends FunSuite with Matchers {
  test("Returning tuple from function and dotting it") {
    val compile = Compilation(
      """
        |fn makeArray() infer-ret { [2, 3, 4, 5, 6] }
        |fn main() int export {
        |  makeArray().3
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Simple tuple with one int") {
    val compile = Compilation("fn main() int export { [9].0 }")

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").header.returnType.referend shouldEqual Int2()
    // Funny story, theres no such thing as a one element tuple! It becomes a one element array.
    temputs.lookupFunction("main").only({ case ArraySequenceE2(_, _, _) => })

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Tuple with two things") {
    val compile = Compilation("fn main() bool export { [9, true].1 }")
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }


  // Intentional failure 2020-10-15
  test("Tuple type") {
    val compile = Compilation(
      """
        |fn moo(a [int, int]) { }
        |
        |fn main() {
        |  moo([3, 4]);
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

  // todo: indexing into it with a variable, to get a union type
}
