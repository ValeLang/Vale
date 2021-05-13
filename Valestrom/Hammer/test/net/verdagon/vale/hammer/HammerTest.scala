package net.verdagon.vale.hammer

import net.verdagon.vale.{FileCoordinateMap, Samples, vassert}
import net.verdagon.vale.templar._
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.metal.{FunctionH, ProgramH, StackifyH, VariableIdH}

import scala.collection.immutable.List

object HammerCompilation {
  def multiple(code: List[String]): HammerCompilation = {
    new HammerCompilation(FileCoordinateMap.test(code.zipWithIndex.map({ case (code, index) => (index + ".vale", code) }).toMap))
  }
  def apply(code: String): HammerCompilation = {
    new HammerCompilation(FileCoordinateMap.test(code))
  }
}

class HammerCompilation(var codeMap: FileCoordinateMap[String]) {
  var templarCompilation = new TemplarCompilation(codeMap)

  def getHamuts(): ProgramH = {
    val hinputs = templarCompilation.getTemputs()
    val hamuts = Hammer.translate(hinputs)
    hamuts
  }
}



class HammerTest extends FunSuite with Matchers {
  def recursiveCollect[T, R](a: Any, partialFunction: PartialFunction[Any, R]): List[R] = {
    if (partialFunction.isDefinedAt(a)) {
      return List(partialFunction.apply(a))
    }
    a match {
      case p : Product => p.productIterator.flatMap(x => recursiveCollect(x, partialFunction)).toList
      case _ => List()
    }
  }

  test("Local IDs unique") {
    val compile = HammerCompilation(
        """
          |fn main() export {
          |  a = 6;
          |  if (true) {
          |    b = 7;
          |    c = 8;
          |  } else {
          |    while (false) {
          |      d = 9;
          |    }
          |    e = 10;
          |  }
          |  f = 11;
          |}
          |""".stripMargin)
    val hamuts = compile.getHamuts()
    val main = hamuts.functions.find(_.`export`).get
    val stackifies = recursiveCollect(main, { case s @ StackifyH(_, _, _) => s })
    val localIds = stackifies.map(_.local.id.number).sorted
    localIds shouldEqual localIds.distinct
    vassert(localIds.size >= 6)
  }
}

