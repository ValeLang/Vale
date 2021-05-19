package net.verdagon.vale.driver

import net.verdagon.vale.astronomer.{Astronomer, ICompileErrorA, ProgramA}
import net.verdagon.vale.driver.Driver.SourceInput
import net.verdagon.vale.hammer.{Hammer, VonHammer}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{CombinatorParsers, FileP, ImportP, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier, TopLevelImportP}
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS, Scout}
import net.verdagon.vale.templar.{ICompileErrorT, Templar, TemplarErrorHumanizer, Temputs}
import net.verdagon.vale.{Err, FileCoordinate, FileCoordinateMap, IProfiler, NamespaceCoordinate, NamespaceCoordinateMap, NullProfiler, Ok, Result, Samples, vassert, vassertSome, vfail, vimpl, vwat}
import net.verdagon.vale.vivem.{Heap, PrimitiveReferendV, ReferenceV, Vivem}
import net.verdagon.von.{IVonData, JsonSyntax, VonPrinter}

import scala.collection.immutable.List

case class CompilationOptions(
  debugOut: String => Unit = (x => {
    println("##: " + x)
  }),
  verbose: Boolean = true,
  profiler: IProfiler = new NullProfiler(),
  useOptimization: Boolean = false,
)

object Compilation {
  val builtins =
    Map(
      "arrayutils" -> Samples.get("builtins/arrayutils.vale"),
      "builtinexterns" -> Samples.get("builtins/builtinexterns.vale"),
      "castutils" -> Samples.get("builtins/castutils.vale"),
      "opt" -> Samples.get("builtins/opt.vale"),
      "printutils" -> Samples.get("builtins/printutils.vale"),
      "strings" -> Samples.get("builtins/strings.vale"),
      "utils" -> Samples.get("builtins/utils.vale"))

  def test(
    dependencies: List[String],
    code: List[String]):
  Compilation = {
    testWithOptions(dependencies, code, CompilationOptions())
  }

  def testWithOptions(
    dependencies: List[String],
    code: List[String],
    options: CompilationOptions):
  Compilation = {
    new Compilation(
      List(FileCoordinateMap.TEST_MODULE),
      {
        case NamespaceCoordinate(FileCoordinateMap.TEST_MODULE, List()) => code.zipWithIndex.map({ case (code, index) => (index + ".vale", code) }).toMap
        case NamespaceCoordinate("", List()) => dependencies.map(d => (d -> builtins(d))).toMap
        case x => vfail("Couldn't find module: " + x)
      },
      options)
  }

  def test(
    dependencies: List[String],
    code: String):
  Compilation = {
    test(dependencies, List(code))
  }

  def loadAndParse(
    neededModules: List[String],
    resolver: NamespaceCoordinate => Map[String, String]):
  (FileCoordinateMap[String], FileCoordinateMap[(FileP, List[(Int, Int)])]) = {
    vassert(neededModules.size == neededModules.distinct.size)

    loadAndParseIteration(neededModules, FileCoordinateMap(Map()), FileCoordinateMap(Map()), resolver)
  }

  def loadAndParseIteration(
    neededModules: List[String],
    alreadyFoundCodeMap: FileCoordinateMap[String],
    alreadyParsedProgramPMap: FileCoordinateMap[(FileP, List[(Int, Int)])],
    resolver: NamespaceCoordinate => Map[String, String]):
  (FileCoordinateMap[String], FileCoordinateMap[(FileP, List[(Int, Int)])]) = {
    val neededNamespaceCoords =
        neededModules.map(module => NamespaceCoordinate(module, List())) ++
        alreadyParsedProgramPMap.flatMap({ case (fileCoord, file) =>
          file._1.topLevelThings.collect({
            case TopLevelImportP(ImportP(_, moduleName, namespaceSteps, importeeName)) => {
              NamespaceCoordinate(moduleName.str, namespaceSteps.map(_.str))
            }
          })
        }).toList.flatten.filter(namespaceCoord => {
          !alreadyParsedProgramPMap.moduleToNamespacesToFilenameToContents
            .getOrElse(namespaceCoord.module, Map())
            .contains(namespaceCoord.namespaces)
        })

    if (neededNamespaceCoords.isEmpty) {
      return (alreadyFoundCodeMap, alreadyParsedProgramPMap)
    }

    val neededCodeMapFlat =
        neededNamespaceCoords.flatMap(neededNamespaceCoord => {
          val filepathsAndContents = resolver(neededNamespaceCoord)
          // Note that filepathsAndContents *can* be empty, see ImportTests.
          List((neededNamespaceCoord.module, neededNamespaceCoord.namespaces, filepathsAndContents))
        })
    val grouped =
      neededCodeMapFlat.groupBy(_._1).mapValues(_.groupBy(_._2).mapValues(_.map(_._3).head))
    val neededCodeMap = FileCoordinateMap(grouped)

    val newProgramPMap =
      neededCodeMap.map({ case (fileCoord, code) =>
        Parser.runParserForProgramAndCommentRanges(code) match {
          case ParseFailure(err) => {
            vwat(ParseErrorHumanizer.humanize(neededCodeMap, fileCoord, err))
          }
          case ParseSuccess((program0, commentsRanges)) => {
            val von = ParserVonifier.vonifyFile(program0)
            val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
            ParsedLoader.load(vpstJson) match {
              case ParseFailure(error) => vwat(ParseErrorHumanizer.humanize(neededCodeMap, fileCoord, error))
              case ParseSuccess(program0) => (program0, commentsRanges)
            }
          }
        }
      })

    val combinedCodeMap = alreadyFoundCodeMap.mergeNonOverlapping(neededCodeMap)
    val combinedProgramPMap = alreadyParsedProgramPMap.mergeNonOverlapping(newProgramPMap)

    loadAndParseIteration(List(), combinedCodeMap, combinedProgramPMap, resolver)
  }
}

class Compilation(
    modulesToBuild: List[String],
    namespaceToContentsResolver: NamespaceCoordinate => Map[String, String],
    options: CompilationOptions = CompilationOptions()) {
  var codeMapCache: Option[FileCoordinateMap[String]] = None
  var vpstMapCache: Option[FileCoordinateMap[String]] = None
  var parsedsCache: Option[FileCoordinateMap[(FileP, List[(Int, Int)])]] = None
  var scoutputCache: Option[FileCoordinateMap[ProgramS]] = None
  var astroutsCache: Option[NamespaceCoordinateMap[ProgramA]] = None
  var hinputsCache: Option[Hinputs] = None
  var hamutsCache: Option[ProgramH] = None

  def getCodeMap(): FileCoordinateMap[String] = {
    getParseds()
    codeMapCache.get
  }
  def getParseds(): FileCoordinateMap[(FileP, List[(Int, Int)])] = {
    parsedsCache match {
      case Some(parseds) => parseds
      case None => {
        // Also build the "" module, which has all the builtins
        val (codeMap, programPMap) =
          Compilation.loadAndParse("" :: modulesToBuild, namespaceToContentsResolver)
        codeMapCache = Some(codeMap)
        parsedsCache = Some(programPMap)
        parsedsCache.get
      }
    }
  }

  def getVpstMap(): FileCoordinateMap[String] = {
    vpstMapCache match {
      case Some(vpst) => vpst
      case None => {
        getParseds().map({ case (fileCoord, (programP, commentRanges)) =>
          val von = ParserVonifier.vonifyFile(programP)
          val json = new VonPrinter(JsonSyntax, 120).print(von)
          json
        })
      }
    }
  }

  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = {
    scoutputCache match {
      case Some(scoutput) => Ok(scoutput)
      case None => {
        val scoutput =
          getParseds().map({ case (fileCoordinate, (code, commentsAndRanges)) =>
            Scout.scoutProgram(fileCoordinate, code) match {
              case Err(e) => return Err(e)
              case Ok(p) => p
            }
          })
        scoutputCache = Some(scoutput)
        Ok(scoutput)
      }
    }
  }
  def expectScoutput(): FileCoordinateMap[ProgramS] = {
    getScoutput().getOrDie()
  }

  def getAstrouts(): Result[NamespaceCoordinateMap[ProgramA], ICompileErrorA] = {
    astroutsCache match {
      case Some(astrouts) => Ok(astrouts)
      case None => {
        Astronomer.runAstronomer(expectScoutput()) match {
          case Right(err) => Err(err)
          case Left(astrouts) => {
            astroutsCache = Some(astrouts)
            Ok(astrouts)
          }
        }
      }
    }
  }
  def expectAstrouts(): NamespaceCoordinateMap[ProgramA] = {
    getAstrouts().getOrDie()
  }

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
