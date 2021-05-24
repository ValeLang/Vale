package net.verdagon.vale.templar

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.parser.FileP
import net.verdagon.vale.scout.{CodeLocationS, ICompileErrorS, ITemplexS, ProgramS, RangeS}

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

case class TemplarCompilationOptions(
  debugOut: String => Unit = (x => {
    println("##: " + x)
  }),
  verbose: Boolean = true,
  profiler: IProfiler = new NullProfiler(),
  useOptimization: Boolean = false,
)

class TemplarCompilation(
  modulesToBuild: List[String],
  namespaceToContentsResolver: INamespaceResolver[Map[String, String]],
  options: TemplarCompilationOptions = TemplarCompilationOptions()) {
  var astronomerCompilation = new AstronomerCompilation(modulesToBuild, namespaceToContentsResolver)
  var hinputsCache: Option[Hinputs] = None

  def getCodeMap(): FileCoordinateMap[String] = astronomerCompilation.getCodeMap()
  def getParseds(): FileCoordinateMap[(FileP, List[(Int, Int)])] = astronomerCompilation.getParseds()
  def getVpstMap(): FileCoordinateMap[String] = astronomerCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = astronomerCompilation.getScoutput()
  def expectScoutput(): FileCoordinateMap[ProgramS] = astronomerCompilation.expectScoutput()

  def getAstrouts(): Result[NamespaceCoordinateMap[ProgramA], ICompileErrorA] = astronomerCompilation.getAstrouts()
  def expectAstrouts(): NamespaceCoordinateMap[ProgramA] = astronomerCompilation.expectAstrouts()

  def getTemputs(): Result[Hinputs, ICompileErrorT] = {
    hinputsCache match {
      case Some(temputs) => Ok(temputs)
      case None => {
        val templar = new Templar(options.debugOut, options.verbose, options.profiler, options.useOptimization)
        templar.evaluate(expectAstrouts()) match {
          case Err(e) => Err(e)
          case Ok(hinputs) => {
            hinputsCache = Some(hinputs)
            Ok(hinputs)
          }
        }
      }
    }
  }
  def expectTemputs(): Hinputs = {
    getTemputs().getOrDie()
  }
}
