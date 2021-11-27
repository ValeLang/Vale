package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.VirtualFreeImpreciseNameS
import net.verdagon.vale.scout.{CodeNameS, GlobalFunctionFamilyNameS}
import net.verdagon.vale.templar.OverloadTemplar.FindFunctionFailure
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{AbstractT, FunctionHeaderT, OverrideT, ParameterT}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.IEnvironment
import net.verdagon.vale.templar.names.{FunctionNameT, VirtualFreeNameT}
import net.verdagon.vale.{RangeS, vassert, vcurious, vfail, vimpl}

import scala.collection.immutable.List

class VirtualTemplar(opts: TemplarOptions, overloadTemplar: OverloadTemplar) {
  // See Virtuals doc for this function's purpose.
  // For the "Templated parent case"
  def evaluateParent(
    env: IEnvironment, temputs: Temputs, sparkHeader: FunctionHeaderT):
  Unit = {
    vassert(sparkHeader.params.count(_.virtuality.nonEmpty) <= 1)
    val maybeSuperInterfaceAndIndex =
      sparkHeader.params.zipWithIndex.collectFirst({
        case (ParameterT(_, Some(OverrideT(ir)), CoordT(_, _, StructTT(_))), index) => (ir, index)
      })

    maybeSuperInterfaceAndIndex match {
      case None => {
        // It's not an override, so nothing to do here.

      }
      case Some((superInterfaceRef2, virtualIndex)) => {
        val overrideFunctionParamTypes = sparkHeader.params.map(_.tyype)
        val needleSuperFunctionParamTypes =
          overrideFunctionParamTypes.zipWithIndex.map({ case (paramType, index) =>
            if (index != virtualIndex) {
              paramType
            } else {
              paramType.copy(kind = superInterfaceRef2)
            }
          })

        val needleSuperFunctionParamFilters =
          needleSuperFunctionParamTypes.zipWithIndex.map({
            case (needleSuperFunctionParamType, index) => {
              ParamFilter(needleSuperFunctionParamType, if (index == virtualIndex) Some(AbstractT) else None)
            }
          })

        val nameToScoutFor =
          sparkHeader.fullName.last match {
            case FunctionNameT(humanName, _, _) => CodeNameS(humanName)
            case VirtualFreeNameT(_, _) => VirtualFreeImpreciseNameS()
//            case ImmInterfaceDestructorNameT(_, _) => ImmInterfaceDestructorImpreciseNameS()
            case other => vimpl(other)
          }

        // See MLIOET
        val superInterfaceEnv = temputs.getEnvForKind(superInterfaceRef2)
        val extraEnvsToLookIn = Vector(superInterfaceEnv)

        // Throw away the result prototype, we just want it to be in the temputs.
        overloadTemplar.findFunction(
          env, temputs, RangeS.internal(-1388), nameToScoutFor, Vector.empty,
          Array.empty, needleSuperFunctionParamFilters, extraEnvsToLookIn, true)
      }
    }
  }
}
