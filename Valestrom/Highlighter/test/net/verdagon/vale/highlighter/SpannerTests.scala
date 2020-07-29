package net.verdagon.vale.highlighter

import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class SpannerTests extends FunSuite with Matchers {
  private def compile(code: String): Program0 = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(program0) => program0
    }
  }

  test("Spanner simple function") {
    val program1 = compile("fn main() { 3 }")
    val main = program1.lookupFunction("main")
    Spanner.forFunction(main) shouldEqual
      Span(Fn,Range(Pos(1,1),Pos(1,16)),List(
        Span(FnName,Range(Pos(1,4),Pos(1,8)),List()),
        Span(Params,Range(Pos(1,8),Pos(1,10)),List()),
        Span(Block,Range(Pos(1,11),Pos(1,16)),List(
          Span(Num,Range(Pos(1,13),Pos(1,14)),List())))))
  }


  test("Spanner map call") {
    val program1 = compile(
      """fn main() {
        |  this.abilities*.getImpulse();
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("main")
    Spanner.forFunction(main) match {
      case Span(
        Fn,_,
        List(
          Span(FnName,_,List()),
          Span(Params,_,List()),
          Span(Block,_,
            List(
              Span(Call,_,
                List(
                  Span(MemberAccess,_,
                    List(
                      Span(Lookup,_,List()),
                      Span(MemberAccess,_,List()),
                      Span(Lookup,_,List()))),
                  Span(MemberAccess,_,List()),
                  Span(CallLookup,_,List()))))))) =>
    }
  }
}
