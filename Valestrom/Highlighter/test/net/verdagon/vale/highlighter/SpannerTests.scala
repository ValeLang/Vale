package net.verdagon.vale.highlighter

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.ast.FileP
import net.verdagon.vale.parser.{ast, _}
import net.verdagon.vale.{Err, FileCoordinateMap, Ok, PackageCoordinate, PackageCoordinateMap, vfail}
import org.scalatest.{FunSuite, Matchers}

class SpannerTests extends FunSuite with Matchers {
  private def compile(code: String): FileP = {
    val compilation =
      new ParserCompilation(
        GlobalOptions(true, true, true, true),
        Vector(PackageCoordinate.TEST_TLD),
        FileCoordinateMap.test(code))
    compilation.getParseds() match {
      case Err(err) => fail(err.toString)
      case Ok(program0) => program0.expectOne()._1
    }
  }

  test("Spanner simple function") {
    val program1 = compile("func main() infer-ret { 3 }")
    val main = program1.lookupFunction("main")
    Spanner.forFunction(main) shouldEqual
      Span(Fn,ast.RangeP(0,27),Vector(
        Span(FnName,ast.RangeP(5,9),Vector.empty),
        Span(Params,ast.RangeP(9,11),Vector.empty),
        Span(Ret,ast.RangeP(12,22),Vector(Span(Ret,ast.RangeP(12,21),Vector.empty))),
        Span(Block,ast.RangeP(22,27),Vector(
          Span(Num,ast.RangeP(24,25),Vector.empty)))))
  }


  test("Spanner map call") {
    val program1 = compile(
      """func main() infer-ret {
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
