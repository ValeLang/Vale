package dev.vale

import dev.vale.postparsing.{BlockSE, BodySE, CodeBodyS, CodeVarNameS, ConsecutorSE, LocalS, NotUsed}
import dev.vale.postparsing._
import dev.vale.typing._
import dev.vale.typing.types.BoolT
import dev.vale.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class BlockTests extends FunSuite with Matchers {
  test("Empty block") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  block {
        |  }
        |  ret 3;
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput().getOrDie().moduleToPackagesToFilenameToContents("test")(Vector.empty)("0.vale")
    val main = scoutput.lookupFunction("main")
    main.body match { case CodeBodyS(BodySE(_, _,BlockSE(_, _,ConsecutorSE(Vector(BlockSE(_, _,_), _))))) => }

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple block with a variable") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
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

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple block with a variable, another variable outside with same name") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  block {
        |    y = 6;
        |  }
        |  y = 3;
        |  ret y;
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput().getOrDie()

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
}
