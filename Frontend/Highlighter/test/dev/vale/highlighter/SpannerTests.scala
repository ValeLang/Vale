package dev.vale.highlighter

import dev.vale.lexing.RangeL
import dev.vale.options.GlobalOptions
import dev.vale.parsing.{ParserCompilation, ast}
import dev.vale.{Err, FileCoordinateMap, Interner, Ok, PackageCoordinate}
import dev.vale.parsing.ast.FileP
import dev.vale.parsing.{ast, _}
import org.scalatest.{FunSuite, Matchers}

class SpannerTests extends FunSuite with Matchers {
  private def compile(code: String): FileP = {
    val interner = new Interner()
    val compilation =
      new ParserCompilation(
        GlobalOptions(true, true, true, true),
        interner,
        Vector(PackageCoordinate.TEST_TLD(interner)),
        FileCoordinateMap.test(interner, code))
    compilation.getParseds() match {
      case Err(err) => fail(err.toString)
      case Ok(program0) => program0.expectOne()._1
    }
  }

  test("Spanner simple function") {
    val program1 = compile("func main() infer-return { 3 }")
    val main = program1.lookupFunction("main")
    Spanner.forFunction(main) shouldEqual
      Span(Fn,RangeL(0,30),Vector(
        Span(FnName,ast.RangeL(5,9),Vector.empty),
        Span(Params,ast.RangeL(9,11),Vector.empty),
        Span(Ret,ast.RangeL(12,24),Vector(Span(Ret,ast.RangeL(12,24),Vector.empty))),
        Span(Block,ast.RangeL(25,30),Vector(
          Span(Num,ast.RangeL(27,28),Vector.empty)))))
  }


  test("Spanner map call") {
    val program1 = compile(
      """func main() infer-return {
        |  this.abilities.getImpulse();
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
              Span(Consecutor,_,
                Vector(
                  Span(Call,_,
                    Vector(
                      Span(MemberAccess,_,
                        Vector(
                          Span(Lookup,_,Vector()),
                          Span(MemberAccess,_,Vector()),
                          Span(Lookup,_,Vector()))),
                      Span(MemberAccess,_,Vector()),
                      Span(CallLookup,_,Vector()))))))))) =>
    }
  }
}
