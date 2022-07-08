package dev.vale.highertyping

import dev.vale.{FileCoordinateMap, Keywords, PackageCoordinate, Tests, _}
import dev.vale.options.GlobalOptions

object HigherTypingTestCompilation {
  def test(code: String*): HigherTypingCompilation = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    new HigherTypingCompilation(
      GlobalOptions.test(),
      interner,
      keywords,
      Vector(PackageCoordinate.TEST_TLD(interner, keywords)),
      FileCoordinateMap.test(interner, code.toVector)
        .or(Tests.getPackageToResourceResolver))
  }
}
