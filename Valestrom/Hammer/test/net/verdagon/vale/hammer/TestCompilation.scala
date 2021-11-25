package net.verdagon.vale.hammer

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.{Builtins, FileCoordinateMap, PackageCoordinate, Tests}

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
