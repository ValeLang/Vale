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
      Span(Fn,Range(0,25),Vector(
        Span(FnName,Range(3,7),Vector.empty),
        Span(Params,Range(7,9),Vector.empty),
        Span(Ret,Range(10,20),Vector(Span(Ret,Range(10,19),Vector.empty))),
        Span(Block,Range(20,25),Vector(
          Span(Num,Range(22,23),Vector.empty)))))
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
        Vector(
          Span(FnName,_,Vector()),
          Span(Params,_,Vector()),
          Span(Ret,_,Vector(Span(Ret,_,Vector()))),
          Span(Block,_,
            Vector(
              Span(Call,_,
                Vector(
                  Span(MemberAccess,_,
                    Vector(
                      Span(Lookup,_,Vector()),
                      Span(MemberAccess,_,Vector()),
                      Span(Lookup,_,Vector()))),
                  Span(MemberAccess,_,Vector()),
                  Span(CallLookup,_,Vector()))))))) =>
    }
  }
}
