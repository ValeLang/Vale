package net.verdagon.vale

import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.env.ReferenceLocalVariableT
import net.verdagon.vale.templar.templata.{functionName, simpleName}
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.names.{FullNameT, FunctionNameT, TemplarTemporaryVarNameT}

class OwnershipTests extends FunSuite with Matchers {
  test("Borrowing a temporary mutable makes a local var") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |exported func main() int {
        |  ret (&Muta(9)).hp;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    Collector.only(main, {
      case letTE @ LetAndLendTE(ReferenceLocalVariableT(FullNameT(_, Vector(FunctionNameT("main",Vector(),Vector())),TemplarTemporaryVarNameT(_)),FinalT,_),refExpr,targetOwnership) => {
        refExpr.result.reference match {
          case CoordT(OwnT, StructTT(simpleName("Muta"))) =>
        }
        targetOwnership shouldEqual BorrowT
        letTE.result.reference.ownership shouldEqual BorrowT
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Owning ref method call") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |func take(m Muta) {
        |  ret m.hp;
        |}
        |exported func main() int {
        |  m = Muta(9);
        |  ret (m).hp;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Derive drop") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |struct Muta { }
        |
        |exported func main() {
        |  Muta();
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    Collector.only(main, { case FunctionCallTE(functionName("drop"), _) => })
    Collector.all(main, { case FunctionCallTE(_, _) => }).size shouldEqual 2

    compile.evalForKind(Vector())
  }

  test("Custom drop result is an owning ref, calls destructor") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |#!DeriveStructDrop
        |struct Muta { }
        |
        |func drop(m ^Muta) void {
        |  println("Destroying!");
        |  Muta[ ] = m;
        |}
        |
        |exported func main() {
        |  Muta();
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    Collector.only(main, { case FunctionCallTE(functionName("drop"), _) => })
    Collector.all(main, { case FunctionCallTE(_, _) => }).size shouldEqual 2

    compile.evalForStdout(Vector()) shouldEqual "Destroying!\n"
  }

  test("Saves return value then destroys temporary") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |#!DeriveStructDrop
        |struct Muta { hp int; }
        |
        |func drop(m ^Muta) {
        |  println("Destroying!");
        |  Muta[hp] = m;
        |}
        |
        |exported func main() int {
        |  ret (Muta(10)).hp;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    Collector.only(main, { case FunctionCallTE(functionName("drop"), _) => })

    compile.evalForKindAndStdout(Vector()) match { case (VonInt(10), "Destroying!\n") => }
  }

  test("Calls destructor on local var") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |#!DeriveStructDrop
        |struct Muta { }
        |
        |func drop(m ^Muta) {
        |  println("Destroying!");
        |  Muta[ ] = m;
        |}
        |
        |exported func main() {
        |  a = Muta();
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    Collector.only(main, { case FunctionCallTE(functionName("drop"), _) => })
    Collector.all(main, { case FunctionCallTE(_, _) => }).size shouldEqual 2

    compile.evalForStdout(Vector()) shouldEqual "Destroying!\n"
  }

  test("Calls destructor on local var unless moved") {
    // Should call the destructor in moo, but not in main
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |#!DeriveStructDrop
        |struct Muta { }
        |
        |func drop(m ^Muta) {
        |  println("Destroying!");
        |  Muta[ ] = m;
        |}
        |
        |func moo(m ^Muta) {
        |}
        |
        |exported func main() {
        |  a = Muta();
        |  moo(a);
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()

    // Destructor should only be calling println, NOT the destructor (itself)
    val destructor = temputs.lookupUserFunction("drop")
    // The only function lookup should be println
    Collector.only(destructor, { case FunctionCallTE(functionName("println"), _) => })
    // Only one call (the above println)
    Collector.all(destructor, { case FunctionCallTE(_, _) => }).size shouldEqual 1

    // moo should be calling the destructor
    val moo = temputs.lookupFunction("moo")
    Collector.only(moo, { case FunctionCallTE(functionName("drop"), _) => })
    Collector.only(moo, { case FunctionCallTE(_, _) => })

    // main should not be calling the destructor
    val main = temputs.lookupFunction("main")
    Collector.all(main, { case FunctionCallTE(functionName("drop"), _) => true }).size shouldEqual 0

    compile.evalForStdout(Vector()) shouldEqual "Destroying!\n"
  }

  test("Saves return value then destroys local var") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |#!DeriveStructDrop
        |struct Muta { hp int; }
        |
        |func drop(m ^Muta) {
        |  println("Destroying!");
        |  Muta[hp] = m;
        |}
        |
        |exported func main() int {
        |  a = Muta(10);
        |  ret a.hp;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    Collector.only(main, { case FunctionCallTE(functionName("drop"), _) => })
    Collector.all(main, { case FunctionCallTE(_, _) => }).size shouldEqual 2

    compile.evalForKindAndStdout(Vector()) match { case (VonInt(10), "Destroying!\n") => }
  }

  test("Gets from temporary struct a member's member") {
    val compile = RunCompilation.test(
      """
        |struct Wand {
        |  charges int;
        |}
        |struct Wizard {
        |  wand ^Wand;
        |}
        |exported func main() int {
        |  ret Wizard(Wand(10)).wand.charges;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }

  // test that when we create a block closure, we hoist to the beginning its constructor,
  // and hoist to the end its destructor.

  // test that when we borrow an owning, we hoist its destructor to the end.

  test("Unstackifies local vars") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  i = 0;
        |  ret i;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")

    val numVariables =
      Collector.all(main, {
        case LetAndLendTE(_, _, _) =>
        case LetNormalTE(_, _) =>
      }).size
    Collector.all(main, { case UnletTE(_) => }).size shouldEqual numVariables
  }

  test("Basic builder pattern") {
    val compile = RunCompilation.test(
      """
        |struct Ship { hp! int; fuel! int; }
        |func setHp(ship Ship, hp int) Ship {
        |  set ship.hp = hp;
        |  ret ship;
        |}
        |func setFuel(ship Ship, fuel int) Ship {
        |  set ship.fuel = fuel;
        |  ret ship;
        |}
        |exported func main() int {
        |  ship = Ship(0, 0).setHp(42).setFuel(43);
        |  ret ship.hp;
        |}
        |""".stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Member access on returned owning ref") {
    val compile = RunCompilation.test(
      """
        |struct Ship { hp int; }
        |exported func main() int {
        |  ret Ship(42).hp;
        |}
        |""".stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

}
