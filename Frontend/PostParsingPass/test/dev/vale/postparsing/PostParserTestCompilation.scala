package dev.vale.postparsing

import dev.vale.{FileCoordinateMap, Interner, Keywords, PackageCoordinate}
import dev.vale.options.GlobalOptions

object PostParserTestCompilation {
  def test(code: String, interner: Interner = new Interner()): ScoutCompilation = {
    val keywords = new Keywords(interner)
    new ScoutCompilation(
      GlobalOptions(true, true, true, false, false),
      interner,
      keywords,
      Vector(PackageCoordinate.TEST_TLD(interner, keywords)),
      FileCoordinateMap.test(interner, Vector(code)))
  }
}
