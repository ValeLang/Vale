package dev.vale

import dev.vale.simplifying.VonHammer
import dev.vale.finalast.YonderH
import dev.vale.passmanager.FullCompilation
import dev.vale.typing._
import dev.vale.typing.types.StrT
import dev.vale.testvm.StructInstanceV
import dev.vale.von.VonInt
import dev.vale.{finalast => m}
import org.scalatest._

class ArithmeticTestsA extends FunSuite with Matchers {
  test("Dividing") {
    val compile = RunCompilation.test("exported func main() int { return 5 / 2; }")
    compile.evalForKind(Vector()) match { case VonInt(2) => }
  }
}
