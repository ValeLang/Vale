package dev.vale

import dev.vale.templar.ast.ParameterT
import dev.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, CodeVarNameT, FullNameT}
import dev.vale.templar.templata.CoordTemplata
import dev.vale.templar.types.{BorrowT, CoordT, OwnT, StructTT}
import dev.vale.templar.names.CitizenTemplateNameT
import dev.vale.templar.templata.simpleName
import dev.vale.templar.types.StructTT
import dev.vale.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class InferTemplateTests extends FunSuite with Matchers {
  test("Test inferring a borrowed argument") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |func moo<T>(m &T) int { ret m.hp; }
        |exported func main() int {
        |  x = Muta(10);
        |  ret moo(&x);
        |}
      """.stripMargin)

    val moo = compile.expectTemputs().lookupFunction("moo")
    moo.header.params match {
      case Vector(ParameterT(CodeVarNameT("m"), _, CoordT(BorrowT,_))) =>
    }
    moo.header.fullName.last.templateArgs match {
      case Vector(CoordTemplata(CoordT(OwnT, StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector(), CitizenNameT(CitizenTemplateNameT("Muta"), Vector())))))) =>
    }

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }
  test("Test inferring a borrowed static sized array") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |func moo<N>(m &[#N]Muta) int { ret m[0].hp; }
        |exported func main() int {
        |  x = [#][Muta(10)];
        |  ret moo(&x);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }
  test("Test inferring an owning static sized array") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |func moo<N>(m [#N]Muta) int { ret m[0].hp; }
        |exported func main() int {
        |  x = [#][Muta(10)];
        |  ret moo(x);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }
}
