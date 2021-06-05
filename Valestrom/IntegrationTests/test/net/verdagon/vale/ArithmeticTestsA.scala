package net.verdagon.vale

import net.verdagon.vale.driver.{FullCompilation}
import net.verdagon.vale.hammer.VonHammer
import net.verdagon.vale.metal.YonderH
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.Signature2
import net.verdagon.vale.templar.types.{Coord, Int2, Share, Str2}
import net.verdagon.vale.vivem.{Heap, IntV, StructInstanceV}
import net.verdagon.vale.{metal => m}
import net.verdagon.von.{VonBool, VonFloat, VonInt}
import org.scalatest.{FunSuite, Matchers}

class ArithmeticTestsA extends FunSuite with Matchers {
  test("Dividing") {
    val compile = RunCompilation.test("fn main() int export {5 / 2}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(2)
  }
}
