package net.verdagon.vale.scout.rules

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.RuleStateBox
import net.verdagon.vale.scout.{IEnvironment, Environment => _, FunctionEnvironment => _, _}
import net.verdagon.vale.{vassert, vassertSome}

object RuleScout {
  // Returns:
  // - new rules produced on the side while translating the given rules
  // - the translated versions of the given rules
  def translateRulexes(
    env: IEnvironment,
    ruleState: RuleStateBox,
    userDeclaredRunes: Set[IRuneS],
    rulesP: List[IRulexPR]):
  List[IRulexSR] = {
    rulesP.map(translateRulex(env, ruleState, userDeclaredRunes, _))
  }
  def translateRulex(
    env: IEnvironment,
    ruleState: RuleStateBox,
    userDeclaredRunes: Set[IRuneS],
    rulex: IRulexPR):
  IRulexSR = {
    rulex match {
//      case PackPR(elements) => {
//        PackSR(translateRulexes(env, ruleState, userDeclaredRunes, elements))
//      }
      case EqualsPR(leftP, rightP) => {
        EqualsSR(translateRulex(env, ruleState, userDeclaredRunes, leftP), translateRulex(env, ruleState, userDeclaredRunes, rightP))
      }
      case OrPR(possibilitiesP) => {
        OrSR(translateRulexes(env, ruleState, userDeclaredRunes, possibilitiesP))
      }
      case ComponentsPR(TypedPR(None, tyype), componentsP) => {
        val rune = ruleState.newImplicitRune()
        ComponentsSR(TypedSR(rune, translateType(tyype)), translateRulexes(env, ruleState, userDeclaredRunes, componentsP))
      }
      case ComponentsPR(TypedPR(Some(StringP(_, rune)), tyype), componentsP) => {
        ComponentsSR(
          TypedSR(
            CodeRuneS(rune),
            translateType(tyype)),
          translateRulexes(env, ruleState, userDeclaredRunes, componentsP))
      }
      case TypedPR(None, tyype) => {
        val rune = ruleState.newImplicitRune()
        TypedSR(rune, translateType(tyype))
      }
      case TypedPR(Some(StringP(_, runeName)), tyype) => {
        vassert(userDeclaredRunes.contains(CodeRuneS(runeName)))
        TypedSR(CodeRuneS(runeName), translateType(tyype))
      }
      case TemplexPR(templex) => TemplexSR(translateTemplex(env, ruleState, userDeclaredRunes, templex))
      case CallPR(StringP(_, name), args) => CallSR(name, args.map(translateRulex(env, ruleState, userDeclaredRunes, _)))
    }
  }
  def translateType(tyype: ITypePR): ITypeSR = {
    tyype match {
      case PrototypeTypePR => PrototypeTypeSR
      case IntTypePR => IntTypeSR
      case BoolTypePR => BoolTypeSR
      case OwnershipTypePR => OwnershipTypeSR
      case MutabilityTypePR => MutabilityTypeSR
      case PermissionTypePR => PermissionTypeSR
      case LocationTypePR => LocationTypeSR
      case CoordTypePR => CoordTypeSR
      case KindTypePR => KindTypeSR
//      case StructTypePR => KindTypeSR
//      case SequenceTypePR => KindTypeSR
//      case ArrayTypePR => KindTypeSR
//      case CallableTypePR => KindTypeSR
//      case InterfaceTypePR => KindTypeSR
    }
  }

  def translateTemplex(
    env: IEnvironment,
    ruleState: RuleStateBox,
    userDeclaredRunes: Set[IRuneS],
    templex: ITemplexPRT):
  ITemplexS = {
    templex match {
      case StringPRT(StringP(_, value)) => StringST(value)
      case IntPRT(value) => IntST(value)
      case MutabilityPRT(mutability) => MutabilityST(mutability)
      case PermissionPRT(permission) => PermissionST(permission)
      case LocationPRT(location) => LocationST(location)
      case OwnershipPRT(ownership) => OwnershipST(ownership)
      case VariabilityPRT(variability) => VariabilityST(variability)
      case BoolPRT(value) => BoolST(value)
      case NameOrRunePRT(StringP(range, name)) => {
        if (userDeclaredRunes.contains(CodeRuneS(name))) {
          RuneST(CodeRuneS(name))
        } else {
          NameST(Scout.evalRange(env.file, range), CodeTypeNameS(name))
        }
      }
      case AnonymousRunePRT() => {
        val rune = ruleState.newImplicitRune()
        RuneST(rune)
      }
      case CallPRT(template, args) => CallST(translateTemplex(env, ruleState, userDeclaredRunes, template), args.map(translateTemplex(env, ruleState, userDeclaredRunes, _)))
      case FunctionPRT(range, mutability, paramsPack, returnType) => {
        CallST(
          NameST(Scout.evalRange(env.file, range), CodeTypeNameS("IFunction")),
          List(
            mutability match { case None => MutabilityST(MutableP) case Some(m) => translateTemplex(env, ruleState, userDeclaredRunes, m) },
            translateTemplex(env, ruleState, userDeclaredRunes, paramsPack),
            translateTemplex(env, ruleState, userDeclaredRunes, returnType)))
      }
      case PrototypePRT(StringP(_, name), parameters, returnType) => PrototypeST(name, parameters.map(translateTemplex(env, ruleState, userDeclaredRunes, _)), translateTemplex(env, ruleState, userDeclaredRunes, returnType))
      case PackPRT(members) => PackST(members.map(translateTemplex(env, ruleState, userDeclaredRunes, _)))
      case BorrowPRT(inner) => BorrowST(translateTemplex(env, ruleState, userDeclaredRunes, inner))
      case RepeaterSequencePRT(mutability, size, element) => RepeaterSequenceST(translateTemplex(env, ruleState, userDeclaredRunes, mutability), translateTemplex(env, ruleState, userDeclaredRunes, size), translateTemplex(env, ruleState, userDeclaredRunes, element))
      case ManualSequencePRT(elements) => ManualSequenceST(elements.map(translateTemplex(env, ruleState, userDeclaredRunes, _)))
    }
  }
}
