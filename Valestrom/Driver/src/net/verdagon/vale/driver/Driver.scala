package net.verdagon.vale.driver

import java.io.{BufferedWriter, File, FileNotFoundException, FileWriter, OutputStream, PrintStream}
import java.util.InputMismatchException
import net.verdagon.vale.astronomer.{Astronomer, AstronomerErrorHumanizer, ProgramA}
import net.verdagon.vale.hammer.{Hammer, Hamuts, VonHammer}
import net.verdagon.vale.highlighter.{Highlighter, Spanner}
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{CombinatorParsers, FileP, InputException, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier, Vonifier}
import net.verdagon.vale.scout.{Scout, ScoutErrorHumanizer}
import net.verdagon.vale.templar.{Templar, TemplarErrorHumanizer}
import net.verdagon.vale.vivem.Vivem
import net.verdagon.vale.{Builtins, Err, FileCoordinate, FileCoordinateMap, NamespaceCoordinate, NullProfiler, Ok, Result, vassert, vassertSome, vcheck, vfail, vwat}
import net.verdagon.von.{IVonData, JsonSyntax, VonInt, VonPrinter}

import scala.io.Source
import scala.util.matching.Regex

object Driver {
  val defaultModuleName = "my_module"
  sealed trait IValestromInput {
    def moduleName: String
  }
  case class ModulePathInput(moduleName: String, path: String) extends IValestromInput
  case class DirectFilePathInput(moduleName: String, path: String) extends IValestromInput
  case class SourceInput(
    moduleName: String,
    // Name isnt guaranteed to be unique, we sometimes hand in strings like "builtins.vale"
    name: String,
    code: String) extends IValestromInput

  case class Options(
    inputs: List[IValestromInput],
//    modulePaths: Map[String, String],
    modulesToBuild: List[String],
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
          if (value.contains(":")) {
            val parts = value.split(":")
            vcheck(parts.size == 2, "Arguments can only have 1 colon. Saw: " + value, InputException)
            vcheck(parts(0) != "", "Must have a module name before a colon. Saw: " + value, InputException)
            vcheck(parts(1) != "", "Must have a file path after a colon. Saw: " + value, InputException)
            val Array(moduleName, path) = parts
            val input =
              if (path.endsWith(".vale") || path.endsWith(".vpst")) {
                DirectFilePathInput(moduleName, path)
              } else {
                ModulePathInput(moduleName, path)
              }
            parseOpts(opts.copy(inputs = opts.inputs :+ input), tail)
          } else {
            if (value.endsWith(".vale") || value.endsWith(".vpst")) {
              throw InputException(".vale and .vpst inputs must be prefixed with their module name and a colon.")
            }
            parseOpts(opts.copy(modulesToBuild = opts.modulesToBuild :+ value), tail)
          }
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
    } else {
      val file = path
      val bufferedSource = Source.fromFile(file)
      val code = bufferedSource.getLines.mkString("\n")
      bufferedSource.close
      code
    }
  }

  def resolveNamespaceContents(
      inputs: List[IValestromInput],
      nsCoord: NamespaceCoordinate):
  Option[Map[String, String]] = {
    val NamespaceCoordinate(module, namespaces) = nsCoord

    val sourceInputs =
      inputs.zipWithIndex.filter(_._1.moduleName == module).flatMap({
        case (SourceInput(_, name, code), index) if (namespaces == List.empty) => {
          // All .vpst and .vale direct inputs are considered part of the root namespace.
          List((index + "(" + name + ")" -> code))
        }
        case (ModulePathInput(_, modulePath), _) => {
          val directoryPath = modulePath + namespaces.map(File.separator + _).mkString("")
          val directory = new java.io.File(directoryPath)
          val filesInDirectory = directory.listFiles()
          if (filesInDirectory == null) {
            return None
          }
          val inputFiles =
            filesInDirectory.filter(_.getName.endsWith(".vale")) ++
              filesInDirectory.filter(_.getName.endsWith(".vpst"))
          val inputFilePaths = inputFiles.map(_.getPath)
          inputFilePaths.toList.map(filepath => {
            val bufferedSource = Source.fromFile(filepath)
            val code = bufferedSource.getLines.mkString("\n")
            bufferedSource.close
            (filepath -> code)
          })
        }
        case (DirectFilePathInput(_, path), _) => {
          val file = path
          val bufferedSource = Source.fromFile(file)
          val code = bufferedSource.getLines.mkString("\n")
          bufferedSource.close
          List((path -> code))
        }
      })
    val filepathToSource = sourceInputs.groupBy(_._1).mapValues(_.head._2)
    vassert(sourceInputs.size == filepathToSource.size, "Input filepaths overlap!")
    Some(filepathToSource)
  }

//  def loadAndParseInputs(
//    startTime: Long,
//    benchmark: Boolean,
//    compilation: Compilation):
//  Result[
//    (FileCoordinateMap[String],
//      FileCoordinateMap[(String, List[(Int, Int)])],
//      FileCoordinateMap[FileP],
//      Long),
//    String] = {
//
//    val expandedInputs =
//      inputs.flatMap({
//        case si @ SourceInput(_, _, _) => {
//          List(si)
//        }
//        case pi @ PathInput(moduleName, path) => {
//          if (path.endsWith(".vale")) {
//            List(pi)
//          } else if (path.endsWith(".vpst")) {
//            List(pi)
//          } else {
//            try {
//              val directory = new java.io.File(path)
//              val filesInDirectory = directory.listFiles
//              val inputFiles =
//                filesInDirectory.filter(_.getName.endsWith(".vale")) ++
//                  filesInDirectory.filter(_.getName.endsWith(".vpst"))
//              inputFiles.map(_.getPath).map(x => PathInput(moduleName, x)).toList
//            } catch {
//              case _ : FileNotFoundException => {
//                throw InputException("Couldn't find file or folder: " + path)
//              }
//            }
//          }
//        }
//      })

    //    val moduleToExpandedInputs =
    //      moduleAndExpandedInputPairs.groupBy(_.moduleName)
//
//    val loadedInputs =
//      expandedInputs.map({
//        case si@SourceInput(_, _, _) => si
//        case PathInput(moduleName, path) => {
//          val contents =
//            (try {
//              val file = new java.io.File(path)
//              val lineSource = Source.fromFile(file)
//              val source = lineSource.getLines().mkString("\n")
//              lineSource.close()
//              source
//            } catch {
//              case _: FileNotFoundException => {
//                throw InputException("Couldn't find file or folder: " + path)
//              }
//            })
//          SourceInput(moduleName, path, contents)
//        }
//        case other => vwat(other.toString)
//      })
//
//    val moduleToNamespaceToFilepathToCode =
//      loadedInputs.groupBy(_.moduleName).mapValues(loadedInputsInModule => {
//        val namespace = List[String]()
//        val filepathToCode =
//          loadedInputsInModule.groupBy(_.path).map({
//            case (path, List.empty) => vfail("No files with path: " + path)
//            case (path, List(onlyCodeWithThisFilename)) => (path -> onlyCodeWithThisFilename.code)
//            case (path, multipleCodeWithThisFilename) => vfail("Multiple files with path " + path + ": " + multipleCodeWithThisFilename.mkString(", "))
//          })
//        val namespaceToFilepathToCode = Map(namespace -> filepathToCode)
//        namespaceToFilepathToCode
//      })
//    val valeCodeMap = FileCoordinateMap(moduleToNamespaceToFilepathToCode)

//    val startParsingTime = java.lang.System.currentTimeMillis()
//    if (benchmark) {
//      println("Load duration: " + (startParsingTime - startTime))
//    }
//
//    val vpstCodeMap =
//      valeCodeMap.map({ case (fileCoord @ FileCoordinate(_, _, filepath), contents) =>
//        //        println("Parsing " + filepath + "...")
//        if (filepath.endsWith(".vale")) {
//          Parser.runParserForProgramAndCommentRanges(contents) match {
//            case ParseFailure(error) => return Err(ParseErrorHumanizer.humanize(valeCodeMap, fileCoord, error))
//            case ParseSuccess((program0, commentRanges)) => {
//              val von = ParserVonifier.vonifyFile(program0)
//              val json = new VonPrinter(JsonSyntax, 120).print(von)
//              (json, commentRanges)
//            }
//          }
//        } else if (filepath.endsWith(".vpst")) {
//          (contents, List.empty)
//        } else {
//          throw new InputException("Unknown input type: " + filepath)
//        }
//      })
//
//    val startLoadingVpstTime = java.lang.System.currentTimeMillis()
//    if (benchmark) {
//      println("Parse .vale duration: " + (startLoadingVpstTime - startParsingTime))
//    }
//
//    val parsedsMap =
//      vpstCodeMap.map({ case (fileCoord, (vpstJson, commentRanges)) =>
//        ParsedLoader.load(vpstJson) match {
//          case ParseFailure(error) => return Err(ParseErrorHumanizer.humanize(valeCodeMap, fileCoord, error))
//          case ParseSuccess(program0) => program0
//        }
//      })
//
//    val doneParsingVpstTime = java.lang.System.currentTimeMillis()
//    if (benchmark) {
//      println("Parse .vpst duration: " + (doneParsingVpstTime - startLoadingVpstTime))
//    }
//
//    Ok((valeCodeMap, vpstCodeMap, parsedsMap, doneParsingVpstTime))
//  }

  def build(opts: Options):
  Result[Option[ProgramH], String] = {
    val startTime = java.lang.System.currentTimeMillis()

    val compilation =
      new FullCompilation(
        "" :: opts.modulesToBuild,
        Builtins.getCodeMap().or(nsCoord => resolveNamespaceContents(opts.inputs, nsCoord)),
        FullCompilationOptions(
          if (opts.verbose) {
            (x => {
              println("#: " + x)
            })
          } else {
            x => Unit // do nothing with it
          },
          opts.verbose,
          new NullProfiler(),
          false
        )
      )

    val startLoadAndParseTime = java.lang.System.currentTimeMillis()

    val valeCodeMap = compilation.getCodeMap()
    val parseds = compilation.getParseds()

    if (opts.outputVPST) {
      parseds.map({ case (FileCoordinate(_, _, filepath), (programP, commentRanges)) =>
        val von = ParserVonifier.vonifyFile(programP)
        val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
        val parts = filepath.split("[/\\\\]")
        val vpstFilepath = opts.outputDirPath.get + "/" + parts.last.replaceAll("\\.vale", ".vpst")
        writeFile(vpstFilepath, vpstJson)
      })
    }

    val startScoutTime = java.lang.System.currentTimeMillis()
    if (opts.benchmark) {
      println("Loading and parsing duration: " + (startScoutTime - startLoadAndParseTime))
    }

    if (opts.outputVAST) {
      compilation.getScoutput() match {
        case Err(e) => return Err(ScoutErrorHumanizer.humanize(valeCodeMap, e))
        case Ok(p) => p
      }

      val startAstronomerTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Scout phase duration: " + (startAstronomerTime - startScoutTime))
      }

      compilation.getAstrouts() match {
        case Err(error) => return Err(AstronomerErrorHumanizer.humanize(valeCodeMap, error))
        case Ok(result) => result
      }

      val startTemplarTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Astronomer phase duration: " + (startTemplarTime - startAstronomerTime))
      }

      compilation.getTemputs() match {
        case Err(error) => return Err(TemplarErrorHumanizer.humanize(opts.verbose, valeCodeMap, error))
        case Ok(x) => x
      }

      val startHammerTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Templar phase duration: " + (startHammerTime - startTemplarTime))
      }

      val programH = compilation.getHamuts()

      val finishTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Hammer phase duration: " + (finishTime - startHammerTime))
      }

      val outputVastFilepath = opts.outputDirPath.get + "/build.vast"
      val json = jsonifyProgram(programH)
      writeFile(outputVastFilepath, json)
      println("Wrote VAST to file " + outputVastFilepath)

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
          System.err.println("#: " + error)
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
      val opts = parseOpts(Options(List.empty, List.empty, None, false, true, true, false, true, None, false), args.toList)
      vcheck(opts.mode.nonEmpty, "No mode!", InputException)
      vcheck(opts.inputs.nonEmpty, "No input files!", InputException)

      opts.mode.get match {
        case "highlight" => {
          vcheck(opts.inputs.size == 1, "Must have exactly 1 input file for highlighting", InputException)
          val List(inputFilePath) = opts.inputs

          val compilation =
            new FullCompilation
(
              opts.modulesToBuild,
              Builtins.getCodeMap().or(nsCoord => resolveNamespaceContents(opts.inputs, nsCoord)),
              FullCompilationOptions(
                if (opts.verbose) {
                  (x => {
                    println("##: " + x)
                  })
                } else {
                  x => Unit // do nothing with it
                },
                opts.verbose,
                new NullProfiler(),
                false))

          val valeCodeMap = compilation.getCodeMap()
          val vpstCodeMap = compilation.getVpstMap()

          val code =
            valeCodeMap.moduleToNamespacesToFilenameToContents.values.flatMap(_.values.flatMap(_.values)).toList match {
              case List.empty => throw InputException("No vale code given to highlight!")
              case List(x) => x
              case _ => throw InputException("No vale code given to highlight!")
            }
          val List(vpst) = vpstCodeMap.moduleToNamespacesToFilenameToContents.values.flatMap(_.values.flatMap(_.values)).toList

          compilation.getParseds().map({ case (FileCoordinate(module, namespaces, filepath), (parsed, commentRanges)) =>
            val span = Spanner.forProgram(parsed)
            val highlights = Highlighter.toHTML(code, span, commentRanges)
            if (opts.outputDirPath == Some("")) {
              println(highlights)
            } else {
              val outputFilepath = filepath.replaceAll("\\.vale", ".html")
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
