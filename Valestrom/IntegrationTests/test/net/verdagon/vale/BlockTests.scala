package net.verdagon.vale

import net.verdagon.vale.parser.{FinalP, VariabilityP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types.{Bool2, Coord, Int2, Share}
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class BlockTests extends FunSuite with Matchers {
  test("Empty block") {
    val compile = Compilation(
      """
        |fn main() {
        |  block {
        |  }
        |  = 3;
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput()
    val main = scoutput.lookupFunction("main")
    main.body match { case CodeBody1(BodySE(_,BlockSE(_,List(BlockSE(_,_), _)))) => }

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }
  test("Simple block with a variable") {
    val compile = Compilation(
      """
        |fn main() {
        |  block {
        |    y = 6;
        |  }
        |  = 3;
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput()
    val main = scoutput.lookupFunction("main")
    val block = main.body match { case CodeBody1(BodySE(_,BlockSE(_,List(b @ BlockSE(_,_), _)))) => b }
    vassert(block.locals.size == 1)
    block.locals.head match {
      case LocalVariable1(CodeVarNameS("y"), FinalP, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }
  test("Simple block with a variable, another variable outside with same name") {
    val compile = Compilation(
      """
        |fn main() {
        |  block {
        |    y = 6;
        |  }
        |  y = 3;
        |  = y;
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput()

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }
}
