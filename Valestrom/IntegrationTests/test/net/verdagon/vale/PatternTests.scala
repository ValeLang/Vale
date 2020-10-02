package net.verdagon.vale

import net.verdagon.vale.parser.FinalP
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types.{Coord, Final, Int2, Share}
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class PatternTests extends FunSuite with Matchers {
  // To get something like this to work would be rather involved.
  //test("Test matching a single-member pack") {
  //  val compile = Compilation("fn main() { [x] = (4); = x; }")
  //  compile.getTemputs()
  //  val main = temputs.lookupFunction("main")
  //  main.header.returnType shouldEqual Coord(Share, Int2())
  //  compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  //}

  test("Test matching a multiple-member seq of immutables") {
    // Checks that the 5 made it into y, and it was an int
    val compile = Compilation("fn main() { (x, y) = [4, 5]; = y; }")
    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main.header.returnType shouldEqual Coord(Share, Int2())
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Test matching a multiple-member seq of mutables") {
    // Checks that the 5 made it into y, and it was an int
    val compile = Compilation(
      """
        |struct Marine { hp int; }
        |fn main() { (x, y) = [Marine(6), Marine(8)]; = y.hp; }
      """.stripMargin)
    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main");
    main.header.returnType shouldEqual Coord(Share, Int2())
    compile.evalForReferend(Vector()) shouldEqual VonInt(8)
  }

  test("Test matching a multiple-member pack of immutable and own") {
    // Checks that the 5 made it into y, and it was an int
    val compile = Compilation(
      """
        |struct Marine { hp int; }
        |fn main() { (x, y) = [7, Marine(8)]; = y.hp; }
      """.stripMargin)
    val temputs = compile.getTemputs()
    temputs.getAllFunctions().head.header.returnType == Coord(Share, Int2())
    compile.evalForReferend(Vector()) shouldEqual VonInt(8)
  }

  test("Test matching a multiple-member pack of immutable and borrow") {
    // Checks that the 5 made it into y, and it was an int
    val compile = Compilation(
      """
        |struct Marine { hp int; }
        |fn main() {
        |  m = Marine(8);
        |  (x, y) = [7, &m];
        |  = y.hp;
        |}
      """.stripMargin)
    val temputs = compile.getTemputs()
    temputs.getAllFunctions().head.header.returnType == Coord(Share, Int2())
    compile.evalForReferend(Vector()) shouldEqual VonInt(8)
  }
}
