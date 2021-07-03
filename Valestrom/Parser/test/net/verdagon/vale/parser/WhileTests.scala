package net.verdagon.vale.parser

import net.verdagon.vale.{Tests, vassert}
import org.scalatest.{FunSuite, Matchers}


class WhileTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Simple while loop") {
    compile(CombinatorParsers.statement,"while () {}") shouldHave {
      case WhilePE(_, BlockPE(_, List(VoidPE(_))), BlockPE(_, List(VoidPE(_)))) =>
    }
  }

  test("Result after while loop") {
    compile(CombinatorParsers.blockExprs,"while () {} = false;") shouldHave {
      case List(
      WhilePE(_, BlockPE(_, List(VoidPE(_))), BlockPE(_, List(VoidPE(_)))),
      ConstantBoolPE(_, false)) =>
    }
  }
}
