package dev.vale.highertyping

import dev.vale.{FileCoordinateMap, PackageCoordinate, Tests}
import dev.vale.options.GlobalOptions
import dev.vale._

object HigherTypingTestCompilation {
  def test(code: String*): HigherTypingCompilation = {
    val interner = new Interner()
    new HigherTypingCompilation(
      GlobalOptions.test(),
      interner,
      Vector(PackageCoordinate.TEST_TLD(interner)),
      FileCoordinateMap.test(interner, code.toVector)
        .or(Tests.getPackageToResourceResolver))
  }
}
