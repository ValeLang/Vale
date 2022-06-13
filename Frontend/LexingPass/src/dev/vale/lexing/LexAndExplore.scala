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
    packages: Array[PackageCoordinate],
    resolver: IPackageResolver[Map[String, String]]):
  Result[
    (
      Accumulator[(FileCoordinate, String, Array[ImportL], IDenizenL)],
      Accumulator[(FileCoordinate, String, Array[RangeL], Array[IDenizenL])]),
  FailedParse] = {
    val denizens = new Accumulator[(FileCoordinate, String, Array[ImportL], IDenizenL)]()
    val files = new Accumulator[(FileCoordinate, String, Array[RangeL], Array[IDenizenL])]()

    lexAndExplore[IDenizenL, Unit](
      interner, packages, resolver,
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
    packages: Array[PackageCoordinate],
    resolver: IPackageResolver[Map[String, String]],
    denizenHandler: (FileCoordinate, String, Array[ImportL], IDenizenL) => D,
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

        val filepathsAndContents =
          resolver.resolve(neededPackageCoord) match {
            case None => {
              throw InputException("Couldn't find: " + neededPackageCoord)
            }
            case Some(filepathToCode) => {
              filepathToCode.map({ case (filepath, code) =>
                vassert(interner != null)
                val fileCoord = interner.intern(FileCoordinate(neededPackageCoord, filepath))
                vassert(!alreadyFoundFileToCode.fileCoordToContents.contains(fileCoord))
                fileCoord -> code
              })
            }
          }
        alreadyFoundFileToCode.putPackage(interner, neededPackageCoord, filepathsAndContents)

        filepathsAndContents.foreach({ case (fileCoord, code) =>
          val resultAcc = new Accumulator[D]()

          val iter = new LexingIterator(code, 0)
          val lexer = new Lexer(interner)

          iter.consumeCommentsAndWhitespace()

          var maybeImportsAccum: Option[Accumulator[ImportL]] = Some(new Accumulator[ImportL]())
          var maybeImports: Option[Array[ImportL]] = None

          // Imports must come first, so that we can ship these denizens off with all
          // their relevant imports.

          while (!iter.atEnd()) {
            val denizen =
              lexer.lexDenizen(iter) match {
                case Err(e) => vfail(e)
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
                interner.intern(PackageCoordinate(moduleName.str, packageSteps.map(_.str).toVector))
                if (!startedPackages.contains(nextNeededPackageCoord)) {
                  unexploredPackages.add(nextNeededPackageCoord)
                }
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

          val commentsRanges = iter.comments
          val file = fileHandler(fileCoord, code, commentsRanges, resultAcc)
          filesAcc.add(file)
        })
      }

      Ok(filesAcc)
    })
  }
}
