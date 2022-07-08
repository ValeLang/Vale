package dev.vale.simplifying

import dev.vale.options.GlobalOptions
import dev.vale.{Builtins, FileCoordinateMap, Interner, Keywords, PackageCoordinate, Tests}

import scala.collection.immutable.List

object HammerTestCompilation {
  def test(code: String*): HammerCompilation = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    new HammerCompilation(
      interner,
      keywords,
      Vector(PackageCoordinate.BUILTIN(interner, keywords), PackageCoordinate.TEST_TLD(interner, keywords)),
      Builtins.getCodeMap(interner, keywords)
        .or(FileCoordinateMap.test(interner, code.toVector))
        .or(Tests.getPackageToResourceResolver),
      HammerCompilationOptions())
  }
}
