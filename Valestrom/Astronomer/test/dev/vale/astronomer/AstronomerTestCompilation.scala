package dev.vale.astronomer

import dev.vale.{FileCoordinateMap, PackageCoordinate, Tests}
import dev.vale.options.GlobalOptions
import dev.vale._

object AstronomerTestCompilation {
  def test(code: String*): AstronomerCompilation = {
    new AstronomerCompilation(
      GlobalOptions.test(),
      Vector(PackageCoordinate.TEST_TLD),
      FileCoordinateMap.test(code.toVector)
        .or(Tests.getPackageToResourceResolver))
  }
}
