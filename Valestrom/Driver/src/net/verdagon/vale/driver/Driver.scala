package net.verdagon.vale.driver

import java.io.{BufferedWriter, File, FileWriter, OutputStream, PrintStream}
import java.util.InputMismatchException

import net.verdagon.vale.astronomer.{Astronomer, AstronomerErrorHumanizer, ProgramA}
import net.verdagon.vale.carpenter.Carpenter
import net.verdagon.vale.hammer.{Hammer, Hamuts, VonHammer}
import net.verdagon.vale.highlighter.{Highlighter, Spanner}
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{CombinatorParsers, FileP, ParseErrorHumanizer, ParseFailure, ParseSuccess, Parser, Vonifier}
import net.verdagon.vale.scout.Scout
import net.verdagon.vale.templar.{Templar, TemplarErrorHumanizer}
import net.verdagon.vale.vivem.Vivem
import net.verdagon.vale.{Err, Ok, Terrain, vassert, vassertSome, vcheck, vfail}
import net.verdagon.von.{IVonData, JsonSyntax, VonInt, VonPrinter}

import scala.io.Source

object Driver {
  case class InputException(message: String) extends Throwable

  case class Options(
    inputFiles: List[String],
    outputFile: Option[String],
    parsedsOutputFile: Option[String],
    highlightOutputFile: Option[String],
    mode: Option[String], // build v run etc
    verbose: Boolean,
  )

  def parseOpts(opts: Options, list: List[String]) : Options = {
    list match {
      case Nil => opts
      case "-o" :: value :: tail => {
        vcheck(opts.outputFile.isEmpty, "Multiple output files specified!", InputException)
        parseOpts(opts.copy(outputFile = Some(value)), tail)
      }
      case "-op" :: value :: tail => {
        vcheck(opts.parsedsOutputFile.isEmpty, "Multiple parseds output files specified!", InputException)
        parseOpts(opts.copy(parsedsOutputFile = Some(value)), tail)
      }
      case "-oh" :: value :: tail => {
        vcheck(opts.highlightOutputFile.isEmpty, "Multiple highlight output files specified!", InputException)
        parseOpts(opts.copy(highlightOutputFile = Some(value)), tail)
      }
      case "-v" :: tail => {
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
    } else if (path.startsWith("sample:")) {
      path.toLowerCase().slice("sample:".length, path.length) match {
        case "terrain" => Terrain.generatorCode
        case other => throw InputException("Unknown sample: " + other)
      }
    } else {
      val file = path
      val bufferedSource = Source.fromFile(file)
      val code = bufferedSource.getLines.mkString("\n")
      bufferedSource.close
      code
    }
  }

  def build(opts: Options): ProgramH = {
    val debugOut =
      if (opts.verbose) {
        (string: String) => { println(string) }
      } else {
        (string: String) => {}
      }

    val sources = opts.inputFiles.map(readCode)
    val parseds =
      sources.map(sourceCode => {
        Parser.runParserForProgramAndCommentRanges(sourceCode) match {
          case ParseFailure(error) => {
            println(ParseErrorHumanizer.humanize(sourceCode, error))
            System.exit(22)
            vfail()
          }
          case ParseSuccess((program0, _)) => program0
        }
      })
    val scoutput = Scout.scoutProgram(parseds)
    val astrouts =
      Astronomer.runAstronomer(scoutput) match {
        case Right(error) => {
          println(AstronomerErrorHumanizer.humanize(sources, error))
          System.exit(22)
          vfail()
        }
        case Left(result) => result
      }
    val temputs =
      new Templar(if (opts.verbose) println else (_), opts.verbose).evaluate(astrouts) match {
        case Err(error) => {
          println(TemplarErrorHumanizer.humanize(opts.verbose, sources, error))
          System.exit(22)
          vfail()
        }
        case Ok(x) => x
      }
    val hinputs = Carpenter.translate(debugOut, temputs)
    val programH = Hammer.translate(hinputs)

    val programV = VonHammer.vonifyProgram(programH)
    val json = new VonPrinter(JsonSyntax, 120).print(programV)
    println("Wrote to file " + opts.outputFile.get)
    writeFile(opts.outputFile.get, json)

    programH
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
      val opts = parseOpts(Options(List(), None, None, None, None, false), args.toList)
      vcheck(opts.mode.nonEmpty, "No mode!", InputException)
      vcheck(opts.inputFiles.nonEmpty, "No input files!", InputException)
      vcheck(opts.outputFile.nonEmpty || opts.highlightOutputFile.nonEmpty || opts.parsedsOutputFile.nonEmpty, "No output file!", InputException)

      opts.mode.get match {
        case "highlight" => {
          vcheck(opts.inputFiles.size == 1, "Must have exactly 1 input file for highlighting", InputException)
          val code = readCode(opts.inputFiles.head)
          val (parsed, commentRanges) =
            Parser.runParserForProgramAndCommentRanges(code) match {
              case ParseFailure(err) => {
                println(ParseErrorHumanizer.humanize(code, err))
                System.exit(22)
                vfail()
              }
              case ParseSuccess(program0) => program0
            }
          val span = Spanner.forProgram(parsed)
          val highlights = Highlighter.toHTML(code, span, commentRanges)
          opts.highlightOutputFile.get match {
            case "" => {
              println(highlights)
            }
            case file => {
              writeFile(file, highlights)
            }
          }
        }
        case "build" => {
          build(opts)
        }
        case "run" => {
          vcheck(args.size >= 2, "Need name!", InputException)
          val program = build(opts)

          val verbose = args.slice(2, args.length).contains("--verbose")
          val result =
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
          println("Program result: " + result)
          println()
          val programV = VonHammer.vonifyProgram(program)
          val json = new VonPrinter(JsonSyntax, 120).print(programV)
          println("Writing to file " + opts.outputFile.get)
          writeFile(opts.outputFile.get, json)
        }
      }
    } catch {
      case ie @ InputException(msg) => {
        println(msg)
        throw ie
      }
    }
  }

  def writeFile(filename: String, s: String): Unit = {
    if (filename == "stdout:") {
      println(s)
    } else {
      val file = new File(filename)
      val bw = new BufferedWriter(new FileWriter(file))
      bw.write(s)
      bw.close()
    }
  }
}