package net.verdagon.vale.astronomer

import net.verdagon.vale.SourceCodeUtils.{humanizePos, lineContaining}

object AstronomerErrorHumanizer {
  def humanize(
      filenamesAndSources: List[(String, String)],
      err: ICompileErrorA):
  String = {
    err match {
      case CouldntFindType(range, name) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) + ": Couldn't find type `" + name + "`:\n" + lineContaining(filenamesAndSources, range.file, range.begin.offset) + "\n"
      }
    }
  }
}
