package dev.vale.passmanager

import dev.vale.finalast.ProgramH
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.postparsing._
import dev.vale.{Builtins, Err, FileCoordinate, FileCoordinateMap, IPackageResolver, Interner, Keywords, Ok, PackageCoordinate, PackageCoordinateMap, Profiler, Result, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import dev.vale.highertyping.ICompileErrorA
import PassManager.SourceInput
import dev.vale.highertyping.{ICompileErrorA, ProgramA}
import dev.vale.instantiating.ast.HinputsI
import dev.vale.lexing.{FailedParse, RangeL}
import dev.vale.simplifying._
import dev.vale.typing.{HinputsT, ICompileErrorT}
import dev.vale.instantiating.ast.HinputsI
import dev.vale.postparsing.PostParser
import dev.vale.simplifying._
import dev.vale.typing.ICompileErrorT
import dev.vale.testvm.ReferenceV

import scala.collection.immutable.List

case class FullCompilationOptions(
  globalOptions: GlobalOptions = GlobalOptions(false, true, true, false, false),
  debugOut: (=> String) => Unit = (x => {
    println("##: " + x)
  }),
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class FullCompilation(
  interner: Interner,
  keywords: Keywords,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: FullCompilationOptions = FullCompilationOptions()) {
  var hammerCompilation =
    new HammerCompilation(
      interner,
      keywords,
      packagesToBuild,
      packageToContentsResolver,
      HammerCompilationOptions(
        options.debugOut,
        options.globalOptions))

  def getVonHammer(): VonHammer = hammerCompilation.getVonHammer()

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = hammerCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[RangeL])], FailedParse] = hammerCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = hammerCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = hammerCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = hammerCompilation.getAstrouts()
  def getCompilerOutputs(): Result[HinputsT, ICompileErrorT] = hammerCompilation.getCompilerOutputs()
  def expectCompilerOutputs(): HinputsT = hammerCompilation.expectCompilerOutputs()
  def getHamuts(): ProgramH = hammerCompilation.getHamuts()
  def getMonouts(): HinputsI = hammerCompilation.getMonouts()
}
