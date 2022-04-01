package dev.vale.templar

import dev.vale.options.GlobalOptions
import dev.vale.parser.ast.FileP
import dev.vale.{Builtins, FileCoordinateMap, PackageCoordinate, Tests}
import dev.vale._
import dev.vale.astronomer._

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

object TemplarTestCompilation {
  def test(code: String*): TemplarCompilation = {
    new TemplarCompilation(
      Vector(PackageCoordinate.TEST_TLD),
      Builtins.getModulizedCodeMap()
        .or(FileCoordinateMap.test(code.toVector))
        .or(Tests.getPackageToResourceResolver),
      TemplarCompilationOptions(GlobalOptions(true, true, true, true)))
  }
}
