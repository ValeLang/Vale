package net.verdagon.vale.hammer

import net.verdagon.vale.astronomer.{ICompileErrorA, ProgramA}
import net.verdagon.vale.Collector
import net.verdagon.vale.{FileCoordinateMap, PackageCoordinate, PackageCoordinateMap, Result, Tests, vassert}
import net.verdagon.vale.templar.{Hinputs, _}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.metal.{FunctionH, ProgramH, StackifyH, VariableIdH}
import net.verdagon.vale.parser.FileP
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS}

import scala.collection.immutable.List



class HammerTest extends FunSuite with Matchers with Collector {
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
    val paackage = hamuts.lookupPackage(PackageCoordinate.TEST_TLD)
    val main = paackage.lookupFunction("main")

    vassert(paackage.exportNameToFunction.exists(_._2 == main.prototype))

    val stackifies = recursiveCollect(main, { case s @ StackifyH(_, _, _) => s })
    val localIds = stackifies.map(_.local.id.number).toVector.sorted
    localIds shouldEqual localIds.distinct.toVector
    vassert(localIds.size >= 6)
  }
}

