package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariableT
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class OptTests extends FunSuite with Matchers {
  test("Test empty and get for Some") {
    val compile = RunCompilation.test(
        """
          |fn main() int export {
          |  opt Opt<int> = Some(9);
          |  = if (opt.isEmpty()) { 0 }
          |    else { opt.get() }
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("Test empty and get for None") {
    val compile = RunCompilation.test(
        """
          |fn main() int export {
          |  opt Opt<int> = None<int>();
          |  = if (opt.isEmpty()) { 0 }
          |    else { opt.get() }
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(0)
  }

  test("Test empty and get for borrow") {
    val profiler = new Profiler()
    val compile = RunCompilation.test(
        """
          |// This is the same as the one in optutils.vale, just named differently,
          |// so its easier to debug.
          |fn borrowGet<T>(opt &Some<T>) &T { opt.value }
          |
          |struct Spaceship { fuel int; }
          |fn main() int export {
          |  s = Spaceship(42);
          |  ret Some<&Spaceship>(&s).borrowGet().fuel;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }
}
