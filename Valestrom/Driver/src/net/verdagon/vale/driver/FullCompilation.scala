package net.verdagon.vale.driver

import net.verdagon.vale.hammer.{Hammer, HammerCompilation, HammerCompilationOptions, VonHammer}
import net.verdagon.vale.astronomer.{Astronomer, ICompileErrorA, ProgramA}
import net.verdagon.vale.driver.Driver.SourceInput
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{CombinatorParsers, FailedParse, FileP, ImportP, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier, TopLevelImportP}
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS, Scout}
import net.verdagon.vale.templar.{ICompileErrorT, Templar, TemplarErrorHumanizer, Temputs}
import net.verdagon.vale.{Builtins, Err, FileCoordinate, FileCoordinateMap, IPackageResolver, IProfiler, NullProfiler, Ok, PackageCoordinate, PackageCoordinateMap, Result, vassert, vassertSome, vfail, vimpl, vwat}
import net.verdagon.vale.vivem.{Heap, PrimitiveKindV, ReferenceV, Vivem}
import net.verdagon.von.{IVonData, JsonSyntax, VonPrinter}

import scala.collection.immutable.List

case class FullCompilationOptions(
  debugOut: String => Unit = (x => {
    println("##: " + x)
  }),
  verbose: Boolean = true,
  profiler: IProfiler = new NullProfiler(),
  useOptimization: Boolean = false,
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

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

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = hammerCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, List[(Int, Int)])], FailedParse] = hammerCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = hammerCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = hammerCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = hammerCompilation.getAstrouts()
  def getTemputs(): Result[Hinputs, ICompileErrorT] = hammerCompilation.getTemputs()
  def expectTemputs(): Hinputs = hammerCompilation.expectTemputs()
  def getHamuts(): ProgramH = hammerCompilation.getHamuts()
}
