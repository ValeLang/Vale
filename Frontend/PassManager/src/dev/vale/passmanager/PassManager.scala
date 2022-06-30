package dev.vale.passmanager

import dev.vale.highertyping.HigherTypingErrorHumanizer
import dev.vale.simplifying.VonHammer
import dev.vale.highlighter.{Highlighter, Spanner}
import dev.vale.finalast.{PackageH, ProgramH}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.{ParseErrorHumanizer, ParserVonifier}
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.typing.CompilerErrorHumanizer
import dev.vale.testvm.Vivem
import dev.vale.{Builtins, Err, FileCoordinate, Interner, Keywords, Ok, PackageCoordinate, Result, SourceCodeUtils, StrI, passmanager, vassert, vassertOne, vcheck, vcurious, vfail}

import java.io.{BufferedWriter, File, FileNotFoundException, FileOutputStream, FileWriter, OutputStream, PrintStream}
import java.util.InputMismatchException
import dev.vale.highertyping.ProgramA
import dev.vale.simplifying.Hammer
import dev.vale.highlighter.Spanner
import dev.vale.finalast.PackageH
import dev.vale.lexing.{FailedParse, InputException}
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.typing.CompilerErrorHumanizer
import dev.vale.von.{IVonData, JsonSyntax, VonPrinter}

import java.nio.charset.Charset
import scala.io.Source
import scala.util.matching.Regex

object PassManager {
  def DEFAULT_PACKAGE_COORD(interner: Interner, keywords: Keywords) = interner.intern(PackageCoordinate(keywords.my_module, Vector.empty))

  sealed trait IFrontendInput {
    def packageCoord(interner: Interner): PackageCoordinate
  }
  case class ModulePathInput(moduleName: StrI, path: String) extends IFrontendInput {
    val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
    override def packageCoord(interner: Interner): PackageCoordinate = interner.intern(PackageCoordinate(moduleName, Vector.empty))
  }
  case class DirectFilePathInput(packageCoordinate: PackageCoordinate, path: String) extends IFrontendInput {
    val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
    override def packageCoord(interner: Interner): PackageCoordinate = packageCoordinate
  }
  case class SourceInput(
      packageCoordinate: PackageCoordinate,
      // Name isnt guaranteed to be unique, we sometimes hand in strings like "builtins.vale"
      name: String,
      code: String) extends IFrontendInput {
    val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
    override def packageCoord(interner: Interner): PackageCoordinate = packageCoordinate
  }

  case class Options(
    inputs: Vector[IFrontendInput],
//    modulePaths: Map[String, String],
//    packagesToBuild: Vector[PackageCoordinate],
    outputDirPath: Option[String],
    benchmark: Boolean,
    outputVPST: Boolean,
    outputVAST: Boolean,
    outputHighlights: Boolean,
    includeBuiltins: Boolean,
    mode: Option[String], // build v run etc
    sanityCheck: Boolean,
    useOptimizedSolver: Boolean,
    verboseErrors: Boolean,
    debugOutput: Boolean
  ) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

  def parseOpts(interner: Interner, opts: Options, list: List[String]) : Options = {
    list match {
      case Nil => opts
      case "--output_dir" :: value :: tail => {
        vcheck(opts.outputDirPath.isEmpty, "Multiple output files specified!", InputException)
        parseOpts(interner, opts.copy(outputDirPath = Some(value)), tail)
      }
      case "--output_vpst" :: value :: tail => {
        parseOpts(interner, opts.copy(outputVPST = value.toBoolean), tail)
      }
      case "--output_vast" :: value :: tail => {
        parseOpts(interner, opts.copy(outputVAST = value.toBoolean), tail)
      }
      case "--sanity_check" :: value :: tail => {
        parseOpts(interner, opts.copy(sanityCheck = value.toBoolean), tail)
      }
      case "--include_builtins" :: value :: tail => {
        parseOpts(interner, opts.copy(includeBuiltins = value.toBoolean), tail)
      }
      case "--benchmark" :: tail => {
        parseOpts(interner, opts.copy(benchmark = true), tail)
      }
      case "--output_highlights" :: value :: tail => {
        parseOpts(interner, opts.copy(outputHighlights = value.toBoolean), tail)
      }
      case ("-v" | "--verbose") :: tail => {
        parseOpts(interner, opts.copy(verboseErrors = true), tail)
      }
      case ("--debug_output") :: tail => {
        parseOpts(interner, opts.copy(debugOutput = true), tail)
      }
      case value :: _ if value.startsWith("-") => throw InputException("Unknown option " + value)
      case value :: tail => {
        if (opts.mode.isEmpty) {
          parseOpts(interner, opts.copy(mode = Some(value)), tail)
        } else {
          if (value.contains("=")) {
            val packageCoordAndPath = value.split("=")
            vcheck(packageCoordAndPath.size == 2, "Arguments can only have 1 equals. Saw: " + value, InputException)
            vcheck(packageCoordAndPath(0) != "", "Must have a module name before a colon. Saw: " + value, InputException)
            vcheck(packageCoordAndPath(1) != "", "Must have a file path after a colon. Saw: " + value, InputException)
            val Array(packageCoordStr, path) = packageCoordAndPath
            val packageCoordinate =
              if (packageCoordStr.contains(".")) {
                val packageCoordinateParts = packageCoordStr.split("\\.")
                interner.intern(
                  PackageCoordinate(
                    interner.intern(StrI(packageCoordinateParts.head)),
                    packageCoordinateParts.tail.toVector.map(s => interner.intern(StrI(s)))))
              } else {
                interner.intern(
                  PackageCoordinate(
                    interner.intern(StrI(packageCoordStr)), Vector.empty))
              }
            val input =
              if (path.endsWith(".vale") || path.endsWith(".vpst")) {
                DirectFilePathInput(packageCoordinate, path)
              } else {
                if (packageCoordinate.packages.nonEmpty) {
                  throw InputException("Cannot define a directory for a specific package, only for a module.")
                }
                ModulePathInput(packageCoordinate.module, path)
              }
            parseOpts(interner, opts.copy(inputs = opts.inputs :+ input), tail)
          } else {
            throw InputException("Unrecognized input: " + value)
          }
        }
      }
    }
  }

  def resolvePackageContents(
    interner: Interner,
      inputs: Vector[IFrontendInput],
      packageCoord: PackageCoordinate):
  Option[Map[String, String]] = {
    val PackageCoordinate(module, packages) = packageCoord

//    println("resolving " + packageCoord + " with inputs:\n" + inputs)

    val sourceInputs =
      inputs.zipWithIndex.filter(_._1.packageCoord(interner).module == module).flatMap({
        case (SourceInput(_, name, code), index) if (packages == Vector.empty) => {
          // All .vpst and .vale direct inputs are considered part of the root paackage.
          Vector((index + "(" + name + ")" -> code))
        }
        case (mpi @ ModulePathInput(_, modulePath), _) => {
//          println("checking with modulepathinput " + mpi)
          val directoryPath = modulePath + packages.map(File.separator + _).mkString("")
//          println("looking in dir " + directoryPath)
          val directory = new java.io.File(directoryPath)
          val filesInDirectory = directory.listFiles()
          if (filesInDirectory == null) {
            Vector()
          } else {
            val inputFiles =
              filesInDirectory.filter(_.getName.endsWith(".vale")) ++
                filesInDirectory.filter(_.getName.endsWith(".vpst"))
            //          println("found files: " + inputFiles)
            val inputFilePaths = inputFiles.map(_.getPath)
            inputFilePaths.toVector.map(filepath => {
              val bufferedSource = Source.fromFile(filepath)
              val code = bufferedSource.getLines.mkString("\n")
              bufferedSource.close
              (filepath -> code)
            })
          }
        }
        case (DirectFilePathInput(_, path), _) => {
          val file = path
          val bufferedSource = Source.fromFile(file)
          val code = bufferedSource.getLines.mkString("\n")
          bufferedSource.close
          Vector((path -> code))
        }
      })
    val filepathToSource = sourceInputs.groupBy(_._1).mapValues(_.head._2)
    vassert(sourceInputs.size == filepathToSource.size, "Input filepaths overlap!")
    Some(filepathToSource)
  }

  def build(interner: Interner, keywords: Keywords, opts: Options):
  Result[Option[ProgramH], String] = {
    new java.io.File(opts.outputDirPath.get).mkdirs()
    new java.io.File(opts.outputDirPath.get + "/vast").mkdir()
    new java.io.File(opts.outputDirPath.get + "/vpst").mkdir()

    val startTime = java.lang.System.currentTimeMillis()

    val compilation =
      new FullCompilation(
        interner,
        keywords,
        Vector(PackageCoordinate.BUILTIN(interner, keywords)) ++ opts.inputs.map(_.packageCoord(interner)).distinct,
        Builtins.getCodeMap(interner, keywords)
          .or(packageCoord => resolvePackageContents(interner, opts.inputs, packageCoord)),
        passmanager.FullCompilationOptions(
          GlobalOptions(
            sanityCheck = opts.sanityCheck,
            useOptimizedSolver = opts.useOptimizedSolver,
            verboseErrors = opts.verboseErrors,
            debugOutput = opts.debugOutput),
          if (opts.debugOutput) {
            (x => {
              println("#: " + x)
            })
          } else {
            x => Unit // do nothing with it
          }
        )
      )

    val startLoadAndParseTime = java.lang.System.currentTimeMillis()

    val parseds =
      compilation.getParseds() match {
        case Err(FailedParse(code, fileCoord, err)) => {
          vfail(ParseErrorHumanizer.humanize(SourceCodeUtils.humanizeFile(fileCoord), code, err))
        }
        case Ok(p) => p
      }
    val valeCodeMap = compilation.getCodeMap().getOrDie()

    if (opts.outputVPST) {
      parseds.map({ case (FileCoordinate(_, filepath), (programP, commentRanges)) =>
        val von = ParserVonifier.vonifyFile(programP)
        val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
        val parts = filepath.split("[/\\\\]")
        val vpstFilepath = opts.outputDirPath.get + "/vpst/" + parts.last.replaceAll("\\.vale", ".vpst")
        writeFile(vpstFilepath, vpstJson)
      })
    }

    val startScoutTime = java.lang.System.currentTimeMillis()
    if (opts.benchmark) {
      println("Loading and parsing duration: " + (startScoutTime - startLoadAndParseTime))
    }

    if (opts.outputVAST) {
      compilation.getScoutput() match {
        case Err(e) => return Err(PostParserErrorHumanizer.humanize(valeCodeMap, e))
        case Ok(p) => p
      }

      val startHigherTypingTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Scout phase duration: " + (startHigherTypingTime - startScoutTime))
      }

      compilation.getAstrouts() match {
        case Err(error) => return Err(HigherTypingErrorHumanizer.humanize(valeCodeMap, error))
        case Ok(result) => result
      }

      val startTypingPassTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Higher typing phase duration: " + (startTypingPassTime - startHigherTypingTime))
      }

      compilation.getCompilerOutputs() match {
        case Err(error) => return Err(CompilerErrorHumanizer.humanize(opts.verboseErrors, valeCodeMap, error))
        case Ok(x) => x
      }

      val startHammerTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Compiler phase duration: " + (startHammerTime - startTypingPassTime))
      }

      val programH = compilation.getHamuts()

      val finishTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Hammer phase duration: " + (finishTime - startHammerTime))
      }

      programH.packages.flatMap({ case (packageCoord, paackage) =>
        val outputVastFilepath =
          opts.outputDirPath.get + "/vast/" +
          (if (packageCoord.isInternal) {
            "__vale"
          } else {
            packageCoord.module + packageCoord.packages.map("." + _).mkString("")
          }) +
          ".vast"
        val json = jsonifyPackage(compilation.getVonHammer(), packageCoord, paackage)
        writeFile(outputVastFilepath, json)
//        println("Wrote VAST to file " + outputVastFilepath)
      })

      Ok(Some(programH))
    } else {
      Ok(None)
    }
  }

  def jsonifyPackage(vonHammer: VonHammer, packageCoord: PackageCoordinate, packageH: PackageH): String = {
    val programV = vonHammer.vonifyPackage(packageCoord, packageH)
    val json = new VonPrinter(JsonSyntax, 120).print(programV)
    json
  }

  def jsonifyProgram(vonHammer: VonHammer, programH: ProgramH): String = {
    val programV = vonHammer.vonifyProgram(programH)
    val json = new VonPrinter(JsonSyntax, 120).print(programV)
    json
  }

  def buildAndOutput(interner: Interner, keywords: Keywords, opts: Options) = {
      build(interner, keywords, opts) match {
        case Ok(_) => {
        }
        case Err(error) => {
          System.err.println("Error: " + error)
          System.exit(22)
          vfail()
        }
      }
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
      val interner = new Interner()
      val keywords = new Keywords(interner)

      val opts =
        parseOpts(
          interner,
          Options(
            inputs = Vector.empty,
            outputDirPath = None,
            benchmark = false,
            outputVPST = true,
            outputVAST = true,
            outputHighlights = false,
            includeBuiltins = true,
            mode = None,
            sanityCheck = false,
            useOptimizedSolver = true,
            verboseErrors = false,
            debugOutput = false),
          args.toList)
      vcheck(opts.mode.nonEmpty, "No mode!", InputException)
      vcheck(opts.inputs.nonEmpty, "No input files!", InputException)

      opts.mode.get match {
        case "highlight" => {
          vcheck(opts.inputs.size == 1, "Must have exactly 1 input file for highlighting", InputException)
          val Vector(inputFilePath) = opts.inputs

          val compilation =
            new FullCompilation(
              interner,
              keywords,
              opts.inputs.map(_.packageCoord(interner)).distinct,
              Builtins.getCodeMap(interner, keywords).or(packageCoord => resolvePackageContents(interner, opts.inputs, packageCoord)),
              passmanager.FullCompilationOptions(
                GlobalOptions(opts.sanityCheck, opts.useOptimizedSolver, opts.verboseErrors, opts.debugOutput),
                if (opts.verboseErrors) {
                  (x => {
                    println("##: " + x)
                  })
                } else {
                  x => Unit // do nothing with it
                }))

          val parseds =
            compilation.getParseds() match {
              case Err(FailedParse(code, fileCoord, error)) => {
                throw InputException(ParseErrorHumanizer.humanize(SourceCodeUtils.humanizeFile(fileCoord), code, error))
              }
              case Ok(p) => p
            }
          val valeCodeMap = compilation.getCodeMap().getOrDie()
          val vpstCodeMap = compilation.getVpstMap().getOrDie()

          val code =
            valeCodeMap.fileCoordToContents.values.toVector match {
              case Vector() => throw InputException("No vale code given to highlight!")
              case Vector(x) => x
              case _ => throw InputException("Too many files given to highlight!")
            }
          val vpst = vassertOne(vpstCodeMap.fileCoordToContents.values)

          parseds.map({ case (FileCoordinate(_, filepath), (parsed, commentRanges)) =>
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
          buildAndOutput(interner, keywords, opts)
        }
        case "run" => {
          throw InputException("Run command has been disabled.");
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
      val bytes = s.getBytes(Charset.forName("UTF-8"))
      val outputStream = new FileOutputStream(filepath)
      outputStream.write(bytes)
      outputStream.close()
    }
  }
}
