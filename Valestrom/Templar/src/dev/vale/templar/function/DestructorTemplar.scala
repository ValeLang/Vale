package dev.vale.templar.function

//import dev.vale.astronomer.{AbstractAP, CallAR, CodeRuneS, CodeTypeNameS, CodeVarNameS, ComponentsAR, EqualsAR, FunctionA, FunctionNameS, GeneratedBodyS, ImmConcreteDestructorImpreciseNameS, ImmConcreteDestructorNameS, ImmDropImpreciseNameS, ImmDropNameS, ImmInterfaceDestructorImpreciseNameS, ImmInterfaceDestructorNameS, LocalS, MutabilityAR, NameSR, OrAR, OverrideAP, OwnershipAR, ParameterS, PermissionAR, RuneSR, TemplexAR, UserFunctionA}
import dev.vale.scout.{CodeNameS, FreeImpreciseNameS}
import dev.vale.templar.citizen.StructTemplar
import dev.vale.templar.expression.CallTemplar
import dev.vale.{Err, Interner, Ok, PackageCoordinate, RangeS}
import dev.vale.astronomer._
import dev.vale.scout.patterns._
import dev.vale.scout.rules.OwnershipLiteralSL
import dev.vale.scout.GlobalFunctionFamilyNameS
import dev.vale.templar.types._
import dev.vale.templar.templata._
import dev.vale.templar.OverloadTemplar.FindFunctionFailure
import dev.vale.templar.{CompileErrorExceptionT, CouldntFindFunctionToCallT, OverloadTemplar, RangedInternalErrorT, TemplarOptions, Temputs}
import dev.vale.templar.ast.{DiscardTE, FunctionCallTE, PrototypeT, ReferenceExpressionTE}
import dev.vale.templar.env.{GlobalEnvironment, IEnvironment, PackageEnvironment}
import dev.vale.templar.names.{FullNameT, PackageTopLevelNameT}
import dev.vale.templar.types.{BoolT, BorrowT, CoordT, FloatT, IntT, InterfaceTT, NeverT, OverloadSetT, OwnT, ParamFilter, RuntimeSizedArrayTT, ShareT, StaticSizedArrayTT, StrT, StructTT, VoidT, WeakT}
import dev.vale.templar.{ast, _}
import dev.vale.templar.ast._
import dev.vale.templar.env._
import dev.vale.templar.names.PackageTopLevelNameT
import dev.vale.Err

import scala.collection.immutable.List

class DestructorTemplar(
    opts: TemplarOptions,
    interner: Interner,
    structTemplar: StructTemplar,
    overloadTemplar: OverloadTemplar) {
  def getDropFunction(
    globalEnv: GlobalEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    type2: CoordT):
  (PrototypeT) = {
    val env =
      PackageEnvironment(
        globalEnv,
        FullNameT(PackageCoordinate.BUILTIN, Vector(), interner.intern(PackageTopLevelNameT())),
        globalEnv.nameToTopLevelEnvironment.values.toVector)
    val name = interner.intern(CodeNameS(CallTemplar.DROP_FUNCTION_NAME))
    val args = Vector(ParamFilter(type2, None))
    overloadTemplar.findFunction(env, temputs, callRange, name, Vector.empty, Array.empty, args, Vector(), true) match {
      case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
      case Ok(x) => x
    }
  }

  def getFreeFunction(
    globalEnv: GlobalEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    type2: CoordT):
  (PrototypeT) = {
    val env =
      PackageEnvironment(
        globalEnv,
        FullNameT(PackageCoordinate.BUILTIN, Vector(), interner.intern(PackageTopLevelNameT())),
        globalEnv.nameToTopLevelEnvironment.values.toVector)
    val name = interner.intern(FreeImpreciseNameS())
    val args = Vector(ParamFilter(type2, None))
    overloadTemplar.findFunction(env, temputs, callRange, name, Vector.empty, Array.empty, args, Vector(), true) match {
      case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
      case Ok(x) => x
    }
  }

  def drop(
    env: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    undestructedExpr2: ReferenceExpressionTE):
  (ReferenceExpressionTE) = {
    val resultExpr2 =
      undestructedExpr2.result.reference match {
        case r@CoordT(OwnT, kind) => {
          val destructorPrototype =
            kind match {
              case StructTT(_) | InterfaceTT(_) => {
                getDropFunction(env.globalEnv, temputs, callRange, r)
              }
              case StaticSizedArrayTT(_, _, _, _) | RuntimeSizedArrayTT(_, _) => {
                getDropFunction(env.globalEnv, temputs, callRange, r)
              }
            }
          FunctionCallTE(destructorPrototype, Vector(undestructedExpr2))
        }
        case CoordT(BorrowT, _) => (DiscardTE(undestructedExpr2))
        case CoordT(WeakT, _) => (DiscardTE(undestructedExpr2))
        case CoordT(ShareT, _) => {
          val destroySharedCitizen =
            (temputs: Temputs, coord: CoordT) => {
              val destructorHeader = getDropFunction(env.globalEnv, temputs, callRange, coord)
              // We just needed to ensure it's in the temputs, so that the backend can use it
              // for when reference counts drop to zero.
              // If/when we have a GC backend, we can skip generating share destructors.
              val _ = destructorHeader
              DiscardTE(undestructedExpr2)
            };
          val destroySharedArray =
            (temputs: Temputs, coord: CoordT) => {
              val destructorHeader = getDropFunction(env.globalEnv, temputs, callRange, coord)
              // We just needed to ensure it's in the temputs, so that the backend can use it
              // for when reference counts drop to zero.
              // If/when we have a GC backend, we can skip generating share destructors.
              val _ = destructorHeader
              DiscardTE(undestructedExpr2)
            };


          val unshareExpr2 =
            undestructedExpr2.result.reference.kind match {
              case NeverT(_) => undestructedExpr2
              case IntT(_) | StrT() | BoolT() | FloatT() | VoidT() => {
                DiscardTE(undestructedExpr2)
              }
              case OverloadSetT(overloadSetEnv, name) => {
                DiscardTE(undestructedExpr2)
              }
              case as@StaticSizedArrayTT(_, _, _, _) => {
                val underarrayReference2 =
                  CoordT(
                    undestructedExpr2.result.reference.ownership,
                    as)
                destroySharedArray(temputs, underarrayReference2)
              }
              case as@RuntimeSizedArrayTT(_, _) => {
                val underarrayReference2 =
                  CoordT(
                    undestructedExpr2.result.reference.ownership,
                    as)
                destroySharedArray(temputs, underarrayReference2)
              }
              case StructTT(_) | InterfaceTT(_) => {
                destroySharedCitizen(temputs, undestructedExpr2.result.reference)
              }
            }
          unshareExpr2
        }
      }
    resultExpr2.result.reference.kind match {
      case VoidT() | NeverT(_) =>
      case _ => {
        throw CompileErrorExceptionT(
          RangedInternalErrorT(
            callRange,
            "Unexpected return type for drop autocall.\nReturn: " + resultExpr2.result.reference.kind + "\nParam: " + undestructedExpr2.result.reference))
      }
    }
    resultExpr2
  }
}
