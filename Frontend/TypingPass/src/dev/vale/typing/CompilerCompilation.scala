package dev.vale.typing

import dev.vale.highertyping.{HigherTypingCompilation, ICompileErrorA, ProgramA}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.FailedParse
import dev.vale.parsing.ast.FileP
import dev.vale.postparsing.{ICompileErrorS, ProgramS}
import dev.vale.{Err, FileCoordinateMap, IPackageResolver, Ok, PackageCoordinate, PackageCoordinateMap, Result, vcurious, vfail}
import dev.vale._
import dev.vale.highertyping._
import dev.vale.postparsing.ICompileErrorS

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

case class TypingPassCompilationOptions(
  globalOptions: GlobalOptions = GlobalOptions(),
  debugOut: (=> String) => Unit = DefaultPrintyThing.print,
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class TypingPassCompilation(
  val interner: Interner,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: TypingPassCompilationOptions = TypingPassCompilationOptions()) {
  var higherTypingCompilation =
    new HigherTypingCompilation(
      options.globalOptions, interner, packagesToBuild, packageToContentsResolver)
  var hinputsCache: Option[Hinputs] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = higherTypingCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = higherTypingCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = higherTypingCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = higherTypingCompilation.getScoutput()

  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = higherTypingCompilation.getAstrouts()

  def getCompilerOutputs(): Result[Hinputs, ICompileErrorT] = {
    hinputsCache match {
      case Some(coutputs) => Ok(coutputs)
      case None => {
        val compiler =
          new Compiler(
            options.debugOut,
            interner,
            options.globalOptions)
        compiler.evaluate(higherTypingCompilation.expectAstrouts()) match {
          case Err(e) => Err(e)
          case Ok(hinputs) => {
            hinputsCache = Some(hinputs)
            Ok(hinputs)
          }
        }
      }
    }
  }

  def expectCompilerOutputs(): Hinputs = {
    getCompilerOutputs() match {
      case Err(err) => {
        vfail(CompilerErrorHumanizer.humanize(true, getCodeMap().getOrDie(), err))
      }
      case Ok(x) => x
    }
  }
}
