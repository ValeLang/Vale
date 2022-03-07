package net.verdagon.vale

import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.IfTE
import net.verdagon.vale.templar.types._
import net.verdagon.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.vivem.IntV

class IfTests extends FunSuite with Matchers {
  test("Simple true branch returning an int") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  ret if (true) { 3 } else { 5 }
        |}
      """.stripMargin)
    val programS = compile.getScoutput().getOrDie().moduleToPackagesToFilenameToContents("test")(Vector.empty)("0.vale")
    val main = programS.lookupFunction("main")
    val ret = Collector.only(main.body, { case r @ ReturnSE(_, _) => r })
    val iff = Collector.only(ret, { case i @ IfSE(_, _, _, _) => i })
    Collector.only(iff.condition, { case ConstantBoolSE(_, true) => })
    Collector.only(iff.thenBody, { case ConstantIntSE(_, 3, _) => })
    Collector.only(iff.elseBody, { case ConstantIntSE(_, 5, _) => })

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), { case IfTE(_, _, _) => })

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Simple false branch returning an int") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  ret if (false) { 3 } else { 5 }
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Ladder") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  ret if (false) { 3 } else if (true) { 5 } else { 7 }
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val ifs = Collector.all(temputs.lookupFunction("main"), { case if2 @ IfTE(_, _, _) => if2 })
    ifs.foreach(iff => iff.result.reference shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32))
    ifs.size shouldEqual 2
    val userFuncs = temputs.getAllUserFunctions
    userFuncs.foreach(func => {
      func.header.returnType match {
        case CoordT(ShareT, ReadonlyT, IntT.i32) =>
        case CoordT(ShareT, ReadonlyT, BoolT()) =>
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Moving from inside if") {
    val compile = RunCompilation.test(
      """
        |struct Marine { x int; }
        |exported func main() int {
        |  m = Marine(5);
        |  ret if (false) {
        |      [x] = m;
        |      x
        |    } else {
        |      [y] = m;
        |      y
        |    }
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val ifs = Collector.all(temputs.lookupFunction("main"), { case if2 @ IfTE(_, _, _) => if2 })
    ifs.foreach(iff => iff.result.reference shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32))
    val userFuncs = temputs.getAllUserFunctions
    userFuncs.foreach(func => {
      func.header.returnType match {
        case CoordT(ShareT, ReadonlyT, IntT.i32) =>
        case CoordT(ShareT, ReadonlyT, BoolT()) =>
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("If with complex condition") {
    val compile = RunCompilation.test(
      """
        |struct Marine { x int; }
        |exported func main() str {
        |  m = Marine(5);
        |  ret if (m.x == 5) { "#" }
        |  else if (0 == 0) { "?" }
        |  else { "." }
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val ifs = Collector.all(temputs.lookupFunction("main"), { case if2 @ IfTE(_, _, _) => if2 })
    ifs.foreach(iff => iff.result.reference shouldEqual CoordT(ShareT, ReadonlyT, StrT()))

    compile.evalForKind(Vector()) match { case VonStr("#") => }
  }

  test("If with condition declaration") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  ret if x = 42; x < 50 { x }
        |    else { 73 }
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Ret from inside if will destroy locals") {
    val compile = RunCompilation.test(
      """import printutils.*;
        |#!DeriveStructDrop
        |struct Marine { hp int; }
        |func drop(marine Marine) void {
        |  println("Destroying marine!");
        |  Marine[weapon] = marine;
        |}
        |exported func main() int {
        |  m = Marine(5);
        |  x =
        |    if (true) {
        |      println("In then!");
        |      ret 7;
        |    } else {
        |      println("In else!");
        |      m.hp
        |    };
        |  println("In rest!");
        |  ret x;
        |}
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "In then!\nDestroying marine!\n"
  }

  test("Can continue if other branch would have returned") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |#!DeriveStructDrop
        |struct Marine { hp int; }
        |func drop(marine Marine) void {
        |  println("Destroying marine!");
        |  Marine[weapon] = marine;
        |}
        |exported func main() int {
        |  m = Marine(5);
        |  x =
        |    if (false) {
        |      println("In then!");
        |      ret 7;
        |    } else {
        |      println("In else!");
        |      m.hp
        |    };
        |  println("In rest!");
        |  ret x;
        |}
        |""".stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    compile.evalForStdout(Vector()) shouldEqual "In else!\nIn rest!\nDestroying marine!\n"
  }

  test("Destructure inside if") {
    val compile = RunCompilation.test(
      """import printutils.*;
        |struct Bork {
        |  num int;
        |}
        |struct Moo {
        |  bork Bork;
        |}
        |
        |exported func main() {
        |  zork = 0;
        |  while (zork < 4) {
        |    moo = Moo(Bork(5));
        |    if (true) {
        |      [bork] = moo;
        |      println(bork.num);
        |    } else {
        |      drop(moo);
        |    }
        |    set zork = zork + 1;
        |  }
        |}
        |""".stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    compile.evalForStdout(Vector()) shouldEqual "5\n5\n5\n5\n"
  }

  test("If nevers") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/if/ifnevers.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("If with panics and rets") {
    val compile =
      RunCompilation.test(
        """
          |exported func main() int {
          |  a = 7;
          |  if false {
          |    panic("lol");
          |    ret 73;
          |  } else {
          |    ret 42;
          |  }
          |  ret 73;
          |}
          |
          |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Toast") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  a = 0;
        |  if (a == 2) {
        |    ret 71;
        |  } else if (a == 5) {
        |    ret 73;
        |  } else {
        |    ret 42;
        |  }
        |}
        |""".stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

}
