package net.verdagon.vale.highlighter

import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class SpannerTests extends FunSuite with Matchers {
  private def compile(code: String): Program0 = {
    VParser.parse(VParser.program, code.toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail();
      }
      case VParser.Success(program0, rest) => {
        if (!rest.atEnd) {
          vfail(rest.pos.longString)
        }
        program0
      }
    }
  }

  test("Spanner simple function") {
    val program1 = compile("fn main() { 3 }")
    val main = program1.lookupFunction("main")
    Spanner.forFunction(main) shouldEqual
      Span(Fn,Range(Pos(1,1),Pos(1,16)),List(
        Span(FnName,Range(Pos(1,4),Pos(1,8)),List()),
        Span(Params,Range(Pos(1,8),Pos(1,10)),List())))
  }
}
