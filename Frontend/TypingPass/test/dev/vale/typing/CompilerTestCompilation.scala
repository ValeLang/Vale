package dev.vale.typing

import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.{Builtins, FileCoordinateMap, Keywords, PackageCoordinate, Tests, _}
import dev.vale.highertyping._

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

object CompilerTestCompilation {
  def test(code: String, interner: Interner = new Interner()): TypingPassCompilation = {
    val keywords = new Keywords(interner)
    new TypingPassCompilation(
      interner,
      keywords,
      Vector(PackageCoordinate.TEST_TLD(interner, keywords)),
      Builtins.getModulizedCodeMap(interner, keywords)
        .or(FileCoordinateMap.test(interner, code))
        .or(Tests.getPackageToResourceResolver),
      TypingPassOptions(
        GlobalOptions(true, true, true, true),
        x => println(x),
        false))
  }
}
