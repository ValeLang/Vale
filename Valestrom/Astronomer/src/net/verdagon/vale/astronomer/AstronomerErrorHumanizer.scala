package net.verdagon.vale.astronomer

import net.verdagon.vale.FileCoordinateMap
import net.verdagon.vale.SourceCodeUtils.{humanizePos, lineContaining}

object AstronomerErrorHumanizer {
  def humanize(
      filenamesAndSources: FileCoordinateMap[String],
      err: ICompileErrorA):
  String = {
    err match {
      case RangedInternalErrorA(range, message) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) + message
      }
      case CouldntFindTypeA(range, name) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) + ": Couldn't find type `" + name + "`:\n" + lineContaining(filenamesAndSources, range.file, range.begin.offset) + "\n"
      }
      case WrongNumArgsForTemplateA(range, expectedNumArgs, actualNumArgs) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) + ": Expected " + expectedNumArgs + " template args but received " + actualNumArgs + "\n"
      }
    }
  }
}
