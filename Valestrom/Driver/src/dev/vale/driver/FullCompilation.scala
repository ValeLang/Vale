package dev.vale.driver

import dev.vale.metal.ProgramH
import dev.vale.options.GlobalOptions
import dev.vale.parser.ast.FileP
import dev.vale.scout.{ICompileErrorS, ProgramS}
import dev.vale.{FileCoordinateMap, IPackageResolver, PackageCoordinate, PackageCoordinateMap, Result, vcurious}
import dev.vale.hammer.HammerCompilation
import dev.vale.astronomer.ICompileErrorA
import Driver.SourceInput
import dev.vale.astronomer.{ICompileErrorA, ProgramA}
import dev.vale.hammer.{HammerCompilation, HammerCompilationOptions, VonHammer}
import dev.vale.parser.FailedParse
import dev.vale.templar.{Hinputs, ICompileErrorT}
import dev.vale.parser.FailedParse
import dev.vale.scout.Scout
import dev.vale.templar.ICompileErrorT
import dev.vale.{Builtins, Err, FileCoordinate, FileCoordinateMap, IPackageResolver, Ok, PackageCoordinate, PackageCoordinateMap, Profiler, Result, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import dev.vale.vivem.ReferenceV

import scala.collection.immutable.List

case class FullCompilationOptions(
  globalOptions: GlobalOptions = GlobalOptions(false, true, false, false),
  debugOut: (=> String) => Unit = (x => {
    println("##: " + x)
  }),
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class FullCompilation(
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: FullCompilationOptions = FullCompilationOptions()) {
  var hammerCompilation =
    new HammerCompilation(
      packagesToBuild,
      packageToContentsResolver,
      HammerCompilationOptions(
        options.debugOut,
        options.globalOptions))

  def interner = hammerCompilation.interner
  def getVonHammer(): VonHammer = hammerCompilation.getVonHammer()

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = hammerCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = hammerCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = hammerCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = hammerCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = hammerCompilation.getAstrouts()
  def getTemputs(): Result[Hinputs, ICompileErrorT] = hammerCompilation.getTemputs()
  def expectTemputs(): Hinputs = hammerCompilation.expectTemputs()
  def getHamuts(): ProgramH = hammerCompilation.getHamuts()
}
