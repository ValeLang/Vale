package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{PatternScout, RuleState, RuleStateBox}
import net.verdagon.vale.scout.predictor.Conclusions
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.scout.templatepredictor.PredictorEvaluator
import net.verdagon.vale.{Err, FileCoordinate, FileCoordinateMap, IPackageResolver, IProfiler, PackageCoordinate, NullProfiler, Ok, Result, vfail, vimpl, vwat}

import scala.collection.immutable.List
import scala.util.parsing.input.OffsetPosition

case class CompileErrorExceptionS(err: ICompileErrorS) extends RuntimeException

sealed trait ICompileErrorS
case class CouldntFindVarToMutateS(range: RangeS, name: String) extends ICompileErrorS
case class ForgotSetKeywordError(range: RangeS) extends ICompileErrorS
case class CantUseThatLocalName(range: RangeS, name: String) extends ICompileErrorS
case class ExternHasBody(range: RangeS) extends ICompileErrorS
case class CantInitializeIndividualElementsOfRuntimeSizedArray(range: RangeS) extends ICompileErrorS
case class InitializingRuntimeSizedArrayRequiresSizeAndCallable(range: RangeS) extends ICompileErrorS
case class InitializingStaticSizedArrayRequiresSizeAndCallable(range: RangeS) extends ICompileErrorS
case class InitializingStaticSizedArrayFromCallableNeedsSizeTemplex(range: RangeS) extends ICompileErrorS
case class CantOwnershipInterfaceInImpl(range: RangeS) extends ICompileErrorS
case class CantOwnershipStructInImpl(range: RangeS) extends ICompileErrorS
case class CantOverrideOwnershipped(range: RangeS) extends ICompileErrorS
case class VariableNameAlreadyExists(range: RangeS, name: IVarNameS) extends ICompileErrorS
case class InterfaceMethodNeedsSelf(range: RangeS) extends ICompileErrorS
case class VirtualAndAbstractGoTogether(range: RangeS) extends ICompileErrorS

case class RangedInternalErrorS(range: RangeS, message: String) extends ICompileErrorS

sealed trait IEnvironment {
  def file: FileCoordinate
  def name: INameS
  def allUserDeclaredRunes(): Set[IRuneS]
}

// Someday we might split this into PackageEnvironment and CitizenEnvironment
case class Environment(
    file: FileCoordinate,
    parentEnv: Option[Environment],
    name: INameS,
    userDeclaredRunes: Set[IRuneS]
) extends IEnvironment {
  override def allUserDeclaredRunes(): Set[IRuneS] = {
    userDeclaredRunes ++ parentEnv.toList.flatMap(pe => pe.allUserDeclaredRunes())
  }
}

case class FunctionEnvironment(
    file: FileCoordinate,
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
    file: FileCoordinate,
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

  def scoutProgram(fileCoordinate: FileCoordinate, parsed: FileP): Result[ProgramS, ICompileErrorS] = {
    try {
      val structsS = parsed.topLevelThings.collect({ case TopLevelStructP(s) => s }).map(scoutStruct(fileCoordinate, _));
      val interfacesS = parsed.topLevelThings.collect({ case TopLevelInterfaceP(i) => i }).map(scoutInterface(fileCoordinate, _));
      val implsS = parsed.topLevelThings.collect({ case TopLevelImplP(i) => i }).map(scoutImpl(fileCoordinate, _))
      val functionsS = parsed.topLevelThings.collect({ case TopLevelFunctionP(f) => f }).map(FunctionScout.scoutTopLevelFunction(fileCoordinate, _))
      val exportsS = parsed.topLevelThings.collect({ case TopLevelExportAsP(e) => e }).map(scoutExportAs(fileCoordinate, _))
      val importsS = parsed.topLevelThings.collect({ case TopLevelImportP(e) => e }).map(scoutImport(fileCoordinate, _))
      val programS = ProgramS(structsS, interfacesS, implsS, functionsS, exportsS, importsS)
      Ok(programS)
    } catch {
      case CompileErrorExceptionS(err) => Err(err)
    }
  }

  private def scoutImpl(file: FileCoordinate, impl0: ImplP): ImplS = {
    val ImplP(range, identifyingRuneNames, maybeTemplateRulesP, struct, interface) = impl0


    val templateRulesP = maybeTemplateRulesP.toList.flatMap(_.rules)

    val codeLocation = Scout.evalPos(file, range.begin)
    val implName = ImplNameS(getHumanName(struct), codeLocation)

    val identifyingRunes: List[IRuneS] =
      identifyingRuneNames
        .toList.flatMap(_.runes)
        .map(_.name.str)
        .map(identifyingRuneName => CodeRuneS(identifyingRuneName))
    val runesFromRules =
      RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(templateRulesP)
        .map(identifyingRuneName => CodeRuneS(identifyingRuneName))
    val userDeclaredRunes = identifyingRunes ++ runesFromRules

    val implEnv = Environment(file, None, implName, userDeclaredRunes.toSet)

    val rate = RuleStateBox(RuleState(implEnv.name, 0))
    val userRulesS =
      RuleScout.translateRulexes(implEnv, rate, implEnv.allUserDeclaredRunes(), templateRulesP)

    // We gather all the runes from the scouted rules to be consistent with the function scout.
    val allRunes = PredictorEvaluator.getAllRunes(identifyingRunes, userRulesS, List(), None)
    val Conclusions(knowableValueRunes, _) = PredictorEvaluator.solve(Set(), userRulesS, List())
    val localRunes = allRunes
    val isTemplate = knowableValueRunes != allRunes

    val (implicitRulesFromStructDirection, structRune) =
      PatternScout.translateMaybeTypeIntoRune(
        implEnv,
        rate,
        Scout.evalRange(file, range),
        Some(struct),
        KindTypePR)

    interface match {
      case InterpretedPT(range, _, _, _) => {
        throw CompileErrorExceptionS(CantOwnershipInterfaceInImpl(Scout.evalRange(file, range)))
      }
      case _ =>
    }

    struct match {
      case InterpretedPT(range, _, _, _) => {
        throw CompileErrorExceptionS(CantOwnershipStructInImpl(Scout.evalRange(file, range)))
      }
      case _ =>
    }

    val (implicitRulesFromInterfaceDirection, interfaceRune) =
      PatternScout.translateMaybeTypeIntoRune(
        implEnv,
        rate,
        Scout.evalRange(file, range),
        Some(interface),
        KindTypePR)

    // See NMORFI for why these are different.
    val rulesFromStructDirectionS = implicitRulesFromStructDirection ++ implicitRulesFromInterfaceDirection ++ userRulesS
    val rulesFromInterfaceDirectionS = implicitRulesFromInterfaceDirection ++ implicitRulesFromStructDirection ++ userRulesS

    ImplS(
      Scout.evalRange(file, range),
      implName,
      rulesFromStructDirectionS,
      rulesFromInterfaceDirectionS,
      knowableValueRunes ++ (if (isTemplate) List() else List(structRune, interfaceRune)),
      localRunes ++ List(structRune, interfaceRune),
      isTemplate,
      structRune,
      interfaceRune)
  }

  private def scoutExportAs(file: FileCoordinate, exportAsP: ExportAsP): ExportAsS = {
    val ExportAsP(range, templexP, exportedName) = exportAsP

    val pos = Scout.evalPos(file, range.begin)
    val exportName = ExportAsNameS(pos)
    val exportEnv = Environment(file, None, exportName, Set())
    val templexS = TemplexScout.translateTemplex(exportEnv, templexP)

    ExportAsS(Scout.evalRange(file, range), exportName, templexS, exportedName.str)
  }

  private def scoutImport(file: FileCoordinate, importP: ImportP): ImportS = {
    val ImportP(range, moduleName, packageNames, importeeName) = importP

    val pos = Scout.evalPos(file, range.begin)

    ImportS(Scout.evalRange(file, range), moduleName.str, packageNames.map(_.str), importeeName.str)
  }

  private def scoutStruct(file: FileCoordinate, head: StructP): StructS = {
    val StructP(range, NameP(_, structHumanName), attributesP, mutability, maybeIdentifyingRunes, maybeTemplateRulesP, StructMembersP(_, members)) = head
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
      members.zip(memberRunes).flatMap({
        case (StructMemberP(range, NameP(_, name), variability, _), memberRune) => {
          List(StructMemberS(Scout.evalRange(structEnv.file, range), name, variability, memberRune))
        }
        case (StructMethodP(_), memberRune) => {
          // Implement struct methods one day
          List()
        }
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
    val attrsS = translateCitizenAttributes(file, attributesP.filter({ case WeakableP(_) => false case _ => true}))

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

  def translateCitizenAttributes(file: FileCoordinate, attrsP: List[ICitizenAttributeP]): List[ICitizenAttributeS] = {
    attrsP.map({
      case ExportP(_) => ExportS(file.packageCoordinate)
      case x => vimpl(x.toString)
    })
  }

  private def scoutInterface(file: FileCoordinate, headP: InterfaceP): InterfaceS = {
    val InterfaceP(range, NameP(_, interfaceHumanName), attributesP, mutability, maybeIdentifyingRunes, maybeRulesP, internalMethodsP) = headP
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
    val attrsS = translateCitizenAttributes(file, attributesP.filter({ case WeakableP(_) => false case _ => true}))

    val interfaceS =
      InterfaceS(
        Scout.evalRange(file, range),
        interfaceFullName,
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
        internalMethodsS)

    interfaceS
  }

  def evalRange(file: FileCoordinate, range: Range): RangeS = {
    RangeS(evalPos(file, range.begin), evalPos(file, range.end))
  }

  def evalPos(file: FileCoordinate, pos: Int): CodeLocationS = {
    CodeLocationS(file, pos)
  }

  def getHumanName(templex: ITemplexPT): String = {
    templex match {
      case NullablePT(_, inner) => getHumanName(inner)
      case InlinePT(_, inner) => getHumanName(inner)
//      case PermissionedPT(_, permission, inner) => getHumanName(inner)
      case InterpretedPT(_, ownership, permission, inner) => getHumanName(inner)
      case AnonymousRunePT(_) => vwat()
      case NameOrRunePT(NameP(_, name)) => name
      case CallPT(_, template, args) => getHumanName(template)
      case RepeaterSequencePT(_, mutability, size, element) => vwat()
      case ManualSequencePT(_, members) => vwat()
      case IntPT(_, value) => vwat()
      case BoolPT(_, value) => vwat()
      case OwnershipPT(_, ownership) => vwat()
      case MutabilityPT(_, mutability) => vwat()
      case LocationPT(_, location) => vwat()
      case PermissionPT(_, permission) => vwat()
    }
  }
}

class ScoutCompilation(
  packagesToBuild: List[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]]) {
  var parserCompilation = new ParserCompilation(packagesToBuild, packageToContentsResolver)
  var scoutputCache: Option[FileCoordinateMap[ProgramS]] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = parserCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, List[(Int, Int)])], FailedParse] = parserCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = parserCompilation.getVpstMap()

  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = {
    scoutputCache match {
      case Some(scoutput) => Ok(scoutput)
      case None => {
        val scoutput =
          parserCompilation.expectParseds().map({ case (fileCoordinate, (code, commentsAndRanges)) =>
            Scout.scoutProgram(fileCoordinate, code) match {
              case Err(e) => return Err(e)
              case Ok(p) => p
            }
          })
        scoutputCache = Some(scoutput)
        Ok(scoutput)
      }
    }
  }
  def expectScoutput(): FileCoordinateMap[ProgramS] = {
    getScoutput().getOrDie()
  }
}
