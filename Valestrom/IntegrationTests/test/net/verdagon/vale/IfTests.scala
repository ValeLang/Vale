package net.verdagon.vale

import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types._
import net.verdagon.von.{VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class IfTests extends FunSuite with Matchers {
  test("Simple true branch returning an int") {
    val compile = new Compilation(
      """
        |fn main() {
        |  = if (true) { 3 } else { 5 }
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput()
    val main = scoutput.lookupFunction("main")
    val CodeBody1(BodySE(_, BlockSE(_, List(IfSE(_, _, _))))) = main.body

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").only({ case If2(_, _, _) => })

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Simple false branch returning an int") {
    val compile = new Compilation(
      """
        |fn main() {
        |  = if (false) { 3 } else { 5 }
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Ladder") {
    val compile = new Compilation(
      """
        |fn main() {
        |  = if (false) { 3 } else if (true) { 5 } else { 7 }
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val ifs = temputs.lookupFunction("main").all({ case if2 @ If2(_, _, _) => if2 })
    ifs.foreach(iff => iff.resultRegister.reference shouldEqual Coord(Share, Int2()))
    ifs.size shouldEqual 2
    val userFuncs = temputs.getAllUserFunctions
    userFuncs.foreach(func => {
      func.header.returnType match {
        case Coord(Share, Int2()) =>
        case Coord(Share, Bool2()) =>
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Moving from inside if") {
    val compile = new Compilation(
      """
        |struct Marine { x *Int; }
        |fn main() {
        |  m = Marine(5);
        |  = if (false) {
        |      (x) = m;
        |      = x;
        |    } else {
        |      (y) = m;
        |      = y;
        |    }
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val ifs = temputs.lookupFunction("main").all({ case if2 @ If2(_, _, _) => if2 })
    ifs.foreach(iff => iff.resultRegister.reference shouldEqual Coord(Share, Int2()))
    val userFuncs = temputs.getAllUserFunctions
    userFuncs.foreach(func => {
      func.header.returnType match {
        case Coord(Share, Int2()) =>
        case Coord(Share, Bool2()) =>
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("If with complex condition") {
    val compile = new Compilation(
      """
        |struct Marine { x *Int; }
        |fn main() {
        |  m = Marine(5);
        |  = if (m.x == 5) { "#" }
        |  else if (0 == 0) { "?" }
        |  else { "." }
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val ifs = temputs.lookupFunction("main").all({ case if2 @ If2(_, _, _) => if2 })
    ifs.foreach(iff => iff.resultRegister.reference shouldEqual Coord(Share, Str2()))

    compile.evalForReferend(Vector()) shouldEqual VonStr("#")
  }

  test("Ret from inside if will destroy locals") {
    val compile = new Compilation(
      """struct Marine { hp Int; }
        |fn destructor(marine Marine) Void {
        |  println("Destroying marine!");
        |  Marine(weapon) = marine;
        |}
        |fn main() {
        |  m = Marine(5);
        |  x =
        |    if (true) {
        |      println("In then!");
        |      ret 7;
        |    } else {
        |      println("In else!");
        |      = m.hp;
        |    };
        |  println("In rest!");
        |  = x;
        |}
        |""".stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "In then!\nDestroying marine!\n"
  }

  test("Can continue if other branch would have returned") {
    val compile = new Compilation(
      """struct Marine { hp Int; }
        |fn destructor(marine Marine) Void {
        |  println("Destroying marine!");
        |  Marine(weapon) = marine;
        |}
        |fn main() {
        |  m = Marine(5);
        |  x =
        |    if (false) {
        |      println("In then!");
        |      ret 7;
        |    } else {
        |      println("In else!");
        |      = m.hp;
        |    };
        |  println("In rest!");
        |  = x;
        |}
        |""".stripMargin)

    val main = compile.getTemputs().lookupFunction("main")
    compile.evalForStdout(Vector()) shouldEqual "In else!\nIn rest!\nDestroying marine!\n"
  }
}
