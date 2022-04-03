package dev.vale.simplifying

import dev.vale.options.GlobalOptions
import dev.vale.{Builtins, FileCoordinateMap, PackageCoordinate, Tests}
import dev.vale.Tests

import scala.collection.immutable.List

object HammerTestCompilation {
  def test(code: String*): HammerCompilation = {
    new HammerCompilation(
      Vector(PackageCoordinate.BUILTIN, PackageCoordinate.TEST_TLD),
      Builtins.getCodeMap()
        .or(FileCoordinateMap.test(code.toVector))
        .or(Tests.getPackageToResourceResolver),
      HammerCompilationOptions())
  }
}
