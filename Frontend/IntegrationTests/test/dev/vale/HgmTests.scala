package dev.vale

import dev.vale.instantiating.ast._
import dev.vale.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class HgmTests extends FunSuite with Matchers {
  test("Tests pre borrow") {
    val compile = RunCompilation.test(
      """
        |import v.builtins.arith.*;
        |#!DeriveStructDrop
        |struct Ship { hp int; }
        |func bork(x pre&Ship) int {
        |  x.hp
        |}
        |exported func main() int {
        |  ship = Ship(42);
        |  x = bork(&ship);
        |  [z] = ship;
        |  return x;
        |}
        """.stripMargin)
    val borkT = compile.expectCompilerOutputs().lookupFunction("bork")
    borkT.header.params.head.preChecked shouldEqual true

    val borkC = compile.getMonouts().lookupFunction("bork")
    borkC.header.params.head.preChecked shouldEqual true

    val mainC = compile.getMonouts().lookupFunction("main")
    Collector.only(mainC.body, {
      case PreCheckBorrowIE(_) =>
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }
}
