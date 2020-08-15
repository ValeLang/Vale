package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.von.{VonBool, VonInt}
import org.scalatest.{FunSuite, Matchers}

class ConjunctionTests extends FunSuite with Matchers {
  test("And") {
    val compile = Compilation("fn main(){true and true}")
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

  test("Or") {
    val compile = Compilation("fn main(){true or false}")
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }
}
