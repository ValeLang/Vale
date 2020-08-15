package net.verdagon.vale

import net.verdagon.vale.templar.{CitizenName2, CodeVarName2, FullName2, simpleName}
import net.verdagon.vale.templar.templata.{CoordTemplata, Parameter2}
import net.verdagon.vale.templar.types.{Borrow, Coord, Own, StructRef2}
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class InferTemplateTests extends FunSuite with Matchers {
  test("Test inferring a borrowed argument") {
    val compile = Compilation(
      """
        |struct Muta { hp int; }
        |fn moo<T>(m &T) { m.hp }
        |fn main() {
        |  x = Muta(10);
        |  = moo(&x);
        |}
      """.stripMargin)

    val moo = compile.getTemputs().lookupFunction("moo")
    moo.header.params match {
      case List(Parameter2(CodeVarName2("m"), _, Coord(Borrow, _))) =>
    }
    moo.header.fullName.last.templateArgs shouldEqual
      List(CoordTemplata(Coord(Own,StructRef2(FullName2(List(),CitizenName2("Muta",List()))))), CoordTemplata(Coord(Borrow,StructRef2(FullName2(List(),CitizenName2("Muta",List()))))))

    compile.evalForReferend(Vector()) shouldEqual VonInt(10)
  }
}
