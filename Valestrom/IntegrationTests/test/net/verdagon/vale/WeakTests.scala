package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vivem.ConstraintViolatedException
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

  test("Drop while locked") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        """
          |struct Muta weakable { hp int; }
          |fn main() {
          |  ownMuta = Muta(73);
          |  weakMuta = &&ownMuta;
          |  maybeBorrowMuta = lock(weakMuta);
          |  drop(ownMuta);
          |  = maybeBorrowMuta^.get().hp;
          |}
      """.stripMargin)

    try {
      compile.evalForReferend(Vector()) shouldEqual VonInt(42)
      vfail()
    } catch {
      case ConstraintViolatedException(_) =>
      case _ => vfail()
    }
  }

  test("Make and lock weak ref from borrow then destroy own") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        """
          |struct Muta weakable { hp int; }
          |fn main() {
          |  ownMuta = Muta(7);
          |  borrowMuta = &ownMuta;
          |  weakMuta = &&borrowMuta;
          |  maybeBorrowMuta = lock(weakMuta);
          |  = maybeBorrowMuta^.get().hp;
          |}
          |""".stripMargin)

    val main = compile.getTemputs().lookupFunction("main")

    vassert(main.body.all({ case SoftLoad2(_, Weak) => true }).size >= 1)

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Make weak ref from temporary") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        """
          |struct Muta weakable { hp int; }
          |fn getHp(weakMuta &&Muta) { lock(weakMuta)^.get().hp }
          |fn main() { getHp(&&Muta(7)) }
          |""".stripMargin)

    val main = compile.getTemputs().lookupFunction("main")
    main.body.only({ case WeakAlias2(_) => })
    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

}
