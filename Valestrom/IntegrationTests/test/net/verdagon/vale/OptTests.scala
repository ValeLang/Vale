package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class OptTests extends FunSuite with Matchers {
  test("Test empty and get for Some") {
    val compile = Compilation(
      Samples.get("libraries/utils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("libraries/castutils.vale") +
      Samples.get("libraries/opt.vale") +
        """
          |fn main() {
          |  opt Opt<int> = Some(9);
          |  = if (opt.isEmpty()) { 0 }
          |    else { opt.get() }
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Test empty and get for None") {
    val profiler = new Profiler()
    val compile = Compilation.multiple(
      List(
      Samples.get("libraries/utils.vale"),
        Samples.get("libraries/printutils.vale"),
        Samples.get("libraries/castutils.vale"),
      Samples.get("libraries/opt.vale"),
        """
          |fn main() {
          |  opt Opt<int> = None<int>();
          |  = if (opt.isEmpty()) { 0 }
          |    else { opt.get() }
          |}
        """.stripMargin),
      false,
      profiler)

    compile.evalForReferend(Vector()) shouldEqual VonInt(0)

    println(profiler.assembleResults())
  }

}
