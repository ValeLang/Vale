package dev.vale

import dev.vale.templar.env.ReferenceLocalVariableT
import dev.vale.templar.expression.CallTemplar
import dev.vale.templar._
import dev.vale.templar.types._
import dev.vale.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class FloatTests extends FunSuite with Matchers {
  test("Print float") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |exported func main() {
        |  a = 42.125;
        |  print(a);
        |}
      """.stripMargin)

    compile.evalForStdout(Vector()).trim() shouldEqual "42.125"
  }

  test("Float arithmetic") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/floatarithmetic.vale"))

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Float equals") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/floateq.vale"))

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Concat string and float") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/concatstrfloat.vale"))

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }
}
