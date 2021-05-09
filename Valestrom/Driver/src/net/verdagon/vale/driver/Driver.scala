package net.verdagon.vale.driver

import java.io.{BufferedWriter, File, FileNotFoundException, FileWriter, OutputStream, PrintStream}
import java.util.InputMismatchException
import net.verdagon.vale.astronomer.{Astronomer, AstronomerErrorHumanizer, ProgramA}
import net.verdagon.vale.hammer.{Hammer, Hamuts, VonHammer}
import net.verdagon.vale.highlighter.{Highlighter, Spanner}
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{CombinatorParsers, FileP, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier, Vonifier}
import net.verdagon.vale.scout.{Scout, ScoutErrorHumanizer}
import net.verdagon.vale.templar.{Templar, TemplarErrorHumanizer}
import net.verdagon.vale.vivem.Vivem
import net.verdagon.vale.{Err, NullProfiler, Ok, Result, Samples, Terrain, vassert, vassertSome, vcheck, vfail, vwat}
import net.verdagon.von.{IVonData, JsonSyntax, VonInt, VonPrinter}

import scala.io.Source
import scala.util.matching.Regex

object Driver {
  case class InputException(message: String) extends Throwable

  sealed trait IValestromInput
  case class PathInput(path: String) extends IValestromInput
  // Path probably doesn't actually exist, it might be something made up like "in.vale", "0.vale", "1.vale", etc
  case class SourceInput(path: String, code: String) extends IValestromInput

  case class Options(
    inputs: List[IValestromInput],
    outputDirPath: Option[String],
    benchmark: Boolean,
    outputVPST: Boolean,
    outputVAST: Boolean,
    outputHighlights: Boolean,
    includeBuiltins: Boolean,
    mode: Option[String], // build v run etc
    verbose: Boolean,
  )

  def parseOpts(opts: Options, list: List[String]) : Options = {
    list match {
      case Nil => opts
      case "--output-dir" :: value :: tail => {
        vcheck(opts.outputDirPath.isEmpty, "Multiple output files specified!", InputException)
        parseOpts(opts.copy(outputDirPath = Some(value)), tail)
      }
      case "--output-vpst" :: value :: tail => {
        parseOpts(opts.copy(outputVPST = value.toBoolean), tail)
      }
      case "--output-vast" :: value :: tail => {
        parseOpts(opts.copy(outputVAST = value.toBoolean), tail)
      }
      case "--include-builtins" :: value :: tail => {
        parseOpts(opts.copy(includeBuiltins = value.toBoolean), tail)
      }
      case "--benchmark" :: tail => {
        parseOpts(opts.copy(benchmark = true), tail)
      }
      case "--output-highlights" :: value :: tail => {
        parseOpts(opts.copy(outputHighlights = value.toBoolean), tail)
      }
      case ("-v" | "--verbose") :: tail => {
        parseOpts(opts.copy(verbose = true), tail)
      }
      //          case "--min-size" :: value :: tail =>
      //            parseOpts(opts ++ Map('minsize -> value.toInt), tail)
      //          case string :: opt2 :: tail if isSwitch(opt2) =>
      //            parseOpts(opts ++ Map('infile -> string), list.tail)
      case value :: _ if value.startsWith("-") => throw InputException("Unknown option " + value)
      case value :: tail => {
        if (opts.mode.isEmpty) {
          parseOpts(opts.copy(mode = Some(value)), tail)
        } else {
          parseOpts(opts.copy(inputs = opts.inputs :+ PathInput(value)), tail)
        }
      }
    }
  }

  def readCode(path: String): String = {
    if (path == "stdin:") {
      val allLines = new StringBuilder()
      var ok = true
      while (ok) {
        val ln = scala.io.StdIn.readLine()
        ok = ln != null
        if (ok) allLines.append(ln + "\n")
      }
      allLines.toString()
    } else if (path.startsWith("v:")) {
      // For example:
      //   java -cp out/artifacts/Driver_jar/Driver.jar net.verdagon.vale.driver.Driver run v:roguelike.vale v:genericvirtuals/opt.vale v:genericvirtuals/hashmap.vale v:utils.vale v:generics/arrayutils.vale v:printutils.vale v:castutils.vale v:genericvirtuals/optingarraylist.vale -o built
      val builtin = path.toLowerCase().slice("v:".length, path.length)
      Samples.get(builtin)
    } else {
      val file = path
      val bufferedSource = Source.fromFile(file)
      val code = bufferedSource.getLines.mkString("\n")
      bufferedSource.close
      code
    }
  }

  def build(opts: Options):
  Result[Option[ProgramH], String] = {
    val startTime = java.lang.System.currentTimeMillis()

    val inputs =
        opts.inputs ++
          (if (opts.includeBuiltins) {
            List(
              SourceInput("builtins/arrayutils.vale", Samples.get("builtins/arrayutils.vale")),
              SourceInput("builtins/builtinexterns.vale", Samples.get("builtins/builtinexterns.vale")),
              SourceInput("builtins/castutils.vale", Samples.get("builtins/castutils.vale")),
              SourceInput("builtins/opt.vale", Samples.get("builtins/opt.vale")),
              SourceInput("builtins/printutils.vale", Samples.get("builtins/printutils.vale")),
              SourceInput("builtins/strings.vale", Samples.get("builtins/strings.vale")),
              SourceInput("builtins/utils.vale", Samples.get("builtins/utils.vale")))
          } else {
            List()
          })

    val expandedInputs =
      inputs.flatMap({
        case si @ SourceInput(_, _) => List(si)
        case pi @ PathInput(path) => {
          if (path.endsWith(".vale")) {
            List(pi)
          } else if (path.endsWith(".vpst")) {
            List(pi)
          } else {
            try {
              val directory = new java.io.File(path)
              val filesInDirectory = directory.listFiles
              val inputFiles =
                filesInDirectory.filter(_.getName.endsWith(".vale")) ++
                filesInDirectory.filter(_.getName.endsWith(".vpst"))
              inputFiles.map(_.getPath).map(PathInput).toList
            } catch {
              case _ : FileNotFoundException => {
                throw InputException("Couldn't find file or folder: " + path)
              }
            }
          }
        }
      })

    val loadedInputs =
      expandedInputs.map({
        case si @ SourceInput(_, _) => si
        case PathInput(path) => {
          val contents =
            (try {
              val file = new java.io.File(path)
              val lineSource = Source.fromFile(file)
              val source = lineSource.getLines().mkString("\n")
              lineSource.close()
              source
            } catch {
              case _ : FileNotFoundException => {
                throw InputException("Couldn't find file or folder: " + path)
              }
            })
          SourceInput(path, contents)
        }
        case o @ PathInput(_) => vwat(o.toString)
        case other => vwat(other.toString)
      })

    val startParsingTime = java.lang.System.currentTimeMillis()
    if (opts.benchmark) {
      println("Load duration: " + (startParsingTime - startTime))
    }

    val filepathsAndSources = loadedInputs.map(l => (l.path, l.code))
    val vpstJsons =
      loadedInputs.zipWithIndex.map({ case (SourceInput(filepath, contents), fileIndex) =>
//        println("Parsing " + filepath + "...")
        if (filepath.endsWith(".vale")) {
          Parser.runParserForProgramAndCommentRanges(contents) match {
            case ParseFailure(error) => return Err(ParseErrorHumanizer.humanize(filepathsAndSources, fileIndex, error))
            case ParseSuccess((program0, _)) => {
              val von = ParserVonifier.vonifyFile(program0)
              val json = new VonPrinter(JsonSyntax, 120).print(von)
              if (opts.outputVPST) {
                val parts = filepath.split("[/\\\\]")
                val vpstFilepath = opts.outputDirPath.get + "/" + parts.last.replaceAll("\\.vale", ".vpst")
                writeFile(vpstFilepath, json)
              }
              json
            }
          }
        } else if (filepath.endsWith(".vpst")) {
          contents
        } else {
          throw new InputException("Unknown input type: " + filepath)
        }
      })

    val startLoadingVpstTime = java.lang.System.currentTimeMillis()
    if (opts.benchmark) {
      println("Parse .vale duration: " + (startLoadingVpstTime - startParsingTime))
    }

    val parseds =
      vpstJsons.zipWithIndex.map({ case (vpstJson, fileIndex) =>
        ParsedLoader.load(vpstJson) match {
          case ParseFailure(error) => return Err(ParseErrorHumanizer.humanize(filepathsAndSources, fileIndex, error))
          case ParseSuccess(program0) => program0
        }
      })

    val doneParsingVpstTime = java.lang.System.currentTimeMillis()
    if (opts.benchmark) {
      println("Parse .vpst duration: " + (doneParsingVpstTime - startLoadingVpstTime))
    }

    if (opts.outputVAST) {
      val startScoutTime = doneParsingVpstTime

      val outputVastFilepath = opts.outputDirPath.get + "/build.vast"
      val scoutput =
        Scout.scoutProgram(parseds) match {
          case Err(e) => return Err(ScoutErrorHumanizer.humanize(filepathsAndSources, e))
          case Ok(p) => p
        }

      val startAstronomerTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Scout phase duration: " + (startAstronomerTime - startScoutTime))
      }

      val astrouts =
        Astronomer.runAstronomer(scoutput) match {
          case Right(error) => return Err(AstronomerErrorHumanizer.humanize(filepathsAndSources, error))
          case Left(result) => result
        }

      val startTemplarTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Astronomer phase duration: " + (startTemplarTime - startAstronomerTime))
      }

      val hinputs =
        new Templar(if (opts.verbose) println else (_), opts.verbose, new NullProfiler(), false).evaluate(astrouts) match {
          case Err(error) => return Err(TemplarErrorHumanizer.humanize(opts.verbose, filepathsAndSources, error))
          case Ok(x) => x
        }

      val startHammerTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Templar phase duration: " + (startHammerTime - startTemplarTime))
      }

      val programH = Hammer.translate(hinputs)

      val finishTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Hammer phase duration: " + (finishTime - startHammerTime))
      }

      if (outputVastFilepath != "") {
        val json = jsonifyProgram(programH)

        writeFile(outputVastFilepath, json)
        println("Wrote VAST to file " + outputVastFilepath)
      }

      Ok(Some(programH))
    } else {
      Ok(None)
    }
  }

  def jsonifyProgram(programH: ProgramH): String = {
    val programV = VonHammer.vonifyProgram(programH)
    val json = new VonPrinter(JsonSyntax, 120).print(programV)
    json
  }

  def buildAndOutput(opts: Options) = {
      build(opts) match {
        case Ok(_) => {
        }
        case Err(error) => {
          System.err.println(error)
          System.exit(22)
          vfail()
        }
      }
  }

  def outputParseds(outputFile: String, program0: FileP): Unit = {
    val program0J = Vonifier.vonifyProgram(program0)
    val json = new VonPrinter(JsonSyntax, 120).print(program0J)
    println("Wrote to file " + outputFile)
    writeFile(outputFile, json)
  }

  def outputHamuts(outputFile: String, programH: ProgramH): Unit = {
    val programV = VonHammer.vonifyProgram(programH)
    val json = new VonPrinter(JsonSyntax, 120).print(programV)
    println("Wrote to file " + outputFile)
    writeFile(outputFile, json)
  }

  def run(program: ProgramH, verbose: Boolean): IVonData = {
    if (verbose) {
      Vivem.executeWithPrimitiveArgs(
        program, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
    } else {
      Vivem.executeWithPrimitiveArgs(
        program,
        Vector(),
        new PrintStream(new OutputStream() {
          override def write(b: Int): Unit = {
            // System.out.write(b)
          }
        }),
        () => {
          scala.io.StdIn.readLine()
        },
        (str: String) => {
          print(str)
        })
    }
  }

  def main(args: Array[String]): Unit = {
    try {
      val opts = parseOpts(Options(List(), None, false, true, true, false, true, None, false), args.toList)
      vcheck(opts.mode.nonEmpty, "No mode!", InputException)
      vcheck(opts.inputs.nonEmpty, "No input files!", InputException)

      opts.mode.get match {
        case "highlight" => {
          vcheck(opts.inputs.size == 1, "Must have exactly 1 input file for highlighting", InputException)
          opts.inputs.foreach({ case PathInput(inputFilePath) =>
            val code = readCode(inputFilePath)
            val (parsed, commentRanges) =
              Parser.runParserForProgramAndCommentRanges(code) match {
                case ParseFailure(err) => {
                  println(ParseErrorHumanizer.humanize(List(("in.vale", code)), 0, err))
                  System.exit(22)
                  vfail()
                }
                case ParseSuccess(program0) => program0
              }
            val span = Spanner.forProgram(parsed)
            val highlights = Highlighter.toHTML(code, span, commentRanges)
            if (opts.outputDirPath == Some("")) {
              println(highlights)
            } else {
              val outputFilepath = inputFilePath.replaceAll("\\.vale", ".html")
              writeFile(outputFilepath, highlights)
            }
          })
        }
        case "build" => {
          vcheck(opts.outputDirPath.nonEmpty, "Must specify --output-dir!", InputException)
          buildAndOutput(opts)
        }
        case "run" => {
          throw InputException("Run command has been disabled.");

//          vcheck(args.size >= 2, "Need name!", InputException)
//
//          val optsWithForcedCompile =
//            opts.outputVastFilepath match {
//              case None => opts.copy(outputVastFilepath = Some(""))
//              case Some(_) => opts
//            }
//
//          val program =
//            build(optsWithForcedCompile) match {
//              case Ok(Some(programH)) => programH
//              case Err(error) => {
//                System.err.println(error)
//                System.exit(22)
//                vfail()
//              }
//            }
//
//          val verbose = args.slice(2, args.length).contains("--verbose")
//          val result =
//            if (verbose) {
//              Vivem.executeWithPrimitiveArgs(
//                program, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
//            } else {
//              Vivem.executeWithPrimitiveArgs(
//                program,
//                Vector(),
//                new PrintStream(new OutputStream() {
//                  override def write(b: Int): Unit = {
//                    // System.out.write(b)
//                  }
//                }),
//                () => {
//                  scala.io.StdIn.readLine()
//                },
//                (str: String) => {
//                  print(str)
//                })
//            }
//          println("Program result: " + result)
//          println()
        }
      }
    } catch {
      case ie @ InputException(msg) => {
        println(msg)
        System.exit(22)
      }
    }
  }

  def writeFile(filepath: String, s: String): Unit = {
    if (filepath == "stdout:") {
      println(s)
    } else {
      val file = new File(filepath)
      val bw = new BufferedWriter(new FileWriter(file))
      bw.write(s)
      bw.close()
    }
  }
}
