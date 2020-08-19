package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.WeakableImplingMismatch
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vivem.ConstraintViolatedException
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class WeakTests extends FunSuite with Matchers {
  test("Make and lock weak ref then destroy own, with struct") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("weaks/lockWhileLiveStruct.vale"))

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

  test("Destroy own then locking gives none, with struct") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("weaks/dropThenLockStruct.vale"))

    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("Drop while locked, with struct") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("weaks/dropWhileLockedStruct.vale"))

    try {
      compile.evalForReferend(Vector()) shouldEqual VonInt(42)
      vfail()
    } catch {
      case ConstraintViolatedException(_) =>
      case _ => vfail()
    }
  }

  test("Make and lock weak ref from borrow local then destroy own, with struct") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
      Samples.get("weaks/weakFromLocalCRefStruct.vale"))

    val main = compile.getTemputs().lookupFunction("main")

    vassert(main.body.all({ case SoftLoad2(_, Weak) => true }).size >= 1)

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Make and lock weak ref from borrow then destroy own, with struct") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("weaks/weakFromCRefStruct.vale"))

    val main = compile.getTemputs().lookupFunction("main")

    vassert(main.body.all({ case SoftLoad2(_, Weak) => true }).size >= 1)

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Make weak ref from temporary") {
    val compile = Compilation(
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

  test("Cant make weak ref to non-weakable") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        """
          |struct Muta { hp int; }
          |fn getHp(weakMuta &&Muta) { lock(weakMuta)^.get().hp }
          |fn main() { getHp(&&Muta(7)) }
          |""".stripMargin)

    try {
      compile.getTemputs().lookupFunction("main")
      vfail()
    } catch {
      case TookWeakRefOfNonWeakableError() =>
      case _ => vfail()
    }

  }

  test("Cant make weakable extend a non-weakable") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        """
          |interface IUnit {}
          |struct Muta weakable { hp int; }
          |impl IUnit for Muta;
          |fn main(muta Muta) { 7 }
          |""".stripMargin)

    try {
      compile.getTemputs().lookupFunction("main")
      vfail()
    } catch {
      case WeakableImplingMismatch(true, false) =>
      case _ => vfail()
    }
  }

  test("Cant make non-weakable extend a weakable") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        """
          |interface IUnit weakable {}
          |struct Muta { hp int; }
          |impl IUnit for Muta;
          |fn main(muta Muta) { 7 }
          |""".stripMargin)

    try {
      compile.getTemputs().lookupFunction("main")
      vfail()
    } catch {
      case WeakableImplingMismatch(false, true) =>
      case other => {
        other.printStackTrace()
        vfail()
      }
    }
  }


  test("Make and lock weak ref then destroy own, with interface") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("weaks/lockWhileLiveInterface.vale"))

    val main = compile.getTemputs().lookupFunction("main")
    main.only({
      case LetNormal2(ReferenceLocalVariable2(FullName2(_,CodeVarName2("weakUnit")),Final,Coord(Weak, _)),refExpr) => {
        refExpr.resultRegister.reference match {
          case Coord(Weak, InterfaceRef2(simpleName("IUnit"))) =>
        }
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Destroy own then locking gives none, with interface") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("weaks/dropThenLockInterface.vale"))

    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("Drop while locked, with interface") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("weaks/dropWhileLockedInterface.vale"))

    try {
      compile.evalForReferend(Vector()) shouldEqual VonInt(42)
      vfail()
    } catch {
      case ConstraintViolatedException(_) =>
      case _ => vfail()
    }
  }

  test("Make and lock weak ref from borrow local then destroy own, with interface") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("weaks/weakFromLocalCRefInterface.vale"))

    val main = compile.getTemputs().lookupFunction("main")

    vassert(main.body.all({ case SoftLoad2(_, Weak) => true }).size >= 1)

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Make and lock weak ref from borrow then destroy own, with interface") {
    val compile = Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("weaks/weakFromCRefInterface.vale"))

    val main = compile.getTemputs().lookupFunction("main")

    vassert(main.body.all({ case SoftLoad2(_, Weak) => true }).size >= 1)

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

}
