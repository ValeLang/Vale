package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{PatternScout, RuleState, RuleStateBox}
import net.verdagon.vale.scout.predictor.Conclusions
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.scout.templatepredictor.PredictorEvaluator
import net.verdagon.vale.vimpl

import scala.util.parsing.input.OffsetPosition

sealed trait IEnvironment {
  def file: Int
  def name: INameS
  def allUserDeclaredRunes(): Set[IRuneS]
}

// Someday we might split this into NamespaceEnvironment and CitizenEnvironment
case class Environment(
    file: Int,
    parentEnv: Option[Environment],
    name: INameS,
    userDeclaredRunes: Set[IRuneS]
) extends IEnvironment {
  override def allUserDeclaredRunes(): Set[IRuneS] = {
    userDeclaredRunes ++ parentEnv.toList.flatMap(pe => pe.allUserDeclaredRunes())
  }
}

case class FunctionEnvironment(
    file: Int,
    name: IFunctionDeclarationNameS,
    parentEnv: Option[IEnvironment],
    userDeclaredRunes: Set[IRuneS],
    // So that when we run into a magic param, we can add this to the number of previous magic
    // params to get the final param index.
    numExplicitParams: Int
) extends IEnvironment {
  override def allUserDeclaredRunes(): Set[IRuneS] = {
    userDeclaredRunes ++ parentEnv.toList.flatMap(_.allUserDeclaredRunes())
  }
}

case class StackFrame(
    file: Int,
    name: IFunctionDeclarationNameS,
    parentEnv: FunctionEnvironment,
    maybeParent: Option[StackFrame],
    locals: VariableDeclarations) {
  def ++(newVars: VariableDeclarations): StackFrame = {
    StackFrame(file, name, parentEnv, maybeParent, locals ++ newVars)
  }
  def allDeclarations: VariableDeclarations = {
    locals ++ maybeParent.map(_.allDeclarations).getOrElse(Scout.noDeclarations)
  }
  def findVariable(name: String): Option[IVarNameS] = {
    locals.find(name) match {
      case Some(fullNameS) => Some(fullNameS)
      case None => {
        maybeParent match {
          case None => None
          case Some(parent) => parent.findVariable(name)
        }
      }
    }
  }
}

object Scout {
  def noVariableUses = VariableUses(List())
  def noDeclarations = VariableDeclarations(List())

//  val unnamedParamNamePrefix = "__param_"
//  val unrunedParamOverrideRuneSuffix = "Override"
//  val unnamedMemberNameSeparator = "_mem_"

  def scoutProgram(files: List[FileP]): ProgramS = {
    val programsS =
      files.zipWithIndex.map({ case (FileP(topLevelThings), file) =>
        val structsS = topLevelThings.collect({ case TopLevelStructP(s) => s }).map(scoutStruct(file, _));
        val interfacesS = topLevelThings.collect({ case TopLevelInterfaceP(i) => i }).map(scoutInterface(file, _));
        val implsS = topLevelThings.collect({ case TopLevelImplP(i) => i }).map(scoutImpl(file, _))
        val functionsS = topLevelThings.collect({ case TopLevelFunctionP(f) => f }).map(FunctionScout.scoutTopLevelFunction(file, _))
        ProgramS(structsS, interfacesS, implsS, functionsS)
      })
    ProgramS(
      programsS.flatMap(_.structs),
      programsS.flatMap(_.interfaces),
      programsS.flatMap(_.impls),
      programsS.flatMap(_.implementedFunctions))
  }

  private def scoutImpl(file: Int, impl0: ImplP): ImplS = {
    val ImplP(range, identifyingRuneNames, maybeTemplateRulesP, struct, interface) = impl0

    val templateRulesP = maybeTemplateRulesP.toList.flatMap(_.rules)

    val codeLocation = Scout.evalPos(file, range.begin)
    val nameS = ImplNameS(codeLocation)

    val identifyingRunes: List[IRuneS] =
      identifyingRuneNames
        .toList.flatMap(_.runes)
        .map(_.name.str)
        .map(identifyingRuneName => CodeRuneS(identifyingRuneName))
    val runesFromRules =
      RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(templateRulesP)
        .map(identifyingRuneName => CodeRuneS(identifyingRuneName))
    val userDeclaredRunes = identifyingRunes ++ runesFromRules

    val implEnv = Environment(file, None, nameS, userDeclaredRunes.toSet)

    val rate = RuleStateBox(RuleState(implEnv.name, 0))
    val userRulesS =
      RuleScout.translateRulexes(implEnv, rate, implEnv.allUserDeclaredRunes(), templateRulesP)

    // We gather all the runes from the scouted rules to be consistent with the function scout.
    val allRunes = PredictorEvaluator.getAllRunes(identifyingRunes, userRulesS, List(), None)
    val Conclusions(knowableValueRunes, _) = PredictorEvaluator.solve(Set(), userRulesS, List())
    val localRunes = allRunes
    val isTemplate = knowableValueRunes != allRunes

    val (implicitRulesFromStruct, structRune) =
      PatternScout.translateMaybeTypeIntoRune(
        implEnv,
        rate,
        Scout.evalRange(file, range),
        Some(struct),
        KindTypePR)

    val (implicitRulesFromInterface, interfaceRune) =
      PatternScout.translateMaybeTypeIntoRune(
        implEnv,
        rate,
        Scout.evalRange(file, range),
        Some(interface),
        KindTypePR)

    val rulesS = userRulesS ++ implicitRulesFromStruct ++ implicitRulesFromInterface

    ImplS(
      Scout.evalRange(file, range),
      nameS,
      rulesS,
      knowableValueRunes ++ (if (isTemplate) List() else List(structRune, interfaceRune)),
      localRunes ++ List(structRune, interfaceRune),
      isTemplate,
      structRune,
      interfaceRune)
  }

  private def scoutStruct(file: Int, head: StructP): StructS = {
    val StructP(range, StringP(_, structHumanName), attributesP, mutability, maybeIdentifyingRunes, maybeTemplateRulesP, StructMembersP(_, members)) = head
    val codeLocation = Scout.evalPos(file, range.begin)
    val structName = TopLevelCitizenDeclarationNameS(structHumanName, codeLocation)

    val structRangeS = Scout.evalRange(file, range)

    val templateRulesP = maybeTemplateRulesP.toList.flatMap(_.rules)

    val identifyingRunes: List[IRuneS] =
      maybeIdentifyingRunes
          .toList.flatMap(_.runes).map(_.name.str)
        .map(identifyingRuneName => CodeRuneS(identifyingRuneName))
    val runesFromRules =
      RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(templateRulesP)
        .map(identifyingRuneName => CodeRuneS(identifyingRuneName))
    val userDeclaredRunes = identifyingRunes ++ runesFromRules
    val structEnv = Environment(file, None, structName, userDeclaredRunes.toSet)

    val memberRunes = members.indices.map(index => MemberRuneS(index))
    val memberRules =
      memberRunes.zip(members).collect({ case (memberRune, StructMemberP(range, _, _, memberType)) =>
        val memberRange = Scout.evalRange(file, range)
        EqualsSR(
          memberRange,
          TypedSR(memberRange, memberRune, CoordTypeSR),
          TemplexSR(TemplexScout.translateTemplex(structEnv, memberType)))
      })

    val rate = RuleStateBox(RuleState(structEnv.name, 0))
    val rulesWithoutMutabilityS =
      RuleScout.translateRulexes(structEnv, rate, structEnv.allUserDeclaredRunes(), templateRulesP) ++
      memberRules

    val mutabilityRune = rate.newImplicitRune()
    val rulesS =
      rulesWithoutMutabilityS :+
        EqualsSR(
          structRangeS,
          TemplexSR(RuneST(structRangeS, mutabilityRune)),
          TemplexSR(MutabilityST(structRangeS, mutability)))

    // We gather all the runes from the scouted rules to be consistent with the function scout.
    val allRunes = PredictorEvaluator.getAllRunes(identifyingRunes, rulesS, List(), None)
    val Conclusions(knowableValueRunes, predictedTypeByRune) = PredictorEvaluator.solve(Set(), rulesS, List())
    val localRunes = allRunes
    val isTemplate = knowableValueRunes != allRunes

    val membersS =
      members.zip(memberRunes).map({ case (StructMemberP(range, StringP(_, name), variability, _), memberRune) =>
        StructMemberS(Scout.evalRange(structEnv.file, range), name, variability, memberRune)
      })

    val maybePredictedType =
      if (isTemplate) {
        if ((identifyingRunes.toSet -- predictedTypeByRune.keySet).isEmpty) {
          Some(TemplateTypeSR(identifyingRunes.map(predictedTypeByRune), KindTypeSR))
        } else {
          None
        }
      } else {
        Some(KindTypeSR)
      }

    val weakable = attributesP.exists({ case w @ WeakableP(_) => true case _ => false })
    val attrsS = translateCitizenAttributes(attributesP.filter({ case WeakableP(_) => false case _ => true}))

    StructS(
      Scout.evalRange(file, range),
      structName,
      attrsS,
      weakable,
      mutabilityRune,
      Some(mutability),
      knowableValueRunes,
      identifyingRunes,
      localRunes,
      maybePredictedType,
      isTemplate,
      rulesS,
      membersS)
  }

  def translateCitizenAttributes(attrsP: List[ICitizenAttributeP]): List[ICitizenAttributeS] = {
    attrsP.map({
      case ExportP(_) => ExportS
      case x => vimpl(x.toString)
    })
  }

  private def scoutInterface(file: Int, headP: InterfaceP): InterfaceS = {
    val InterfaceP(range, StringP(_, interfaceHumanName), attributesP, mutability, maybeIdentifyingRunes, maybeRulesP, internalMethodsP) = headP
    val codeLocation = Scout.evalPos(file, range.begin)
    val interfaceFullName = TopLevelCitizenDeclarationNameS(interfaceHumanName, codeLocation)
    val rulesP = maybeRulesP.toList.flatMap(_.rules)

    val interfaceRangeS = Scout.evalRange(file, range)

    val identifyingRunes: List[IRuneS] =
      maybeIdentifyingRunes
        .toList.flatMap(_.runes).map(_.name.str)
        .map(identifyingRuneName => CodeRuneS(identifyingRuneName))
    val runesFromRules =
      RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(rulesP)
        .map(identifyingRuneName => CodeRuneS(identifyingRuneName))
    val userDeclaredRunes = (identifyingRunes ++ runesFromRules).toSet
    val interfaceEnv = Environment(file, None, interfaceFullName, userDeclaredRunes.toSet)

    val ruleState = RuleStateBox(RuleState(interfaceEnv.name, 0))

    val rulesWithoutMutabilityS = RuleScout.translateRulexes(interfaceEnv, ruleState, interfaceEnv.allUserDeclaredRunes(), rulesP)

    val mutabilityRune = ruleState.newImplicitRune()
    val rulesS =
      rulesWithoutMutabilityS :+
      EqualsSR(
        interfaceRangeS,
        TemplexSR(RuneST(interfaceRangeS, mutabilityRune)),
        TemplexSR(MutabilityST(interfaceRangeS, mutability)))

    // We gather all the runes from the scouted rules to be consistent with the function scout.
    val allRunes = PredictorEvaluator.getAllRunes(identifyingRunes, rulesS, List(), None)
    val Conclusions(knowableValueRunes, predictedTypeByRune) =
      PredictorEvaluator.solve(Set(), rulesS, List())
    val localRunes = allRunes
    val isTemplate = knowableValueRunes != allRunes.toSet

    val maybePredictedType =
      if (isTemplate) {
        if ((identifyingRunes.toSet -- predictedTypeByRune.keySet).isEmpty) {
          Some(TemplateTypeSR(identifyingRunes.map(predictedTypeByRune), KindTypeSR))
        } else {
          None
        }
      } else {
        Some(KindTypeSR)
      }

    val internalMethodsS = internalMethodsP.map(FunctionScout.scoutInterfaceMember(interfaceEnv, _))

    val weakable = attributesP.exists({ case w @ WeakableP(_) => true case _ => false })
    val seealed = attributesP.exists({ case w @ SealedP(_) => true case _ => false })

    val interfaceS =
      InterfaceS(
        Scout.evalRange(file, range),
        interfaceFullName,
        translateCitizenAttributes(attributesP),
        weakable,
        mutabilityRune,
        Some(mutability),
        knowableValueRunes,
        identifyingRunes,
        localRunes,
        maybePredictedType,
        isTemplate,
        rulesS,
        internalMethodsS)

    interfaceS
  }

  def evalRange(file: Int, range: Range): RangeS = {
    RangeS(evalPos(file, range.begin), evalPos(file, range.end))
  }

  def evalPos(file: Int, pos: Int): CodeLocationS = {
    CodeLocationS(file, pos)
  }
}
