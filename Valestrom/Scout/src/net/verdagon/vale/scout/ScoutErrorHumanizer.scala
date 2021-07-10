package net.verdagon.vale.scout

import net.verdagon.vale.FileCoordinateMap
import net.verdagon.vale.SourceCodeUtils.{humanizePos, lineContaining, nextThingAndRestOfLine}

object ScoutErrorHumanizer {
  def humanize(
    codeMap: FileCoordinateMap[String],
    err: ICompileErrorS):
  String = {
    val errorStrBody =
      (err match {
        case RangedInternalErrorS(range, message) => " " + message
        case CouldntFindVarToMutateS(range, name) => s": No variable named ${name}. Try declaring it above, like `${name} = 42;`\n"
        case CantOwnershipInterfaceInImpl(range) => s": Can only impl a plain interface, remove symbol."
        case CantOwnershipStructInImpl(range) => s": Only a plain struct/interface can be in an impl, remove symbol."
        case CantOverrideOwnershipped(range) => s": Can only impl a plain interface, remove symbol."
        case VariableNameAlreadyExists(range, name) => s": Local named " + humanizeVarName(name) + " already exists!\n(If you meant to modify the variable, use the `set` keyword beforehand.)"
        case InterfaceMethodNeedsSelf(range) => s": Interface's method needs a virtual param of interface's type!"
        case ForgotSetKeywordError(range) => s": Changing a struct's member must start with the `set` keyword."
        case CantUseThatLocalName(range, name) => s": Can't use the name ${name} for a local."
        case ExternHasBody(range) => s": Extern function can't have a body too."
        case CantInitializeIndividualElementsOfRuntimeSizedArray(range) => s": Can't initialize individual elements of a runtime-sized array."
        case InitializingRuntimeSizedArrayRequiresSizeAndCallable(range) => s": Initializing a runtime-sized array requires two arguments: a size, and a function that will populate the elements."
        case InitializingStaticSizedArrayRequiresSizeAndCallable(range) => s": Initializing a statically-sized array requires one argument: a function that will populate the elements."
        case InitializingStaticSizedArrayFromCallableNeedsSizeTemplex(range) => s": Initializing a statically-sized array requires a size in-between the square brackets."
      })

    val posStr = humanizePos(codeMap, err.range.file, err.range.begin.offset)
    val nextStuff = lineContaining(codeMap, err.range.file, err.range.begin.offset)
    val errorId = "S"
    f"${posStr} error ${errorId}: ${errorStrBody}\n${nextStuff}\n"
  }

  def humanizeVarName(varName: IVarNameS): String = {
    varName match {
//      case UnnamedLocalNameS(codeLocation) => "(unnamed)"
      case ClosureParamNameS() => "(closure)"
      case MagicParamNameS(codeLocation) => "(magic)"
      case CodeVarNameS(name) => name
      case ConstructingMemberNameS(name) => "member " + name
    }
  }
}
