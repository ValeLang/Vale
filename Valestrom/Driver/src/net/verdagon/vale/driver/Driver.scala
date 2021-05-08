package net.verdagon.vale.driver

import java.io.{BufferedWriter, File, FileWriter, OutputStream, PrintStream}
import java.util.InputMismatchException
import net.verdagon.vale.astronomer.{Astronomer, AstronomerErrorHumanizer, ProgramA}
import net.verdagon.vale.hammer.{Hammer, Hamuts, VonHammer}
import net.verdagon.vale.highlighter.{Highlighter, Spanner}
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{CombinatorParsers, FileP, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier, Vonifier}
import net.verdagon.vale.scout.{Scout, ScoutErrorHumanizer}
import net.verdagon.vale.templar.{Templar, TemplarErrorHumanizer}
import net.verdagon.vale.vivem.Vivem
import net.verdagon.vale.{Err, NullProfiler, Ok, Result, Samples, Terrain, vassert, vassertSome, vcheck, vfail}
import net.verdagon.von.{IVonData, JsonSyntax, VonInt, VonPrinter}

import scala.io.Source
import scala.util.matching.Regex

object Driver {
  case class InputException(message: String) extends Throwable

  case class Options(
    inputFiles: List[String],
    outputDirPath: Option[String],
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
          parseOpts(opts.copy(inputFiles = opts.inputFiles :+ value), tail)
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

  def build(opts: Options): Result[Option[ProgramH], String] = {
    build(opts, opts.inputFiles.map(readCode))
  }

  def build(opts: Options, sources: List[String]): Result[Option[ProgramH], String] = {
    vassert(opts.inputFiles.size == sources.size)
    val filepathsAndSources =
        opts.inputFiles.zip(sources) ++
          (if (opts.includeBuiltins) {
            List(
              ("builtins/arrayutils.vale", Samples.get("builtins/arrayutils.vale")),
              ("builtins/builtinexterns.vale", Samples.get("builtins/builtinexterns.vale")),
              ("builtins/castutils.vale", Samples.get("builtins/castutils.vale")),
              ("builtins/file.vale", Samples.get("builtins/file.vale")),
              ("builtins/opt.vale", Samples.get("builtins/opt.vale")),
              ("builtins/printutils.vale", Samples.get("builtins/printutils.vale")),
              ("builtins/strings.vale", Samples.get("builtins/strings.vale")),
              ("builtins/utils.vale", Samples.get("builtins/utils.vale")))
          } else {
            List()
          })

    val debugOut =
      if (opts.verbose) {
        (string: String) => { println(string) }
      } else {
        (string: String) => {}
      }

    val parseds =
      filepathsAndSources.zipWithIndex.map({ case ((filepath, source), file) =>
        val vpstJson =
          if (filepath.endsWith(".vale")) {
            Parser.runParserForProgramAndCommentRanges(source) match {
              case ParseFailure(error) => return Err(ParseErrorHumanizer.humanize(filepathsAndSources, file, error))
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
            source
          } else {
            throw new InputException("Unknown input type: " + filepath)
          }
        ParsedLoader.load(vpstJson) match {
          case ParseFailure(error) => return Err(ParseErrorHumanizer.humanize(filepathsAndSources, file, error))
          case ParseSuccess(program0) => program0
        }
      })
//    opts.parsedsOutputDir match {
//      case None =>
//      case Some(parsedsOutputDir) => {
//        filepathsAndSources.map(_._1).zip(parseds).foreach({ case (filepath, parsed) =>
//          val von = ParserVonifier.vonifyFile(parsed)
//          val json = new VonPrinter(JsonSyntax, 120).print(von)
//          val valeFilename = filepath.split("[/\\\\]").last
//          val vpstFilename = valeFilename.replaceAll("\\.vale", ".vpst")
//          val vpstFilepath = parsedsOutputDir + (if (parsedsOutputDir.endsWith("/")) "" else "/") + vpstFilename
//          writeFile(vpstFilepath, json)
//        })
//      }
//    }
    if (opts.outputVAST) {
      val outputVastFilepath = opts.outputDirPath.get + "/build.vast"
      val scoutput =
        Scout.scoutProgram(parseds) match {
          case Err(e) => return Err(ScoutErrorHumanizer.humanize(filepathsAndSources, e))
          case Ok(p) => p
        }
      val astrouts =
        Astronomer.runAstronomer(scoutput) match {
          case Right(error) => return Err(AstronomerErrorHumanizer.humanize(filepathsAndSources, error))
          case Left(result) => result
        }
      val hinputs =
        new Templar(if (opts.verbose) println else (_), opts.verbose, new NullProfiler(), false).evaluate(astrouts) match {
          case Err(error) => return Err(TemplarErrorHumanizer.humanize(opts.verbose, filepathsAndSources, error))
          case Ok(x) => x
        }
      val programH = Hammer.translate(hinputs)

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
      val opts = parseOpts(Options(List(), None, true, true, false, true, None, false), args.toList)
      vcheck(opts.mode.nonEmpty, "No mode!", InputException)
      vcheck(opts.inputFiles.nonEmpty, "No input files!", InputException)

      opts.mode.get match {
        case "highlight" => {
          vcheck(opts.inputFiles.size == 1, "Must have exactly 1 input file for highlighting", InputException)
          opts.inputFiles.foreach(inputFile => {
            val code = readCode(inputFile)
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
              val outputFilepath = inputFile.replaceAll("\\.vale", ".html")
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