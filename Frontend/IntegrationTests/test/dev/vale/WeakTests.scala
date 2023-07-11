package dev.vale

import dev.vale.typing.ast.{BorrowToWeakTE, LetNormalTE, SoftLoadTE}
import dev.vale.typing.citizen.WeakableImplingMismatch
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.expression.TookWeakRefOfNonWeakableError
import dev.vale.typing.names.{CodeVarNameT, IdT}
import dev.vale.typing.templata.simpleName
import dev.vale.typing.types._
import dev.vale.testvm.ConstraintViolatedException
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.names.CodeVarNameT
import dev.vale.typing.types._
import dev.vale.von.VonInt
import org.scalatest._

class WeakTests extends FunSuite with Matchers {
  test("Make and lock weak ref then destroy own, with struct") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/lockWhileLiveStruct.vale"))

    val main = compile.expectCompilerOutputs().lookupFunction("main")
    Collector.only(main, {
      case LetNormalTE(ReferenceLocalVariableT(CodeVarNameT(StrI("weakMuta")),FinalT,CoordT(WeakT, _, _)),refExpr) => {
        refExpr.result.coord match {
          case CoordT(WeakT, _, StructTT(simpleName("Muta"))) =>
        }
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Destroy own then locking gives none, with struct") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/dropThenLockStruct.vale"))

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Drop while locked, with struct") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/dropWhileLockedStruct.vale"))

    try {
      compile.evalForKind(Vector()) match { case VonInt(42) => }
      vfail()
    } catch {
      case ConstraintViolatedException(_) =>
      case _ => vfail()
    }
  }

  test("Make and lock weak ref from borrow local then destroy own, with struct") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/weaks/weakFromLocalCRefStruct.vale"))

    val main = compile.expectCompilerOutputs().lookupFunction("main")

    vassert(Collector.all(main.body, { case SoftLoadTE(_, WeakT) => true }).size >= 1)

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Make and lock weak ref from borrow then destroy own, with struct") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/weakFromCRefStruct.vale"))

    val main = compile.expectCompilerOutputs().lookupFunction("main")

    vassert(Collector.all(main.body, { case SoftLoadTE(_, WeakT) => true }).size >= 1)

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Make weak ref from temporary") {
    val compile = RunCompilation.test(
        """
          |weakable struct Muta { hp int; }
          |func getHp(weakMuta &&Muta) int { return (lock(weakMuta)).get().hp; }
          |exported func main() int { return getHp(&&Muta(7)); }
          |""".stripMargin)

    val main = compile.expectCompilerOutputs().lookupFunction("main")
    Collector.only(main.body, { case BorrowToWeakTE(_) => })
    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }


  test("Make and lock weak ref then destroy own, with interface") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/lockWhileLiveInterface.vale"))

    val main = compile.expectCompilerOutputs().lookupFunction("main")
    Collector.only(main, {
      case LetNormalTE(ReferenceLocalVariableT(CodeVarNameT(StrI("weakUnit")),FinalT,CoordT(WeakT, _, _)),refExpr) => {
        refExpr.result.coord match {
          case CoordT(WeakT, _, InterfaceTT(simpleName("IUnit"))) =>
        }
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Destroy own then locking gives none, with interface") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/dropThenLockInterface.vale"))

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Drop while locked, with interface") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/dropWhileLockedInterface.vale"))

    try {
      compile.evalForKind(Vector()) match { case VonInt(42) => }
      vfail()
    } catch {
      case ConstraintViolatedException(_) =>
      case other => vfail(other)
    }
  }

  test("Make and lock weak ref from borrow local then destroy own, with interface") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/weakFromLocalCRefInterface.vale"))

    val main = compile.expectCompilerOutputs().lookupFunction("main")

    vassert(Collector.all(main.body, { case SoftLoadTE(_, WeakT) => true }).size >= 1)

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Make and lock weak ref from borrow then destroy own, with interface") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/weakFromCRefInterface.vale"))

    val main = compile.expectCompilerOutputs().lookupFunction("main")

    vassert(Collector.all(main.body, { case SoftLoadTE(_, WeakT) => true }).size >= 1)

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Call weak-self method, after drop") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/callWeakSelfMethodAfterDrop.vale"))

    val main = compile.expectCompilerOutputs().lookupFunction("main")

    vassert(Collector.all(main.body, { case SoftLoadTE(_, WeakT) => true }).size >= 1)

    val hamuts = compile.getHamuts()

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Call weak-self method, while alive") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/weaks/callWeakSelfMethodWhileLive.vale"))

    val main = compile.expectCompilerOutputs().lookupFunction("main")

    vassert(Collector.all(main.body, { case SoftLoadTE(_, WeakT) => true }).size >= 1)

    val hamuts = compile.getHamuts()

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Weak yonder member") {
    val compile = RunCompilation.test(
        """
          |struct Base {
          |  hp int;
          |}
          |struct Spaceship {
          |  origin &&Base;
          |}
          |exported func main() int {
          |  base = Base(73);
          |  ship = Spaceship(&&base);
          |
          |  (base).drop(); // Destroys base.
          |
          |  maybeOrigin = lock(ship.origin); «14»«15»
          |  if (not maybeOrigin.isEmpty()) { «16»
          |    o = maybeOrigin.get();
          |    return o.hp;
          |  } else {
          |    return 42;
          |  }
          |}
          |""".stripMargin)

    val main = compile.expectCompilerOutputs().lookupFunction("main")

    val hamuts = compile.getHamuts()

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }


}
