package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns._
import net.verdagon.vale.templar.{ast, names, _}
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.{Profiler, Interner, RangeS, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.names.{AnonymousSubstructConstructorNameT, AnonymousSubstructConstructorTemplateNameT, BuildingFunctionNameWithClosuredsAndTemplateArgsT, ConstructorTemplateNameT, FreeTemplateNameT, FullNameT, FunctionNameT, FunctionTemplateNameT, ICitizenNameT, ICitizenTemplateNameT, IFunctionNameT, LambdaTemplateNameT, NameTranslator, TemplarIgnoredParamNameT}

import scala.collection.immutable.{List, Set}

class FunctionTemplarMiddleLayer(
    opts: TemplarOptions,

    interner: Interner,
    nameTranslator: NameTranslator,
    templataTemplar: TemplataTemplar,
    convertHelper: ConvertHelper,
    structTemplar: StructTemplar,
    delegate: IFunctionTemplarDelegate) {
  val core = new FunctionTemplarCore(opts, interner, nameTranslator, templataTemplar, convertHelper, delegate)

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
    function1.runeToType.keySet.foreach(templateParam => {
      vassert(runedEnv.lookupNearestWithImpreciseName(vimpl(templateParam.toString), Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty)
    })
    function1.body match {
      case CodeBodyS(body1) => vassert(body1.closuredNames.isEmpty)
      case _ =>
    }

    val params2 = assembleFunctionParams(runedEnv, temputs, function1.params)
    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
    val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)
    val banner = ast.FunctionBannerT(Some(function1), namedEnv.fullName, params2)
    banner
  }

  private def evaluateMaybeVirtuality(
      env: IEnvironment,
      temputs: Temputs,
      paramKind: KindT,
      maybeVirtuality1: Option[AbstractSP]):
  (Option[AbstractT]) = {
    maybeVirtuality1 match {
      case None => (None)
      case Some(AbstractSP(rangeS, isInternalMethod)) => {
        val interfaceTT =
          paramKind match {
            case i @ InterfaceTT(_) => i
            case _ => throw CompileErrorExceptionT(RangedInternalErrorT(rangeS, "Can only have virtual parameters for interfaces"))
          }
        // Open (non-sealed) interfaces can't have abstract methods defined outside the interface.
        // See https://github.com/ValeLang/Vale/issues/374
        if (!isInternalMethod) {
          val interfaceDef = temputs.getInterfaceDefForRef(interfaceTT)
          if (!interfaceDef.attributes.contains(SealedT)) {
            throw CompileErrorExceptionT(AbstractMethodOutsideOpenInterface(rangeS))
          }
        }
        (Some(AbstractT()))
      }
//      case Some(OverrideSP(range, interfaceRuneA)) => {
//        val interface =
//          env.lookupNearestWithImpreciseName(interner.intern(RuneNameS(interfaceRuneA.rune)), Set(TemplataLookupContext)) match {
//            case None => vcurious()
//            case Some(KindTemplata(ir @ InterfaceTT(_))) => ir
//            case Some(it @ InterfaceTemplata(_, _)) => structTemplar.getInterfaceRef(temputs, range, it, Vector.empty)
//            case Some(KindTemplata(kind)) => {
//              throw CompileErrorExceptionT(CantImplNonInterface(range, kind))
//            }
//            case _ => vwat()
//          }
//        Some(OverrideT(interface))
//      }
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
    function1.runeToType.keySet.foreach(templateParam => {
      vassert(runedEnv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(templateParam)), Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty);
    })

    val params2 = assembleFunctionParams(runedEnv, temputs, function1.params)

    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
    val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)
    val banner = ast.FunctionBannerT(Some(function1), namedEnv.fullName, params2)

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
        if (!header.toBanner.same(banner)) {
          val bannerFromHeader = header.toBanner
          vfail("wut\n" + bannerFromHeader + "\n" + banner)
        }

//        delegate.evaluateParent(namedEnv, temputs, callRange, header)

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
    function1.runeToType.keySet.foreach(templateParam => {
      vassert(
        runedEnv
          .lookupNearestWithImpreciseName(
            interner.intern(RuneNameS(templateParam)),
            Set(TemplataLookupContext, ExpressionLookupContext))
          .nonEmpty);
    })

    val paramTypes2 = evaluateFunctionParamTypes(runedEnv, function1.params);
    val functionFullName = assembleName(runedEnv.fullName, paramTypes2)
    val needleSignature = SignatureT(functionFullName)
    temputs.lookupFunction(needleSignature) match {
      case Some(FunctionT(header, _)) => {
        (header)
      }
      case None => {
        val params2 = assembleFunctionParams(runedEnv, temputs, function1.params)

        val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
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

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
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
    function1.runeToType.keySet.foreach(templateParam => {
      vassert(
        runedEnv.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(templateParam)),
          Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty);
    })

    val paramTypes2 = evaluateFunctionParamTypes(runedEnv, function1.params)
    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
    val namedEnv = makeNamedEnv(runedEnv, paramTypes2, maybeReturnType)
    val needleSignature = ast.SignatureT(namedEnv.fullName)

    temputs.getDeclaredSignatureOrigin(needleSignature) match {
      case None => {
        temputs.declareFunctionSignature(function1.range, needleSignature, Some(namedEnv))
        val params2 = assembleFunctionParams(namedEnv, temputs, function1.params)
        val header =
          core.evaluateFunctionForHeader(
            namedEnv, temputs, callRange, params2)

//        delegate.evaluateParent(namedEnv, temputs, function1.range, header)

        vassert(header.toSignature == needleSignature)
        (header.toPrototype)
      }
      case Some(existingOriginS) => {
        if (existingOriginS != function1.range) {
          throw CompileErrorExceptionT(FunctionAlreadyExists(existingOriginS, function1.range, needleSignature))
        }
        temputs.getReturnTypeForSignature(needleSignature) match {
          case Some(returnType2) => {
            (ast.PrototypeT(namedEnv.fullName, returnType2))
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
    params1: Vector[ParameterS]):
  Vector[CoordT] = {
    params1.map(param1 => {
      val CoordTemplata(coord) =
        env
          .lookupNearestWithImpreciseName(
            interner.intern(RuneNameS(param1.pattern.coordRune.get.rune)),
            Set(TemplataLookupContext))
          .get
      coord
    })
  }

  def assembleFunctionParams(
    env: IEnvironment,
    temputs: Temputs,
    params1: Vector[ParameterS]):
  (Vector[ParameterT]) = {
    params1.zipWithIndex.map({ case (param1, index) =>
        val CoordTemplata(coord) =
          vassertSome(
            env
              .lookupNearestWithImpreciseName(

                interner.intern(RuneNameS(param1.pattern.coordRune.get.rune)),
                Set(TemplataLookupContext)))
        val maybeVirtuality = evaluateMaybeVirtuality(env, temputs, coord.kind, param1.pattern.virtuality)
        val nameT =
          param1.pattern.name match {
            case None => interner.intern(TemplarIgnoredParamNameT(index))
            case Some(x) => nameTranslator.translateVarNameStep(x.name)
          }
        ast.ParameterT(nameT, maybeVirtuality, coord)
      })
  }

  def makeNamedEnv(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    paramTypes: Vector[CoordT],
    maybeReturnType: Option[CoordT]
  ): FunctionEnvironment = {
    val BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs(globalEnv, parentEnv, oldName, templatas, function, variables) = runedEnv

    // We fill out the params here to get the function's full name.
    val newName = assembleName(oldName, paramTypes)

    FunctionEnvironment(globalEnv, parentEnv, newName, templatas, function, maybeReturnType, variables)
  }

  private def assembleName(
    name: FullNameT[BuildingFunctionNameWithClosuredsAndTemplateArgsT],
    params: Vector[CoordT]):
  FullNameT[IFunctionNameT] = {
    val BuildingFunctionNameWithClosuredsAndTemplateArgsT(templateName, templateArgs) = name.last
    val newLastStep = templateName.makeFunctionName(interner, templateArgs, params)
    names.FullNameT(name.packageCoord, name.initSteps, newLastStep)
  }

  private def getMaybeReturnType(
    nearEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
    maybeRetCoordRune: Option[IRuneS]
  ): Option[CoordT] = {
    maybeRetCoordRune.map(retCoordRuneA => {
      val retCoordRune = (retCoordRuneA)
      nearEnv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(retCoordRune)), Set(TemplataLookupContext)) match {
        case Some(CoordTemplata(coord)) => coord
        case _ => vwat(retCoordRune.toString)
      }
    })
  }
}
