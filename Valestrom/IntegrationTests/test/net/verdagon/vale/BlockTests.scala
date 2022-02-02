package net.verdagon.vale

import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types.{BoolT, CoordT, IntT, ShareT}
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class BlockTests extends FunSuite with Matchers {
  test("Empty block") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  block {
        |  }
        |  ret 3;
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput().getOrDie().moduleToPackagesToFilenameToContents("test")(Vector.empty)("0.vale")
    val main = scoutput.lookupFunction("main")
    main.body match { case CodeBodyS(BodySE(_, _,BlockSE(_, _,ConsecutorSE(Vector(BlockSE(_, _,_), _))))) => }

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }
  test("Simple block with a variable") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  block {
        |    y = 6;
        |  }
        |  ret 3;
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput().getOrDie().moduleToPackagesToFilenameToContents("test")(Vector.empty)("0.vale")
    val main = scoutput.lookupFunction("main")
    val block = main.body match { case CodeBodyS(BodySE(_, _,BlockSE(_, _, ConsecutorSE(Vector(b @ BlockSE(_, _,_), _))))) => b }
    vassert(block.locals.size == 1)
    block.locals.head match {
      case LocalS(CodeVarNameS("y"), NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) =>
    }

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }
  test("Simple block with a variable, another variable outside with same name") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  block {
        |    y = 6;
        |  }
        |  y = 3;
        |  ret y;
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput().getOrDie()

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }
}
