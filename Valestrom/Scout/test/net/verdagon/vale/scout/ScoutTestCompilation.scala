package net.verdagon.vale.scout

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.{FileCoordinateMap, PackageCoordinate}

object ScoutTestCompilation {
  def test(code: String*): ScoutCompilation = {
    new ScoutCompilation(
      GlobalOptions(true, true, false, false),
      Vector(PackageCoordinate.TEST_TLD),
      FileCoordinateMap.test(code.toVector))
  }
}
