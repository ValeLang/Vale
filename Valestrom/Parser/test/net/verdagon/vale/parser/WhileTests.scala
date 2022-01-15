package net.verdagon.vale.parser

import net.verdagon.vale.{Collector, Tests, vassert}
import org.scalatest.{FunSuite, Matchers}


class WhileTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Simple while loop") {
    compile(CombinatorParsers.statement,"while true {}") shouldHave {
      case WhilePE(_, BlockPE(_, Vector(ConstantBoolPE(_, true))), BlockPE(_, Vector(VoidPE(_)))) =>
    }
  }

  test("Result after while loop") {
    compile(CombinatorParsers.blockExprs(true),"while true {} = false;") shouldHave {
      case Vector(
      WhilePE(_, BlockPE(_, Vector(ConstantBoolPE(_, true))), BlockPE(_, Vector(VoidPE(_)))),
      ConstantBoolPE(_, false)) =>
    }
  }
}
