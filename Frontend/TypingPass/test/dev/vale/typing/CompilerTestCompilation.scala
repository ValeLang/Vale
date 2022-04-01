package dev.vale.typing

import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.{Builtins, FileCoordinateMap, PackageCoordinate, Tests}
import dev.vale._
import dev.vale.highertyping._

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

object CompilerTestCompilation {
  def test(code: String*): TypingPassCompilation = {
    new TypingPassCompilation(
      Vector(PackageCoordinate.TEST_TLD),
      Builtins.getModulizedCodeMap()
        .or(FileCoordinateMap.test(code.toVector))
        .or(Tests.getPackageToResourceResolver),
      TypingPassCompilationOptions(GlobalOptions(true, true, true, true)))
  }
}
