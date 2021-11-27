package net.verdagon.vale.templar

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.{FailedParse, FileP}
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS}

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

case class TemplarCompilationOptions(
  globalOptions: GlobalOptions = GlobalOptions(),
  debugOut: (=> String) => Unit = DefaultPrintyThing.print,
  profiler: IProfiler = new NullProfiler(),
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

class TemplarCompilation(
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: TemplarCompilationOptions = TemplarCompilationOptions()) {
  var astronomerCompilation = new AstronomerCompilation(options.globalOptions, packagesToBuild, packageToContentsResolver)
  var hinputsCache: Option[Hinputs] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = astronomerCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = astronomerCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = astronomerCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = astronomerCompilation.getScoutput()

  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = astronomerCompilation.getAstrouts()

  def getTemputs(): Result[Hinputs, ICompileErrorT] = {
    hinputsCache match {
      case Some(temputs) => Ok(temputs)
      case None => {
        val templar = new Templar(options.debugOut, options.profiler, options.globalOptions)
        templar.evaluate(astronomerCompilation.expectAstrouts()) match {
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
    getTemputs() match {
      case Err(err) => {
        vfail(TemplarErrorHumanizer.humanize(true, getCodeMap().getOrDie(), err))
      }
      case Ok(x) => x
    }
  }
}
