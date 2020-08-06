package net.verdagon.vale.astronomer

import net.verdagon.vale.SourceCodeUtils.{lineAndCol, lineContaining}

object AstronomerErrorHumanizer {
  def humanize(text: String, err: ICompileErrorA): String = {
    err match {
      case CouldntFindType(range, name) => lineAndCol(text, range.begin.offset) + ": Couldn't find type `" + name + "`:\n" + lineContaining(text, range.end.offset) + "\n"
    }
  }
}
