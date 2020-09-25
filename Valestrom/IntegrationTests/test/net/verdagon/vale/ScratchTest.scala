package net.verdagon.vale

import java.io.FileNotFoundException

import net.verdagon.vale.templar._
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.vivem.{Heap, IntV, StructInstanceV}
import net.verdagon.von.{VonBool, VonFloat, VonInt}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation
import net.verdagon.vale.hammer.VonHammer
import net.verdagon.vale.metal.YonderH
import net.verdagon.vale.templar.templata.Signature2
import net.verdagon.vale.templar.types.{Coord, Int2, Share, Str2}

import scala.io.Source
import scala.util.Try

class ScratchTest extends FunSuite with Matchers {
  test("Test scratch") {
    val compile =
      Compilation.multiple(
        List(
          Samples.get("programs/scratch.vale"),
          Samples.get("libraries/list.vale"),
          Samples.get("libraries/opt.vale")))
    compile.evalForReferend(Vector())
  }
}
