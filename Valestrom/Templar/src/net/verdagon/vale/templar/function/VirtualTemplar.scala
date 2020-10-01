package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, ImmInterfaceDestructorImpreciseNameA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.IEnvironment
import net.verdagon.vale.{vassert, vcurious, vfail, vimpl}

import scala.collection.immutable.List

class VirtualTemplar(opts: TemplarOptions, overloadTemplar: OverloadTemplar) {
  // See Virtuals doc for this function's purpose.
  // For the "Templated parent case"
  def evaluateParent(
    env: IEnvironment, temputs: Temputs, sparkHeader: FunctionHeader2):
  Unit = {
    vassert(sparkHeader.params.count(_.virtuality.nonEmpty) <= 1)
    val maybeSuperInterfaceAndIndex =
      sparkHeader.params.zipWithIndex.collectFirst({
        case (Parameter2(_, Some(Override2(ir)), Coord(_, StructRef2(_))), index) => (ir, index)
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
              paramType.copy(referend = superInterfaceRef2)
            }
          })

        val needleSuperFunctionParamFilters =
          needleSuperFunctionParamTypes.zipWithIndex.map({
            case (needleSuperFunctionParamType, index) => {
              ParamFilter(needleSuperFunctionParamType, if (index == virtualIndex) Some(Abstract2) else None)
            }
          })

        val nameToScoutFor =
          sparkHeader.fullName.last match {
            case FunctionName2(humanName, _, _) => GlobalFunctionFamilyNameA(humanName)
            case ImmInterfaceDestructorName2(_, _) => ImmInterfaceDestructorImpreciseNameA()
            case _ => vcurious()
          }

        // See MLIOET
        val superInterfaceEnv = temputs.getEnvForInterfaceRef(superInterfaceRef2)
        val extraEnvsToLookIn = List(superInterfaceEnv)

        overloadTemplar.scoutExpectedFunctionForPrototype(
          env, temputs, RangeS.internal(-1388), nameToScoutFor, List(), needleSuperFunctionParamFilters, extraEnvsToLookIn, true) match {
          case (ScoutExpectedFunctionSuccess(_)) => {
            // Throw away the prototype, we just want it to be in the temputs.

          }
          case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => vfail(seff.toString)
        }
      }
    }
  }
}
