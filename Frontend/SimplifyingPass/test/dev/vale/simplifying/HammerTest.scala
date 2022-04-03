package dev.vale.simplifying

import dev.vale.finalast.StackifyH
import dev.vale.{Collector, PackageCoordinate, vassert}
import dev.vale.parsing.ast.FileP
import dev.vale.highertyping.ICompileErrorA
import dev.vale.Result
import dev.vale.typing._
import org.scalatest.{FunSuite, Matchers}
import dev.vale.finalast.VariableIdH
import dev.vale.postparsing.ICompileErrorS

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

