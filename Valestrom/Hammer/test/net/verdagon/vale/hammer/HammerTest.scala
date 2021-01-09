package net.verdagon.vale.hammer

import net.verdagon.vale.Samples
import net.verdagon.vale.templar._
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.metal.ProgramH

import scala.collection.immutable.List

object HammerCompilation {
  def multiple(code: List[String]): HammerCompilation = {
    new HammerCompilation(code.zipWithIndex.map({ case (code, index) => (index + ".vale", code) }))
  }
  def apply(code: String): HammerCompilation = {
    new HammerCompilation(List(("in.vale", code)))
  }
}

class HammerCompilation(var filenamesAndSources: List[(String, String)]) {
  var templarCompilation = new TemplarCompilation(filenamesAndSources)

  def getHamuts(): ProgramH = {
    val hinputs = templarCompilation.getTemputs()
    val hamuts = Hammer.translate(hinputs)
    hamuts
  }
}

class HammerTest extends FunSuite with Matchers {
}

