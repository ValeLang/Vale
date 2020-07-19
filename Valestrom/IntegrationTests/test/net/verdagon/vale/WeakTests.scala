package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class WeakTests extends FunSuite with Matchers {
  test("Make and lock weak ref") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
      """
        |struct Muta weakable { hp int; }
        |fn main() {
        |  ownMuta = Muta(7);
        |  weakMuta = &&ownMuta;
        |  borrowMuta = lock(weakMuta);
        |  = borrowMuta.hp;
        |}
      """.stripMargin)

    val main = compile.getTemputs().lookupFunction("main")
    main.only({
      case LetAndLend2(ReferenceLocalVariable2(FullName2(List(FunctionName2("main",List(),List())),TemplarTemporaryVarName2(0)),Final,_),refExpr) => {
        refExpr.resultRegister.reference match {
          case Coord(Own, StructRef2(simpleName("Muta"))) =>
        }
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

}
