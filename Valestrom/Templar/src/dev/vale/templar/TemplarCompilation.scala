package dev.vale.templar

import dev.vale.astronomer.{AstronomerCompilation, ICompileErrorA, ProgramA}
import dev.vale.options.GlobalOptions
import dev.vale.parser.FailedParse
import dev.vale.parser.ast.FileP
import dev.vale.scout.{ICompileErrorS, ProgramS}
import dev.vale.{Err, FileCoordinateMap, IPackageResolver, Ok, PackageCoordinate, PackageCoordinateMap, Result, vcurious, vfail}
import dev.vale._
import dev.vale.astronomer._
import dev.vale.scout.ICompileErrorS

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

case class TemplarCompilationOptions(
  globalOptions: GlobalOptions = GlobalOptions(),
  debugOut: (=> String) => Unit = DefaultPrintyThing.print,
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class TemplarCompilation(
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: TemplarCompilationOptions = TemplarCompilationOptions()) {
  var astronomerCompilation = new AstronomerCompilation(options.globalOptions, packagesToBuild, packageToContentsResolver)
  def interner = astronomerCompilation.interner
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
        val templar =
          new Templar(
            options.debugOut,
            astronomerCompilation.scoutCompilation.interner,
            options.globalOptions)
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
