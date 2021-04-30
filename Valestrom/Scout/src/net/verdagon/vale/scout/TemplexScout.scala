package net.verdagon.vale.scout

import net.verdagon.vale.parser._

object TemplexScout {
  def translateMaybeTemplex(env: IEnvironment, maybeTemplexP: Option[ITemplexPT]): Option[ITemplexS] = {
    maybeTemplexP match {
      case None => None
      case Some(t) => Some(translateTemplex(env, t))
    }
  }

  def translateTemplex(env: IEnvironment, templexP: ITemplexPT): ITemplexS = {
    val evalRange = (range: Range) => Scout.evalRange(env.file, range)

    templexP match {
      case NameOrRunePT(StringP(range, nameOrRune)) => {
        if (env.allUserDeclaredRunes().contains(CodeRuneS(nameOrRune))) {
          RuneST(evalRange(range), CodeRuneS(nameOrRune))
        } else {
          NameST(Scout.evalRange(env.file, range), CodeTypeNameS(nameOrRune))
        }
      }
      case InlinePT(range, inner) => {
        // Ignore the inl, not supported yet.
        translateTemplex(env, inner)
      }
      case ManualSequencePT(range,members) => ManualSequenceST(evalRange(range), members.map(translateTemplex(env, _)))
      case IntPT(range,num) => IntST(evalRange(range), num)
      case MutabilityPT(range,mutability) => MutabilityST(evalRange(range), mutability)
      case CallPT(range,template, args) => CallST(evalRange(range), translateTemplex(env, template), args.map(arg => translateTemplex(env, arg)))
      case NullablePT(range,inner) => NullableST(evalRange(range), translateTemplex(env, inner))
      case InterpretedPT(range,ownership,permission,inner) => InterpretedST(evalRange(range), ownership, permission, translateTemplex(env, inner))
      case RepeaterSequencePT(range,mutability, size, element) => RepeaterSequenceST(evalRange(range), translateTemplex(env, mutability), translateTemplex(env, size), translateTemplex(env, element))
    }
  }
}
