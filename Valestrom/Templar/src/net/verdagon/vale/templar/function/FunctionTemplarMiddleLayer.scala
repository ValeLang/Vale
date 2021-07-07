package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.{AbstractAP, CodeBodyA, FunctionA, IRuneA, OverrideAP, ParameterA, VirtualityAP}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, OverrideSP, VirtualitySP}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.{IProfiler, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import net.verdagon.vale.templar.expression.CallTemplar

import scala.collection.immutable.{List, Set}

class FunctionTemplarMiddleLayer(
    opts: TemplarOptions,
  profiler: IProfiler,
  newTemplataStore: () => TemplatasStore,
  templataTemplar: TemplataTemplar,
  convertHelper: ConvertHelper,
    structTemplar: StructTemplar,
    delegate: IFunctionTemplarDelegate) {
  val core = new FunctionTemplarCore(opts, profiler, newTemplataStore, templataTemplar, convertHelper, delegate)

  // This is for the early stages of Templar when it's scanning banners to put in
  // its env. We just want its banner, we don't want to evaluate it.
  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def predictOrdinaryFunctionBanner(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    temputs: Temputs,
    function1: FunctionA):
  (FunctionBannerT) = {

    // Check preconditions
    function1.typeByRune.keySet.foreach(templateParam => {
      vassert(runedEnv.getNearestTemplataWithName(vimpl(templateParam.toString), Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty)
    })
    function1.body match {
      case CodeBodyA(body1) => vassert(body1.closuredNames.isEmpty)
      case _ =>
    }

    val params2 = assembleFunctionParams(runedEnv, temputs, function1.params)
    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune)
    val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)
    val banner = FunctionBannerT(Some(function1), namedEnv.fullName, params2)
    banner
  }

  private def evaluateMaybeVirtuality(
      env: IEnvironment,
      temputs: Temputs,
      maybeVirtuality1: Option[VirtualityAP]):
  (Option[VirtualityT]) = {
    maybeVirtuality1 match {
      case None => (None)
      case Some(AbstractAP) => (Some(AbstractT$))
      case Some(OverrideAP(range, interfaceRuneA)) => {
        env.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(interfaceRuneA), Set(TemplataLookupContext)) match {
          case None => vcurious()
          case Some(KindTemplata(ir @ InterfaceRefT(_))) => (Some(OverrideT(ir)))
          case Some(it @ InterfaceTemplata(_, _)) => {
            val ir =
              structTemplar.getInterfaceRef(temputs, range, it, List.empty)
            (Some(OverrideT(ir)))
          }
        }
      }
    }
  }

  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def getOrEvaluateFunctionForBanner(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    temputs: Temputs,
    callRange: RangeS,
    function1: FunctionA):
  (FunctionBannerT) = {

    // Check preconditions
    function1.typeByRune.keySet.foreach(templateParam => {
      vassert(runedEnv.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(templateParam), Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty);
    })

    val params2 = assembleFunctionParams(runedEnv, temputs, function1.params)

    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune)
    val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)
    val banner = FunctionBannerT(Some(function1), namedEnv.fullName, params2)

    // Now we want to add its Function2 into the temputs.
    temputs.getDeclaredSignatureOrigin(banner.toSignature) match {
      case Some(existingFunctionOrigin) => {
        if (function1.range != existingFunctionOrigin) {
          throw CompileErrorExceptionT(FunctionAlreadyExists(existingFunctionOrigin, function1.range, banner.toSignature))
        }
        // Someone else is already working on it (or has finished), so
        // just return.
        banner
      }
      case None => {
        val signature = banner.toSignature
        temputs.declareFunctionSignature(function1.range, signature, Some(namedEnv))
        val params2 = assembleFunctionParams(namedEnv, temputs, function1.params)
        val header =
          core.evaluateFunctionForHeader(namedEnv, temputs, callRange, params2)
        if (header.toBanner != banner) {
          val bannerFromHeader = header.toBanner
          vfail("wut\n" + bannerFromHeader + "\n" + banner)
        }

        delegate.evaluateParent(namedEnv, temputs, header)

        (header.toBanner)
      }
    }
  }

  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def getOrEvaluateFunctionForHeader(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    temputs: Temputs,
    callRange: RangeS,
    function1: FunctionA):
  (FunctionHeaderT) = {

    // Check preconditions
    function1.typeByRune.keySet.foreach(templateParam => {
      vassert(
        runedEnv
          .getNearestTemplataWithAbsoluteName2(
            NameTranslator.translateRune(templateParam),
            Set(TemplataLookupContext, ExpressionLookupContext))
          .nonEmpty);
    })

    val paramTypes2 = evaluateFunctionParamTypes(runedEnv, function1.params);
    val functionFullName = assembleName(runedEnv.fullName, paramTypes2)
    val needleSignature = SignatureT(functionFullName)
    temputs.lookupFunction(needleSignature) match {
      case Some(FunctionT(header, _, _)) => {
        (header)
      }
      case None => {
        val params2 = assembleFunctionParams(runedEnv, temputs, function1.params)

        val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune)
        val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)

        temputs.declareFunctionSignature(function1.range, needleSignature, Some(namedEnv))

        val header =
          core.evaluateFunctionForHeader(
            namedEnv, temputs, callRange, params2)
        vassert(header.toSignature == needleSignature)
        (header)
      }
    }
  }

//  def makeInterfaceFunction(
//     env: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
//     temputs: Temputs):
//  (FunctionHeader2) = {
//    val params2 =
//      FunctionTemplarMiddleLayer.assembleFunctionParams(
//        env, temputs, env.function.params)
//
//    val returnType = vassertSome(getMaybeReturnType(env, env.function.maybeRetCoordRune))
//
//    val newEnv = makeNamedEnv(env, params2.map(_.tyype), Some(returnType))
//
//    core.makeInterfaceFunction(newEnv, temputs, Some(env.function), params2, returnType)
//  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // fn main():Int{main()}
  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def getOrEvaluateFunctionForPrototype(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    temputs: Temputs,
    callRange: RangeS,
    function1: FunctionA):
  (PrototypeT) = {

    // Check preconditions
    function1.typeByRune.keySet.foreach(templateParam => {
      vassert(
        runedEnv.getNearestTemplataWithAbsoluteName2(
          NameTranslator.translateRune(templateParam),
          Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty);
    })

    val paramTypes2 = evaluateFunctionParamTypes(runedEnv, function1.params)
    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune)
    val namedEnv = makeNamedEnv(runedEnv, paramTypes2, maybeReturnType)
    val needleSignature = SignatureT(namedEnv.fullName)

    temputs.getDeclaredSignatureOrigin(needleSignature) match {
      case None => {
        temputs.declareFunctionSignature(function1.range, needleSignature, Some(namedEnv))
        val params2 = assembleFunctionParams(namedEnv, temputs, function1.params)
        val header =
          core.evaluateFunctionForHeader(
            namedEnv, temputs, callRange, params2)

        delegate.evaluateParent(namedEnv, temputs, header)

        vassert(header.toSignature == needleSignature)
        (header.toPrototype)
      }
      case Some(existingOriginS) => {
        if (existingOriginS != function1.range) {
          throw CompileErrorExceptionT(FunctionAlreadyExists(existingOriginS, function1.range, needleSignature))
        }
        temputs.getReturnTypeForSignature(needleSignature) match {
          case Some(returnType2) => {
            (PrototypeT(namedEnv.fullName, returnType2))
          }
          case None => {
            throw CompileErrorExceptionT(RangedInternalErrorT(runedEnv.function.range, "Need return type for " + needleSignature + ", cycle found"))
          }
        }
      }
    }
  }



  private def evaluateFunctionParamTypes(
    env: IEnvironment,
    params1: List[ParameterA]):
  List[CoordT] = {
    params1.map(param1 => {
      val CoordTemplata(coord) =
        env
          .getNearestTemplataWithAbsoluteName2(
            NameTranslator.translateRune(param1.pattern.coordRune),
            Set(TemplataLookupContext))
          .get
      coord
    })
  }

  def assembleFunctionParams(
    env: IEnvironment,
    temputs: Temputs,
    params1: List[ParameterA]):
  (List[ParameterT]) = {
    params1.zipWithIndex.map({ case (param1, index) =>
        val CoordTemplata(coord) =
          env
            .getNearestTemplataWithAbsoluteName2(
              NameTranslator.translateRune(param1.pattern.coordRune),
              Set(TemplataLookupContext))
            .get
        val maybeVirtuality = evaluateMaybeVirtuality(env, temputs, param1.pattern.virtuality)
        val nameT =
          param1.pattern.capture match {
            case None => TemplarIgnoredParamNameT(index)
            case Some(x) => NameTranslator.translateVarNameStep(x.varName)
          }
        ParameterT(nameT, maybeVirtuality, coord)
      })
  }

//  def makeImplDestructor(
//    env: IEnvironment,
//    temputs: Temputs,
//    structDefT: StructDefinition2,
//    interfaceRef2: InterfaceRef2):
//  Temputs = {
//    val ownership = if (structDefT.mutability == MutableP) Own else Share
//    val structRefT = structDefT.getRef
//    val structType2 = Coord(ownership, structRefT)
//    val interfaceType2 = Coord(ownership, interfaceRef2)
//    val signature2 =
//      Signature2(
//        CallTemplar.INTERFACE_DESTRUCTOR_NAME,
//        List(CoercedFinalTemplateArg2(ReferenceTemplata(interfaceType2))),
//        List(structType2))
//    temputs.declareFunctionSignature(signature2)
//
//    val header =
//      core.makeImplDestructor(
//        env, temputs, structDefT, interfaceRef2)
//
//
//      VirtualTemplar.evaluateParent(env, temputs, header)
//
//
//      VirtualTemplar.evaluateOverrides(env, temputs, header)
//
//    temputs
//  }

  def makeNamedEnv(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    paramTypes: List[CoordT],
    maybeReturnType: Option[CoordT]
  ): FunctionEnvironment = {
    val BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs(parentEnv, oldName, function, variables, entries) = runedEnv

    // We fill out the params here to get the function's full name.
    val newName = assembleName(oldName, paramTypes)

    FunctionEnvironment(parentEnv, newName, function, entries, maybeReturnType, 0, variables, Set())
  }

  private def assembleName(
    name: FullNameT[BuildingFunctionNameWithClosuredsAndTemplateArgsT],
    params: List[CoordT]):
  FullNameT[IFunctionNameT] = {
    val BuildingFunctionNameWithClosuredsAndTemplateArgsT(templateName, templateArgs) = name.last
    val newLastStep =
      templateName match {
        case ConstructorTemplateNameT(_) => vimpl() // no idea
        case FunctionTemplateNameT(humanName, _) => FunctionNameT(humanName, templateArgs, params)
        case LambdaTemplateNameT(_) => FunctionNameT(CallTemplar.CALL_FUNCTION_NAME, templateArgs, params)
        case ImmConcreteDestructorTemplateNameT() => {
          val List(CoordT(ShareT, ReadonlyT, immRef)) = params
          ImmConcreteDestructorNameT(immRef)
        }
        case ImmInterfaceDestructorTemplateNameT() => {
          ImmInterfaceDestructorNameT(templateArgs, params)
        }
        case ImmDropTemplateNameT() => {
          val List(CoordT(ShareT, ReadonlyT, kind)) = params
          ImmDropNameT(kind)
        }
      }
    FullNameT(name.packageCoord, name.initSteps, newLastStep)
  }

  private def getMaybeReturnType(
    nearEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    maybeRetCoordRune: Option[IRuneA]
  ): Option[CoordT] = {
    maybeRetCoordRune.map(retCoordRuneA => {
      val retCoordRune = NameTranslator.translateRune(retCoordRuneA)
      nearEnv.getNearestTemplataWithAbsoluteName2(retCoordRune, Set(TemplataLookupContext)) match {
        case Some(CoordTemplata(coord)) => coord
        case None => vwat(retCoordRune.toString)
      }
    })
  }
}
