package net.verdagon.vale.driver

import java.io.{BufferedWriter, File, FileWriter, OutputStream, PrintStream}
import java.util.InputMismatchException

import net.verdagon.vale.astronomer.{Astronomer, ProgramA}
import net.verdagon.vale.carpenter.Carpenter
import net.verdagon.vale.hammer.{Hammer, Hamuts, VonHammer}
import net.verdagon.vale.highlighter.{Highlighter, Spanner}
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{Program0, VParser, Vonifier}
import net.verdagon.vale.scout.Scout
import net.verdagon.vale.templar.Templar
import net.verdagon.vale.vivem.Vivem
import net.verdagon.vale.samples.Roguelike
import net.verdagon.vale.{MainRetAdd, OrdinaryLinkedList, Sum, Terrain, vassert, vassertSome, vcheck, vfail}
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
        vcheck(opts.parsedsOutputFile.isEmpty, "Multiple highlight output files specified!", InputException)
        parseOpts(opts.copy(highlightOutputFile = Some(value)), tail)
      }
      case "-ph" :: tail => {
        vcheck(opts.outputFile.isEmpty, "Multiple highlight output files specified!", InputException)
        parseOpts(opts.copy(highlightOutputFile = Some("")), tail)
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
        case "roguelike" => Roguelike.code
        case "terrain" => Terrain.generatorCode
        case "linkedlist" => OrdinaryLinkedList.code
        case "mainretadd" => MainRetAdd.code
        case "sum" => Sum.code
        case other => throw InputException("Unknown sample: " + other)
      }
    } else {
      val file = path
      val bufferedSource = Source.fromFile(file)
      val code = bufferedSource.getLines.mkString
      bufferedSource.close
      code
    }
  }

  def build(opts: Options): ProgramH = {
    val code = opts.inputFiles.map(readCode).mkString("\n\n\n")
    val parsed =
      VParser.runParser(code) match {
        case f @ VParser.Failure(msg, next) => vfail(f.toString())
        case VParser.Success((program0, commentRanges), next) => {
          vassert(next.atEnd)
          program0
        }
      }
    val scoutput = Scout.scoutProgram(parsed)
    val astrouts = Astronomer.runAstronomer(scoutput)
    val temputs = Templar.evaluate(astrouts)
    val hinputs = Carpenter.translate(temputs)
    val programH = Hammer.translate(hinputs)

    val programV = VonHammer.vonifyProgram(programH)
    val json = new VonPrinter(JsonSyntax, 120).print(programV)
    println("Wrote to file " + opts.outputFile.get)
    writeFile(opts.outputFile.get, json)

    programH
  }

  def outputParseds(outputFile: String, program0: Program0): Unit = {
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
      val opts = parseOpts(Options(List(), None, None, None, None), args.toList)
      vcheck(opts.mode.nonEmpty, "No mode!", InputException)
      vcheck(opts.inputFiles.nonEmpty, "No input files!", InputException)
      vcheck(opts.outputFile.nonEmpty || opts.highlightOutputFile.nonEmpty || opts.parsedsOutputFile.nonEmpty, "No output file!", InputException)

      opts.mode.get match {
        case "highlight" => {
          vcheck(opts.inputFiles.size == 1, "Must have exactly 1 input file for highlighting", InputException)
          val code = readCode(opts.inputFiles.head)
          val (parsed, commentRanges) =
            VParser.runParser(code) match {
              case f @ VParser.Failure(msg, next) => vfail(f.toString())
              case VParser.Success((program0, commentRanges), next) => {
                vassert(next.atEnd)
                (program0, commentRanges)
              }
            }
          val span = Spanner.forProgram(parsed)
          val highlights = Highlighter.toHTML(code, span, commentRanges)
          opts.highlightOutputFile.get match {
            case "" => {
              println(highlights)
            }
            case file => {
              println("Writing to file " + opts.highlightOutputFile.get)
              writeFile(opts.highlightOutputFile.get, highlights)
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
      case InputException(msg) => {
        println(msg)
        return
      }
    }
  }

  def writeFile(filename: String, s: String): Unit = {
    val file = new File(filename)
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(s)
    bw.close()
  }
}