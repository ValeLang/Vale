package net.verdagon.vale

import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class WhileTests extends FunSuite with Matchers {
  test("Simple while loop that doesnt execute") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  while (false) {}
        |  = 5;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Test a for-ish while loop") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  i! = 0;
        |  while (i < 4) {
        |    set i = i + 1;
        |  }
        |  = i;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }

  test("Tests a while loop with a complex condition") {
    val compile = RunCompilation.test(
      """import ioutils.*;
        |import printutils.*;
        |fn main() int export {
        |  key! = 0;
        |  while (set key = __getch(); = key < 96;) {
        |    print(key);
        |  }
        |  = key;
        |}
      """.stripMargin)

    compile.evalForKind(Vector(), Vector("A", "B", "c")) shouldEqual VonInt(99)
  }

  test("Tests a while loop with a != in it") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |import ioutils.*;
        |import logic.*;
        |
        |fn main() int export {
        |  key! = 0;
        |  while (set key = __getch(); = key != 99;) {
        |    print(key);
        |  }
        |  = key;
        |}
      """.stripMargin)

    compile.evalForKind(Vector(), Vector("A", "B", "c")) shouldEqual VonInt(99)
  }

  test("Return from infinite while loop") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  while (true) {
        |    ret 9;
        |  }
        |  = __vbi_panic();
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }
//
//  test("Tests a while loop with a move in it") {
//    val compile = RunCompilation.test(
//      """
//        |fn doThings(m: Marine) { }
//        |struct Marine { hp: int; }
//        |fn main() int export {
//        |  m = Marine(7);
//        |  while (true) {
//        |    doThings(m);
//        |  }
//        |  = 4;
//        |}
//      """.stripMargin)
//
//    // should fail
//
//    compile.evalForKind(Vector()) shouldEqual VonInt(4)
//  }
}
