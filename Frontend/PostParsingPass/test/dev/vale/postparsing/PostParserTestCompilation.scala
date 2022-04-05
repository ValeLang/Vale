package dev.vale.postparsing

import dev.vale.{FileCoordinateMap, Interner, PackageCoordinate}
import dev.vale.options.GlobalOptions

object PostParserTestCompilation {
  def test(code: String*): ScoutCompilation = {
    val interner = new Interner()
    new ScoutCompilation(
      GlobalOptions(true, true, false, false),
      interner,
      Vector(PackageCoordinate.TEST_TLD(interner)),
      FileCoordinateMap.test(interner, code.toVector))
  }
}
