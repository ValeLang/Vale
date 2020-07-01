package net.verdagon.vale.scout

import net.verdagon.vale.parser._

object TemplexScout {
  def translateMaybeTemplex(declaredRunes: Set[IRuneS], maybeTemplexP: Option[ITemplexPT]): Option[ITemplexS] = {
    maybeTemplexP match {
      case None => None
      case Some(t) => Some(translateTemplex(declaredRunes, t))
    }
  }

  def translateTemplex(declaredRunes: Set[IRuneS], templexP: ITemplexPT): ITemplexS = {
    templexP match {
      case NameOrRunePT(StringP(_, nameOrRune)) => {
        if (declaredRunes.contains(CodeRuneS(nameOrRune))) {
          RuneST(CodeRuneS(nameOrRune))
        } else {
          NameST(CodeTypeNameS(nameOrRune))
        }
      }
      case MutabilityPT(mutability) => MutabilityST(mutability)
      case CallPT(_,template, args) => CallST(translateTemplex(declaredRunes, template), args.map(arg => translateTemplex(declaredRunes, arg)))
      case NullablePT(inner) => NullableST(translateTemplex(declaredRunes, inner))
      case OwnershippedPT(_,ownership,inner) => OwnershippedST(ownership, translateTemplex(declaredRunes, inner))
      case RepeaterSequencePT(_,mutability, size, element) => RepeaterSequenceST(translateTemplex(declaredRunes, mutability), translateTemplex(declaredRunes, size), translateTemplex(declaredRunes, element))
    }
  }
}
