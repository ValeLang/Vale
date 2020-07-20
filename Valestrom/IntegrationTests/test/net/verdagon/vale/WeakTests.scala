package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class WeakTests extends FunSuite with Matchers {
  test("Make and lock weak ref then destroy own") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        """
          |struct Muta weakable { hp int; }
          |fn main() {
          |  ownMuta = Muta(7);
          |  weakMuta = &&ownMuta;
          |  maybeBorrowMuta = lock(weakMuta);
          |  = if (maybeBorrowMuta.empty?()) {
          |      drop(maybeBorrowMuta);
          |      = 73;
          |    } else {
          |      maybeBorrowMuta^.get().hp
          |    }
          |}
      """.stripMargin)

    val main = compile.getTemputs().lookupFunction("main")
    main.only({
      case LetNormal2(ReferenceLocalVariable2(FullName2(_,CodeVarName2("weakMuta")),Final,Coord(Weak, _)),refExpr) => {
        refExpr.resultRegister.reference match {
          case Coord(Weak, StructRef2(simpleName("Muta"))) =>
        }
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Destroy own then locking gives none") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        """
          |struct Muta weakable { hp int; }
          |fn main() {
          |  ownMuta = Muta(73);
          |  weakMuta = &&ownMuta;
          |  drop(ownMuta);
          |  maybeBorrowMuta = lock(weakMuta);
          |  = if (maybeBorrowMuta.empty?()) {
          |      drop(maybeBorrowMuta);
          |      = 42;
          |    } else {
          |      maybeBorrowMuta^.get().hp
          |    }
          |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

}
