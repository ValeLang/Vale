package net.verdagon.vale

import net.verdagon.vale.templar.ast.TupleTE
//import net.verdagon.vale.templar.types.{IntT, PackTT}
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class PackTests extends FunSuite with Matchers {
  test("Extract seq") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  [x, y, z] = (5, 6, 7);
        |  ret x;
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.all(main, { case TupleTE(Vector(_, _, _), _) => }).size shouldEqual 1

    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Nested seqs") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  [x, [y, z]] = ((4, 5), (6, 7));
        |  ret y;
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.all(main, {
      case TupleTE(
        Vector(
          TupleTE(Vector(_, _), _),
          TupleTE(Vector(_, _), _)),
        _) =>
    }).size shouldEqual 1

    compile.evalForKind(Vector()) shouldEqual VonInt(6)
  }

  test("Nested tuples") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  [x, [y, z]] = (5, (6, false));
        |  ret x;
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.all(main, { case TupleTE(Vector(_, TupleTE(Vector(_, _), _)), _) => }).size shouldEqual 1

    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

}
