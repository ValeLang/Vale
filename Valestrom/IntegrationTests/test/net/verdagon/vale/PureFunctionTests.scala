package net.verdagon.vale

import net.verdagon.vale.hammer.VonHammer
import net.verdagon.vale.metal.{IntH, YonderH}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.SignatureT
import net.verdagon.vale.templar.types.{CoordT, IntT, ShareT, StrT}
import net.verdagon.vale.vivem.{ConstraintViolatedException, Heap, IntV, StructInstanceV}
import net.verdagon.vale.{metal => m}
import net.verdagon.von.{VonBool, VonFloat, VonInt}
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
          |fn pfunc(s &Spaceship) pure int {
          |  s.engine.fuel
          |}
          |fn main() int export {
          |  s = Spaceship(Engine(10));
          |  = pfunc(&s);
          |}
          |""".stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(10)
  }
}
