package dev.vale.highertyping

import dev.vale.{FileCoordinateMap, PackageCoordinate, Tests}
import dev.vale.options.GlobalOptions
import dev.vale._

object HigherTypingTestCompilation {
  def test(code: String*): HigherTypingCompilation = {
    new HigherTypingCompilation(
      GlobalOptions.test(),
      Vector(PackageCoordinate.TEST_TLD),
      FileCoordinateMap.test(code.toVector)
        .or(Tests.getPackageToResourceResolver))
  }
}
