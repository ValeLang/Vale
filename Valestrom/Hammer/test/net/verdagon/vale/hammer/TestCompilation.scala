package net.verdagon.vale.hammer

import net.verdagon.vale.{Builtins, FileCoordinateMap, Tests}

import scala.collection.immutable.List

object HammerTestCompilation {
  def test(code: String*): HammerCompilation = {
    new HammerCompilation(
      List("", FileCoordinateMap.TEST_MODULE),
      Builtins.getCodeMap()
        .or(FileCoordinateMap.test(code.toList))
        .or(Tests.getNamespaceToResourceResolver),
      HammerCompilationOptions())
  }
}
