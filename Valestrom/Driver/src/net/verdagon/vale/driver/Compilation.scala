package net.verdagon.vale.driver

import net.verdagon.vale.astronomer.{Astronomer, ProgramA}
import net.verdagon.vale.hammer.{Hammer, VonHammer}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{CombinatorParsers, FileP, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier}
import net.verdagon.vale.scout.{ProgramS, Scout}
import net.verdagon.vale.templar.{Templar, TemplarErrorHumanizer, Temputs}
import net.verdagon.vale.{Err, IProfiler, NullProfiler, Ok, Samples, vassert, vfail, vwat}
import net.verdagon.vale.vivem.{Heap, PrimitiveReferendV, ReferenceV, Vivem}
import net.verdagon.von.{IVonData, JsonSyntax, VonPrinter}

import scala.collection.immutable.List

object Compilation {
  def multiple(
    code: List[String],
    options: CompilationOptions = CompilationOptions()):
  Compilation = {
    new Compilation(code.zipWithIndex.map({ case (code, index) => (index + ".vale", code) }), options)
  }
  def apply(
    code: String,
    options: CompilationOptions = CompilationOptions()):
  Compilation = {
    new Compilation(List(("in.vale", code)), options)
  }
}

case class CompilationOptions(
  debugOut: String => Unit = println,
  verbose: Boolean = true,
  profiler: IProfiler = new NullProfiler(),
  useOptimization: Boolean = false,
)

class Compilation(
    var filenamesAndSources: List[(String, String)],
    options: CompilationOptions = CompilationOptions()) {
  filenamesAndSources = filenamesAndSources :+ ("builtins/builtinexterns.vale", Samples.get("builtins/builtinexterns.vale"))

  var parsedsCache: Option[List[FileP]] = None
  var scoutputCache: Option[ProgramS] = None
  var astroutsCache: Option[ProgramA] = None
  var hinputsCache: Option[Hinputs] = None
  var hamutsCache: Option[ProgramH] = None

  def getParseds(): List[FileP] = {
    parsedsCache match {
      case Some(parseds) => parseds
      case None => {
        parsedsCache =
          Some(
            filenamesAndSources.zipWithIndex.map({ case ((filename, source), fileIndex) =>
              Parser.runParserForProgramAndCommentRanges(source) match {
                case ParseFailure(err) => {
                  vwat(ParseErrorHumanizer.humanize(filenamesAndSources, fileIndex, err))
                }
                case ParseSuccess((program0, _)) => {
                  val von = ParserVonifier.vonifyFile(program0)
                  val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
                  ParsedLoader.load(vpstJson) match {
                    case ParseFailure(error) => vwat(ParseErrorHumanizer.humanize(filenamesAndSources, fileIndex, error))
                    case ParseSuccess(program0) => program0
                  }
                }
              }
            }))
        parsedsCache.get
      }
    }
  }

  def getScoutput(): ProgramS = {
    scoutputCache match {
      case Some(scoutput) => scoutput
      case None => {
        val scoutput =
          Scout.scoutProgram(getParseds()) match {
            case Err(e) => vfail(e.toString)
            case Ok(p) => p
          }
        scoutputCache = Some(scoutput)
        scoutput
      }
    }
  }

  def getAstrouts(): ProgramA = {
    astroutsCache match {
      case Some(astrouts) => astrouts
      case None => {
        Astronomer.runAstronomer(getScoutput()) match {
          case Right(err) => vfail(err.toString)
          case Left(astrouts) => {
            astroutsCache = Some(astrouts)
            astrouts
          }
        }
      }
    }
  }

  def getTemputs(): Hinputs = {
    hinputsCache match {
      case Some(temputs) => temputs
      case None => {
        val hamuts =
          new Templar(options.debugOut, options.verbose, options.profiler, options.useOptimization).evaluate(getAstrouts()) match {
            case Ok(t) => t
            case Err(e) => vfail(TemplarErrorHumanizer.humanize(true, filenamesAndSources, e))
          }
        hinputsCache = Some(hamuts)
        hamuts
      }
    }
  }

  def getHamuts(): ProgramH = {
    hamutsCache match {
      case Some(hamuts) => hamuts
      case None => {
        val hamuts = Hammer.translate(getTemputs())
        VonHammer.vonifyProgram(hamuts)
        hamutsCache = Some(hamuts)
        hamuts
      }
    }
  }

  def evalForReferend(heap: Heap, args: Vector[ReferenceV]): IVonData = {
    Vivem.executeWithHeap(getHamuts(), heap, args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def run(heap: Heap, args: Vector[ReferenceV]): Unit = {
    Vivem.executeWithHeap(getHamuts(), heap, args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def run(args: Vector[PrimitiveReferendV]): Unit = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def evalForReferend(args: Vector[PrimitiveReferendV]): IVonData = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def evalForReferend(
      args: Vector[PrimitiveReferendV],
      stdin: List[String]):
  IVonData = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.stdinFromList(stdin), Vivem.regularStdout)
  }
  def evalForStdout(args: Vector[PrimitiveReferendV]): String = {
    val (stdoutStringBuilder, stdoutFunc) = Vivem.stdoutCollector()
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, stdoutFunc)
    stdoutStringBuilder.mkString
  }
  def evalForReferendAndStdout(args: Vector[PrimitiveReferendV]): (IVonData, String) = {
    val (stdoutStringBuilder, stdoutFunc) = Vivem.stdoutCollector()
    val referend = Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, stdoutFunc)
    (referend, stdoutStringBuilder.mkString)
  }
}