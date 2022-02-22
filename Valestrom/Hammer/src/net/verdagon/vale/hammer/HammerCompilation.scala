package net.verdagon.vale.hammer

import net.verdagon.vale.{FileCoordinateMap, IPackageResolver, IProfiler, NullProfiler, PackageCoordinate, PackageCoordinateMap, Result, vassertSome, vcurious, vimpl}
import net.verdagon.vale.astronomer.{ICompileErrorA, ProgramA}
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.FailedParse
import net.verdagon.vale.parser.ast.FileP
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS}
import net.verdagon.vale.templar.{Hinputs, ICompileErrorT, TemplarCompilation, TemplarCompilationOptions}

import scala.collection.immutable.List

case class HammerCompilationOptions(
  debugOut: (=> String) => Unit = (x => {
    println("##: " + x)
  }),
  profiler: IProfiler = new NullProfiler(),
  globalOptions: GlobalOptions = GlobalOptions()
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class HammerCompilation(
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: HammerCompilationOptions = HammerCompilationOptions()) {
  var templarCompilation =
    new TemplarCompilation(
      packagesToBuild,
      packageToContentsResolver,
      TemplarCompilationOptions(
        options.globalOptions,
        options.debugOut,
        options.profiler))
  var hamutsCache: Option[ProgramH] = None
  var vonHammerCache: Option[VonHammer] = None

  def getVonHammer() = vassertSome(vonHammerCache)

  def interner = templarCompilation.interner

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = templarCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = templarCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = templarCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = templarCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = templarCompilation.getAstrouts()
  def getTemputs(): Result[Hinputs, ICompileErrorT] = templarCompilation.getTemputs()
  def expectTemputs(): Hinputs = templarCompilation.expectTemputs()

  def getHamuts(): ProgramH = {
    hamutsCache match {
      case Some(hamuts) => hamuts
      case None => {
        val hammer = new Hammer(interner)
        val hamuts = hammer.translate(templarCompilation.expectTemputs())
        hamutsCache = Some(hamuts)
        vonHammerCache = Some(hammer.vonHammer)
        hamuts
      }
    }
  }
}
