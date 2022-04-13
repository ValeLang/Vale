package dev.vale.parsing

import dev.vale.lexing.{FailedParse, IDenizenL, ImportL, LexAndExplore, RangeL, TopLevelFunctionL}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.{FileP, IDenizenP, TopLevelFunctionP}
import dev.vale.von.{JsonSyntax, VonPrinter}
import dev.vale._

import scala.collection.immutable.Map

object ParseAndExplore {
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
          }
        if (opts.sanityCheck) {
          val von = ParserVonifier.vonifyFile(FileP(Vector(denizenP)))
          val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
          new ParsedLoader(interner).load(vpstJson)
        }
        handleParsedDenizen(fileCoord, code, imports, denizenP)
      },
      (fileCoord: FileCoordinate, code: String, commentsRanges: Accumulator[RangeL], denizensAcc: Accumulator[D]) => {
        fileHandler(fileCoord, code, commentsRanges, denizensAcc)
      })
  }
}
