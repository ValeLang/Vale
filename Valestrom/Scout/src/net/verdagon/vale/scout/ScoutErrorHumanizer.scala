package net.verdagon.vale.scout

import net.verdagon.vale.FileCoordinateMap
import net.verdagon.vale.SourceCodeUtils.humanizePos
import net.verdagon.vale.SourceCodeUtils.nextThingAndRestOfLine

object ScoutErrorHumanizer {
  def humanize(
    codeMap: FileCoordinateMap[String],
    err: ICompileErrorS):
  String = {
    err match {
      case RangedInternalErrorS(range, message) => humanizePos(codeMap, range.file, range.begin.offset) + " " + message
      case CouldntFindVarToMutateS(range, name) => humanizePos(codeMap, range.file, range.begin.offset) + s": No variable named ${name}. Try declaring it above, like `${name} = 42;`\n"
      case CantOwnershipInterfaceInImpl(range) => humanizePos(codeMap, range.file, range.begin.offset) + s": Can only impl a plain interface, remove symbol."
      case CantOwnershipStructInImpl(range) => humanizePos(codeMap, range.file, range.begin.offset) + s": Only a plain struct/interface can be in an impl, remove symbol."
      case CantOverrideOwnershipped(range) => humanizePos(codeMap, range.file, range.begin.offset) + s": Can only impl a plain interface, remove symbol."
      case VariableNameAlreadyExists(range, name) => humanizePos(codeMap, range.file, range.begin.offset) + s": Local named " + humanizeVarName(name) + " already exists!\n(If you meant to modify the variable, use the `set` keyword beforehand.)"
      case InterfaceMethodNeedsSelf(range) => humanizePos(codeMap, range.file, range.begin.offset) + s": Interface's method needs a virtual param of interface's type!"
      case ForgotSetKeywordError(range) => humanizePos(codeMap, range.file, range.begin.offset) + s": Changing a struct's member must start with the `set` keyword."
    }
  }

  def humanizeVarName(varName: IVarNameS): String = {
    varName match {
      case UnnamedLocalNameS(codeLocation) => "(unnamed)"
      case ClosureParamNameS() => "(closure)"
      case MagicParamNameS(codeLocation) => "(magic)"
      case CodeVarNameS(name) => name
      case ConstructingMemberNameS(name) => "member " + name
    }
  }
}
