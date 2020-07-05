package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, IImpreciseNameStepA, INameA, ImmConcreteDestructorImpreciseNameA, ImmConcreteDestructorNameA, ImmInterfaceDestructorImpreciseNameA}
import net.verdagon.vale.templar.templata.{FunctionBanner2, Override2, Prototype2, Signature2}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vfail, vwat}

object EdgeTemplar {
  sealed trait IMethod
  case class NeededOverride(
    name: IImpreciseNameStepA,
    paramFilters: List[ParamFilter]
  ) extends IMethod
  case class FoundFunction(prototype: Prototype2) extends IMethod

  case class PartialEdge2(
    struct: StructRef2,
    interface: InterfaceRef2,
    methods: List[IMethod])

  def assemblePartialEdges(
    functions: List[net.verdagon.vale.templar.Function2],
    interfaces: List[InterfaceDefinition2],
    impls: List[Impl2]):
  List[PartialEdge2] = {

    val interfaceEdgeBlueprints = makeInterfaceEdgeBlueprints(functions, interfaces)

    val overrideFunctionsAndIndicesByStructAndInterface = doBlah(functions, interfaces, impls, interfaceEdgeBlueprints)

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
                      case (Coord(ownership, _), index) if index == superFunction.getVirtualIndex.get => {
                        ParamFilter(Coord(ownership, struct), Some(Override2(superInterface)))
                      }
                      case (tyype, _) => ParamFilter(tyype, None)
                    })
                  superFunction.fullName.last match {
                    case FunctionName2(humanName, _, _) => NeededOverride(GlobalFunctionFamilyNameA(humanName), overrideParamFilters)
                    case ImmInterfaceDestructorName2(_, _) => NeededOverride(ImmInterfaceDestructorImpreciseNameA(), overrideParamFilters)
                    case _ => vwat()
                  }
                }
                case Some(List()) => vfail("wot")
                case Some(List(onlyOverride)) => FoundFunction(onlyOverride.header.toPrototype)
                case Some(multipleOverrides) => {
                  vfail("Multiple overrides for struct " + struct + " for interface " + superInterface + ": " + multipleOverrides.map(_.header.toSignature).mkString(", "))
                }
              }
            })
          PartialEdge2(struct, superInterface, methods)
        })

    partialEdges2.toList
  }


  def doBlah(
    functions: List[Function2],
    interfaces: List[InterfaceDefinition2],
    impls: List[Impl2],
    interfaceEdgeBlueprints: Vector[InterfaceEdgeBlueprint]
  ): Map[(StructRef2, InterfaceRef2), List[(Function2, Int)]] = {
    functions.flatMap(overrideFunction => {
      overrideFunction.header.getOverride match {
        case None => List()
        case Some((struct, superInterface)) => {
          // Make sure that the struct actually overrides that function
          if (!impls.exists(impl => impl.struct == struct && impl.interface == superInterface)) {
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
                paramType.copy(referend = superInterface)
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
                    case (FunctionName2(possibleSuperFunctionHumanName, _, _), FunctionName2(overrideFunctionHumanName, _, _)) => {
                      possibleSuperFunctionHumanName == overrideFunctionHumanName
                    }
                    case (ImmInterfaceDestructorName2(_, _), ImmInterfaceDestructorName2(_, _)) => true
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

  def makeInterfaceEdgeBlueprints(
    functions: List[Function2],
    interfaces: List[InterfaceDefinition2]
  ): Vector[InterfaceEdgeBlueprint] = {
    val abstractFunctionHeadersByInterfaceWithoutEmpties =
      functions.flatMap(function => {
        function.header.getAbstractInterface match {
          case None => List()
          case Some(abstractInterface) => List(abstractInterface -> function)
        }
      })
        .groupBy(_._1)
        .mapValues(_.map(_._2))
        .map({ case (interfaceRef, functions) =>
          // Sort so that the interface's internal methods are first and in the same order
          // they were declared in. It feels right, and vivem also depends on it
          // when it calls array generators/consumers' first method.
          val interfaceDef = interfaces.find(_.getRef == interfaceRef).get
          // Make sure `functions` has everything that the interface def wanted.
          vassert((interfaceDef.internalMethods.toSet -- functions.map(_.header).toSet).isEmpty)
          // Move all the internal methods to the front.
          val orderedMethods =
            interfaceDef.internalMethods ++
              functions.map(_.header).filter(!interfaceDef.internalMethods.contains(_))
          (interfaceRef -> orderedMethods)
        })
    // Some interfaces would be empty and they wouldn't be in
    // abstractFunctionsByInterfaceWithoutEmpties, so we add them here.
    val abstractFunctionHeadersByInterface =
    abstractFunctionHeadersByInterfaceWithoutEmpties ++
      interfaces.map(i => {
        (i.getRef -> abstractFunctionHeadersByInterfaceWithoutEmpties.getOrElse(i.getRef, Set()))
      })

    val interfaceEdgeBlueprints =
      abstractFunctionHeadersByInterface
        .map({ case (interfaceRef2, functionHeaders2) =>
          InterfaceEdgeBlueprint(
            interfaceRef2,
            // This is where they're given order and get an implied index
            functionHeaders2.map(_.toBanner).toList)
        })
    interfaceEdgeBlueprints.toVector
  }
}
