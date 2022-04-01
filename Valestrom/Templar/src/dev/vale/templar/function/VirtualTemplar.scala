package dev.vale.templar.function

import dev.vale.Interner
import dev.vale.templar.citizen.StructTemplar
import dev.vale.scout.GlobalFunctionFamilyNameS
import dev.vale.templar.OverloadTemplar.FindFunctionFailure
import dev.vale.templar.{OverloadTemplar, TemplarOptions}
import dev.vale.templar.types._
import dev.vale.templar.templata._
import dev.vale.templar._
import dev.vale.templar.ast._
import dev.vale.templar.env.TemplatasStore
import dev.vale.Err

import scala.collection.immutable.List

class VirtualTemplar(opts: TemplarOptions, interner: Interner, overloadTemplar: OverloadTemplar) {
//  // See Virtuals doc for this function's purpose.
//  // For the "Templated parent case"
//  def evaluateParent(
//    env: IEnvironment, temputs: Temputs, callRange: RangeS, sparkHeader: FunctionHeaderT):
//  Unit = {
//    vassert(sparkHeader.params.count(_.virtuality.nonEmpty) <= 1)
//    val maybeSuperInterfaceAndIndex =
//      sparkHeader.params.zipWithIndex.collectFirst({
//        case (ParameterT(_, Some(OverrideT(ir)), CoordT(_, _, StructTT(_))), index) => (ir, index)
//      })
//
//    maybeSuperInterfaceAndIndex match {
//      case None => {
//        // It's not an override, so nothing to do here.
//
//      }
//      case Some((superInterfaceRef2, virtualIndex)) => {
//        val overrideFunctionParamTypes = sparkHeader.params.map(_.tyype)
//        val needleSuperFunctionParamTypes =
//          overrideFunctionParamTypes.zipWithIndex.map({ case (paramType, index) =>
//            if (index != virtualIndex) {
//              paramType
//            } else {
//              paramType.copy(kind = superInterfaceRef2)
//            }
//          })
//
//        val needleSuperFunctionParamFilters =
//          needleSuperFunctionParamTypes.zipWithIndex.map({
//            case (needleSuperFunctionParamType, index) => {
//              ParamFilter(needleSuperFunctionParamType, if (index == virtualIndex) Some(AbstractT()) else None)
//            }
//          })
//
//        val nameToScoutFor =
//          vassertSome(TemplatasStore.getImpreciseName(interner, sparkHeader.fullName.last))
//
//        // See MLIOET
//        val superInterfaceEnv = temputs.getEnvForKind(superInterfaceRef2)
//        val extraEnvsToLookIn = Vector(superInterfaceEnv)
//
//        // Throw away the result prototype, we just want it to be in the temputs.
//
//        overloadTemplar.findFunction(
//          env,
//          temputs,
//          callRange,
//          nameToScoutFor,
//          Vector.empty,
//          Array.empty, needleSuperFunctionParamFilters, extraEnvsToLookIn, true) match {
//          case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
//          case Ok(x) => x
//        }
//      }
//    }
//  }
}
