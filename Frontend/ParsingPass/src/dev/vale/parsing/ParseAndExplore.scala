package dev.vale.parsing

import dev.vale.lexing.{FailedParse, IDenizenL, ImportL, LexAndExplore, RangeL, TopLevelFunctionL, TopLevelStructL}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.{FileP, IDenizenP, TopLevelFunctionP, TopLevelStructP}
import dev.vale.von.{JsonSyntax, VonPrinter}
import dev.vale._

import scala.collection.immutable.Map

object ParseAndExplore {
  // This is a helper function that one doesn't need to use, but it can be handy and also
  // serves as a great example on how to use the parseAndExplore() method.
  def parseAndExploreAndCollect(
    interner: Interner,
    opts: GlobalOptions,
    parser: Parser,
    packages: Array[PackageCoordinate],
    resolver: IPackageResolver[Map[String, String]]):
  Result[Accumulator[(String, FileP)], FailedParse] = {
    parseAndExplore[IDenizenP, (String, FileP)](
      interner, opts, parser, packages, resolver,
      (file, code, imports, denizen) => denizen,
      (file, code, commentRanges, denizens) => {
        (code, FileP(file, commentRanges.buildArray(), denizens.buildArray()))
      })
  }

  def parseAndExplore[D, F](
    interner: Interner,
    opts: GlobalOptions,
    parser: Parser,
    packages: Array[PackageCoordinate],
    resolver: IPackageResolver[Map[String, String]],
    handleParsedDenizen: (FileCoordinate, String, Array[ImportL], IDenizenP) => D,
    fileHandler: (FileCoordinate, String, Accumulator[RangeL], Accumulator[D]) => F
  ): Result[Accumulator[F], FailedParse] = {
    LexAndExplore.lexAndExplore[D, F](
      interner,
      packages,
      resolver,
      (fileCoord: FileCoordinate, code: String, imports: Array[ImportL], denizenL: IDenizenL) => {
        val denizenP: IDenizenP =
          denizenL match {
            case TopLevelFunctionL(functionL) => {
              TopLevelFunctionP(
                parser.parseFunction(functionL) match {
                  case Err(e) => return Err(FailedParse(code, fileCoord, e))
                  case Ok(x) => x
                })
            }
            case TopLevelStructL(structL) => {
              TopLevelStructP(
                parser.parseStruct(structL) match {
                  case Err(e) => return Err(FailedParse(code, fileCoord, e))
                  case Ok(x) => x
                })
            }
          }
        handleParsedDenizen(fileCoord, code, imports, denizenP)
      },
      (fileCoord: FileCoordinate, code: String, commentsRanges: Accumulator[RangeL], denizensAcc: Accumulator[D]) => {
        fileHandler(fileCoord, code, commentsRanges, denizensAcc)
      })
  }
}
