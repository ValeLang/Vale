package net.verdagon.vale.scout.rules

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.RuleStateBox
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.{vassert, vassertSome}

object RuleScout {
  // Returns:
  // - new rules produced on the side while translating the given rules
  // - the translated versions of the given rules
  def translateRulexes(
    ruleState: RuleStateBox,
    userDeclaredRunes: Set[IRuneS],
    rulesP: List[IRulexPR]):
  List[IRulexSR] = {
    rulesP.map(translateRulex(ruleState, userDeclaredRunes, _))
  }
  def translateRulex(
    ruleState: RuleStateBox,
    userDeclaredRunes: Set[IRuneS],
    rulex: IRulexPR):
  IRulexSR = {
    rulex match {
//      case PackPR(elements) => {
//        PackSR(translateRulexes(ruleState, userDeclaredRunes, elements))
//      }
      case EqualsPR(leftP, rightP) => {
        EqualsSR(translateRulex(ruleState, userDeclaredRunes, leftP), translateRulex(ruleState, userDeclaredRunes, rightP))
      }
      case OrPR(possibilitiesP) => {
        OrSR(translateRulexes(ruleState, userDeclaredRunes, possibilitiesP))
      }
      case ComponentsPR(TypedPR(None, tyype), componentsP) => {
        val rune = ruleState.newImplicitRune()
        ComponentsSR(TypedSR(rune, translateType(tyype)), translateRulexes(ruleState, userDeclaredRunes, componentsP))
      }
      case ComponentsPR(TypedPR(Some(StringP(_, rune)), tyype), componentsP) => {
        ComponentsSR(
          TypedSR(
            CodeRuneS(rune),
            translateType(tyype)),
          translateRulexes(ruleState, userDeclaredRunes, componentsP))
      }
      case TypedPR(None, tyype) => {
        val rune = ruleState.newImplicitRune()
        TypedSR(rune, translateType(tyype))
      }
      case TypedPR(Some(StringP(_, runeName)), tyype) => {
        vassert(userDeclaredRunes.contains(CodeRuneS(runeName)))
        TypedSR(CodeRuneS(runeName), translateType(tyype))
      }
      case TemplexPR(templex) => TemplexSR(translateTemplex(ruleState, userDeclaredRunes, templex))
      case CallPR(StringP(_, name), args) => CallSR(name, args.map(translateRulex(ruleState, userDeclaredRunes, _)))
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
      case NameOrRunePRT(StringP(_, name)) => {
        if (userDeclaredRunes.contains(CodeRuneS(name))) {
          RuneST(CodeRuneS(name))
        } else {
          NameST(CodeTypeNameS(name))
        }
      }
      case AnonymousRunePRT() => {
        val rune = ruleState.newImplicitRune()
        RuneST(rune)
      }
      case CallPRT(template, args) => CallST(translateTemplex(ruleState, userDeclaredRunes, template), args.map(translateTemplex(ruleState, userDeclaredRunes, _)))
      case FunctionPRT(mutability, paramsPack, returnType) => {
        CallST(
          NameST(CodeTypeNameS("IFunction")),
          List(
            mutability match { case None => MutabilityST(MutableP) case Some(m) => translateTemplex(ruleState, userDeclaredRunes, m) },
            translateTemplex(ruleState, userDeclaredRunes, paramsPack),
            translateTemplex(ruleState, userDeclaredRunes, returnType)))
      }
      case PrototypePRT(StringP(_, name), parameters, returnType) => PrototypeST(name, parameters.map(translateTemplex(ruleState, userDeclaredRunes, _)), translateTemplex(ruleState, userDeclaredRunes, returnType))
      case PackPRT(members) => PackST(members.map(translateTemplex(ruleState, userDeclaredRunes, _)))
      case BorrowPRT(inner) => BorrowST(translateTemplex(ruleState, userDeclaredRunes, inner))
      case RepeaterSequencePRT(mutability, size, element) => RepeaterSequenceST(translateTemplex(ruleState, userDeclaredRunes, mutability), translateTemplex(ruleState, userDeclaredRunes, size), translateTemplex(ruleState, userDeclaredRunes, element))
      case ManualSequencePRT(elements) => ManualSequenceST(elements.map(translateTemplex(ruleState, userDeclaredRunes, _)))
    }
  }
}
