package dev.vale

import dev.vale.typing.ast.{FunctionCallTE, ParameterT, PrototypeT}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, CodeVarNameT, IdT, FunctionNameT, FunctionTemplateNameT, StructNameT, StructTemplateNameT}
import dev.vale.typing.templata.CoordTemplata
import dev.vale.typing.types._
import dev.vale.typing.templata.simpleName
import dev.vale.typing.types.StructTT
import dev.vale.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class InferTemplateTests extends FunSuite with Matchers {
  test("Test inferring a borrowed argument") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |func moo<T>(m &T) &T { return m; }
        |exported func main() int {
        |  x = Muta(10);
        |  return moo(&x).hp;
        |}
      """.stripMargin)

    val moo = compile.expectCompilerOutputs().lookupFunction("moo")
    moo.header.params match {
      case Vector(ParameterT(CodeVarNameT(StrI("m")), _, CoordT(BorrowT,_))) =>
    }
    val main = compile.expectCompilerOutputs().lookupFunction("main")
    Collector.only(main, {
      case FunctionCallTE(PrototypeT(IdT(_, _, FunctionNameT(FunctionTemplateNameT(StrI("moo"), _), templateArgs, _)), _), _) => {
        templateArgs match {
          case Vector(CoordTemplata(CoordT(OwnT, StructTT(IdT(x, Vector(), StructNameT(StructTemplateNameT(StrI("Muta")), Vector())))))) => {
            vassert(x.isTest)
          }
        }
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }
  test("Test inferring a borrowed static sized array") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |func moo<N Int>(m &[#N]Muta) int { return m[0].hp; }
        |exported func main() int {
        |  x = [#](Muta(10));
        |  return moo(&x);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }
  test("Test inferring an owning static sized array") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |func moo<N Int>(m [#N]Muta) int { return m[0].hp; }
        |exported func main() int {
        |  x = [#](Muta(10));
        |  return moo(x);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }
}
