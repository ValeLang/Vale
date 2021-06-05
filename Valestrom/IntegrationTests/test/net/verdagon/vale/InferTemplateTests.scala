package net.verdagon.vale

import net.verdagon.vale.templar.{CitizenName2, CodeVarName2, FullName2, simpleName}
import net.verdagon.vale.templar.templata.{CoordTemplata, Parameter2}
import net.verdagon.vale.templar.types.{Constraint, Coord, Own, Readonly, Readwrite, StructRef2}
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class InferTemplateTests extends FunSuite with Matchers {
  test("Test inferring a borrowed argument") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |fn moo<T>(m &T) int { m.hp }
        |fn main() int export {
        |  x = Muta(10);
        |  = moo(&x);
        |}
      """.stripMargin)

    val moo = compile.expectTemputs().lookupFunction("moo")
    moo.header.params match {
      case List(Parameter2(CodeVarName2("m"), _, Coord(Constraint,Readonly, _))) =>
    }
    moo.header.fullName.last.templateArgs shouldEqual
      List(CoordTemplata(Coord(Own,Readwrite,StructRef2(FullName2(List(),CitizenName2("Muta",List()))))), CoordTemplata(Coord(Constraint,Readonly,StructRef2(FullName2(List(),CitizenName2("Muta",List()))))))

    compile.evalForReferend(Vector()) shouldEqual VonInt(10)
  }
  test("Test inferring a borrowed known size array") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |fn moo<N>(m &[N * Muta]) int { m[0].hp }
        |fn main() int export {
        |  x = [][Muta(10)];
        |  = moo(&x);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(10)
  }
  test("Test inferring an owning known size array") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |fn moo<N>(m [N * Muta]) int { m[0].hp }
        |fn main() int export {
        |  x = [][Muta(10)];
        |  = moo(x);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(10)
  }
}
