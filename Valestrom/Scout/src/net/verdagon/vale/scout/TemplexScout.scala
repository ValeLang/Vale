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
    templexP match {
      case NameOrRunePT(StringP(range, nameOrRune)) => {
        if (env.allUserDeclaredRunes().contains(CodeRuneS(nameOrRune))) {
          RuneST(CodeRuneS(nameOrRune))
        } else {
          NameST(Scout.evalRange(env.file, range), CodeTypeNameS(nameOrRune))
        }
      }
      case IntPT(_, num) => IntST(num)
      case MutabilityPT(mutability) => MutabilityST(mutability)
      case CallPT(_,template, args) => CallST(translateTemplex(env, template), args.map(arg => translateTemplex(env, arg)))
      case NullablePT(inner) => NullableST(translateTemplex(env, inner))
      case OwnershippedPT(_,ownership,inner) => OwnershippedST(ownership, translateTemplex(env, inner))
      case RepeaterSequencePT(_,mutability, size, element) => RepeaterSequenceST(translateTemplex(env, mutability), translateTemplex(env, size), translateTemplex(env, element))
    }
  }
}
