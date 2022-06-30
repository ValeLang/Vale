package dev.vale.postparsing

import dev.vale.{FileCoordinateMap, Interner, Keywords, PackageCoordinate}
import dev.vale.options.GlobalOptions

object PostParserTestCompilation {
  def test(code: String*): ScoutCompilation = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    new ScoutCompilation(
      GlobalOptions(true, true, false, false),
      interner,
      keywords,
      Vector(PackageCoordinate.TEST_TLD(interner, keywords)),
      FileCoordinateMap.test(interner, code.toVector))
  }
}
