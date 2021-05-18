package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.vale.hammer.VonHammer
import net.verdagon.vale.metal.{IntH, YonderH}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.Signature2
import net.verdagon.vale.templar.types.{Coord, Int2, Share, Str2}
import net.verdagon.vale.vivem.{ConstraintViolatedException, Heap, IntV, StructInstanceV}
import net.verdagon.vale.{metal => m}
import net.verdagon.von.{VonBool, VonFloat, VonInt}
import org.scalatest.{FunSuite, Matchers}

class PureFunctionTests extends FunSuite with Matchers {
  test("Simple pure function") {
    val compile =
      Compilation.test(List("builtinexterns"),
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
    compile.evalForReferend(Vector()) shouldEqual VonInt(10)
  }
}
