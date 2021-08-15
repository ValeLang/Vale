package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, IImpreciseNameStepA, INameA, ImmConcreteDestructorImpreciseNameA, ImmConcreteDestructorNameA, ImmInterfaceDestructorImpreciseNameA}
import net.verdagon.vale.templar.templata.{FunctionBannerT, OverrideT, PrototypeT, SignatureT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vfail, vimpl, vwat}

object EdgeTemplar {
  sealed trait IMethod
  case class NeededOverride(
    name: IImpreciseNameStepA,
    paramFilters: List[ParamFilter]
  ) extends IMethod { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
  case class FoundFunction(prototype: PrototypeT) extends IMethod { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

  case class PartialEdgeT(
    struct: StructTT,
    interface: InterfaceTT,
    methods: List[IMethod]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

  def assemblePartialEdges(temputs: Temputs): List[PartialEdgeT] = {
    val interfaceEdgeBlueprints = makeInterfaceEdgeBlueprints(temputs)

    val overrideFunctionsAndIndicesByStructAndInterface = doBlah(temputs, interfaceEdgeBlueprints)

    val partialEdges2 =
      overrideFunctionsAndIndicesByStructAndInterface
        .map({ case ((struct, superInterface), overrideFunctionsAndIndices) =>
          val blueprint = interfaceEdgeBlueprints.find(_.interface == superInterface).get
          val overrideFunctionsByIndex =
            overrideFunctionsAndIndices.groupBy(_._2).mapValues(_.map(_._1).toList)
          // overrideIndex is the index in the itable
          val methods =
            blueprint.superFamilyRootBanners.zipWithIndex.map({ case (superFunction, overrideIndex) =>
              overrideFunctionsByIndex.get(overrideIndex) match {
                case None => {
                  val overrideParamFilters =
                    superFunction.paramTypes.zipWithIndex.map({
                      case (CoordT(ownership, permission, _), index) if index == superFunction.getVirtualIndex.get => {
                        ParamFilter(CoordT(ownership, permission, struct), Some(OverrideT(superInterface)))
                      }
                      case (tyype, _) => ParamFilter(tyype, None)
                    })
                  superFunction.fullName.last match {
                    case FunctionNameT(humanName, _, _) => NeededOverride(GlobalFunctionFamilyNameA(humanName), overrideParamFilters)
                    case ImmInterfaceDestructorNameT(_, _) => NeededOverride(ImmInterfaceDestructorImpreciseNameA(), overrideParamFilters)
                    case _ => vwat()
                  }
                }
                case Some(Nil) => vfail("wot")
                case Some(List(onlyOverride)) => FoundFunction(onlyOverride.header.toPrototype)
                case Some(multipleOverrides) => {
                  vfail("Multiple overrides for struct " + struct + " for interface " + superInterface + ": " + multipleOverrides.map(_.header.toSignature).mkString(", "))
                }
              }
            })
          PartialEdgeT(struct, superInterface, methods)
        })

    partialEdges2.toList
  }


  def doBlah(
    temputs: Temputs,
    interfaceEdgeBlueprints: Vector[InterfaceEdgeBlueprint]
  ): Map[(StructTT, InterfaceTT), List[(FunctionT, Int)]] = {
    temputs.getAllFunctions().toList.flatMap({ case overrideFunction =>
      overrideFunction.header.getOverride match {
        case None => List.empty
        case Some((struct, superInterface)) => {
          // Make sure that the struct actually overrides that function
          if (!temputs.getAllImpls().exists(impl => impl.struct == struct && impl.interface == superInterface)) {
            vfail("Struct " + struct + " doesn't extend " + superInterface + "!")
          }

          val virtualIndex = overrideFunction.header.getVirtualIndex.get
          vassert(virtualIndex >= 0)
          val overrideFunctionParamTypes =
            overrideFunction.header.params.map(_.tyype)
          val needleSuperFunctionParamTypes =
            overrideFunctionParamTypes.zipWithIndex.map({ case (paramType, index) =>
              if (index != virtualIndex) {
                paramType
              } else {
                paramType.copy(kind = superInterface)
              }
            })

          // Make sure that there's an abstract function in that edge that this
          // overrides
          val edgeBlueprint =
          interfaceEdgeBlueprints.find(_.interface == superInterface) match {
            case None => {
              // every interface should have a blueprint...
              vfail("wot")
            }
            case Some(ieb) => ieb
          }
          val matchesAndIndices =
            edgeBlueprint.superFamilyRootBanners.zipWithIndex
              .filter({ case (possibleSuperFunction, index) =>
                val namesMatch =
                  (possibleSuperFunction.fullName.last, overrideFunction.header.fullName.last) match {
                    case (FunctionNameT(possibleSuperFunctionHumanName, _, _), FunctionNameT(overrideFunctionHumanName, _, _)) => {
                      possibleSuperFunctionHumanName == overrideFunctionHumanName
                    }
                    case (ImmInterfaceDestructorNameT(_, _), ImmInterfaceDestructorNameT(_, _)) => true
                    case _ => false
                  }
                namesMatch && possibleSuperFunction.paramTypes == needleSuperFunctionParamTypes
              })
          matchesAndIndices match {
            case Nil => {
              vfail("Function " + overrideFunction.header.toSignature + " doesn't override anything in " + superInterface)
            }
            case List((_, indexInEdge)) => {
              List((struct, superInterface) -> (overrideFunction, indexInEdge))
            }
            case _ => {
              vfail("Function " + overrideFunction.header.toSignature + " overrides multiple things in " + superInterface + ": " + matchesAndIndices.map(_._1.toSignature).mkString(", "))
            }
          }
        }
      }
    })
      .groupBy(_._1)
      .mapValues(_.map(_._2))
  }

  def makeInterfaceEdgeBlueprints(temputs: Temputs): Vector[InterfaceEdgeBlueprint] = {
    val abstractFunctionHeadersByInterfaceWithoutEmpties =
      temputs.getAllFunctions().flatMap({ case function =>
        function.header.getAbstractInterface match {
          case None => List.empty
          case Some(abstractInterface) => List(abstractInterface -> function)
        }
      })
        .groupBy(_._1)
        .mapValues(_.map(_._2))
        .map({ case (interfaceTT, functions) =>
          // Sort so that the interface's internal methods are first and in the same order
          // they were declared in. It feels right, and vivem also depends on it
          // when it calls array generators/consumers' first method.
          val interfaceDef = temputs.getAllInterfaces().find(_.getRef == interfaceTT).get
          // Make sure `functions` has everything that the interface def wanted.
          vassert((interfaceDef.internalMethods.toSet -- functions.map(_.header).toSet).isEmpty)
          // Move all the internal methods to the front.
          val orderedMethods =
            interfaceDef.internalMethods ++
              functions.map(_.header).filter(!interfaceDef.internalMethods.contains(_))
          (interfaceTT -> orderedMethods)
        })
    // Some interfaces would be empty and they wouldn't be in
    // abstractFunctionsByInterfaceWithoutEmpties, so we add them here.
    val abstractFunctionHeadersByInterface =
    abstractFunctionHeadersByInterfaceWithoutEmpties ++
      temputs.getAllInterfaces().map({ case i =>
        (i.getRef -> abstractFunctionHeadersByInterfaceWithoutEmpties.getOrElse(i.getRef, Set()))
      })

    val interfaceEdgeBlueprints =
      abstractFunctionHeadersByInterface
        .map({ case (interfaceTT, functionHeaders2) =>
          InterfaceEdgeBlueprint(
            interfaceTT,
            // This is where they're given order and get an implied index
            functionHeaders2.map(_.toBanner).toList)
        })
    interfaceEdgeBlueprints.toVector
  }
}
