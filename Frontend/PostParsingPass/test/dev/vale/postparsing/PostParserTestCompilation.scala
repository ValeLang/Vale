package dev.vale.postparsing

import dev.vale.{FileCoordinateMap, PackageCoordinate}
import dev.vale.options.GlobalOptions
import dev.vale.FileCoordinateMap

object PostParserTestCompilation {
  def test(code: String*): ScoutCompilation = {
    new ScoutCompilation(
      GlobalOptions(true, true, false, false),
      Vector(PackageCoordinate.TEST_TLD),
      FileCoordinateMap.test(code.toVector))
  }
}
