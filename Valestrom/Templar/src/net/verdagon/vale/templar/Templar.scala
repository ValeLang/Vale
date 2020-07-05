package net.verdagon.vale.templar;

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.templar.EdgeTemplar.{FoundFunction, NeededOverride}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.function.{BuiltInFunctions, FunctionTemplar, FunctionTemplarCore, VirtualTemplar}

import scala.collection.immutable.{List, ListMap, Map, Set}

object Templar {
  def evaluate(program: ProgramA):
  Temputs = {

    val ProgramA(structsA, interfacesA, impls1, functions1) = program;

    val env0 =
      NamespaceEnvironment(
        None,
        FullName2(List(), GlobalNamespaceName2()),
        Map(
          PrimitiveName2("Int") -> List(TemplataEnvEntry(KindTemplata(Int2()))),
          PrimitiveName2("Array") -> List(TemplataEnvEntry(ArrayTemplateTemplata())),
          PrimitiveName2("Bool") -> List(TemplataEnvEntry(KindTemplata(Bool2()))),
          PrimitiveName2("Float") -> List(TemplataEnvEntry(KindTemplata(Float2()))),
          PrimitiveName2("__Never") -> List(TemplataEnvEntry(KindTemplata(Never2()))),
          PrimitiveName2("Str") -> List(TemplataEnvEntry(KindTemplata(Str2()))),
          PrimitiveName2("Void") -> List(TemplataEnvEntry(KindTemplata(Void2())))))
    val functionGeneratorByName0 = Map[String, IFunctionGenerator]()
    val (env1, functionGeneratorByName1) = BuiltInFunctions.addBuiltInFunctions(env0, functionGeneratorByName0)
    val functionGeneratorByName2 = functionGeneratorByName1 ++ StructTemplar.getFunctionGenerators()

    val env3 = env1

    // This has to come before the structs and interfaces because part of evaluating a
    // struct or interface is figuring out what it extends.
    val env5 =
      impls1.foldLeft(env3)({
        case (env4, impl1) => env4.addEntry(NameTranslator.translateImplName(impl1.name), ImplEnvEntry(impl1))
      })
    val env7 =
      structsA.foldLeft(env5)({
        case (env6, s) => env6.addEntries(makeStructEnvironmentEntries(s))
      })
    val env9 =
      interfacesA.foldLeft(env7)({
        case (env8, interfaceA) => env8.addEntries(makeInterfaceEnvironmentEntries(interfaceA))
      })

    val env11 =
      functions1.foldLeft(env9)({
        case (env10, functionS) => {
          env10.addUnevaluatedFunction(functionS)
        }
      })

    val temputs =
      TemputsBox(
      Temputs(
        functionGeneratorByName2,
        Set(),
        Map(),
        List(),
        ListMap(),
        Map(),
        Set(),
        ListMap(),
        Map(),
        Set(),
        ListMap(),
        Map(),
        List(),
        Map(),
        Map(),
        Map()))

    val emptyPackStructRef = StructTemplar.addBuiltInStructs(env11, temputs)

//    structsA.foreach({
//      case (structS @ StructA(_, _, _, _, _, _, _, _, _, _)) => {
//        if (structS.isTemplate) {
//          // Do nothing, it's a template
//        } else {
//          val structTemplata = StructTemplata(env11, structS)
//          val _ = StructTemplar.getStructRef(temputs, structTemplata, List())
//        }
//      }
//    })
//
//    interfacesA.foreach({
//      case (interfaceS @ InterfaceA(_, _, _, _, _, _, _, _, _, _)) => {
//        if (interfaceS.isTemplate) {
//          // Do nothing, it's a template
//        } else {
//          val _ =
//            StructTemplar.getInterfaceRef(
//              temputs, InterfaceTemplata(env11, interfaceS), List())
//        }
//      }
//    })

    functions1.foreach({
      case (functionS) => {
        if (functionS.isTemplate) {
          // Do nothing, it's a template
        } else {
          functionS.name match {
            case FunctionNameA("main", _) => {
              val _ =
                FunctionTemplar.evaluateOrdinaryFunctionFromNonCallForPrototype(
                  temputs, FunctionTemplata(env11, functionS))
            }
            case _ => {
              // Do nothing. We only eagerly create main.
            }
          }
        }
      }
    })

//    // We already stamped the structs, this is just to get the constructors.
//    structsA.foreach({
//      case (structS @ StructA(_, _, _, _, _, _, _, _, _, _)) => {
//        if (structS.isTemplate) {
//          // Do nothing, it's a template
//        } else {
//          val structTemplata = StructTemplata(env11, structS)
//          val structRef2 = StructTemplar.getStructRef(temputs, structTemplata, List())
//          val structDef2 = temputs.lookupStruct(structRef2)
//          val memberCoords = structDef2.members.map(_.tyype).collect({ case ReferenceMemberType2(c) => c })
//          val TopLevelCitizenDeclarationNameA(name, _) = structS.name
//          OverloadTemplar.scoutExpectedFunctionForPrototype(
//            env11, temputs, GlobalFunctionFamilyNameA(name), List(), memberCoords.map(ParamFilter(_, None)), List(), true)
//        }
//      }
//    })

    stampNeededOverridesUntilSettled(env11, temputs)

    temputs.temputs
//    val result =
//      CompleteProgram2(
//        temputs.getAllInterfaces(),
//        temputs.getAllStructs(),
//        temputs.impls,
//        emptyPackStructRef,
//        temputs.getAllFunctions())
//
//    result
  }

  // (Once we add namespaces, this will probably change)
  def makeInterfaceEnvironmentEntries(
    interfaceA: InterfaceA
  ): Map[IName2, List[IEnvEntry]] = {
    val interfaceEnvEntry = InterfaceEnvEntry(interfaceA)

    val TopLevelCitizenDeclarationNameA(humanName, codeLocationS) = interfaceA.name
    val name = CitizenTemplateName2(humanName, NameTranslator.translateCodeLocation(codeLocationS))

    val env0 = Map[IName2, List[IEnvEntry]]()
    val env1 = EnvironmentUtils.addEntry(env0, name, interfaceEnvEntry)
    val env2 =
      EnvironmentUtils.addUnevaluatedFunction(
        env1,
        StructTemplar.getInterfaceConstructor(interfaceA))

    val env4 = env2
//    val env4 =
//      interfaceA.internalMethods.foldLeft(env2)({
//        case (env3, internalMethodA) => {
//          EnvironmentUtils.addUnevaluatedFunction(
//            env3,
//            internalMethodA)
//        }
//      })

    // Once we have sub-interfaces and sub-structs, we could recursively call this function.
    // We'll put our interfaceA onto the top of the list of every entry from the sub-struct/sub-interface.

    env4
  }

  // (Once we add namespaces, this will probably change)
  def makeStructEnvironmentEntries(
    structA: StructA
  ): Map[IName2, List[IEnvEntry]] = {
    val interfaceEnvEntry = StructEnvEntry(structA)

    val env0 = Map[IName2, List[IEnvEntry]]()
    val env1 = EnvironmentUtils.addEntry(env0, NameTranslator.translateNameStep(structA.name), interfaceEnvEntry)
    val env2 =
      EnvironmentUtils.addUnevaluatedFunction(
        env1,
        StructTemplar.getConstructor(structA))

    // To add once we have methods inside structs:
//    val env4 =
//      structA.internalMethods.foldLeft(env2)({
//        case (env3, internalMethodA) => EnvironmentUtils.addFunction(env3, Some(interfaceEnvEntry), internalMethodA)
//      })

    // Once we have sub-interfaces and sub-structs, we could recursively call this function.
    // We'll put our structA onto the top of the list of every entry from the sub-struct/sub-interface.

    env2
  }

  def stampNeededOverridesUntilSettled(env: NamespaceEnvironment[IName2], temputs: TemputsBox): Unit = {
    val partialEdges =
      EdgeTemplar.assemblePartialEdges(
        temputs.functions, temputs.getAllInterfaces(), temputs.impls)
    val neededOverrides =
      partialEdges.flatMap(e => e.methods.flatMap({
        case n @ NeededOverride(_, _) => List(n)
        case FoundFunction(_) => List()
      }))
    if (neededOverrides.isEmpty) {
      return temputs
    }

    // right now we're just assuming global env, but it might not be there...
    // perhaps look in the struct's env and the function's env? cant think of where else to look.
    println("which envs do we look in?")

      neededOverrides.foreach({
        case (neededOverride) => {
            OverloadTemplar.scoutExpectedFunctionForPrototype(
              env,
              temputs,
              neededOverride.name,
              List(), // No explicitly specified ones. It has to be findable just by param filters.
              neededOverride.paramFilters,
              List(),
              true) match {
            case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
              vfail("Couldn't find function for vtable!\n" + seff.toString)
            }
            case (ScoutExpectedFunctionSuccess(_)) =>
          }
        }
      })

    stampNeededOverridesUntilSettled(env, temputs)
  }

  def getMutabilities(temputs: TemputsBox, concreteValues2: List[Kind]):
  List[Mutability] = {
    concreteValues2.map(concreteValue2 => getMutability(temputs, concreteValue2))
  }

  def getMutability(temputs: TemputsBox, concreteValue2: Kind):
  Mutability = {
    concreteValue2 match {
      case Never2() => Immutable
      case Int2() => Immutable
      case Float2() => Immutable
      case Bool2() => Immutable
      case Str2() => Immutable
      case Void2() => Immutable
      case UnknownSizeArrayT2(RawArrayT2(_, mutability)) => mutability
      case KnownSizeArrayT2(_, RawArrayT2(_, mutability)) => mutability
      case sr @ StructRef2(_) => temputs.lookupMutability(sr)
      case ir @ InterfaceRef2(_) => temputs.lookupMutability(ir)
      case PackT2(_, sr) => temputs.lookupMutability(sr)
      case TupleT2(_, sr) => temputs.lookupMutability(sr)
      case OverloadSet(_, _, _) => {
        // Just like FunctionT2
        Immutable
      }
    }
  }

  def runTemplar(code: String): Option[Temputs] = {
    Astronomer.runAstronomer(code) match {
      case None => None
      case Some(program1) => Some(evaluate(program1))
    }
  }
}
