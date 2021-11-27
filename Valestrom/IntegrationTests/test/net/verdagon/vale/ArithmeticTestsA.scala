package net.verdagon.vale

import net.verdagon.vale.driver.{FullCompilation}
import net.verdagon.vale.hammer.VonHammer
import net.verdagon.vale.metal.YonderH
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types.{CoordT, IntT, ShareT, StrT}
import net.verdagon.vale.vivem.{Heap, IntV, StructInstanceV}
import net.verdagon.vale.{metal => m}
import net.verdagon.von.{VonBool, VonFloat, VonInt}
import org.scalatest.{FunSuite, Matchers}

class ArithmeticTestsA extends FunSuite with Matchers {
  test("Dividing") {
    val compile = RunCompilation.test("fn main() int export {5 / 2}")
    compile.evalForKind(Vector()) shouldEqual VonInt(2)
  }
}
