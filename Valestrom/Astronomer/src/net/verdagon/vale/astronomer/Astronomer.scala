package net.verdagon.vale.astronomer

import net.verdagon.vale.astronomer.builtins._
import net.verdagon.vale.astronomer.externs.Externs
import net.verdagon.vale.astronomer.ruletyper._
import net.verdagon.vale.parser.{CaptureP, ImmutableP, MutabilityP, MutableP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{vassert, vfail, vimpl, vwat}

import scala.collection.immutable.List

// Environments dont have an AbsoluteName, because an environment can span multiple
// files.
case class Environment(
    maybeName: Option[INameS],
    maybeParentEnv: Option[Environment],
    primitives: Map[String, ITypeSR],
    structs: List[StructS],
    interfaces: List[InterfaceS],
    impls: List[ImplS],
    functions: List[FunctionS],
    typeByRune: Map[IRuneA, ITemplataType]) {
  def addRunes(newTypeByRune: Map[IRuneA, ITemplataType]): Environment = {
    Environment(maybeName, maybeParentEnv, primitives, structs, interfaces, impls, functions, typeByRune ++ newTypeByRune)
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

    val nearStructs = structs.filter(struct => {
      impreciseNameMatchesAbsoluteName(struct.name, needleImpreciseNameS)
    })
    val nearInterfaces = interfaces.filter(interface => {
      impreciseNameMatchesAbsoluteName(interface.name, needleImpreciseNameS)
    })
    val nearPrimitives =
      needleImpreciseNameS match {
        case CodeTypeNameS(nameStr) => primitives.get(nameStr)
        case _ => None
      }

    if (nearPrimitives.nonEmpty || nearStructs.nonEmpty || nearInterfaces.nonEmpty) {
      return (nearPrimitives, nearStructs, nearInterfaces)
    }
    maybeParentEnv match {
      case None => (None, List(), List())
      case Some(parentEnv) => parentEnv.lookupType(needleImpreciseNameS)
    }
  }

  def lookupType(name: INameS):
  (List[StructS], List[InterfaceS]) = {
    val nearStructs = structs.filter(_.name == name)
    val nearInterfaces = interfaces.filter(_.name == name)

    if (nearStructs.nonEmpty || nearInterfaces.nonEmpty) {
      return (nearStructs, nearInterfaces)
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
  def getImpl(name: INameA) = {
    astrouts.impls.get(name)
  }
  def getStruct(name: INameA) = {
    astrouts.structs.get(name)
  }
  def getInterface(name: INameA) = {
    astrouts.interfaces.get(name)
  }
}

case class Astrouts(
  structs: Map[INameA, StructA],
  interfaces: Map[INameA, InterfaceA],
  impls: Map[INameA, ImplA],
  functions: Map[INameA, FunctionA])

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
      case ImplNameS(_) => vwat()
      case LetNameS(_) => vwat()
      case UnnamedLocalNameS(_) => vwat()
      case ClosureParamNameS() => vwat()
      case MagicParamNameS(_) => vwat()
      case CodeVarNameS(_) => vwat()
    }

    val (structsS, interfacesS) = env.lookupType(name)

    if (structsS.isEmpty && interfacesS.isEmpty) {
      vfail("Nothing found with name " + name)
    }
    if (structsS.size.signum + interfacesS.size.signum > 1) {
      vfail("Name doesn't correspond to only one of primitive or struct or interface: " + name)
    }

    if (structsS.nonEmpty) {
      val types = structsS.map(lookupStructType(astrouts, env, _))
      if (types.toSet.size > 1) {
        vfail("'" + name + "' has multiple types: " + types.toSet)
      }
      val tyype = types.head
      tyype
    } else if (interfacesS.nonEmpty) {
      val types = interfacesS.map(lookupInterfaceType(astrouts, env, _))
      if (types.toSet.size > 1) {
        vfail("'" + name + "' has multiple types: " + types.toSet)
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
      ErrorReporter.report(CouldntFindType(range, name.name))
    }
    if (primitivesS.size.signum + structsS.size.signum + interfacesS.size.signum > 1) {
      vfail("Name doesn't correspond to only one of primitive or struct or interface: " + name)
    }

    if (primitivesS.nonEmpty) {
      vassert(primitivesS.size == 1)
      translateRuneType(primitivesS.get)
    } else if (structsS.nonEmpty) {
      val types = structsS.map(lookupStructType(astrouts, env, _))
      if (types.toSet.size > 1) {
        vfail("'" + name + "' has multiple types: " + types.toSet)
      }
      val tyype = types.head
      tyype
    } else if (interfacesS.nonEmpty) {
      val types = interfacesS.map(lookupInterfaceType(astrouts, env, _))
      if (types.toSet.size > 1) {
        vfail("'" + name + "' has multiple types: " + types.toSet)
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
    val StructS(nameS, export, weakable, mutabilityRuneS, maybePredictedMutabilityS, knowableRunesS, identifyingRunesS, localRunesS, predictedTypeByRune, isTemplate, rules, members) = structS
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
      makeRuleTyper().solve(astrouts, env, rules, List(), Some(localRunesA ++ knowableRunesA)) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _)) => vfail(rtsf.toString)
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
        case StructMemberS(name, variablility, typeRune) => StructMemberA(name, variablility, translateRune(typeRune))
      })

    StructA(
      nameA,
      export,
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

  def translateInterface(astrouts: AstroutsBox, env: Environment, interfaceS: InterfaceS): InterfaceA = {
    val InterfaceS(nameS, weakable, mutabilityRuneS, maybePredictedMutability, knowableRunesS, identifyingRunesS, localRunesS, predictedTypeByRune, isTemplate, rules, internalMethodsS) = interfaceS
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
      makeRuleTyper().solve(astrouts, env, rules, List(), Some(knowableRunesA ++ localRunesA)) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _)) => vfail(rtsf.toString)
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
        nameA,
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
    val ImplS(nameS, rules, knowableRunesS, localRunesS, isTemplate, structKindRuneS, interfaceKindRuneS) = implS
    val nameA = translateImplName(nameS)
    val localRunesA = localRunesS.map(Astronomer.translateRune)
    val knowableRunesA = knowableRunesS.map(Astronomer.translateRune)

    astrouts.getImpl(nameA) match {
      case Some(existingImplA) => return existingImplA
      case _ =>
    }

    val (conclusions, rulesA) =
      makeRuleTyper().solve(astrouts, env, rules, List(), Some(knowableRunesA ++ localRunesA)) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _)) => vfail(rtsf.toString)
        case (c, RuleTyperSolveSuccess(r)) => (c, r)
      }

    ImplA(
      nameA,
      rulesA,
      conclusions.typeByRune,
      localRunesA,
      translateRune(structKindRuneS),
      translateRune(interfaceKindRuneS))
  }

  def translateParameter(paramS: ParameterS): ParameterA = {
    val ParameterS(atomS) = paramS
    ParameterA(translateAtom(atomS))
  }

  def translateAtom(atomS: AtomSP): AtomAP = {
    val AtomSP(CaptureS(nameS, variability), virtualityS, coordRuneS, destructureS) = atomS
    val nameA = translateVarNameStep(nameS)

    val virtualityA =
      virtualityS.map({
        case AbstractSP => AbstractAP
        case OverrideSP(kindRune) => OverrideAP(translateRune(kindRune))
      })

    val coordRuneA = translateRune(coordRuneS)

    val destructureA = destructureS.map(_.map(translateAtom))

    AtomAP(CaptureA(nameA, variability), virtualityA, coordRuneA, destructureA)
  }

  def translateFunction(astrouts: AstroutsBox, env: Environment, functionS: FunctionS): FunctionA = {
    val FunctionS(nameS, knowableRunesS, identifyingRunesS, localRunesS, maybePredictedType, paramsS, maybeRetCoordRune, isTemplate, templateRules, bodyS) = functionS
    val nameA = translateFunctionDeclarationName(nameS)
    val knowableRunesA = knowableRunesS.map(Astronomer.translateRune)
    val localRunesA = localRunesS.map(Astronomer.translateRune)
    val identifyingRunesA = identifyingRunesS.map(Astronomer.translateRune)

    val paramsA = paramsS.map(translateParameter)

    val (conclusions, rulesA) =
      makeRuleTyper().solve(astrouts, env, templateRules, List(), Some(localRunesA)) match {
        case (_, rtsf @ RuleTyperSolveFailure(_, _, _)) => vfail(rtsf.toString)
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
      nameA,
      true,
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
      case CodeBody1(BodySE(closuredNamesS, blockS)) => {
        val blockA = ExpressionAstronomer.translateBlock(env, astrouts, blockS)
        CodeBodyA(BodyAE(closuredNamesS.map(translateVarNameStep), blockA))
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
      case i @ ImplNameS(_) => translateImplName(i)
      case LetNameS(codeLocation) => LetNameA(codeLocation)
      case UnnamedLocalNameS(codeLocation) => UnnamedLocalNameA(codeLocation)
      case ClosureParamNameS() => ClosureParamNameA()
      case MagicParamNameS(codeLocation) => MagicParamNameA(codeLocation)
      case CodeVarNameS(name) => CodeVarNameA(name)
    }
  }

  def translateImplName(s: ImplNameS): ImplNameA = {
    val ImplNameS(codeLocationS) = s;
    ImplNameA(codeLocationS)
  }

  def translateTopLevelCitizenDeclarationName(tlcd: TopLevelCitizenDeclarationNameS): TopLevelCitizenDeclarationNameA = {
    val TopLevelCitizenDeclarationNameS(name, codeLocation) = tlcd
    TopLevelCitizenDeclarationNameA(name, codeLocation)
  }

  def translateRune(rune: IRuneS): IRuneA = {
    rune match {
      case CodeRuneS(name) => CodeRuneA(name)
      case ImplicitRuneS(parentName, name) => ImplicitRuneA(translateName(parentName), name)
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
      programS: ProgramS,
      primitives: Map[String, ITypeSR],
      suppliedFunctions: List[FunctionA],
      suppliedInterfaces: List[InterfaceA]):
  ProgramA = {
    val ProgramS(structsS, interfacesS, implsS, functionsS) = programS

    val astrouts = AstroutsBox(Astrouts(Map(), Map(), Map(), Map()))


    val env = Environment(None, None, primitives, structsS, interfacesS, implsS, functionsS, Map())

    val structsA = structsS.map(translateStruct(astrouts, env, _))

    val interfacesA = interfacesS.map(translateInterface(astrouts, env, _))

    val implsA = implsS.map(translateImpl(astrouts, env, _))

    val functionsA = functionsS.map(translateFunction(astrouts, env, _))

    val _ = astrouts

    ProgramA(structsA, suppliedInterfaces ++ interfacesA, implsA, suppliedFunctions ++ functionsA)
  }


  val stlFunctions =
    Forwarders.forwarders ++
    List(
      NotEquals.function,
      Printing.printInt,
      Printing.printlnInt,
      Printing.printlnStr)

  val wrapperFunctions =
    List(
      Arrays.makeArrayFunction(MutableP),
      Arrays.makeArrayFunction(ImmutableP),
      RefCounting.checkmemberrc,
      RefCounting.checkvarrc)

  def runAstronomer(programS: ProgramS): Either[ProgramA, ICompileErrorA] = {
    try {
      val suppliedFunctions = stlFunctions ++ wrapperFunctions ++ Forwarders.forwarders ++ Externs.externs
      val suppliedInterfaces = List(IFunction1.interface)
      val ProgramA(originalStructs, originalInterfaces, originalImpls, originalImplementedFunctionsS) =
        Astronomer.translateProgram(programS, primitives, suppliedFunctions, suppliedInterfaces)
      val programA =
        ProgramA(
          originalStructs,
          originalInterfaces,
          originalImpls,
          originalImplementedFunctionsS)
      Left(programA)
    } catch {
      case CompileErrorExceptionA(err) => {
        Right(err)
      }
    }
  }
}
