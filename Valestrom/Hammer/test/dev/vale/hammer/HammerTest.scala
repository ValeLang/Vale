package dev.vale.hammer

import dev.vale.metal.StackifyH
import dev.vale.{Collector, PackageCoordinate, vassert}
import dev.vale.parser.ast.FileP
import dev.vale.astronomer.ICompileErrorA
import dev.vale.Result
import dev.vale.templar._
import org.scalatest.{FunSuite, Matchers}
import dev.vale.metal.VariableIdH
import dev.vale.scout.ICompileErrorS

import scala.collection.immutable.List



class HammerTest extends FunSuite with Matchers with Collector {
  test("Local IDs unique") {
    val compile = HammerTestCompilation.test(
        """
          |exported func main() {
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

