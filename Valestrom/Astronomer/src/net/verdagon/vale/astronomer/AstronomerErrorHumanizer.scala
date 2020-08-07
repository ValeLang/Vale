package net.verdagon.vale.astronomer

import net.verdagon.vale.SourceCodeUtils.{lineAndCol, lineContaining}

object AstronomerErrorHumanizer {
  def humanize(sources: List[String], err: ICompileErrorA): String = {
    err match {
      case CouldntFindType(range, name) => {
        lineAndCol(sources(range.file), range.begin.offset) + ": Couldn't find type `" + name + "`:\n" + lineContaining(sources(range.file), range.end.offset) + "\n"
      }
    }
  }
}
