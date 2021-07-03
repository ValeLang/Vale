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
    val evalRange = (range: Range) => Scout.evalRange(env.file, range)

    rulex match {
//      case PackPR(elements) => {
//        PackSR(translateRulexes(env, ruleState, userDeclaredRunes, elements))
//      }
      case EqualsPR(range, leftP, rightP) => {
        EqualsSR(evalRange(range), translateRulex(env, ruleState, userDeclaredRunes, leftP), translateRulex(env, ruleState, userDeclaredRunes, rightP))
      }
      case OrPR(range, possibilitiesP) => {
        OrSR(evalRange(range), translateRulexes(env, ruleState, userDeclaredRunes, possibilitiesP))
      }
      case ComponentsPR(range, TypedPR(typeRange, None, tyype), componentsP) => {
        val rune = ruleState.newImplicitRune()
        ComponentsSR(
          Scout.evalRange(env.file, range),
          TypedSR(evalRange(typeRange), rune, translateType(tyype)),
          translateRulexes(env, ruleState, userDeclaredRunes, componentsP))
      }
      case ComponentsPR(range, TypedPR(typeRange, Some(NameP(_, rune)), tyype), componentsP) => {
        ComponentsSR(
          Scout.evalRange(env.file, range),
          TypedSR(
            Scout.evalRange(env.file, typeRange),
            CodeRuneS(rune),
            translateType(tyype)),
          translateRulexes(env, ruleState, userDeclaredRunes, componentsP))
      }
      case TypedPR(range, None, tyype) => {
        val rune = ruleState.newImplicitRune()
        TypedSR(Scout.evalRange(env.file, range), rune, translateType(tyype))
      }
      case TypedPR(range, Some(NameP(_, runeName)), tyype) => {
        vassert(userDeclaredRunes.contains(CodeRuneS(runeName)))
        TypedSR(evalRange(range), CodeRuneS(runeName), translateType(tyype))
      }
      case TemplexPR(templex) => TemplexSR(translateTemplex(env, ruleState, userDeclaredRunes, templex))
      case CallPR(range, NameP(_, name), args) => CallSR(Scout.evalRange(env.file, range), name, args.map(translateRulex(env, ruleState, userDeclaredRunes, _)))
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
    templex: ITemplexPT):
  ITemplexS = {
    val evalRange = (range: Range) => Scout.evalRange(env.file, range)
    templex match {
      case StringPT(range, value) => StringST(evalRange(range), value)
      case IntPT(range, value) => IntST(evalRange(range), value)
      case MutabilityPT(range, mutability) => MutabilityST(evalRange(range), mutability)
      case PermissionPT(range, permission) => PermissionST(evalRange(range), permission)
      case LocationPT(range, location) => LocationST(evalRange(range), location)
      case OwnershipPT(range, ownership) => OwnershipST(evalRange(range), ownership)
      case VariabilityPT(range, variability) => VariabilityST(evalRange(range), variability)
      case BoolPT(range, value) => BoolST(evalRange(range), value)
      case NameOrRunePT(NameP(range, name)) => {
        if (userDeclaredRunes.contains(CodeRuneS(name))) {
          RuneST(evalRange(range), CodeRuneS(name))
        } else {
          NameST(Scout.evalRange(env.file, range), CodeTypeNameS(name))
        }
      }
      case AnonymousRunePT(range) => {
        val rune = ruleState.newImplicitRune()
        RuneST(evalRange(range), rune)
      }
      case CallPT(range, template, args) => {
        CallST(evalRange(range), translateTemplex(env, ruleState, userDeclaredRunes, template), args.map(translateTemplex(env, ruleState, userDeclaredRunes, _)))
      }
      case FunctionPT(range, mutability, paramsPack, returnType) => {
        CallST(
          evalRange(range),
          NameST(Scout.evalRange(env.file, range), CodeTypeNameS("IFunction")),
          List(
            mutability match { case None => MutabilityST(evalRange(range), MutableP) case Some(m) => translateTemplex(env, ruleState, userDeclaredRunes, m) },
            translateTemplex(env, ruleState, userDeclaredRunes, paramsPack),
            translateTemplex(env, ruleState, userDeclaredRunes, returnType)))
      }
      case PrototypePT(range, NameP(_, name), parameters, returnType) => PrototypeST(evalRange(range), name, parameters.map(translateTemplex(env, ruleState, userDeclaredRunes, _)), translateTemplex(env, ruleState, userDeclaredRunes, returnType))
      case PackPT(range, members) => PackST(evalRange(range), members.map(translateTemplex(env, ruleState, userDeclaredRunes, _)))
      case BorrowPT(range, inner) => BorrowST(evalRange(range), translateTemplex(env, ruleState, userDeclaredRunes, inner))
      case RepeaterSequencePT(range, mutability, variability, size, element) => {
        RepeaterSequenceST(
          evalRange(range),
          translateTemplex(env, ruleState, userDeclaredRunes, mutability),
          translateTemplex(env, ruleState, userDeclaredRunes, variability),
          translateTemplex(env, ruleState, userDeclaredRunes, size),
          translateTemplex(env, ruleState, userDeclaredRunes, element))
      }
      case ManualSequencePT(range, elements) => ManualSequenceST(evalRange(range), elements.map(translateTemplex(env, ruleState, userDeclaredRunes, _)))
    }
  }
}
