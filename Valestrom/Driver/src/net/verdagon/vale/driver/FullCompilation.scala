package net.verdagon.vale.driver

import net.verdagon.vale.hammer.{Hammer, HammerCompilation, HammerCompilationOptions, VonHammer}
import net.verdagon.vale.astronomer.{Astronomer, ICompileErrorA, ProgramA}
import net.verdagon.vale.driver.Driver.SourceInput
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{CombinatorParsers, FileP, ImportP, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier, TopLevelImportP}
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS, Scout}
import net.verdagon.vale.templar.{ICompileErrorT, Templar, TemplarErrorHumanizer, Temputs}
import net.verdagon.vale.{Builtins, Err, FileCoordinate, FileCoordinateMap, IPackageResolver, IProfiler, PackageCoordinate, PackageCoordinateMap, NullProfiler, Ok, Result, vassert, vassertSome, vfail, vimpl, vwat}
import net.verdagon.vale.vivem.{Heap, PrimitiveReferendV, ReferenceV, Vivem}
import net.verdagon.von.{IVonData, JsonSyntax, VonPrinter}

import scala.collection.immutable.List

case class FullCompilationOptions(
  debugOut: String => Unit = (x => {
    println("##: " + x)
  }),
  verbose: Boolean = true,
  profiler: IProfiler = new NullProfiler(),
  useOptimization: Boolean = false,
)

class FullCompilation(
  packagesToBuild: List[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: FullCompilationOptions = FullCompilationOptions()) {
  var hammerCompilation =
    new HammerCompilation(
      packagesToBuild,
      packageToContentsResolver,
      HammerCompilationOptions(
        options.debugOut,
        options.verbose,
        options.profiler,
        options.useOptimization))

  def getCodeMap(): FileCoordinateMap[String] = hammerCompilation.getCodeMap()
  def getParseds(): FileCoordinateMap[(FileP, List[(Int, Int)])] = hammerCompilation.getParseds()
  def getVpstMap(): FileCoordinateMap[String] = hammerCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = hammerCompilation.getScoutput()
  def expectScoutput(): FileCoordinateMap[ProgramS] = hammerCompilation.expectScoutput()

  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = hammerCompilation.getAstrouts()
  def expectAstrouts(): PackageCoordinateMap[ProgramA] = hammerCompilation.expectAstrouts()

  def getTemputs(): Result[Hinputs, ICompileErrorT] = hammerCompilation.getTemputs()
  def expectTemputs(): Hinputs = hammerCompilation.expectTemputs()

  def getHamuts(): ProgramH = hammerCompilation.getHamuts()
}
