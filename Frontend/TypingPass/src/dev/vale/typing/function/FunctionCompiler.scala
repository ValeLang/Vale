package dev.vale.typing.function

import dev.vale.{Interner, Keywords, Profiler, RangeS, postparsing, vassertOne, vimpl, vwat}
import dev.vale.postparsing.{BlockSE, CodeBodyS, IFunctionDeclarationNameS, IVarNameS, LambdaDeclarationNameS}
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.highertyping.CouldntSolveRulesA
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.parsing._
import dev.vale.postparsing.RuneTypeSolver
import dev.vale.postparsing.patterns._
import dev.vale.postparsing.rules._
import dev.vale.typing.OverloadResolver.IFindFunctionFailureReason
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import FunctionCompiler.IEvaluateFunctionResult
import dev.vale.highertyping.FunctionA
import dev.vale.typing.{CompilerOutputs, ConvertHelper, IFunctionGenerator, InferCompiler, TemplataCompiler, TypingPassOptions}
import dev.vale.typing.ast.{FunctionBannerT, FunctionHeaderT, LocationInFunctionEnvironment, ParameterT, PrototypeT, ReferenceExpressionTE}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.{AddressibleClosureVariableT, AddressibleLocalVariableT, FunctionEnvironment, IEnvironment, NodeEnvironment, NodeEnvironmentBox, ReferenceClosureVariableT, ReferenceLocalVariableT, TemplataLookupContext}
import dev.vale.typing.names.{LambdaCitizenNameT, LambdaCitizenTemplateNameT, NameTranslator}
import dev.vale.typing.templata.{FunctionTemplata, ITemplata, KindTemplata}
import dev.vale.typing.types.{AddressMemberTypeT, BorrowT, CoordT, OwnT, ParamFilter, ReferenceMemberTypeT, ShareT, StructMemberT, StructTT}
import dev.vale.typing.names.LambdaCitizenNameT

import scala.collection.immutable.{List, Set}



trait IFunctionCompilerDelegate {
  def evaluateBlockStatements(
    coutputs: CompilerOutputs,
    startingNenv: NodeEnvironment,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    exprs: BlockSE):
  (ReferenceExpressionTE, Set[CoordT])

  def translatePatternList(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    patterns1: Vector[AtomSP],
    patternInputExprs2: Vector[ReferenceExpressionTE]):
  ReferenceExpressionTE

//  def evaluateParent(
//    env: IEnvironment, coutputs: CompilerOutputs, callRange: RangeS, sparkHeader: FunctionHeaderT):
//  Unit

  def generateFunction(
    functionCompilerCore: FunctionCompilerCore,
    generator: IFunctionGenerator,
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    // We might be able to move these all into the function environment... maybe....
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT
}

object FunctionCompiler {
  trait IEvaluateFunctionResult[T]
  case class EvaluateFunctionSuccess[T](function: T) extends IEvaluateFunctionResult[T]
  case class EvaluateFunctionFailure[T](reason: IFindFunctionFailureReason) extends IEvaluateFunctionResult[T]
}

// When typingpassing a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
class FunctionCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler,
    convertHelper: ConvertHelper,
    structCompiler: StructCompiler,
    delegate: IFunctionCompilerDelegate) {
  val closureOrLightLayer =
    new FunctionCompilerClosureOrLightLayer(
      opts, interner, keywords, nameTranslator, templataCompiler, inferCompiler, convertHelper, structCompiler, delegate)

  private def determineClosureVariableMember(
      env: NodeEnvironment,
      coutputs: CompilerOutputs,
      name: IVarNameS) = {
    val (variability2, memberType) =
      env.getVariable(nameTranslator.translateVarNameStep(name)).get match {
        case ReferenceLocalVariableT(_, variability, reference) => {
          // See "Captured own is borrow" test for why we do this
          val tyype =
            reference.ownership match {
              case OwnT => ReferenceMemberTypeT(CoordT(BorrowT, reference.kind))
              case BorrowT | ShareT => ReferenceMemberTypeT(reference)
            }
          (variability, tyype)
        }
        case AddressibleLocalVariableT(_, variability, reference) => {
          (variability, AddressMemberTypeT(reference))
        }
        case ReferenceClosureVariableT(_, _, variability, reference) => {
          // See "Captured own is borrow" test for why we do this
          val tyype =
            reference.ownership match {
              case OwnT => ReferenceMemberTypeT(CoordT(BorrowT, reference.kind))
              case BorrowT | ShareT => ReferenceMemberTypeT(reference)
            }
          (variability, tyype)
        }
        case AddressibleClosureVariableT(_, _, variability, reference) => {
          (variability, AddressMemberTypeT(reference))
        }
      }
    StructMemberT(nameTranslator.translateVarNameStep(name), variability2, memberType)
  }

  def evaluateClosureStruct(
      coutputs: CompilerOutputs,
      containingNodeEnv: NodeEnvironment,
    callRange: RangeS,
    name: IFunctionDeclarationNameS,
      functionA: FunctionA):
  (StructTT) = {
    val CodeBodyS(body) = functionA.body
    val closuredNames = body.closuredNames;

    // Note, this is where the unordered closuredNames set becomes ordered.
    val closuredVarNamesAndTypes =
      closuredNames
        .map(name => determineClosureVariableMember(containingNodeEnv, coutputs, name))
        .toVector;

    val (structTT, _, functionTemplata) =
      structCompiler.makeClosureUnderstruct(
        containingNodeEnv, coutputs, name, functionA, closuredVarNamesAndTypes)

    // Eagerly evaluate the function if it's not a template.
    if (functionA.isTemplate) {
      // Do nothing
    } else {
      val _ =
        evaluateOrdinaryClosureFunctionFromNonCallForHeader(
          functionTemplata.outerEnv, coutputs, structTT, functionA)
    }

    (structTT)
  }


  def evaluateOrdinaryFunctionFromNonCallForHeader(
    coutputs: CompilerOutputs,
    functionTemplata: FunctionTemplata):
  FunctionHeaderT = {
    Profiler.frame(() => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight) {
          evaluateOrdinaryLightFunctionFromNonCallForHeader(
            env, coutputs, function)
        } else {
          val List(KindTemplata(closureStructRef@StructTT(_))) =
            env.lookupNearestWithImpreciseName(

              vimpl(), //FunctionScout.CLOSURE_STRUCT_ENV_ENTRY_NAME,
              Set(TemplataLookupContext)).toList
          val header =
            evaluateOrdinaryClosureFunctionFromNonCallForHeader(
              env, coutputs, closureStructRef, function)
          header
        }
      })

  }

  def evaluateTemplatedFunctionFromNonCallForHeader(
    coutputs: CompilerOutputs,
    functionTemplata: FunctionTemplata):
  FunctionHeaderT = {
    Profiler.frame(() => {
      val FunctionTemplata(env, function) = functionTemplata
      if (function.isLight) {
        evaluateTemplatedLightFunctionFromNonCallForHeader(
          env, coutputs, function)
      } else {
        val List(KindTemplata(closureStructRef@StructTT(_))) =
          env.lookupNearestWithImpreciseName(

            vimpl(), //FunctionScout.CLOSURE_STRUCT_ENV_ENTRY_NAME,
            Set(TemplataLookupContext)).toList
        val header =
          evaluateTemplatedClosureFunctionFromNonCallForHeader(
            env, coutputs, closureStructRef, function)
        header
      }
    })

  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  def evaluateOrdinaryFunctionFromNonCallForPrototype(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata):
  (PrototypeT) = {
    Profiler.frame(() => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight) {
          evaluateOrdinaryLightFunctionFromNonCallForPrototype(
            env, coutputs, callRange, function)
        } else {
          val lambdaCitizenName2 =
            functionTemplata.function.name match {
              case LambdaDeclarationNameS(codeLocation) => interner.intern(LambdaCitizenNameT(interner.intern(LambdaCitizenTemplateNameT(nameTranslator.translateCodeLocation(codeLocation)))))
              case _ => vwat()
            }

          val KindTemplata(closureStructRef@StructTT(_)) =
            vassertOne(
              env.lookupNearestWithName(

                lambdaCitizenName2,
                Set(TemplataLookupContext)))
          val header =
            evaluateOrdinaryClosureFunctionFromNonCallForHeader(
              env, coutputs, closureStructRef, function)
          (header.toPrototype)
        }
      })

  }

  def evaluateOrdinaryFunctionFromNonCallForBanner(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata):
  (FunctionBannerT) = {
    Profiler.frame(() => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight()) {
          evaluateOrdinaryLightFunctionFromNonCallForBanner(
            env, coutputs, callRange, function)
        } else {
          val lambdaCitizenName2 =
            functionTemplata.function.name match {
              case LambdaDeclarationNameS(codeLocation) => interner.intern(LambdaCitizenNameT(interner.intern(LambdaCitizenTemplateNameT(nameTranslator.translateCodeLocation(codeLocation)))))
              case _ => vwat()
            }

          val KindTemplata(closureStructRef@StructTT(_)) =
            vassertOne(
              env.lookupNearestWithName(

                lambdaCitizenName2,
                Set(TemplataLookupContext)))
          evaluateOrdinaryClosureFunctionFromNonCallForBanner(
            env, coutputs, callRange, closureStructRef, function)
        }
      })

  }

  private def evaluateOrdinaryLightFunctionFromNonCallForBanner(
      env: IEnvironment,
      coutputs: CompilerOutputs,
    callRange: RangeS,
    function: FunctionA):
  (FunctionBannerT) = {
    closureOrLightLayer.evaluateOrdinaryLightFunctionFromNonCallForBanner(
      env, coutputs, callRange, function)
  }

  def evaluateTemplatedFunctionFromCallForBanner(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata,
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
    paramFilters: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    Profiler.frame(() => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight()) {
          evaluateTemplatedLightFunctionFromCallForBanner(
            coutputs, callRange, functionTemplata, alreadySpecifiedTemplateArgs, paramFilters)
        } else {
          val lambdaCitizenName2 =
            functionTemplata.function.name match {
              case LambdaDeclarationNameS(codeLocation) => interner.intern(LambdaCitizenNameT(interner.intern(LambdaCitizenTemplateNameT(nameTranslator.translateCodeLocation(codeLocation)))))
              case _ => vwat()
            }

          val KindTemplata(closureStructRef@StructTT(_)) =
            vassertOne(
              env.lookupNearestWithName(

                lambdaCitizenName2,
                Set(TemplataLookupContext)))
          val banner =
            evaluateTemplatedClosureFunctionFromCallForBanner(
              env, coutputs, callRange, closureStructRef, function, alreadySpecifiedTemplateArgs, paramFilters)
          (banner)
        }
      })

  }

  private def evaluateTemplatedClosureFunctionFromCallForBanner(
      env: IEnvironment,
      coutputs: CompilerOutputs,
      callRange: RangeS,
      closureStructRef: StructTT,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
      argTypes2: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    closureOrLightLayer.evaluateTemplatedClosureFunctionFromCallForBanner(
      env, coutputs, callRange, closureStructRef, function,
      alreadySpecifiedTemplateArgs, argTypes2)
  }

  def evaluateTemplatedLightFunctionFromCallForBanner(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata,
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
    paramFilters: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    Profiler.frame(() => {
        val FunctionTemplata(env, function) = functionTemplata
        closureOrLightLayer.evaluateTemplatedLightBannerFromCall(
          env, coutputs, callRange, function, alreadySpecifiedTemplateArgs, paramFilters)
      })

  }

  private def evaluateOrdinaryClosureFunctionFromNonCallForHeader(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    closureStructRef: StructTT,
    function: FunctionA):
  (FunctionHeaderT) = {
    closureOrLightLayer.evaluateOrdinaryClosureFunctionFromNonCallForHeader(
      env, coutputs, closureStructRef, function)
  }

  private def evaluateTemplatedClosureFunctionFromNonCallForHeader(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    closureStructRef: StructTT,
    function: FunctionA):
  (FunctionHeaderT) = {
    closureOrLightLayer.evaluateTemplatedClosureFunctionFromNonCallForHeader(
      env, coutputs, closureStructRef, function)
  }

  private def evaluateOrdinaryClosureFunctionFromNonCallForBanner(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    closureStructRef: StructTT,
    function: FunctionA):
  (FunctionBannerT) = {
    closureOrLightLayer.evaluateOrdinaryClosureFunctionFromNonCallForBanner(
      env, coutputs, callRange, closureStructRef, function)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  private def evaluateOrdinaryLightFunctionFromNonCallForPrototype(
      env: IEnvironment,
      coutputs: CompilerOutputs,
    callRange: RangeS,
    function: FunctionA):
  (PrototypeT) = {
    closureOrLightLayer.evaluateOrdinaryLightFunctionFromNonCallForPrototype(
      env, coutputs, callRange, function)
  }

  private def evaluateOrdinaryLightFunctionFromNonCallForHeader(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    function: FunctionA):
  (FunctionHeaderT) = {
    closureOrLightLayer.evaluateOrdinaryLightFunctionFromNonCallForHeader(
      env, coutputs, function)
  }

  private def evaluateTemplatedLightFunctionFromNonCallForHeader(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    function: FunctionA):
  (FunctionHeaderT) = {
    closureOrLightLayer.evaluateTemplatedLightFunctionFromNonCallForHeader(
      env, coutputs, function)
  }

  def evaluateOrdinaryLightFunctionFromNonCallForCompilerOutputs(
      coutputs: CompilerOutputs,
      functionTemplata: FunctionTemplata):
  Unit = {
    Profiler.frame(() => {
        val FunctionTemplata(env, function) = functionTemplata
        val _ =
          evaluateOrdinaryLightFunctionFromNonCallForHeader(
            env, coutputs, function)
      })

  }

  def evaluateTemplatedFunctionFromCallForPrototype(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata,
    explicitTemplateArgs: Vector[ITemplata],
    args: Vector[ParamFilter]):
  IEvaluateFunctionResult[PrototypeT] = {
    Profiler.frame(() => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight()) {
          evaluateTemplatedLightFunctionFromCallForPrototype(
            env, coutputs, callRange, function, explicitTemplateArgs, args)
        } else {
          evaluateTemplatedClosureFunctionFromCallForPrototype(
            env, coutputs, callRange, function, explicitTemplateArgs, args)
        }
      })

  }

  private def evaluateTemplatedLightFunctionFromCallForPrototype(
      env: IEnvironment,
      coutputs: CompilerOutputs,
    callRange: RangeS,
    function: FunctionA,
      explicitTemplateArgs: Vector[ITemplata],
      args: Vector[ParamFilter]):
  IEvaluateFunctionResult[PrototypeT] = {
    closureOrLightLayer.evaluateTemplatedLightFunctionFromCallForPrototype2(
        env, coutputs, callRange, function, explicitTemplateArgs, args)
  }

  private def evaluateTemplatedClosureFunctionFromCallForPrototype(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    function: FunctionA,
    explicitTemplateArgs: Vector[ITemplata],
    args: Vector[ParamFilter]):
  IEvaluateFunctionResult[PrototypeT] = {
    val lambdaCitizenName2 =
      function.name match {
        case LambdaDeclarationNameS(codeLocation) => interner.intern(LambdaCitizenNameT(interner.intern(LambdaCitizenTemplateNameT(nameTranslator.translateCodeLocation(codeLocation)))))
        case _ => vwat()
      }
    val KindTemplata(closureStructRef @ StructTT(_)) =
      vassertOne(
        env.lookupNearestWithName(

          lambdaCitizenName2,
          Set(TemplataLookupContext)))
    closureOrLightLayer.evaluateTemplatedClosureFunctionFromCallForPrototype(
      env, coutputs, callRange, closureStructRef, function, explicitTemplateArgs, args)
  }
}
