package dev.vale

import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing._
import dev.vale.typing.types._
import dev.vale.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class OptTests extends FunSuite with Matchers {
  test("Test empty and get for Some") {
    val compile = RunCompilation.test(
        """
          |exported func main() int {
          |  opt Opt<int> = Some(9);
          |  return if (opt.isEmpty()) { 0 }
          |    else { opt.get() };
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Test empty and get for None") {
    val compile = RunCompilation.test(
        """
          |exported func main() int {
          |  opt Opt<int> = None<int>();
          |  return if (opt.isEmpty()) { 0 }
          |    else { opt.get() };
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
          |  return bork.borrowGet().fuel;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }
}
