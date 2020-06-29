package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types.Int2
import net.verdagon.von.{VonBool, VonInt}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class TupleTests extends FunSuite with Matchers {
  test("Simple tuple with one int") {
    val compile = new Compilation("fn main() { [9].0 }")

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").header.returnType.referend shouldEqual Int2()
    // Funny story, theres no such thing as a one element tuple! It becomes a one element array.
    temputs.lookupFunction("main").only({ case ArraySequenceE2(_, _, _) => })

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Tuple with two things") {
    val compile = new Compilation("fn main() { [9, true].1 }")
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

  // todo: indexing into it with a variable, to get a union type
}
