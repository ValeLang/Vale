package net.verdagon.vale.astronomer

import net.verdagon.vale.FileCoordinateMap
import net.verdagon.vale.SourceCodeUtils.{humanizePos, lineContaining, nextThingAndRestOfLine}

object AstronomerErrorHumanizer {
  def humanize(
      filenamesAndSources: FileCoordinateMap[String],
      err: ICompileErrorA):
  String = {
    var errorStrBody =
      err match {
        case RangedInternalErrorA(range, message) => {
          ": internal error: " + message
        }
        case CouldntFindTypeA(range, name) => {
          ": Couldn't find type `" + name + "`:\n"
        }
        case CouldntSolveRulesA(range, rules) => {
          ": Couldn't solve generics rules:\n" + lineContaining(filenamesAndSources, range.file, range.begin.offset) + "\n" + rules.toString
        }
        case WrongNumArgsForTemplateA(range, expectedNumArgs, actualNumArgs) => {
          ": Expected " + expectedNumArgs + " template args but received " + actualNumArgs + "\n"
        }
      }

    val posStr = humanizePos(filenamesAndSources, err.range.file, err.range.begin.offset)
    val nextStuff = lineContaining(filenamesAndSources, err.range.file, err.range.begin.offset)
    val errorId = "A"
    f"${posStr} error ${errorId}: ${errorStrBody}\n${nextStuff}\n"
  }
}
