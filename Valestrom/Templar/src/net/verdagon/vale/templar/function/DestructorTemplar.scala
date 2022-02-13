package net.verdagon.vale.templar.function

//import net.verdagon.vale.astronomer.{AbstractAP, CallAR, CodeRuneS, CodeTypeNameS, CodeVarNameS, ComponentsAR, EqualsAR, FunctionA, FunctionNameS, GeneratedBodyS, ImmConcreteDestructorImpreciseNameS, ImmConcreteDestructorNameS, ImmDropImpreciseNameS, ImmDropNameS, ImmInterfaceDestructorImpreciseNameS, ImmInterfaceDestructorNameS, LocalS, MutabilityAR, NameSR, OrAR, OverrideAP, OwnershipAR, ParameterS, PermissionAR, RuneSR, TemplexAR, UserFunctionA}
import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.patterns._
import net.verdagon.vale.scout.rules.{CallSR, CoordComponentsSR, EqualsSR, IsConcreteSR, IsInterfaceSR, IsStructSR, KindComponentsSR, LiteralSR, LookupSR, MutabilityLiteralSL, OneOfSR, OwnershipLiteralSL, RuneUsage}
import net.verdagon.vale.scout.{CodeNameS, CodeRuneS, CodeVarNameS, FreeImpreciseNameS, FunctionNameS, GeneratedBodyS, GlobalFunctionFamilyNameS, LocalS, NotUsed, ParameterS, Used, UserFunctionS}
import net.verdagon.vale.templar.types.{CoordT, _}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.OverloadTemplar.FindFunctionFailure
import net.verdagon.vale.templar.{ast, _}
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.names.{CodeVarNameT, FullNameT, PackageTopLevelNameT}
import net.verdagon.vale.{CodeLocationS, Err, Interner, Ok, PackageCoordinate, Profiler, RangeS, vassert, vfail, vimpl, vwat}

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
