package dev.vale.simplifying

import dev.vale.options.GlobalOptions
import dev.vale.{Builtins, FileCoordinateMap, Interner, PackageCoordinate, Tests}

import scala.collection.immutable.List

object HammerTestCompilation {
  def test(code: String*): HammerCompilation = {
    val interner = new Interner()
    new HammerCompilation(
      interner,
      Vector(PackageCoordinate.BUILTIN(interner), PackageCoordinate.TEST_TLD(interner)),
      Builtins.getCodeMap(interner)
        .or(FileCoordinateMap.test(interner, code.toVector))
        .or(Tests.getPackageToResourceResolver),
      HammerCompilationOptions())
  }
}
