package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.vale.hammer.VonHammer
import net.verdagon.vale.metal.YonderH
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.Signature2
import net.verdagon.vale.templar.types.{Coord, Int2, Share, Str2}
import net.verdagon.vale.vivem.{Heap, IntV, StructInstanceV}
import net.verdagon.vale.{metal => m}
import net.verdagon.von.{VonBool, VonFloat, VonInt}
import org.scalatest.{FunSuite, Matchers}

class ScratchTest extends FunSuite with Matchers {
  test("Scratch test") {
    val profiler = new Profiler()
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/printutils.vale"),
        Samples.get("libraries/castutils.vale"),
        Samples.get("libraries/utils.vale"),
        Samples.get("libraries/opt.vale"),
        Samples.get("libraries/list.vale"),
        Samples.get("libraries/strings.vale"),
        Samples.get("programs/scratch.vale")),
      false,
      profiler)
    compile.getHamuts()
    println(profiler.assembleResults())
  }
}
