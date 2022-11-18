package dev.vale.simplifying

import dev.vale.highertyping.{ICompileErrorA, ProgramA}
import dev.vale.finalast.ProgramH
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.postparsing._
import dev.vale.typing.{Hinputs, ICompileErrorT}
import dev.vale.{FileCoordinateMap, IPackageResolver, Interner, Keywords, PackageCoordinate, PackageCoordinateMap, Profiler, Result, vassertSome, vcurious, vimpl}
import dev.vale.highertyping.ICompileErrorA
import dev.vale.lexing.{FailedParse, RangeL}
import dev.vale.monomorphizing.{MonomorphizedCompilation, MonomorphizedCompilationOptions}
import dev.vale.postparsing.ICompileErrorS
import dev.vale.typing.ICompileErrorT

import scala.collection.immutable.List

case class HammerCompilationOptions(
  debugOut: (=> String) => Unit = (x => {
    println("##: " + x)
  }),
  globalOptions: GlobalOptions = GlobalOptions()
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class HammerCompilation(
  val interner: Interner,
  val keywords: Keywords,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: HammerCompilationOptions = HammerCompilationOptions()) {
  var monomorphizedCompilation =
    new MonomorphizedCompilation(
      interner,
      keywords,
      packagesToBuild,
      packageToContentsResolver,
      MonomorphizedCompilationOptions(
        options.globalOptions,
        options.debugOut))
  var hamutsCache: Option[ProgramH] = None
  var vonHammerCache: Option[VonHammer] = None

  def getVonHammer() = vassertSome(vonHammerCache)

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = monomorphizedCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[RangeL])], FailedParse] = monomorphizedCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = monomorphizedCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = monomorphizedCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = monomorphizedCompilation.getAstrouts()
  def getCompilerOutputs(): Result[Hinputs, ICompileErrorT] = monomorphizedCompilation.getCompilerOutputs()
  def getMonouts(): Hinputs = monomorphizedCompilation.getMonouts()
  def expectCompilerOutputs(): Hinputs = monomorphizedCompilation.expectCompilerOutputs()

  def getHamuts(): ProgramH = {
    hamutsCache match {
      case Some(hamuts) => hamuts
      case None => {
        val hammer = new Hammer(interner, keywords)
        val hamuts = hammer.translate(monomorphizedCompilation.getMonouts())
        hamutsCache = Some(hamuts)
        vonHammerCache = Some(hammer.vonHammer)
        hamuts
      }
    }
  }
}
