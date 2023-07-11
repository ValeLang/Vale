package dev.vale.typing

import dev.vale.highertyping.{HigherTypingCompilation, ICompileErrorA, ProgramA}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.postparsing._
import dev.vale.{Err, FileCoordinateMap, IPackageResolver, Ok, PackageCoordinate, PackageCoordinateMap, Result, vcurious, vfail}
import dev.vale._
import dev.vale.highertyping._
import dev.vale.lexing.{FailedParse, RangeL}
import dev.vale.postparsing.ICompileErrorS

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

case class TypingPassOptions(
  globalOptions: GlobalOptions = GlobalOptions(),
  debugOut: (=> String) => Unit = DefaultPrintyThing.print,
  treeShakingEnabled: Boolean = true
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class TypingPassCompilation(
  val interner: Interner,
  val keywords: Keywords,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: TypingPassOptions = TypingPassOptions()) {
  var higherTypingCompilation =
    new HigherTypingCompilation(
      options.globalOptions, interner, keywords, packagesToBuild, packageToContentsResolver)
  var hinputsCache: Option[HinputsT] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = higherTypingCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[RangeL])], FailedParse] = higherTypingCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = higherTypingCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = higherTypingCompilation.getScoutput()

  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = higherTypingCompilation.getAstrouts()

  def getCompilerOutputs(): Result[HinputsT, ICompileErrorT] = {
    hinputsCache match {
      case Some(coutputs) => Ok(coutputs)
      case None => {
        val compiler =
          new Compiler(
            options,
            interner,
            keywords)
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

  def expectCompilerOutputs(): HinputsT = {
    getCompilerOutputs() match {
      case Err(err) => {

        val codeMap = getCodeMap().getOrDie()
        val errorText =
          CompilerErrorHumanizer.humanize(
            true,
            SourceCodeUtils.humanizePos(codeMap, _),
            SourceCodeUtils.linesBetween(codeMap, _, _),
            SourceCodeUtils.lineRangeContaining(codeMap, _),
            SourceCodeUtils.lineContaining(codeMap, _),
            err)
        vfail(errorText)
      }
      case Ok(x) => x
    }
  }
}
