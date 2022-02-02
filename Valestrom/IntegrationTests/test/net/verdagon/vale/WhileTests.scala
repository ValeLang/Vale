package net.verdagon.vale

import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class WhileTests extends FunSuite with Matchers {
  test("Simple while loop that doesnt execute") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  while (false) {}
        |  ret 5;
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
        |  ret i;
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
        |  while set key = __getch(); key < 96 {
        |    print(key);
        |  }
        |  ret key;
        |}
      """.stripMargin)

    compile.evalForKind(Vector(), Vector("A", "B", "c")) shouldEqual VonInt(99)
  }

  test("Tests a while loop with a set in it") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |import ioutils.*;
        |import logic.*;
        |
        |fn main() int export {
        |  key = 0;
        |  while set key = __getch(); key != 99 {
        |    print(key);
        |  }
        |  ret key;
        |}
      """.stripMargin)

    compile.evalForKind(Vector(), Vector("A", "B", "c")) shouldEqual VonInt(99)
  }

  test("Tests a while loop with a declaration in it") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |import ioutils.*;
        |import logic.*;
        |
        |fn main() export {
        |  while key = __getch(); key != 99 {
        |    print(key);
        |  }
        |}
      """.stripMargin)

    compile.evalForKind(Vector(), Vector("A", "B", "c"))
  }

  test("Return from infinite while loop") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  while (true) {
        |    ret 9;
        |  }
        |  ret __vbi_panic();
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("While with condition declaration") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  while x = 42; x < 50 { ret x; }
        |  ret 73;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
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
//        |  ret 4;
//        |}
//      """.stripMargin)
//
//    // should fail
//
//    compile.evalForKind(Vector()) shouldEqual VonInt(4)
//  }
}
