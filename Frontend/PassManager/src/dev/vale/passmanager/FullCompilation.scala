package dev.vale.passmanager

import dev.vale.finalast.ProgramH
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.postparsing.{ICompileErrorS, ProgramS}
import dev.vale.{Builtins, Err, FileCoordinate, FileCoordinateMap, IPackageResolver, Interner, Ok, PackageCoordinate, PackageCoordinateMap, Profiler, Result, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import dev.vale.simplifying.HammerCompilation
import dev.vale.highertyping.ICompileErrorA
import PassManager.SourceInput
import dev.vale.highertyping.{ICompileErrorA, ProgramA}
import dev.vale.lexing.FailedParse
import dev.vale.simplifying.{HammerCompilation, HammerCompilationOptions, VonHammer}
import dev.vale.typing.{Hinputs, ICompileErrorT}
import dev.vale.postparsing.PostParser
import dev.vale.typing.ICompileErrorT
import dev.vale.testvm.ReferenceV

import scala.collection.immutable.List

case class FullCompilationOptions(
  globalOptions: GlobalOptions = GlobalOptions(false, true, false, false),
  debugOut: (=> String) => Unit = (x => {
    println("##: " + x)
  }),
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class FullCompilation(
  interner: Interner,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: FullCompilationOptions = FullCompilationOptions()) {
  var hammerCompilation =
    new HammerCompilation(
      interner,
      packagesToBuild,
      packageToContentsResolver,
      HammerCompilationOptions(
        options.debugOut,
        options.globalOptions))

  def getVonHammer(): VonHammer = hammerCompilation.getVonHammer()

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = hammerCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = hammerCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = hammerCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = hammerCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = hammerCompilation.getAstrouts()
  def getCompilerOutputs(): Result[Hinputs, ICompileErrorT] = hammerCompilation.getCompilerOutputs()
  def expectCompilerOutputs(): Hinputs = hammerCompilation.expectCompilerOutputs()
  def getHamuts(): ProgramH = hammerCompilation.getHamuts()
}
