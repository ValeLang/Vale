package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.{AbstractAP, CodeBodyA, FunctionA, IRuneA, OverrideAP, ParameterA, VirtualityAP}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, OverrideSP, VirtualitySP}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Set}

class FunctionTemplarMiddleLayer(
    opts: TemplarOptions,
  templataTemplar: TemplataTemplar,
  convertHelper: ConvertHelper,
    structTemplar: StructTemplar,
    delegate: IFunctionTemplarDelegate) {
  val core = new FunctionTemplarCore(opts, templataTemplar, convertHelper, delegate)

  // This is for the early stages of Templar when it's scanning banners to put in
  // its env. We just want its banner, we don't want to evaluate it.
  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def predictOrdinaryFunctionBanner(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    temputs: TemputsBox,
    function1: FunctionA):
  (FunctionBanner2) = {

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
    val banner = FunctionBanner2(Some(function1), namedEnv.fullName, params2)
    banner
  }

  private def evaluateMaybeVirtuality(
      env: IEnvironment,
      temputs: TemputsBox,
      maybeVirtuality1: Option[VirtualityAP]):
  (Option[Virtuality2]) = {
    maybeVirtuality1 match {
      case None => (None)
      case Some(AbstractAP) => (Some(Abstract2))
      case Some(OverrideAP(range, interfaceRuneA)) => {
        env.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(interfaceRuneA), Set(TemplataLookupContext)) match {
          case None => vcurious()
          case Some(KindTemplata(ir @ InterfaceRef2(_))) => (Some(Override2(ir)))
          case Some(it @ InterfaceTemplata(_, _)) => {
            val ir =
              structTemplar.getInterfaceRef(temputs, range, it, List())
            (Some(Override2(ir)))
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
    temputs: TemputsBox,
    callRange: RangeS,
    function1: FunctionA):
  (FunctionBanner2) = {

    // Check preconditions
    function1.typeByRune.keySet.foreach(templateParam => {
      vassert(runedEnv.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(templateParam), Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty);
    })

    val params2 = assembleFunctionParams(runedEnv, temputs, function1.params)

    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune)
    val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)
    val banner = FunctionBanner2(Some(function1), namedEnv.fullName, params2)

    // Now we want to add its Function2 into the temputs.
    if (temputs.exactDeclaredSignatureExists(banner.toSignature)) {
      // Someone else is already working on it (or has finished), so
      // just return.
      (banner)
    } else {
      val signature = banner.toSignature
      temputs.declareFunctionSignature(signature, Some(namedEnv))
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

  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def getOrEvaluateFunctionForHeader(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    temputs: TemputsBox,
    callRange: RangeS,
    function1: FunctionA):
  (FunctionHeader2) = {

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
    val needleSignature = Signature2(functionFullName)
    temputs.lookupFunction(needleSignature) match {
      case Some(Function2(header, _, _)) => {
        (header)
      }
      case None => {
        val params2 = assembleFunctionParams(runedEnv, temputs, function1.params)

        val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune)
        val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)

        temputs.declareFunctionSignature(needleSignature, Some(namedEnv))

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
//     temputs: TemputsBox):
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
    temputs: TemputsBox,
    callRange: RangeS,
    function1: FunctionA):
  (Prototype2) = {

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
    val needleSignature = Signature2(namedEnv.fullName)
    temputs.returnTypesBySignature.get(needleSignature) match {
      case Some(returnType2) => {
        (Prototype2(namedEnv.fullName, returnType2))
      }
      case None => {
        if (temputs.exactDeclaredSignatureExists(needleSignature)) {
          vfail("Need return type for " + needleSignature + ", cycle found")
        }
        temputs.declareFunctionSignature(needleSignature, Some(namedEnv))
        val params2 = assembleFunctionParams(namedEnv, temputs, function1.params)
        val header =
          core.evaluateFunctionForHeader(
            namedEnv, temputs, callRange, params2)

        delegate.evaluateParent(namedEnv, temputs, header)

        vassert(header.toSignature == needleSignature)
        (header.toPrototype)
      }
    }
  }



  private def evaluateFunctionParamTypes(
    env: IEnvironment,
    params1: List[ParameterA]):
  List[Coord] = {
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
    temputs: TemputsBox,
    params1: List[ParameterA]):
  (List[Parameter2]) = {
    params1.foldLeft((List[Parameter2]()))({
      case ((previousParams2), param1) => {
        val CoordTemplata(coord) =
          env
            .getNearestTemplataWithAbsoluteName2(
              NameTranslator.translateRune(param1.pattern.coordRune),
              Set(TemplataLookupContext))
            .get
        val maybeVirtuality =
          evaluateMaybeVirtuality(env, temputs, param1.pattern.virtuality)
        val newParam2 =
          Parameter2(
            NameTranslator.translateVarNameStep(param1.name.name),
            maybeVirtuality,
            coord)
        (previousParams2 :+ newParam2)
      }
    })
  }

//  def makeImplDestructor(
//    env: IEnvironment,
//    temputs: TemputsBox,
//    structDef2: StructDefinition2,
//    interfaceRef2: InterfaceRef2):
//  Temputs = {
//    val ownership = if (structDef2.mutability == MutableP) Own else Share
//    val structRef2 = structDef2.getRef
//    val structType2 = Coord(ownership, structRef2)
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
//        env, temputs, structDef2, interfaceRef2)
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
    paramTypes: List[Coord],
    maybeReturnType: Option[Coord]
  ): FunctionEnvironment = {
    val BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs(parentEnv, oldName, function, variables, entries) = runedEnv

    // We fill out the params here to get the function's full name.
    val newName = assembleName(oldName, paramTypes)

    FunctionEnvironment(parentEnv, newName, function, entries, maybeReturnType, List(), 0, variables, Set())
  }

  private def assembleName(
    name: FullName2[BuildingFunctionNameWithClosuredsAndTemplateArgs2],
    params: List[Coord]):
  FullName2[IFunctionName2] = {
    val BuildingFunctionNameWithClosuredsAndTemplateArgs2(templateName, templateArgs) = name.last
    val newLastStep =
      templateName match {
        case ConstructorTemplateName2(_) => vimpl() // no idea
        case FunctionTemplateName2(humanName, _) => FunctionName2(humanName, templateArgs, params)
        case LambdaTemplateName2(_) => FunctionName2(CallTemplar.CALL_FUNCTION_NAME, templateArgs, params)
        case ImmConcreteDestructorTemplateName2() => {
          val List(Coord(Share, immRef)) = params
          ImmConcreteDestructorName2(immRef)
        }
        case ImmInterfaceDestructorTemplateName2() => {
          ImmInterfaceDestructorName2(templateArgs, params)
        }
        case ImmDropTemplateName2() => {
          val List(Coord(Share, kind)) = params
          ImmDropName2(kind)
        }
      }
    FullName2(name.initSteps, newLastStep)
  }

  private def getMaybeReturnType(
    nearEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    maybeRetCoordRune: Option[IRuneA]
  ): Option[Coord] = {
    maybeRetCoordRune.map(retCoordRuneA => {
      val retCoordRune = NameTranslator.translateRune(retCoordRuneA)
      nearEnv.getNearestTemplataWithAbsoluteName2(retCoordRune, Set(TemplataLookupContext)) match {
        case Some(CoordTemplata(coord)) => coord
        case None => vwat(retCoordRune.toString)
      }
    })
  }
}
