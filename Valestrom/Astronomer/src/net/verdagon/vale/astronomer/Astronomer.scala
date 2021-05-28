package net.verdagon.vale.astronomer

import net.verdagon.vale.astronomer.OrderModules.orderModules
import net.verdagon.vale.astronomer.builtins._
import net.verdagon.vale.astronomer.ruletyper._
import net.verdagon.vale.parser.{CaptureP, FailedParse, FileP, ImmutableP, MutabilityP, MutableP}
import net.verdagon.vale.scout.{ExportS, ExternS, Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{Err, FileCoordinateMap, IPackageResolver, Ok, PackageCoordinate, PackageCoordinateMap, Result, vassert, vassertSome, vfail, vimpl, vwat}

import scala.collection.immutable.List

// Environments dont have an AbsoluteName, because an environment can span multiple
// files.
case class Environment(
    maybeName: Option[INameS],
    maybeParentEnv: Option[Environment],
    primitives: Map[String, ITypeSR],
    codeMap: PackageCoordinateMap[ProgramS],
    typeByRune: Map[IRuneA, ITemplataType],
    locals: List[LocalVariableA]) {

  val structsS: List[StructS] = codeMap.moduleToPackagesToFilenameToContents.values.flatMap(_.values.flatMap(_.structs)).toList
  val interfacesS: List[InterfaceS] = codeMap.moduleToPackagesToFilenameToContents.values.flatMap(_.values.flatMap(_.interfaces)).toList
  val implsS: List[ImplS] = codeMap.moduleToPackagesToFilenameToContents.values.flatMap(_.values.flatMap(_.impls)).toList
  val functionsS: List[FunctionS] = codeMap.moduleToPackagesToFilenameToContents.values.flatMap(_.values.flatMap(_.implementedFunctions)).toList
  val exportsS: List[ExportAsS] = codeMap.moduleToPackagesToFilenameToContents.values.flatMap(_.values.flatMap(_.exports)).toList
  val imports: List[ImportS] = codeMap.moduleToPackagesToFilenameToContents.values.flatMap(_.values.flatMap(_.imports)).toList

  def addLocals(newLocals: List[LocalVariableA]): Environment = {
    Environment(maybeName, maybeParentEnv, primitives, codeMap, typeByRune, locals ++ newLocals)
  }
  def addRunes(newTypeByRune: Map[IRuneA, ITemplataType]): Environment = {
    Environment(maybeName, maybeParentEnv, primitives, codeMap, typeByRune ++ newTypeByRune, locals)
  }

  // Returns whether the imprecise name could be referring to the absolute name.
  // See MINAAN for what we're doing here.
  def impreciseNameMatchesAbsoluteName(
    absoluteName: INameS,
    needleImpreciseNameS: IImpreciseNameStepS):
  Boolean = {
    (absoluteName, needleImpreciseNameS) match {
      case (TopLevelCitizenDeclarationNameS(humanNameA, _), CodeTypeNameS(humanNameB)) => humanNameA == humanNameB
      case _ => vimpl()
    }

//    val envNameSteps = maybeName.map(_.steps).getOrElse(List())
//
//    // See MINAAN for what we're doing here.
//    absoluteNameEndsWithImpreciseName(absoluteName, needleImpreciseNameS) match {
//      case None => false
//      case Some(absoluteNameFirstHalf) => {
//        if (absoluteNameFirstHalf.steps.size > envNameSteps.size) {
//          false
//        } else {
//          (absoluteNameFirstHalf.steps.map(Some(_)) ++ envNameSteps.map(_ => None))
//            .zip(envNameSteps)
//            .forall({
//              case (None, _) => true
//              case (Some(firstHalfNameStep), envNameStep) => firstHalfNameStep == envNameStep
//            })
//        }
//      }
//    }
  }

  def lookupType(needleImpreciseNameS: IImpreciseNameStepS):
  (Option[ITypeSR], List[StructS], List[InterfaceS]) = {
    // See MINAAN for what we're doing here.

    val nearStructs = structsS.filter(struct => {
      impreciseNameMatchesAbsoluteName(struct.name, needleImpreciseNameS)
    })
    val nearInterfaces = interfacesS.filter(interface => {
      impreciseNameMatchesAbsoluteName(interface.name, needleImpreciseNameS)
    })
    val nearPrimitives =
      needleImpreciseNameS match {
        case CodeTypeNameS(nameStr) => primitives.get(nameStr)
        case _ => None
      }

    if (nearPrimitives.nonEmpty || nearStructs.nonEmpty || nearInterfaces.nonEmpty) {
      return (nearPrimitives, nearStructs.toList, nearInterfaces.toList)
    }
    maybeParentEnv match {
      case None => (None, List(), List())
      case Some(parentEnv) => parentEnv.lookupType(needleImpreciseNameS)
    }
  }

  def lookupType(name: INameS):
  (List[StructS], List[InterfaceS]) = {
    val nearStructs = structsS.filter(_.name == name)
    val nearInterfaces = interfacesS.filter(_.name == name)

    if (nearStructs.nonEmpty || nearInterfaces.nonEmpty) {
      return (nearStructs.toList, nearInterfaces.toList)
    }
    maybeParentEnv match {
      case None => (List(), List())
      case Some(parentEnv) => parentEnv.lookupType(name)
    }
  }

  def lookupRune(name: IRuneA): ITemplataType = {
    typeByRune.get(name) match {
      case Some(tyype) => tyype
      case None => {
        maybeParentEnv match {
          case None => vfail()
          case Some(parentEnv) => parentEnv.lookupRune(name)
        }
      }
    }
  }
}

case class AstroutsBox(var astrouts: Astrouts) {
  def getImpl(name: ImplNameA) = {
    astrouts.moduleAstrouts.get(name.packageCoordinate.module).flatMap(_.impls.get(name))
  }
  def getStruct(name: ITypeDeclarationNameA) = {
    astrouts.moduleAstrouts.get(name.packageCoordinate.module).flatMap(_.structs.get(name))
  }
  def getInterface(name: ITypeDeclarationNameA) = {
    astrouts.moduleAstrouts.get(name.packageCoordinate.module).flatMap(_.interfaces.get(name))
  }
}

case class Astrouts(
  moduleAstrouts: Map[String, ModuleAstrouts])

case class ModuleAstrouts(
  structs: Map[ITypeDeclarationNameA, StructA],
  interfaces: Map[ITypeDeclarationNameA, InterfaceA],
  impls: Map[ImplNameA, ImplA],
  functions: Map[IFunctionDeclarationNameA, FunctionA])

object Astronomer {
  val primitives =
    Map(
      "int" -> KindTypeSR,
      "str" -> KindTypeSR,
      "bool" -> KindTypeSR,
      "float" -> KindTypeSR,
      "void" -> KindTypeSR,
      "IFunction1" -> TemplateTypeSR(List(MutabilityTypeSR, CoordTypeSR, CoordTypeSR), KindTypeSR),
      "Array" -> TemplateTypeSR(List(MutabilityTypeSR, CoordTypeSR), KindTypeSR))

  def translateRuneType(tyype: ITypeSR): ITemplataType = {
    tyype match {
      case IntTypeSR => IntegerTemplataType
      case BoolTypeSR => BooleanTemplataType
      case OwnershipTypeSR => OwnershipTemplataType
      case MutabilityTypeSR => MutabilityTemplataType
      case PermissionTypeSR => PermissionTemplataType
      case LocationTypeSR => LocationTemplataType
      case CoordTypeSR => CoordTemplataType
      case KindTypeSR => KindTemplataType
      case FunctionTypeSR => FunctionTemplataType
      case TemplateTypeSR(params, result) => TemplateTemplataType(params.map(translateRuneType), translateRuneType(result))
      case VariabilityTypeSR => VariabilityTemplataType
    }
  }

  def lookupStructType(astrouts: AstroutsBox, env: Environment, structS: StructS): ITemplataType = {
    structS.maybePredictedType match {
      case Some(predictedType) => {
        translateRuneType(predictedType)
      }
      case None => {
        val structA = translateStruct(astrouts, env, structS)
        structA.tyype
      }
    }
  }

  def lookupInterfaceType(astrouts: AstroutsBox, env: Environment, interfaceS: InterfaceS):
  ITemplataType = {
    interfaceS.maybePredictedType match {
      case Some(predictedType) => {
        translateRuneType(predictedType)
      }
      case None => {
        val interfaceA = translateInterface(astrouts, env, interfaceS)
        interfaceA.tyype
      }
    }
  }

  def lookupType(astrouts: AstroutsBox, env: Environment, range: RangeS, name: INameS): ITemplataType = {
    // When the scout comes across a lambda, it doesn't put the e.g. main:lam1:__Closure struct into
    // the environment or anything, it lets templar to do that (because templar knows the actual types).
    // However, this means that when the lambda function gets to the astronomer, the astronomer doesn't
    // know what to do with it.

    name match {
      case LambdaNameS(_) =>
      case FunctionNameS(_, _) =>
      case TopLevelCitizenDeclarationNameS(_, _) =>
      case LambdaStructNameS(_) => return KindTemplataType
      case ImplNameS(_, _) => vwat()
      case LetNameS(_) => vwat()
      case UnnamedLocalNameS(_) => vwat()
      case ClosureParamNameS() => vwat()
      case MagicParamNameS(_) => vwat()
      case CodeVarNameS(_) => vwat()
    }

    val (structsS, interfacesS) = env.lookupType(name)

    if (structsS.isEmpty && interfacesS.isEmpty) {
      throw CompileErrorExceptionA(RangedInternalErrorA(range, "Nothing found with name " + name))
    }
    if (structsS.size.signum + interfacesS.size.signum > 1) {
      throw CompileErrorExceptionA(RangedInternalErrorA(range, "Name doesn't correspond to only one of primitive or struct or interface: " + name))
    }

    if (structsS.nonEmpty) {
      val types = structsS.map(lookupStructType(astrouts, env, _))
      if (types.toSet.size > 1) {
        throw CompileErrorExceptionA(RangedInternalErrorA(range, "'" + name + "' has multiple types: " + types.toSet))
      }
      val tyype = types.head
      tyype
    } else if (interfacesS.nonEmpty) {
      val types = interfacesS.map(lookupInterfaceType(astrouts, env, _))
      if (types.toSet.size > 1) {
        throw CompileErrorExceptionA(RangedInternalErrorA(range, "'" + name + "' has multiple types: " + types.toSet))
      }
      val tyype = types.head
      tyype
    } else vfail()
  }

  def lookupType(astrouts: AstroutsBox, env: Environment, range: RangeS, name: CodeTypeNameS): ITemplataType = {
    // When the scout comes across a lambda, it doesn't put the e.g. __Closure<main>:lam1 struct into
    // the environment or anything, it lets templar to do that (because templar knows the actual types).
    // However, this means that when the lambda function gets to the astronomer, the astronomer doesn't
    // know what to do with it.

    val (primitivesS, structsS, interfacesS) = env.lookupType(name)

    if (primitivesS.isEmpty && structsS.isEmpty && interfacesS.isEmpty) {
      ErrorReporter.report(CouldntFindTypeA(range, name.name))
    }
    if (primitivesS.size.signum + structsS.size.signum + interfacesS.size.signum > 1) {
      throw CompileErrorExceptionA(RangedInternalErrorA(range, "Name doesn't correspond to only one of primitive or struct or interface: " + name))
    }

    if (primitivesS.nonEmpty) {
      vassert(primitivesS.size == 1)
      translateRuneType(primitivesS.get)
    } else if (structsS.nonEmpty) {
      val types = structsS.map(lookupStructType(astrouts, env, _))
      if (types.toSet.size > 1) {
        throw CompileErrorExceptionA(RangedInternalErrorA(range, "'" + name + "' has multiple types: " + types.toSet))
      }
      val tyype = types.head
      tyype
    } else if (interfacesS.nonEmpty) {
      val types = interfacesS.map(lookupInterfaceType(astrouts, env, _))
      if (types.toSet.size > 1) {
        throw CompileErrorExceptionA(RangedInternalErrorA(range, "'" + name + "' has multiple types: " + types.toSet))
      }
      val tyype = types.head
      tyype
    } else vfail()
  }

  def makeRuleTyper(): RuleTyperEvaluator[Environment, AstroutsBox] = {
    new RuleTyperEvaluator[Environment, AstroutsBox](
      new IRuleTyperEvaluatorDelegate[Environment, AstroutsBox] {
        override def lookupType(state: AstroutsBox, env: Environment, range: RangeS, name: CodeTypeNameS): (ITemplataType) = {
          Astronomer.lookupType(state, env, range, name)
        }

        override def lookupType(state: AstroutsBox, env: Environment, range: RangeS, name: INameS): ITemplataType = {
          Astronomer.lookupType(state, env, range, name)
        }
      })
  }

  def translateStruct(astrouts: AstroutsBox, env: Environment, structS: StructS): StructA = {
    val StructS(rangeS, nameS, attributesS, weakable, mutabilityRuneS, maybePredictedMutabilityS, knowableRunesS, identifyingRunesS, localRunesS, predictedTypeByRune, isTemplate, rules, members) = structS
    val mutabilityRuneA = Astronomer.translateRune(mutabilityRuneS)
    val maybePredictedMutabilityA = maybePredictedMutabilityS
    val nameA = Astronomer.translateTopLevelCitizenDeclarationName(nameS)
    val localRunesA = localRunesS.map(Astronomer.translateRune)
    val knowableRunesA = knowableRunesS.map(Astronomer.translateRune)
    val identifyingRunesA = identifyingRunesS.map(Astronomer.translateRune)

    // predictedTypeByRune is used by the rule typer delegate to short-circuit infinite recursion
    // in types like List, see RTMHTPS.
    val _ = predictedTypeByRune

    astrouts.getStruct(nameA) match {
      case Some(existingStructA) => return existingStructA
      case _ =>
    }

    val (conclusions, rulesA) =
      makeRuleTyper().solve(astrouts, env, rules, rangeS, List(), Some(localRunesA ++ knowableRunesA)) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
        case (c, RuleTyperSolveSuccess(r)) => (c, r)
      }

    val tyype =
      if (isTemplate) {
        TemplateTemplataType(identifyingRunesA.map(conclusions.typeByRune), KindTemplataType)
      } else {
        KindTemplataType
      }

    val membersA =
      members.map({
        case StructMemberS(range, name, variablility, typeRune) => StructMemberA(range, name, variablility, translateRune(typeRune))
      })

    StructA(
      rangeS,
      nameA,
      translateCitizenAttributes(attributesS),
      weakable,
      mutabilityRuneA,
      maybePredictedMutabilityA,
      tyype,
      knowableRunesA,
      identifyingRunesA,
      localRunesA,
      conclusions.typeByRune,
      rulesA,
      membersA)
  }

  def translateCitizenAttributes(attrsS: List[ICitizenAttributeS]) = {
    attrsS.map({
      case ExportS(packageCoordinate) => ExportA(packageCoordinate)
      case x => vimpl(x.toString)
    })
  }

  def translateFunctionAttributes(attrsS: List[IFunctionAttributeS]): List[IFunctionAttributeA] = {
    attrsS.flatMap({
      case ExportS(packageCoordinate) => List(ExportA(packageCoordinate))
      case ExternS(packageCoordinate) => List(ExternA(packageCoordinate))
      case PureS => List(PureA)
      case BuiltinS(_) => List()
      case x => vimpl(x.toString)
    })
  }

  def translateInterface(astrouts: AstroutsBox, env: Environment, interfaceS: InterfaceS): InterfaceA = {
    val InterfaceS(range, nameS, attributesS, weakable, mutabilityRuneS, maybePredictedMutability, knowableRunesS, identifyingRunesS, localRunesS, predictedTypeByRune, isTemplate, rules, internalMethodsS) = interfaceS
    val mutabilityRuneA = Astronomer.translateRune(mutabilityRuneS)
    val localRunesA = localRunesS.map(Astronomer.translateRune)
    val knowableRunesA = knowableRunesS.map(Astronomer.translateRune)
    val identifyingRunesA = identifyingRunesS.map(Astronomer.translateRune)
    val nameA = TopLevelCitizenDeclarationNameA(nameS.name, nameS.codeLocation)

    // predictedTypeByRune is used by the rule typer delegate to short-circuit infinite recursion
    // in types like List, see RTMHTPS.
    val _ = predictedTypeByRune

    astrouts.getInterface(nameA) match {
      case Some(existingInterfaceA) => return existingInterfaceA
      case _ =>
    }

    val (conclusions, rulesA) =
      makeRuleTyper().solve(astrouts, env, rules, range, List(), Some(knowableRunesA ++ localRunesA)) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
        case (c, RuleTyperSolveSuccess(r)) => (c, r)
      }

    val tyype =
      if (isTemplate) {
        TemplateTemplataType(identifyingRunesA.map(conclusions.typeByRune), KindTemplataType)
      } else {
        KindTemplataType
      }

    val internalMethodsA = internalMethodsS.map(translateFunction(astrouts, env, _))

    val interfaceA =
      InterfaceA(
        range,
        nameA,
        translateCitizenAttributes(attributesS),
        weakable,
        mutabilityRuneA,
        maybePredictedMutability,
        tyype,
        knowableRunesA,
        identifyingRunesA,
        localRunesA,
        conclusions.typeByRune,
        rulesA,
        internalMethodsA)
    interfaceA
  }

  def translateImpl(astrouts: AstroutsBox, env: Environment, implS: ImplS): ImplA = {
    val ImplS(range, nameS, rulesFromStructDirection, rulesFromInterfaceDirection, knowableRunesS, localRunesS, isTemplate, structKindRuneS, interfaceKindRuneS) = implS
    val nameA = translateImplName(nameS)
    val localRunesA = localRunesS.map(Astronomer.translateRune)
    val knowableRunesA = knowableRunesS.map(Astronomer.translateRune)

    astrouts.getImpl(nameA) match {
      case Some(existingImplA) => return existingImplA
      case _ =>
    }

    val (conclusionsForRulesFromStructDirection, rulesFromStructDirectionA) =
      makeRuleTyper().solve(astrouts, env, rulesFromStructDirection, range, List(), Some(knowableRunesA ++ localRunesA)) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
        case (c, RuleTyperSolveSuccess(r)) => (c, r)
      }
    val (conclusionsForRulesFromInterfaceDirection, rulesFromInterfaceDirectionA) =
      makeRuleTyper().solve(astrouts, env, rulesFromInterfaceDirection, range, List(), Some(knowableRunesA ++ localRunesA)) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
        case (c, RuleTyperSolveSuccess(r)) => (c, r)
      }
    vassert(conclusionsForRulesFromStructDirection == conclusionsForRulesFromInterfaceDirection)
    val conclusions = conclusionsForRulesFromStructDirection

    ImplA(
      range,
      nameA,
      rulesFromStructDirectionA,
      rulesFromInterfaceDirectionA,
      conclusions.typeByRune,
      localRunesA,
      translateRune(structKindRuneS),
      translateRune(interfaceKindRuneS))
  }

  def translateExport(astrouts: AstroutsBox, env: Environment, exportS: ExportAsS): ExportAsA = {
    val ExportAsS(range, exportName, templexS, exportedName) = exportS

    val runeS = ImplicitRuneS(exportName, 0)
    val runeA = translateRune(runeS)
    val rulesS = List(EqualsSR(range, TypedSR(range, runeS, KindTypeSR), TemplexSR(templexS)))

    val (conclusions, rulesA) =
      makeRuleTyper().solve(astrouts, env, rulesS, range, List(), Some(Set(runeA))) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
        case (c, RuleTyperSolveSuccess(r)) => (c, r)
      }

    ExportAsA(range, exportedName, rulesA, conclusions.typeByRune, runeA)
  }

  def translateParameter(env: Environment, paramS: ParameterS): ParameterA = {
    val ParameterS(atomS) = paramS
    ParameterA(translateAtom(env, atomS))
  }

  def translateAtom(env: Environment, atomS: AtomSP): AtomAP = {
    val AtomSP(range, CaptureS(nameS, variability), virtualityS, coordRuneS, destructureS) = atomS
    val nameA = translateVarNameStep(nameS)

    val virtualityA =
      virtualityS.map({
        case AbstractSP => AbstractAP
        case OverrideSP(range, kindRune) => OverrideAP(range, translateRune(kindRune))
      })

    val coordRuneA = translateRune(coordRuneS)

    val destructureA = destructureS.map(_.map(translateAtom(env, _)))

    val local = env.locals.find(_.varName == nameA).get

    AtomAP(range, local, virtualityA, coordRuneA, destructureA)
  }

  def translateFunction(astrouts: AstroutsBox, outerEnv: Environment, functionS: FunctionS): FunctionA = {
    val FunctionS(rangeS, nameS, attributesS, knowableRunesS, identifyingRunesS, localRunesS, maybePredictedType, paramsS, maybeRetCoordRune, isTemplate, templateRules, bodyS) = functionS
    val nameA = translateFunctionDeclarationName(nameS)
    val knowableRunesA = knowableRunesS.map(Astronomer.translateRune)
    val localRunesA = localRunesS.map(Astronomer.translateRune)
    val identifyingRunesA = identifyingRunesS.map(Astronomer.translateRune)

    val locals =
      bodyS match {
        case CodeBody1(body) => body.block.locals.map(ExpressionAstronomer.translateLocalVariable)
        case _ => {
          // We make some LocalVariableA here to appease translateParameter which expects some locals in the env.
          paramsS.map(_.pattern.name)
            .map({
              case CaptureS(name, variability) => {
                LocalVariableA(
                  Astronomer.translateVarNameStep(name),
                  variability,
                  NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed)
              }
            })
        }
      }
    val env = outerEnv.addLocals(locals)

    val paramsA = paramsS.map(translateParameter(env, _))

    val (conclusions, rulesA) =
      makeRuleTyper().solve(astrouts, env, templateRules, rangeS, List(), Some(localRunesA)) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
        case (c, RuleTyperSolveSuccess(r)) => (c, r)
      }

    val tyype =
      if (isTemplate) {
        TemplateTemplataType(
          identifyingRunesA.map(conclusions.typeByRune),
          FunctionTemplataType)
      } else {
        FunctionTemplataType
      }

    val innerEnv = env.addRunes(conclusions.typeByRune)

    val bodyA = translateBody(astrouts, innerEnv, bodyS)

    FunctionA(
      rangeS,
      nameA,
      UserFunctionA :: translateFunctionAttributes(attributesS),
      tyype,
      knowableRunesA,
      identifyingRunesA,
      localRunesA,
      conclusions.typeByRune ++ env.typeByRune,
      paramsA,
      maybeRetCoordRune.map(translateRune),
      rulesA,
      bodyA)
  }

  def translateBody(astrouts: AstroutsBox, env: Environment, body: IBody1): IBodyA = {
    body match {
      case ExternBody1 => ExternBodyA
      case AbstractBody1 => AbstractBodyA
      case GeneratedBody1(generatorId) => GeneratedBodyA(generatorId)
      case CodeBody1(BodySE(range, closuredNamesS, blockS)) => {
        val blockA = ExpressionAstronomer.translateBlock(env, astrouts, blockS)
        CodeBodyA(BodyAE(range, closuredNamesS.map(translateVarNameStep), blockA))
      }
    }
  }

//  def translateImpreciseTypeName(fullNameS: ImpreciseNameS[CodeTypeNameS]): ImpreciseNameA[CodeTypeNameA] = {
//    val ImpreciseNameS(initS, lastS) = fullNameS
//    ImpreciseNameA(initS.map(translateImpreciseNameStep), translateCodeTypeName(lastS))
//  }
//
//  def translateImpreciseName(fullNameS: ImpreciseNameS[IImpreciseNameStepS]): ImpreciseNameA[IImpreciseNameStepA] = {
//    val ImpreciseNameS(initS, lastS) = fullNameS
//    ImpreciseNameA(initS.map(translateImpreciseNameStep), translateImpreciseNameStep(lastS))
//  }

  def translateCodeTypeName(codeTypeNameS: CodeTypeNameS): CodeTypeNameA = {
    val CodeTypeNameS(name) = codeTypeNameS
    CodeTypeNameA(name)
  }

  def translateImpreciseName(impreciseNameStepS: IImpreciseNameStepS): IImpreciseNameStepA = {
    impreciseNameStepS match {
      case ctn @ CodeTypeNameS(_) => translateCodeTypeName(ctn)
      case GlobalFunctionFamilyNameS(name) => GlobalFunctionFamilyNameA(name)
      case icvn @ ImpreciseCodeVarNameS(_) => translateImpreciseCodeVarName(icvn)
    }
  }

  def translateImpreciseCodeVarName(impreciseNameStepS: ImpreciseCodeVarNameS): ImpreciseCodeVarNameA = {
    var ImpreciseCodeVarNameS(name) = impreciseNameStepS
    ImpreciseCodeVarNameA(name)
  }

//  def translateRune(absoluteNameS: IRuneS): IRuneA = {
//    val AbsoluteNameS(file, initS, lastS) = absoluteNameS
//    AbsoluteNameA(file, initS.map(translateNameStep), translateRune(lastS))
//  }
//
//  def translateVarAbsoluteName(absoluteNameS: IVarNameS): IVarNameA = {
//    val AbsoluteNameS(file, initS, lastS) = absoluteNameS
//    AbsoluteNameA(file, initS.map(translateNameStep), translateVarNameStep(lastS))
//  }

//  def translateVarImpreciseName(absoluteNameS: ImpreciseNameS[ImpreciseCodeVarNameS]):
//  ImpreciseNameA[ImpreciseCodeVarNameA] = {
//    val ImpreciseNameS(initS, lastS) = absoluteNameS
//    ImpreciseNameA(initS.map(translateImpreciseNameStep), translateImpreciseCodeVarNameStep(lastS))
//  }

//  def translateFunctionFamilyName(name: ImpreciseNameS[GlobalFunctionFamilyNameS]):
//  ImpreciseNameA[GlobalFunctionFamilyNameA] = {
//    val ImpreciseNameS(init, last) = name
//    ImpreciseNameA(init.map(translateImpreciseNameStep), translateGlobalFunctionFamilyName(last))
//  }

  def translateGlobalFunctionFamilyName(s: GlobalFunctionFamilyNameS): GlobalFunctionFamilyNameA = {
    val GlobalFunctionFamilyNameS(name) = s
    GlobalFunctionFamilyNameA(name)
  }

//  def translateName(absoluteNameS: INameS): INameA = {
//    val AbsoluteNameS(file, initS, lastS) = absoluteNameS
//    AbsoluteNameA(file, initS.map(translateNameStep), translateNameStep(lastS))
//  }

  def translateFunctionDeclarationName(name: IFunctionDeclarationNameS): IFunctionDeclarationNameA = {
    name match {
      case LambdaNameS(/*parentName,*/ codeLocation) => LambdaNameA(/*translateName(parentName),*/ codeLocation)
      case FunctionNameS(name, codeLocation) => FunctionNameA(name, codeLocation)
    }
  }

  def translateName(name: INameS): INameA = {
    name match {
      case LambdaNameS(/*parentName, */codeLocation) => LambdaNameA(/*translateName(parentName), */codeLocation)
      case FunctionNameS(name, codeLocation) => FunctionNameA(name, codeLocation)
      case tlcd @ TopLevelCitizenDeclarationNameS(_, _) => translateTopLevelCitizenDeclarationName(tlcd)
      case LambdaStructNameS(lambdaName) => LambdaStructNameA(translateLambdaNameStep(lambdaName))
      case i @ ImplNameS(_, _) => translateImplName(i)
      case LetNameS(codeLocation) => LetNameA(codeLocation)
      case UnnamedLocalNameS(codeLocation) => UnnamedLocalNameA(codeLocation)
      case ClosureParamNameS() => ClosureParamNameA()
      case MagicParamNameS(codeLocation) => MagicParamNameA(codeLocation)
      case CodeVarNameS(name) => CodeVarNameA(name)
      case ExportAsNameS(codeLocation) => ExportAsNameA(codeLocation)
    }
  }

  def translateImplName(s: ImplNameS): ImplNameA = {
    val ImplNameS(subCitizenHumanName, codeLocationS) = s;
    ImplNameA(subCitizenHumanName, codeLocationS)
  }

  def translateTopLevelCitizenDeclarationName(tlcd: TopLevelCitizenDeclarationNameS): TopLevelCitizenDeclarationNameA = {
    val TopLevelCitizenDeclarationNameS(name, codeLocation) = tlcd
    TopLevelCitizenDeclarationNameA(name, codeLocation)
  }

  def translateRune(rune: IRuneS): IRuneA = {
    rune match {
      case CodeRuneS(name) => CodeRuneA(name)
      case ImplicitRuneS(parentName, name) => ImplicitRuneA(translateName(parentName), name)
      case ArraySizeImplicitRuneS() => ArraySizeImplicitRuneA()
      case ArrayVariabilityImplicitRuneS() => ArrayVariabilityImplicitRuneA()
      case ArrayMutabilityImplicitRuneS() => ArrayMutabilityImplicitRuneA()
      case LetImplicitRuneS(codeLocation, name) => LetImplicitRuneA(codeLocation, name)
      case MagicParamRuneS(magicParamIndex) => MagicImplicitRuneA(magicParamIndex)
      case MemberRuneS(memberIndex) => MemberRuneA(memberIndex)
      case ReturnRuneS() => ReturnRuneA()
      case ExplicitTemplateArgRuneS(index) => ExplicitTemplateArgRuneA(index)
    }
  }

  def translateVarNameStep(name: IVarNameS): IVarNameA = {
    name match {
      case UnnamedLocalNameS(codeLocation) => UnnamedLocalNameA(codeLocation)
      case ClosureParamNameS() => ClosureParamNameA()
      case ConstructingMemberNameS(n) => ConstructingMemberNameA(n)
      case MagicParamNameS(magicParamNumber) => MagicParamNameA(magicParamNumber)
      case CodeVarNameS(name) => CodeVarNameA(name)
    }
  }

  def translateLambdaNameStep(lambdaNameStep: LambdaNameS): LambdaNameA = {
    val LambdaNameS(/*parentName,*/ codeLocation) = lambdaNameStep
    LambdaNameA(/*translateName(parentName),*/ codeLocation)
  }

  def translateProgram(
      codeMap: PackageCoordinateMap[ProgramS],
      primitives: Map[String, ITypeSR],
      suppliedFunctions: List[FunctionA],
      suppliedInterfaces: List[InterfaceA]):
  ProgramA = {
    val astrouts = AstroutsBox(Astrouts(Map()))

    val env = Environment(None, None, primitives, codeMap, Map(), List())

    val structsA = env.structsS.map(translateStruct(astrouts, env, _))

    val interfacesA = env.interfacesS.map(translateInterface(astrouts, env, _))

    val implsA = env.implsS.map(translateImpl(astrouts, env, _))

    val functionsA = env.functionsS.map(translateFunction(astrouts, env, _))

    val exportsA = env.exportsS.map(translateExport(astrouts, env, _))

    val _ = astrouts

    ProgramA(structsA, suppliedInterfaces ++ interfacesA, implsA, suppliedFunctions ++ functionsA, exportsA)
  }


//  val stlFunctions =
//    Forwarders.forwarders ++
//    List(
//      NotEquals.function,
//      Printing.printInt,
//      Printing.printlnInt,
//      Printing.printBool,
//      Printing.printlnBool,
//      Printing.printlnStr)

  val wrapperFunctions =
    List(
      Arrays.makeArrayFunction(),
      RefCounting.checkmemberrc,
      RefCounting.checkvarrc)

  def runAstronomer(separateProgramsS: FileCoordinateMap[ProgramS]):
  Either[PackageCoordinateMap[ProgramA], ICompileErrorA] = {
    val mergedProgramS =
      PackageCoordinateMap(
        separateProgramsS.moduleToPackagesToFilenameToContents.mapValues(packagesToFilenameToContents => {
          packagesToFilenameToContents.mapValues(filenameToContents => {
            ProgramS(
              filenameToContents.values.flatMap(_.structs).toList,
              filenameToContents.values.flatMap(_.interfaces).toList,
              filenameToContents.values.flatMap(_.impls).toList,
              filenameToContents.values.flatMap(_.implementedFunctions).toList,
              filenameToContents.values.flatMap(_.exports).toList,
              filenameToContents.values.flatMap(_.imports).toList)
          })
        }))

//    val orderedModules = orderModules(mergedProgramS)

    try {
      val suppliedFunctions = wrapperFunctions
      val suppliedInterfaces = List(IFunction1.interface)
      val ProgramA(structsA, interfacesA, implsA, functionsA, exportsA) =
        Astronomer.translateProgram(
          mergedProgramS, primitives, suppliedFunctions, suppliedInterfaces)

      val packageToStructsA = structsA.groupBy(_.name.codeLocation.file.packageCoordinate)
      val packageToInterfacesA = interfacesA.groupBy(_.name.codeLocation.file.packageCoordinate)
      val packageToFunctionsA = functionsA.groupBy(_.name.packageCoordinate)
      val packageToImplsA = implsA.groupBy(_.name.codeLocation.file.packageCoordinate)
      val packageToExportsA = exportsA.groupBy(_.range.file.packageCoordinate)

      val allPackages =
        packageToStructsA.keySet ++
        packageToInterfacesA.keySet ++
        packageToFunctionsA.keySet ++
        packageToImplsA.keySet ++
        packageToExportsA.keySet
      val packageToContents =
        allPackages.map(paackage => {
          val contents =
            ProgramA(
              packageToStructsA.getOrElse(paackage, List()),
              packageToInterfacesA.getOrElse(paackage, List()),
              packageToImplsA.getOrElse(paackage, List()),
              packageToFunctionsA.getOrElse(paackage, List()),
              packageToExportsA.getOrElse(paackage, List()))
          (paackage -> contents)
        }).toMap
      val moduleToPackageToContents =
        packageToContents.keys.toList.groupBy(_.module).mapValues(packageCoordinates => {
          packageCoordinates.map(packageCoordinate => {
            (packageCoordinate.packages -> packageToContents(packageCoordinate))
          }).toMap
        })
      Left(PackageCoordinateMap(moduleToPackageToContents))
    } catch {
      case CompileErrorExceptionA(err) => {
        Right(err)
      }
    }
  }
}

class AstronomerCompilation(
  packagesToBuild: List[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]]) {
  var scoutCompilation = new ScoutCompilation(packagesToBuild, packageToContentsResolver)
  var astroutsCache: Option[PackageCoordinateMap[ProgramA]] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = scoutCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, List[(Int, Int)])], FailedParse] = scoutCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = scoutCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = scoutCompilation.getScoutput()

  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = {
    astroutsCache match {
      case Some(astrouts) => Ok(astrouts)
      case None => {
        Astronomer.runAstronomer(scoutCompilation.getScoutput().getOrDie()) match {
          case Right(err) => Err(err)
          case Left(astrouts) => {
            astroutsCache = Some(astrouts)
            Ok(astrouts)
          }
        }
      }
    }
  }
  def expectAstrouts(): PackageCoordinateMap[ProgramA] = {
    getAstrouts().getOrDie()
  }
}
