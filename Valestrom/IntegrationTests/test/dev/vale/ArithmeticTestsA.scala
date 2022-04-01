package dev.vale

import dev.vale.hammer.VonHammer
import dev.vale.metal.YonderH
import dev.vale.driver.FullCompilation
import dev.vale.templar._
import dev.vale.templar.types.StrT
import dev.vale.vivem.StructInstanceV
import dev.vale.von.VonInt
import dev.vale.{metal => m}
import org.scalatest.{FunSuite, Matchers}

class ArithmeticTestsA extends FunSuite with Matchers {
  test("Dividing") {
    val compile = RunCompilation.test("exported func main() int { ret 5 / 2; }")
    compile.evalForKind(Vector()) match { case VonInt(2) => }
  }
}
