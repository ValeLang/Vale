package net.verdagon.vale.hammer

import net.verdagon.vale.astronomer.{ICompileErrorA, ProgramA}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.{FileCoordinateMap, PackageCoordinate, PackageCoordinateMap, Result, Tests, vassert}
import net.verdagon.vale.templar._
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.metal.{FunctionH, ProgramH, StackifyH, VariableIdH}
import net.verdagon.vale.parser.{FileP}
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS}

import scala.collection.immutable.List



class HammerTest extends FunSuite with Matchers {
  def recursiveCollect[T, R](a: Any, partialFunction: PartialFunction[Any, R]): List[R] = {
    if (partialFunction.isDefinedAt(a)) {
      return List(partialFunction.apply(a))
    }
    a match {
      case p : Product => p.productIterator.flatMap(x => recursiveCollect(x, partialFunction)).toList
      case _ => List.empty
    }
  }

  test("Local IDs unique") {
    val compile = HammerTestCompilation.test(
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
    val main = hamuts.lookupPackage(PackageCoordinate.TEST_TLD).lookupFunction("main")
    vassert(main.`export`)
    val stackifies = recursiveCollect(main, { case s @ StackifyH(_, _, _) => s })
    val localIds = stackifies.map(_.local.id.number).sorted
    localIds shouldEqual localIds.distinct
    vassert(localIds.size >= 6)
  }
}

