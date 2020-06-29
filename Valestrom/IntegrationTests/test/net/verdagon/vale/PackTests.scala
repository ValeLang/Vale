package net.verdagon.vale

import net.verdagon.vale.templar.{ArraySequenceE2, PackE2, TupleE2}
import net.verdagon.vale.templar.types.{Int2, PackT2}
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class PackTests extends FunSuite with Matchers {
  test("Extract seq") {
    val compile = new Compilation(
      """
        |fn main() {
        |  (x, y, z) = [5, 6, 7];
        |  = x;
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main.all({ case ArraySequenceE2(List(_, _, _), _, _) => }).size shouldEqual 1

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Nested seqs") {
    val compile = new Compilation(
      """
        |fn main() {
        |  (x, (y, z)) = [[4, 5], [6, 7]];
        |  = y;
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main.all({
      case ArraySequenceE2(
        List(
          ArraySequenceE2(List(_, _), _, _),
          ArraySequenceE2(List(_, _), _, _)),
        _,
        _) =>
    }).size shouldEqual 1

    compile.evalForReferend(Vector()) shouldEqual VonInt(6)
  }

  test("Nested tuples") {
    val compile = new Compilation(
      """
        |fn main() {
        |  (x, (y, z)) = [5, [6, false]];
        |  = x;
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main .all({ case TupleE2(List(_, TupleE2(List(_, _), _, _)), _, _) => }).size shouldEqual 1

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

}
