package dev.vale.monomorphizing

import dev.vale.highertyping.{ICompileErrorA, ProgramA}
import dev.vale.lexing.{FailedParse, RangeL}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.postparsing.{ICompileErrorS, ProgramS}
import dev.vale.{FileCoordinateMap, IPackageResolver, Interner, Keywords, PackageCoordinate, PackageCoordinateMap, Result, vassertSome, vcurious}
import dev.vale.typing.{Hinputs, ICompileErrorT, TypingPassCompilation, TypingPassOptions}

case class MonomorphizedCompilationOptions(
  globalOptions: GlobalOptions = GlobalOptions(),
  debugOut: (=> String) => Unit = (x => {
    println("##: " + x)
  })
) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class MonomorphizedCompilation(
  val interner: Interner,
  val keywords: Keywords,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: MonomorphizedCompilationOptions = MonomorphizedCompilationOptions()) {
  var typingPassCompilation =
    new TypingPassCompilation(
      interner,
      keywords,
      packagesToBuild,
      packageToContentsResolver,
      TypingPassOptions(
        options.globalOptions,
        options.debugOut))
  var monoutsCache: Option[Hinputs] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = typingPassCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[RangeL])], FailedParse] = typingPassCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = typingPassCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = typingPassCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = typingPassCompilation.getAstrouts()
  def getCompilerOutputs(): Result[Hinputs, ICompileErrorT] = typingPassCompilation.getCompilerOutputs()
  def expectCompilerOutputs(): Hinputs = typingPassCompilation.expectCompilerOutputs()

  def getMonouts(): Hinputs = {
    monoutsCache match {
      case Some(monouts) => monouts
      case None => {
        val monouts =
          Monomorphizer.translate(
            options.globalOptions, interner, keywords, typingPassCompilation.expectCompilerOutputs())
        monoutsCache = Some(monouts)
        monouts
      }
    }
  }
}
