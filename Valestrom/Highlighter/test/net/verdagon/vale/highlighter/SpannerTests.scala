package net.verdagon.vale.highlighter

import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class SpannerTests extends FunSuite with Matchers {
  private def compile(code: String): FileP = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(program0) => program0
    }
  }

  test("Spanner simple function") {
    val program1 = compile("fn main() infer-ret { 3 }")
    val main = program1.lookupFunction("main")
    Spanner.forFunction(main) shouldEqual
      Span(Fn,Range(0,25),List(
        Span(FnName,Range(3,7),List.empty),
        Span(Params,Range(7,9),List.empty),
        Span(Ret,Range(10,20),List(Span(Ret,Range(10,19),List.empty))),
        Span(Block,Range(20,25),List(
          Span(Num,Range(22,23),List.empty)))))
  }


  test("Spanner map call") {
    val program1 = compile(
      """fn main() infer-ret {
        |  this.abilities*.getImpulse();
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("main")
    Spanner.forFunction(main) match {
      case Span(
        Fn,_,
        List(
          Span(FnName,_,List.empty),
          Span(Params,_,List.empty),
          Span(Ret,_,List(Span(Ret,_,List.empty))),
          Span(Block,_,
            List(
              Span(Call,_,
                List(
                  Span(MemberAccess,_,
                    List(
                      Span(Lookup,_,List.empty),
                      Span(MemberAccess,_,List.empty),
                      Span(Lookup,_,List.empty))),
                  Span(MemberAccess,_,List.empty),
                  Span(CallLookup,_,List.empty))))))) =>
    }
  }
}
