package dev.vale.monomorphizing

import dev.vale.options.GlobalOptions
import dev.vale.{Builtins, FileCoordinateMap, Interner, Keywords, PackageCoordinate, Tests}
import org.scalatest.{FunSuite, Matchers}

object MonomorphizingCompilation {
  def test(code: String*): MonomorphizedCompilation = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    new MonomorphizedCompilation(
      interner,
      keywords,
      Vector(
        PackageCoordinate.TEST_TLD(interner, keywords)),
      Builtins.getCodeMap(interner, keywords)
        .or(FileCoordinateMap.test(interner, code.toVector))
        .or(Tests.getPackageToResourceResolver),
      MonomorphizedCompilationOptions(
        GlobalOptions(true, true, true, true)))
  }
}

class MonomorphizingTests extends FunSuite with Matchers {

  test("Test templates") {
    val compile = MonomorphizingCompilation.test(
      """
        |func drop(x int) { }
        |func bork<T>(a T) void where func drop(T)void {
        |  // implicitly calls drop
        |}
        |exported func main() {
        |  bork(3);
        |}
      """.stripMargin)
    compile.getMonouts()
  }

}
