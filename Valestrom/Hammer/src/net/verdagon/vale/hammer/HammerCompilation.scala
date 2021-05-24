package net.verdagon.vale.hammer

import net.verdagon.vale.{FileCoordinateMap, INamespaceResolver, IProfiler, NamespaceCoordinateMap, NullProfiler, Result}
import net.verdagon.vale.astronomer.{ICompileErrorA, ProgramA}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.FileP
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS}
import net.verdagon.vale.templar.{ICompileErrorT, TemplarCompilation, TemplarCompilationOptions}

import scala.collection.immutable.List

case class HammerCompilationOptions(
  debugOut: String => Unit = (x => {
    println("##: " + x)
  }),
  verbose: Boolean = true,
  profiler: IProfiler = new NullProfiler(),
  useOptimization: Boolean = false,
)

class HammerCompilation(
  modulesToBuild: List[String],
  namespaceToContentsResolver: INamespaceResolver[Map[String, String]],
  options: HammerCompilationOptions = HammerCompilationOptions()) {
  var templarCompilation =
    new TemplarCompilation(
      modulesToBuild,
      namespaceToContentsResolver,
      TemplarCompilationOptions(
        options.debugOut,
        options.verbose,
        options.profiler,
        options.useOptimization))
  var hamutsCache: Option[ProgramH] = None

  def getCodeMap(): FileCoordinateMap[String] = templarCompilation.getCodeMap()
  def getParseds(): FileCoordinateMap[(FileP, List[(Int, Int)])] = templarCompilation.getParseds()
  def getVpstMap(): FileCoordinateMap[String] = templarCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = templarCompilation.getScoutput()
  def expectScoutput(): FileCoordinateMap[ProgramS] = templarCompilation.expectScoutput()

  def getAstrouts(): Result[NamespaceCoordinateMap[ProgramA], ICompileErrorA] = templarCompilation.getAstrouts()
  def expectAstrouts(): NamespaceCoordinateMap[ProgramA] = templarCompilation.expectAstrouts()

  def getTemputs(): Result[Hinputs, ICompileErrorT] = templarCompilation.getTemputs()
  def expectTemputs(): Hinputs = templarCompilation.expectTemputs()

  def getHamuts(): ProgramH = {
    hamutsCache match {
      case Some(hamuts) => hamuts
      case None => {
        val hamuts = Hammer.translate(expectTemputs())
        VonHammer.vonifyProgram(hamuts)
        hamutsCache = Some(hamuts)
        hamuts
      }
    }
  }
}
