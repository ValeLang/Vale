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
          |exported func main() int {
          |  opt Opt<int> = Some(9);
          |  ret if (opt.isEmpty()) { 0 }
          |    else { opt.get() }
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Test empty and get for None") {
    val compile = RunCompilation.test(
        """
          |exported func main() int {
          |  opt Opt<int> = None<int>();
          |  ret if (opt.isEmpty()) { 0 }
          |    else { opt.get() }
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(0) => }
  }

  test("Test empty and get for borrow") {
    val compile = RunCompilation.test(
        """
          |// This is the same as the one in optutils.vale, just named differently,
          |// so its easier to debug.
          |func borrowGet<T>(opt &Some<T>) &T { &opt.value }
          |
          |struct Spaceship { fuel int; }
          |exported func main() int {
          |  s = Spaceship(42);
          |  bork = Some<&Spaceship>(&s);
          |  ret bork.borrowGet().fuel;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }
}
