package net.verdagon.vale

import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.types._
import net.verdagon.von.{VonBool, VonFloat, VonInt}
import org.scalatest.{FunSuite, Matchers}

class FloatTests extends FunSuite with Matchers {
  test("Print float") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |fn main() export {
        |  a = 42.125;
        |  print(a);
        |}
      """.stripMargin)

    compile.evalForStdout(Vector()).trim() shouldEqual "42.125"
  }

  test("Float arithmetic") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/floatarithmetic.vale"))

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Float equals") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/floateq.vale"))

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Concat string and float") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/concatstrfloat.vale"))

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }
}
