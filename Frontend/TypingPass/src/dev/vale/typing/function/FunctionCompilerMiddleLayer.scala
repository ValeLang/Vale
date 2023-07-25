package dev.vale.typing.function

import dev.vale.{Interner, Keywords, Profiler, RangeS, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing._
import dev.vale.postparsing.patterns._
import dev.vale.typing.{AbstractMethodOutsideOpenInterface, CompileErrorExceptionT, CompilerOutputs, ConvertHelper, FunctionAlreadyExists, RangedInternalErrorT, TemplataCompiler, TypingPassOptions, ast, env}
import dev.vale.typing.ast.{AbstractT, FunctionBannerT, FunctionHeaderT, FunctionDefinitionT, ParameterT, PrototypeT, SealedT, SignatureT}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.{BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT, ExpressionLookupContext, FunctionEnvironmentT, IInDenizenEnvironmentT, TemplataLookupContext}
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.names.{AnonymousSubstructConstructorNameT, IdT, IFunctionNameT, IFunctionTemplateNameT, NameTranslator, TypingIgnoredParamNameT}
import dev.vale.typing.templata.CoordTemplataT
import dev.vale.typing.types._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing.patterns._
import dev.vale.typing.{ast, names, _}
import dev.vale.typing.ast._
import dev.vale.typing.env._

import scala.collection.immutable.{List, Set}

class FunctionCompilerMiddleLayer(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,
    templataCompiler: TemplataCompiler,
    convertHelper: ConvertHelper,
    structCompiler: StructCompiler,
    delegate: IFunctionCompilerDelegate) {
  val core = new FunctionCompilerCore(opts, interner, keywords, nameTranslator, templataCompiler, convertHelper, delegate)

//  // This is for the early stages of Compiler when it's scanning banners to put in
//  // its env. We just want its banner, we don't want to evaluate it.
//  // Preconditions:
//  // - already spawned local env
//  // - either no template args, or they were already added to the env.
//  // - either no closured vars, or they were already added to the env.
//  def predictOrdinaryFunctionBanner(
//    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
//    coutputs: CompilerOutputs,
//    parentRanges: List[RangeS],
//    function1: FunctionA):
//  (FunctionBannerT) = {
//
//    // Check preconditions
//    function1.runeToType.keySet.foreach(templateParam => {
//      vassert(runedEnv.lookupNearestWithImpreciseName(vimpl(templateParam.toString), Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty)
//    })
//    function1.body match {
//      case CodeBodyS(body1) => vassert(body1.closuredNames.isEmpty)
//      case _ =>
//    }
//
//    val params2 = assembleFunctionParams(runedEnv, coutputs, parentRanges, function1.params)
//    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
//    val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)
//    val banner = FunctionBannerT(Some(function1), namedEnv.fullName)
//    banner
//  }

  private def evaluateMaybeVirtuality(
    env: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    paramKind: KindT,
    maybeVirtuality1: Option[AbstractSP]):
  (Option[AbstractT]) = {
    maybeVirtuality1 match {
      case None => (None)
      case Some(AbstractSP(rangeS, isInternalMethod)) => {
        val interfaceTT =
          paramKind match {
            case i @ InterfaceTT(_) => i
            case _ => throw CompileErrorExceptionT(RangedInternalErrorT(rangeS :: parentRanges, "Can only have virtual parameters for interfaces"))
          }
        // Open (non-sealed) interfaces can't have abstract methods defined outside the interface.
        // See https://github.com/ValeLang/Vale/issues/374
        if (!isInternalMethod) {
          if (!coutputs.lookupSealed(TemplataCompiler.getInterfaceTemplate(interfaceTT.id))) {
            // Macros can put e.g. functions inside an interface by prefixing the function name
            // with the interface.
            // For example, InterfaceFreeMacro will look at mymod.MyInterface and conjure a
            // mymod.MyInterface.free function.
            if (env.id.steps.init != TemplataCompiler.getInterfaceTemplate(interfaceTT.id).steps) {
              throw CompileErrorExceptionT(AbstractMethodOutsideOpenInterface(rangeS :: parentRanges))
            }
          }
        }
        (Some(AbstractT()))
      }
//      case Some(OverrideSP(range, interfaceRuneA)) => {
//        val interface =
//          env.lookupNearestWithImpreciseName(interner.intern(RuneNameS(interfaceRuneA.rune)), Set(TemplataLookupContext)) match {
//            case None => vcurious()
//            case Some(KindTemplata(ir @ InterfaceTT(_))) => ir
//            case Some(it @ InterfaceTemplata(_, _)) => structCompiler.getInterfaceRef(coutputs, range, it, Vector.empty)
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
  def getOrEvaluateTemplatedFunctionForBanner(
    outerEnv: BuildingFunctionEnvironmentWithClosuredsT,
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    function1: FunctionA):
  (PrototypeTemplataT) = {
    // Check preconditions
    function1.runeToType.keySet.foreach(templateParam => {
      vassert(runedEnv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(templateParam)), Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty);
    })

    val params2 = assembleFunctionParams(runedEnv, coutputs, callRange, function1.params)

    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
    val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)
    val banner = ast.FunctionBannerT(Some(namedEnv.templata), namedEnv.id)//, params2)

    coutputs.lookupFunction(SignatureT(banner.name)) match {
      case Some(FunctionDefinitionT(header, _, _, _)) => {
        PrototypeTemplataT(function1.range, header.toPrototype)
      }
      case None => {
        coutputs.declareFunction(callRange, namedEnv.id)
        coutputs.declareFunctionOuterEnv(outerEnv.id, outerEnv)
        coutputs.declareFunctionInnerEnv(namedEnv.id, runedEnv) // DO NOT SUBMIT

        val header =
          core.evaluateFunctionForHeader(namedEnv, coutputs, callRange, callLocation, params2)
        if (!header.toBanner.same(banner)) {
          val bannerFromHeader = header.toBanner
          vfail("wut\n" + bannerFromHeader + "\n" + banner)
        }

        //        delegate.evaluateParent(namedEnv, coutputs, callRange, header)

        PrototypeTemplataT(function1.range, header.toPrototype)
      }
    }
  }

  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def getOrEvaluateFunctionForHeader(
    outerEnv: BuildingFunctionEnvironmentWithClosuredsT,
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    function1: FunctionA):
  (FunctionHeaderT) = {

    // Check preconditions
    function1.runeToType.keySet.foreach(rune => {
      vassert(
        runedEnv
          .lookupNearestWithImpreciseName(
            interner.intern(RuneNameS(rune)),
            Set(TemplataLookupContext, ExpressionLookupContext))
          .nonEmpty)
    })

    val paramTypes2 = evaluateFunctionParamTypes(runedEnv, function1.params);

    val functionId = assembleName(runedEnv.id, runedEnv.templateArgs, paramTypes2)
    val needleSignature = SignatureT(functionId)
    coutputs.lookupFunction(needleSignature) match {
      case Some(FunctionDefinitionT(header, _, _, _)) => {
        (header)
      }
      case None => {
        coutputs.declareFunction(callRange, functionId)
        coutputs.declareFunctionOuterEnv(outerEnv.id, outerEnv)
        coutputs.declareFunctionInnerEnv(functionId, runedEnv) // DO NOT SUBMIT

        val params2 = assembleFunctionParams(runedEnv, coutputs, callRange, function1.params)

        val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
        val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)

//        coutputs.declareFunctionSignature(function1.range, needleSignature, Some(namedEnv))

        val header =
          core.evaluateFunctionForHeader(
            namedEnv, coutputs, callRange, callLocation, params2)
        vassert(header.toSignature == needleSignature)
        (header)
      }
    }
  }

//  // Preconditions:
//  // - already spawned local env
//  // - either no template args, or they were already added to the env.
//  // - either no closured vars, or they were already added to the env.
//  def getOrEvaluateOrdinaryFunctionForPrototype(
//    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS],
//    function1: FunctionA):
//  (PrototypeTemplata) = {
//    // Check preconditions
//    function1.runeToType.keySet.foreach(templateParam => {
//      vassert(
//        runedEnv
//          .lookupNearestWithImpreciseName(
//            interner.intern(RuneNameS(templateParam)),
//            Set(TemplataLookupContext, ExpressionLookupContext))
//          .nonEmpty);
//    })
//
//    val paramTypes2 = evaluateFunctionParamTypes(runedEnv, function1.params);
//    val functionId = assembleName(runedEnv.fullName, runedEnv.templateArgs, paramTypes2)
//    val needleSignature = SignatureT(functionId)
//    val p = vassertSome(coutputs.lookupFunction(needleSignature)).header.toPrototype
//    PrototypeTemplata(function1.range, p)
//  }

//  // We would want only the prototype instead of the entire header if, for example,
//  // we were calling the function. This is necessary for a recursive function like
//  // func main():Int{main()}
//  // Preconditions:
//  // - already spawned local env
//  // - either no template args, or they were already added to the env.
//  // - either no closured vars, or they were already added to the env.
//  def getFunctionPrototype(
//    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS],
//    function1: FunctionA):
//  (PrototypeT) = {
//
//    // Check preconditions
//    function1.runeToType.keySet.foreach(templateParam => {
//      vassert(
//        runedEnv.lookupNearestWithImpreciseName(
//          interner.intern(RuneNameS(templateParam)),
//          Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty);
//    })
//
//    val paramTypes2 = evaluateFunctionParamTypes(runedEnv, function1.params)
//    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
//    val namedEnv = makeNamedEnv(runedEnv, paramTypes2, maybeReturnType)
//    val needleSignature = SignatureT(namedEnv.fullName)
//
//    val params2 = assembleFunctionParams(namedEnv, coutputs, function1.params)
//    val prototype =
//      core.getFunctionPrototypeForCall(
//        namedEnv, coutputs, callRange, params2)
//
//    vassert(prototype.toSignature == needleSignature)
//    prototype
//  }


  // DO NOT SUBMIT private
  def evaluateFunctionParamTypes(
    env: IInDenizenEnvironmentT,
    params1: Vector[ParameterS]):
  Vector[CoordT] = {
    params1.map(param1 => {
      val CoordTemplataT(coord) =
        env
          .lookupNearestWithImpreciseName(
            interner.intern(RuneNameS(param1.pattern.coordRune.get.rune)),
            Set(TemplataLookupContext))
          .get
      coord
    })
  }

  def assembleFunctionParams(
    env: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    params1: Vector[ParameterS]):
  (Vector[ParameterT]) = {
    params1.zipWithIndex.map({ case (param1, index) =>
        val CoordTemplataT(coord) =
          vassertSome(
            env
              .lookupNearestWithImpreciseName(

                interner.intern(RuneNameS(param1.pattern.coordRune.get.rune)),
                Set(TemplataLookupContext)))
        val maybeVirtuality =
          evaluateMaybeVirtuality(
            env, coutputs, parentRanges, coord.kind, param1.virtuality)
        val nameT =
          param1.pattern.name match {
            case None => interner.intern(TypingIgnoredParamNameT(index))
            case Some(x) => nameTranslator.translateVarNameStep(x.name)
          }
        ParameterT(nameT, maybeVirtuality, param1.preChecked, coord)
      })
  }

  private def getMaybeReturnType(
    nearEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT,
    maybeRetCoordRune: Option[IRuneS]
  ): Option[CoordT] = {
    maybeRetCoordRune.map(retCoordRuneA => {
      val retCoordRune = (retCoordRuneA)
      nearEnv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(retCoordRune)), Set(TemplataLookupContext)) match {
        case Some(CoordTemplataT(coord)) => coord
        case other => vwat(retCoordRune, other)
      }
    })
  }

  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def getGenericFunctionBannerFromCall(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    functionTemplata: FunctionTemplataT):
  (FunctionBannerT) = {
    val function1 = functionTemplata.function

    // Check preconditions
    function1.runeToType.keySet.foreach(templateParam => {
      vassert(runedEnv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(templateParam)), Set(TemplataLookupContext, ExpressionLookupContext)).nonEmpty);
    })

    val params2 = assembleFunctionParams(runedEnv, coutputs, callRange, function1.params)

    val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
    val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)
    val banner = ast.FunctionBannerT(Some(functionTemplata), namedEnv.id)//, params2)
    banner
  }

  def getGenericFunctionPrototypeFromCall(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
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
    val needleSignature = SignatureT(namedEnv.id)

    //    coutputs.getDeclaredSignatureOrigin(needleSignature) match {
    //      case None => {
    //        coutputs.declareFunctionSignature(function1.range, needleSignature, Some(namedEnv))
    val params2 = assembleFunctionParams(namedEnv, coutputs, callRange, function1.params)

    val prototype =
      core.getFunctionPrototypeForCall(
        namedEnv, coutputs, callRange, params2)

    vassert(prototype.toSignature == needleSignature)
    (prototype)
    //      }
    //      case Some(existingOriginS) => {
    //        if (existingOriginS != function1.range) {
    //          throw CompileErrorExceptionT(FunctionAlreadyExists(existingOriginS, function1.range, needleSignature))
    //        }
    //        coutputs.getReturnTypeForSignature(needleSignature) match {
    //          case Some(returnType2) => {
    //            (PrototypeT(namedEnv.fullName, returnType2))
    //          }
    //          case None => {
    //            throw CompileErrorExceptionT(RangedInternalErrorT(runedEnv.function.range, "Need return type for " + needleSignature + ", cycle found"))
    //          }
    //        }
    //      }
    //    }
  }

//  // Preconditions:
//  // - already spawned local env
//  // - either no template args, or they were already added to the env.
//  // - either no closured vars, or they were already added to the env.
//  def getOrEvaluateGenericFunction(
//    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs,
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS],
//    function1: FunctionA):
//  (FunctionHeaderT) = {
//
//    // Check preconditions
//    function1.runeToType.keySet.foreach(templateParam => {
//      vassert(
//        runedEnv
//          .lookupNearestWithImpreciseName(
//            interner.intern(RuneNameS(templateParam)),
//            Set(TemplataLookupContext, ExpressionLookupContext))
//          .nonEmpty);
//    })
//
//    val paramTypes2 = evaluateFunctionParamTypes(runedEnv, function1.params);
//    val functionId = assembleName(runedEnv.fullName, runedEnv.templateArgs, paramTypes2)
//    val needleSignature = SignatureT(functionId)
//    coutputs.lookupFunction(needleSignature) match {
//      case Some(FunctionT(header, _)) => {
//        (header)
//      }
//      case None => {
//        val params2 = assembleFunctionParams(runedEnv, coutputs, function1.params)
//
//        val maybeReturnType = getMaybeReturnType(runedEnv, function1.maybeRetCoordRune.map(_.rune))
////        val namedEnv = makeNamedEnv(runedEnv, params2.map(_.tyype), maybeReturnType)
//
//        //        coutputs.declareFunctionSignature(function1.range, needleSignature, Some(namedEnv))
//
//        val header =
//          core.evaluateFunctionForHeader(
//            runedEnv, coutputs, callRange, params2)
////        vassert(header.toSignature == needleSignature)
//        (header)
//      }
//    }
//    vimpl()
//  }

  def assembleName(
      templateName: IdT[IFunctionTemplateNameT],
      templateArgs: Vector[ITemplataT[ITemplataType]],
      paramTypes: Vector[CoordT]):
  IdT[IFunctionNameT] = {
    templateName.copy(
      localName = templateName.localName.makeFunctionName(interner, keywords, templateArgs, paramTypes))
  }

  def makeNamedEnv(
    runedEnv: BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT,
    paramTypes: Vector[CoordT],
    maybeReturnType: Option[CoordT]):
  FunctionEnvironmentT = {
    val BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT(
      globalEnv,
      parentEnv,
      templateId,
      templateArgs,
      templatas,
      function,
      variables,
      isRootCompilingDenizen,
      defaultRegion) = runedEnv
    val id = assembleName(templateId, templateArgs, paramTypes)
    FunctionEnvironmentT(
      globalEnv,
      parentEnv,
      templateId,
      id,
      templatas,
      function,
      maybeReturnType,
      variables,
      isRootCompilingDenizen,
      defaultRegion)
  }
}
