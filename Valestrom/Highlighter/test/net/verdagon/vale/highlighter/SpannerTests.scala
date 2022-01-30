package net.verdagon.vale.highlighter

import net.verdagon.vale.parser.ast.FileP
import net.verdagon.vale.parser.{ast, _}
import net.verdagon.vale.{Err, Ok, vfail}
import org.scalatest.{FunSuite, Matchers}

class SpannerTests extends FunSuite with Matchers {
  private def compile(code: String): FileP = {
    Parser.runParser(code) match {
      case Err(err) => fail(err.toString)
      case Ok(program0) => program0
    }
  }

  test("Spanner simple function") {
    val program1 = compile("fn main() infer-ret { 3; }")
    val main = program1.lookupFunction("main")
    Spanner.forFunction(main) shouldEqual
      Span(Fn,ast.RangeP(0,26),Vector(
        Span(FnName,ast.RangeP(3,7),Vector.empty),
        Span(Params,ast.RangeP(7,9),Vector.empty),
        Span(Ret,ast.RangeP(10,20),Vector(Span(Ret,ast.RangeP(10,19),Vector.empty))),
        Span(Block,ast.RangeP(20,26),Vector(
          Span(Num,ast.RangeP(22,23),Vector.empty)))))
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
