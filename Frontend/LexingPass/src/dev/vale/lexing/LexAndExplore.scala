package dev.vale.lexing

import dev.vale.options.GlobalOptions
import dev.vale._

import scala.collection.immutable.Map
import scala.collection.mutable

object LexAndExplore {
  // This is a helper function that one doesn't need to use, but it can be handy and also
  // serves as a great example on how to use the lexAndExplore() method.
  def lexAndExploreAndCollect[D, F](
    interner: Interner,
    keywords: Keywords,
    packages: Vector[PackageCoordinate],
    resolver: IPackageResolver[Map[String, String]]):
  Result[
    (
      Accumulator[(FileCoordinate, String, Vector[ImportL], IDenizenL)],
      Accumulator[(FileCoordinate, String, Vector[RangeL], Vector[IDenizenL])]),
  FailedParse] = {
    val denizens = new Accumulator[(FileCoordinate, String, Vector[ImportL], IDenizenL)]()
    val files = new Accumulator[(FileCoordinate, String, Vector[RangeL], Vector[IDenizenL])]()

    lexAndExplore[IDenizenL, Unit](
      interner, keywords, packages, resolver,
      (file, code, imports, denizen) => {
        denizens.add((file, code, imports, denizen))
        denizen
      },
      (file, code, ranges, denizens) => {
        files.add((file, code, ranges.buildArray(), denizens.buildArray()))
        Unit
      }) match {
      case Err(e) => return Err(e)
      case Ok(_) =>
    }

    Ok((denizens, files))
  }

  // It would be pretty cool to turn this into an iterator of some sort
  def lexAndExplore[D, F](
    interner: Interner,
    keywords: Keywords,
    packages: Vector[PackageCoordinate],
    resolver: IPackageResolver[Map[String, String]],
    denizenHandler: (FileCoordinate, String, Vector[ImportL], IDenizenL) => D,
    fileHandler: (FileCoordinate, String, Accumulator[RangeL], Accumulator[D]) => F):
  Result[Accumulator[F], FailedParse] = {
    Profiler.frame(() => {
      val unexploredPackages = mutable.HashSet[PackageCoordinate](packages: _*)
      val startedPackages = mutable.HashSet[PackageCoordinate]()
      val alreadyFoundFileToCode = new FileCoordinateMap[String]()

      val filesAcc = new Accumulator[F]()

      while (unexploredPackages.nonEmpty) {
        val neededPackageCoord = unexploredPackages.head
        unexploredPackages.remove(neededPackageCoord)
        startedPackages.add(neededPackageCoord)

//        println(s"Processing ${neededPackageCoord}")

        val filepathsAndContents =
          resolver.resolve(neededPackageCoord) match {
            case None => {
              throw InputException("Couldn't find: " + neededPackageCoord)
            }
            case Some(filepathToCode) => {
              U.map[(String, String), (FileCoordinate, String)](filepathToCode.toVector, { case (filepath, code) =>
                vassert(interner != null)
//                println(s"Found ${neededPackageCoord} file ${filepath}")
                val fileCoord = interner.intern(FileCoordinate(neededPackageCoord, filepath))
                vassert(!alreadyFoundFileToCode.fileCoordToContents.contains(fileCoord))
                fileCoord -> code
              })
            }
          }
        alreadyFoundFileToCode.putPackage(interner, neededPackageCoord, filepathsAndContents.toMap)

        U.foreach[(FileCoordinate, String)](filepathsAndContents, { case (fileCoord, code) =>
          val resultAcc = new Accumulator[D]()

          val iter = new LexingIterator(code, 0)
          val lexer = new Lexer(interner, keywords)

          iter.consumeCommentsAndWhitespace()

          var maybeImportsAccum: Option[Accumulator[ImportL]] = Some(new Accumulator[ImportL]())
          var maybeImports: Option[Vector[ImportL]] = None

          // Imports must come first, so that we can ship these denizens off with all
          // their relevant imports.

          while (!iter.atEnd()) {
            val denizen =
              lexer.lexDenizen(iter) match {
                case Err(e) => return Err(FailedParse(code, fileCoord, e))
                case Ok(x) => x
              }
            iter.consumeCommentsAndWhitespace()

            denizen match {
              case TopLevelImportL(im@ImportL(range, moduleName, packageSteps, importeeName)) => {
                maybeImportsAccum match {
                  case None => vfail("Imports must come before everything else")
                  case Some(importsAccum) => importsAccum.add(im)
                }

                // This is where we could fire off another thread to do any parsing in parallel,
                // because we're still only partway through the lexing.
                val nextNeededPackageCoord =
                  interner.intern(PackageCoordinate(moduleName.str, U.map[WordLE, StrI](packageSteps, x => x.str).toVector))
//                println(s"Want to import ${nextNeededPackageCoord}")
                if (!startedPackages.contains(nextNeededPackageCoord)) {
//                  println(s"Unseen, so adding.")
                  unexploredPackages.add(nextNeededPackageCoord)
                }
                val denizenResult = denizenHandler(fileCoord, code, Vector(), denizen)
                resultAcc.add(denizenResult)
              }
              case _ => {
                maybeImportsAccum match {
                  case None =>
                  case Some(importsAccum) => {
                    maybeImports = Some(importsAccum.buildArray())
                    maybeImportsAccum = None
                  }
                }
                val denizenResult = denizenHandler(fileCoord, code, vassertSome(maybeImports), denizen)
                resultAcc.add(denizenResult)
              }
            }
          }

          unexploredPackages ++=
            U.map[ImportL, PackageCoordinate](maybeImports.toVector.flatten, x => {
              interner.intern(PackageCoordinate(x.moduleName.str, U.map[WordLE, StrI](x.packageSteps, _.str).toVector))
            }).toSet -- startedPackages

          val commentsRanges = iter.comments
          val file = fileHandler(fileCoord, code, commentsRanges, resultAcc)
          filesAcc.add(file)
        })
      }

      Ok(filesAcc)
    })
  }
}
