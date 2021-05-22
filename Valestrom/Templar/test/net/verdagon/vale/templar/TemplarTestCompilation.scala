package net.verdagon.vale.templar

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.parser.FileP
import net.verdagon.vale.scout.{CodeLocationS, ICompileErrorS, ITemplexS, ProgramS, RangeS}

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

object TemplarTestCompilation {
  def test(code: String*): TemplarCompilation = {
    new TemplarCompilation(
      List(FileCoordinateMap.TEST_MODULE),
      Builtins.getModulizedCodeMap()
        .or(FileCoordinateMap.test(code.toList))
        .or(Tests.getNamespaceToResourceResolver),
      TemplarCompilationOptions())
  }
}
