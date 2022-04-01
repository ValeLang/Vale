package dev.vale

import dev.vale.hammer.VonHammer
import dev.vale.metal.YonderH
import dev.vale.templar._
import dev.vale.templar.types.StrT
import dev.vale.vivem.StructInstanceV
import dev.vale.von.VonInt
import dev.vale.{metal => m}
import org.scalatest.{FunSuite, Matchers}

class PureFunctionTests extends FunSuite with Matchers {
  test("Simple pure function") {
    val compile =
      RunCompilation.test(
        """
          |struct Engine {
          |  fuel int;
          |}
          |struct Spaceship {
          |  engine Engine;
          |}
          |pure func pfunc(s &Spaceship) int {
          |  ret s.engine.fuel;
          |}
          |exported func main() int {
          |  s = Spaceship(Engine(10));
          |  ret pfunc(&s);
          |}
          |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }
}
