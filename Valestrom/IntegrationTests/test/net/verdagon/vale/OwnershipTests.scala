package net.verdagon.vale

import net.verdagon.vale.parser.{CaptureP, FinalP, VaryingP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.rules.{CoordTypeSR, TypedSR}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariableT
import net.verdagon.vale.templar.templata.{FunctionHeaderT, PrototypeT}
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.templar.expression.CallTemplar

class OwnershipTests extends FunSuite with Matchers {
  test("Borrowing a temporary mutable makes a local var") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |fn main() int export {
        |  = (&Muta(9)).hp;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    main.only({
      case LetAndLendTE(ReferenceLocalVariableT(FullNameT(_, List(FunctionNameT("main",List(),List())),TemplarTemporaryVarNameT(0)),FinalT,_),refExpr) => {
        refExpr.resultRegister.reference match {
          case CoordT(OwnT, ReadwriteT, StructRefT(simpleName("Muta"))) =>
        }
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("Owning ref method call") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |fn take(m Muta) {
        |  ret m.hp;
        |}
        |fn main() int export {
        |  m = Muta(9);
        |  = (m).hp;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("When statement result is an owning ref, calls destructor") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |struct Muta { }
        |
        |fn destructor(m ^Muta) void {
        |  println("Destroying!");
        |  Muta() = m;
        |}
        |
        |fn main() export {
        |  Muta();
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    main.only({ case FunctionCallTE(functionName(CallTemplar.MUT_DESTRUCTOR_NAME), _) => })
    main.all({ case FunctionCallTE(_, _) => }).size shouldEqual 2

    compile.evalForStdout(Vector()) shouldEqual "Destroying!\n"
  }

  test("Saves return value then destroys temporary") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |struct Muta { hp int; }
        |
        |fn destructor(m ^Muta) {
        |  println("Destroying!");
        |  Muta(hp) = m;
        |}
        |
        |fn main() int export {
        |  = (Muta(10)).hp;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    main.only({ case FunctionCallTE(functionName(CallTemplar.MUT_DESTRUCTOR_NAME), _) => })

    compile.evalForKindAndStdout(Vector()) shouldEqual (VonInt(10), "Destroying!\n")
  }

  test("Calls destructor on local var") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |struct Muta { }
        |
        |fn destructor(m ^Muta) {
        |  println("Destroying!");
        |  Muta() = m;
        |}
        |
        |fn main() export {
        |  a = Muta();
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    main.only({ case FunctionCallTE(functionName(CallTemplar.MUT_DESTRUCTOR_NAME), _) => })
    main.all({ case FunctionCallTE(_, _) => }).size shouldEqual 2

    compile.evalForStdout(Vector()) shouldEqual "Destroying!\n"
  }

  test("Calls destructor on local var unless moved") {
    // Should call the destructor in moo, but not in main
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |struct Muta { }
        |
        |fn destructor(m ^Muta) {
        |  println("Destroying!");
        |  Muta() = m;
        |}
        |
        |fn moo(m ^Muta) {
        |}
        |
        |fn main() export {
        |  a = Muta();
        |  moo(a);
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()

    // Destructor should only be calling println, NOT the destructor (itself)
    val destructor = temputs.lookupUserFunction(CallTemplar.MUT_DESTRUCTOR_NAME)
    // The only function lookup should be println
    destructor.only({ case FunctionCallTE(functionName("println"), _) => })
    // Only one call (the above println)
    destructor.all({ case FunctionCallTE(_, _) => }).size shouldEqual 1

    // moo should be calling the destructor
    val moo = temputs.lookupFunction("moo")
    moo.only({ case FunctionCallTE(functionName(CallTemplar.MUT_DESTRUCTOR_NAME), _) => })
    moo.only({ case FunctionCallTE(_, _) => })

    // main should not be calling the destructor
    val main = temputs.lookupFunction("main")
    main.all({ case FunctionCallTE(functionName(CallTemplar.MUT_DESTRUCTOR_NAME), _) => true }).size shouldEqual 0

    compile.evalForStdout(Vector()) shouldEqual "Destroying!\n"
  }

  test("Saves return value then destroys local var") {
    val compile = RunCompilation.test(
      """
        |import printutils.*;
        |
        |struct Muta { hp int; }
        |
        |fn destructor(m ^Muta) {
        |  println("Destroying!");
        |  Muta(hp) = m;
        |}
        |
        |fn main() int export {
        |  a = Muta(10);
        |  = a.hp;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    main.only({ case FunctionCallTE(functionName(CallTemplar.MUT_DESTRUCTOR_NAME), _) => })
    main.all({ case FunctionCallTE(_, _) => }).size shouldEqual 2

    compile.evalForKindAndStdout(Vector()) shouldEqual (VonInt(10), "Destroying!\n")
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
        |fn main() int export {
        |  = Wizard(Wand(10)).wand.charges;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(10)
  }

  // test that when we create a block closure, we hoist to the beginning its constructor,
  // and hoist to the end its destructor.

  // test that when we borrow an owning, we hoist its destructor to the end.

  test("Checks that we stored a borrowed temporary in a local") {
    // Checking for 2 because one for the temporary, and one inside the
    // templated wrapper function.
    val compile = RunCompilation.test(
      """
        |struct Muta { }
        |fn main() export {
        |  __checkvarrc(&Muta(), 1)
        |}
      """.stripMargin)

    // Should be a temporary for this object
    compile.expectTemputs().lookupFunction("main")
            .allOf(classOf[LetAndLendTE]).size shouldEqual 1

    compile.run(Vector())
  }

  test("Var RC for one local") {
    val compile = RunCompilation.test(
      """
        |struct Muta { }
        |fn main() export {
        |  a = Muta();
        |  __checkvarrc(&a, 1);
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")
    // Only one variable containing a Muta
    main.only({
      case LetNormalTE(ReferenceLocalVariableT(_,FinalT,CoordT(OwnT,ReadwriteT,StructRefT(FullNameT(_, List(),CitizenNameT("Muta",List()))))),_) =>
    })

    compile.run(Vector())
  }

  test("Unstackifies local vars") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  i! = 0;
        |  = i;
        |}
      """.stripMargin)

    val main = compile.expectTemputs().lookupFunction("main")

    val numVariables = main.all({ case ReferenceLocalVariableT(_, _, _) => }).size
    main.all({ case LetAndLendTE(_, _) => case LetNormalTE(_, _) => }).size shouldEqual numVariables
    main.all({ case UnletTE(_) => }).size shouldEqual numVariables
  }





//
//  test("Moving doesn't affect ref count") {
//    val compile = RunCompilation.test(
//      """
//        |struct Muta { }
//        |fn main() int export {
//        |  a = Muta();
//        |  b = a;
//        |  = __varrc(&b);
//        |}
//      """.stripMargin)
//
//    compile.evalForKind(Vector()) shouldEqual VonInt(1)
//  }
//
//  test("Wingman catches hanging borrow") {
//    val compile = RunCompilation.test(
//      """
//        |struct MutaA { }
//        |struct MutaB {
//        |  a &MutaA;
//        |}
//        |fn main() int export {
//        |  a = MutaA();
//        |  b = MutaB(&a);
//        |  c = a;
//        |}
//      """.stripMargin)
//
//    try {
//      compile.evalForKind(Vector())
//      fail();
//    } catch {
//      case VivemPanic("Dangling borrow") =>
//    }
//  }
}
