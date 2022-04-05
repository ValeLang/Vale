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
    val interner = new Interner()
    new TypingPassCompilation(
      interner,
      Vector(PackageCoordinate.TEST_TLD(interner)),
      Builtins.getModulizedCodeMap(interner)
        .or(FileCoordinateMap.test(interner, code.toVector))
        .or(Tests.getPackageToResourceResolver),
      TypingPassCompilationOptions(GlobalOptions(true, true, true, true)))
  }
}
