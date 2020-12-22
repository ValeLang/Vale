package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.{Compilation, CompilationOptions}

class OptTests extends FunSuite with Matchers {
  test("Test empty and get for Some") {
    val compile = Compilation(
      Samples.get("libraries/utils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("libraries/castutils.vale") +
      Samples.get("libraries/opt.vale") +
        """
          |fn main() int export {
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
          |fn main() int export {
          |  opt Opt<int> = None<int>();
          |  = if (opt.isEmpty()) { 0 }
          |    else { opt.get() }
          |}
        """.stripMargin),
      CompilationOptions(profiler = profiler))

    compile.evalForReferend(Vector()) shouldEqual VonInt(0)

    println(profiler.assembleResults())
  }

  test("Test empty and get for borrow") {
    val profiler = new Profiler()
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/utils.vale"),
        Samples.get("libraries/printutils.vale"),
        Samples.get("libraries/castutils.vale"),
        Samples.get("libraries/opt.vale"),
        """
          |// This is the same as the one in opt.vale, just named differently,
          |// so its easier to debug.
          |fn borrowGet<T>(opt &Some<T>) &T { opt.value }
          |
          |struct Spaceship { fuel int; }
          |fn main() int export {
          |  s = Spaceship(42);
          |  ret Some<&Spaceship>(&s).borrowGet().fuel;
          |}
        """.stripMargin),
      CompilationOptions(profiler = profiler))

    compile.evalForReferend(Vector()) shouldEqual VonInt(42)

    println(profiler.assembleResults())
  }
}
