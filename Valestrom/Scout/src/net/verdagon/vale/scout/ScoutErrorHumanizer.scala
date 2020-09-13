package net.verdagon.vale.scout

import net.verdagon.vale.SourceCodeUtils.humanizePos
import net.verdagon.vale.SourceCodeUtils.nextThingAndRestOfLine

object ScoutErrorHumanizer {
  def humanize(
    filenamesAndSources: List[(String, String)],
    err: ICompileErrorS):
  String = {
    err match {
      case CouldntFindVarToMutateS(range, name) => humanizePos(filenamesAndSources, range.file, range.begin.offset) + s": No variable named ${name}. Try declaring it above, like `${name} = 42;`\n"
    }
  }
}
