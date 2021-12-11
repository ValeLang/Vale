package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.{CallMacro, DontCallMacro, FinalP, ImmutableP, MutabilityP, MutableP}
import net.verdagon.vale.scout.{SealedS, Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.OverloadTemplar.FindFunctionFailure
import net.verdagon.vale.templar.{ast, _}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar, FunctionTemplarCore, FunctionTemplarMiddleLayer, FunctionTemplarOrdinaryOrTemplatedLayer}
import net.verdagon.vale._
import net.verdagon.vale.scout.rules.RuneUsage
import net.verdagon.vale.templar.ast.ProgramT.tupleHumanName
import net.verdagon.vale.templar.ast.{AbstractT, ArgLookupTE, BlockTE, DiscardTE, FunctionCallTE, FunctionHeaderT, FunctionT, ICitizenAttributeT, LocationInFunctionEnvironment, OverrideT, ParameterT, ProgramT, PrototypeT, ReferenceMemberLookupTE, ReturnTE, SealedT, SoftLoadTE}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.names.{AnonymousSubstructImplNameT, AnonymousSubstructMemberNameT, AnonymousSubstructNameT, AnonymousSubstructTemplateNameT, CitizenNameT, CitizenTemplateNameT, ClosureParamNameT, CodeVarNameT, FullNameT, FunctionNameT, FunctionTemplateNameT, ICitizenNameT, INameT, ImplDeclareNameT, LambdaCitizenNameT, LambdaCitizenTemplateNameT, NameTranslator, RuneNameT, SelfNameT, FreeTemplateNameT, TemplarTemporaryVarNameT}

import scala.collection.immutable.List

class StructTemplarCore(
    opts: TemplarOptions,
    profiler: IProfiler,
    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  def makeStruct(
    // The environment that the struct was defined in.
    structRunesEnv: CitizenEnvironment[INameT],
    temputs: Temputs,
    structA: StructA,
    coercedFinalTemplateArgs: Vector[ITemplata]):
  (StructDefinitionT) = {
    val templateNameT = NameTranslator.translateCitizenName(structA.name)
    val structNameT = templateNameT.makeCitizenName(coercedFinalTemplateArgs)
    val fullNameT = structRunesEnv.fullName.addStep(structNameT)
    val temporaryStructRef = StructTT(fullNameT)

    val attributesWithoutExportOrMacros =
      structA.attributes.filter({
        case ExportS(_) => false
        case MacroCallS(range, dontCall, macroName) => false
        case _ => true
      })

    val maybeExport =
      structA.attributes.collectFirst { case e@ExportS(_) => e }

    val mutability =
      structRunesEnv.lookupNearestWithImpreciseName(
        profiler,
        RuneNameS(structA.mutabilityRune.rune),
        Set(TemplataLookupContext)).toList match {
        case List(MutabilityTemplata(m)) => m
        case _ => vwat()
      }

    val defaultCalledMacros =
      Vector(
        MacroCallS(structA.range, CallMacro, "DeriveStructDrop"),
        MacroCallS(structA.range, CallMacro, "DeriveStructFree"),
        MacroCallS(structA.range, CallMacro, "DeriveImplFree"))
    val macrosToCall =
      structA.attributes.foldLeft(defaultCalledMacros)({
        case (macrosToCall, mc @ MacroCallS(range, CallMacro, macroName)) => {
          if (macrosToCall.exists(_.macroName == macroName)) {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Calling macro twice: " + macroName))
          }
          macrosToCall :+ mc
        }
        case (macrosToCall, MacroCallS(_, DontCallMacro, macroName)) => macrosToCall.filter(_.macroName != macroName)
        case (macrosToCall, _) => macrosToCall
      })

    val envEntriesFromMacros =
      macrosToCall.flatMap({ case MacroCallS(range, CallMacro, macroName) =>
        val maacro =
          structRunesEnv.globalEnv.nameToStructDefinedMacro.get(macroName) match {
            case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Macro not found: " + macroName))
            case Some(m) => m
          }
        val newEntriesList = maacro.getStructChildEntries(macroName, fullNameT, structA, mutability)
        val newEntries =
          newEntriesList.map({ case (entryName, value) =>
            vcurious(fullNameT.steps.size + 1 == entryName.steps.size)
            val last = entryName.last
            last -> value
          })
        newEntries
      })

    val structInnerEnv =
      CitizenEnvironment(
        structRunesEnv.globalEnv, structRunesEnv, fullNameT,
        TemplatasStore(fullNameT, Map(), Map())
          .addEntries(envEntriesFromMacros))

    temputs.declareKindEnv(temporaryStructRef, structInnerEnv)

    val members = makeStructMembers(structInnerEnv, temputs, structA.members)

    if (mutability == ImmutableT) {
      members.zipWithIndex.foreach({ case (member, index) =>
      if (member.variability == VaryingT) {
          throw CompileErrorExceptionT(
            ImmStructCantHaveVaryingMember(
              structA.members(index).range,
              structA.name,
              structA.members(index) match {
                case NormalStructMemberS(range, name, variability, typeRune) => name
                case VariadicStructMemberS(range, variability, typeRune) => "(unnamed)"
              }))
        }
      })
    }

    val structDefT =
      StructDefinitionT(
        fullNameT,
        translateCitizenAttributes(attributesWithoutExportOrMacros),
        structA.weakable,
        mutability,
        members,
        false)

    temputs.add(structDefT);

    maybeExport match {
      case None =>
      case Some(exportPackageCoord) => {
        val exportedName =
          fullNameT.last match {
            case CitizenNameT(CitizenTemplateNameT(humanName), _) => humanName
            case _ => vfail("Can't export something that doesn't have a human readable name!")
          }
        temputs.addKindExport(
          structA.range,
          structDefT.getRef,
          exportPackageCoord.packageCoordinate,
          exportedName)
      }
    }

    profiler.childFrame("struct ancestor interfaces", () => {
      val ancestorImplsAndInterfaces =
        ancestorHelper.getAncestorInterfaces(temputs, temporaryStructRef)

      ancestorImplsAndInterfaces.foreach({
        case (ancestorInterface, implTemplata) => {
          val interfaceDefinition2 = temputs.lookupInterface(ancestorInterface)
          if (structDefT.weakable != interfaceDefinition2.weakable) {
            throw WeakableImplingMismatch(structDefT.weakable, interfaceDefinition2.weakable)
          }
          temputs.addImpl(temporaryStructRef, ancestorInterface)
        }
      })
    })

    structDefT
  }

  def translateCitizenAttributes(attrs: Vector[ICitizenAttributeS]): Vector[ICitizenAttributeT] = {
    attrs.map({
      case SealedS => SealedT
      case MacroCallS(_, _, _) => vwat() // Should have been processed
      case x => vimpl(x.toString)
    })
  }

  // Takes a IEnvironment because we might be inside a:
  // struct<T> Thing<T> {
  //   t: T;
  // }
  // which means we need some way to know what T is.
  def makeInterface(
    interfaceRunesEnv: CitizenEnvironment[INameT],
    temputs: Temputs,
    interfaceA: InterfaceA,
    coercedFinalTemplateArgs2: Vector[ITemplata]):
  (InterfaceDefinitionT) = {
    val TopLevelCitizenDeclarationNameS(humanName, codeLocation) = interfaceA.name
    val fullNameT = interfaceRunesEnv.fullName.addStep(CitizenNameT(CitizenTemplateNameT(humanName), coercedFinalTemplateArgs2))
    val temporaryInferfaceRef = InterfaceTT(fullNameT)

    val attributesWithoutExport =
      interfaceA.attributes.filter({
        case ExportS(_) => false
        case _ => true
      })
    val maybeExport =
      interfaceA.attributes.collectFirst { case e@ExportS(_) => e }


    val mutability =
      interfaceRunesEnv.lookupNearestWithImpreciseName(
        profiler,
        RuneNameS(interfaceA.mutabilityRune.rune),
        Set(TemplataLookupContext)).toList match {
        case List(MutabilityTemplata(m)) => m
        case _ => vwat()
      }

    val defaultCalledMacros =
      Vector(
        MacroCallS(interfaceA.range, CallMacro, "DeriveInterfaceDrop"),
        MacroCallS(interfaceA.range, CallMacro, "DeriveInterfaceFree"))
    val macrosToCall =
      interfaceA.attributes.foldLeft(defaultCalledMacros)({
        case (macrosToCall, mc @ MacroCallS(_, CallMacro, _)) => macrosToCall :+ mc
        case (macrosToCall, MacroCallS(_, DontCallMacro, macroName)) => macrosToCall.filter(_.macroName != macroName)
        case (macrosToCall, _) => macrosToCall
      })

    val envEntriesFromMacros =
      macrosToCall.flatMap({ case MacroCallS(range, CallMacro, macroName) =>
        val maacro =
          interfaceRunesEnv.globalEnv.nameToInterfaceDefinedMacro.get(macroName) match {
            case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Macro not found: " + macroName))
            case Some(m) => m
          }
        val newEntriesList = maacro.getInterfaceChildEntries(fullNameT, interfaceA, mutability)
        val newEntries =
          newEntriesList.map({ case (entryName, value) =>
            vcurious(fullNameT.steps.size + 1 == entryName.steps.size)
            val last = entryName.last
            last -> value
          })
        newEntries
      })


    val interfaceInnerEnv =
      CitizenEnvironment(
        interfaceRunesEnv.globalEnv,
        interfaceRunesEnv,
        fullNameT,
        TemplatasStore(fullNameT, Map(), Map())
          .addEntries(envEntriesFromMacros)
          .addEntries(
            interfaceA.identifyingRunes.zip(coercedFinalTemplateArgs2)
              .map({ case (rune, templata) => (RuneNameT(rune.rune), TemplataEnvEntry(templata)) }))
          .addEntries(
            Vector(SelfNameT() -> TemplataEnvEntry(KindTemplata(temporaryInferfaceRef))))
          .addEntries(
            interfaceA.internalMethods
              .map(internalMethod => {
                val functionName = NameTranslator.translateFunctionNameToTemplateName(internalMethod.name)
                (functionName -> FunctionEnvEntry(internalMethod))
              })))

    temputs
      .declareKindEnv(
        temporaryInferfaceRef,
        interfaceInnerEnv)

    val internalMethods2 =
      interfaceA.internalMethods.map(internalMethod => {
        if (internalMethod.isTemplate) {
          delegate.evaluateTemplatedFunctionFromNonCallForHeader(
            temputs,
            FunctionTemplata(
              interfaceInnerEnv,
              internalMethod))
        } else {
          delegate.evaluateOrdinaryFunctionFromNonCallForHeader(
            temputs,
            FunctionTemplata(
              interfaceInnerEnv,
              internalMethod))
        }
      })

    val interfaceDef2 =
      InterfaceDefinitionT(
        fullNameT,
        translateCitizenAttributes(attributesWithoutExport),
        interfaceA.weakable,
        mutability,
        internalMethods2)
    temputs.add(interfaceDef2)

    maybeExport match {
      case None =>
      case Some(exportPackageCoord) => {
        val exportedName =
          fullNameT.last match {
            case CitizenNameT(CitizenTemplateNameT(humanName), _) => humanName
            case _ => vfail("Can't export something that doesn't have a human readable name!")
          }
        temputs.addKindExport(
          interfaceA.range,
          interfaceDef2.getRef,
          exportPackageCoord.packageCoordinate,
          exportedName)
      }
    }

    profiler.childFrame("interface ancestor interfaces", () => {
      val _ = ancestorHelper.getParentInterfaces(temputs, temporaryInferfaceRef)
    })

    (interfaceDef2)
  }

  private def makeStructMembers(env: IEnvironment, temputs: Temputs, members: Vector[IStructMemberS]): (Vector[StructMemberT]) = {
    members.flatMap(makeStructMember(env, temputs, _))
  }

  private def makeStructMember(
    env: IEnvironment,
    temputs: Temputs,
    member: IStructMemberS):
  Vector[StructMemberT] = {
    val typeTemplata =
      vassertOne(
        env.lookupNearestWithImpreciseName(
          profiler, RuneNameS(member.typeRune.rune), Set(TemplataLookupContext)))
    val variabilityT = Conversions.evaluateVariability(member.variability)
    member match {
      case NormalStructMemberS(_, name, _, _) => {
        val CoordTemplata(coord) = typeTemplata
        Vector(StructMemberT(CodeVarNameT(name), variabilityT, ReferenceMemberTypeT(coord)))
      }
      case VariadicStructMemberS(_, _, _) => {
        val CoordListTemplata(coords) = typeTemplata
        coords.zipWithIndex.map({ case (coord, index) =>
          StructMemberT(CodeVarNameT(index.toString), variabilityT, ReferenceMemberTypeT(coord))
        })
      }
    }
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    temputs: Temputs,
    name: IFunctionDeclarationNameS,
    functionA: FunctionA,
    members: Vector[StructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
    val isMutable =
      members.exists({ case StructMemberT(name, variability, tyype) =>
        if (variability == VaryingT) {
          true
        } else {
          tyype match {
            case AddressMemberTypeT(reference) => true
            case ReferenceMemberTypeT(reference) => {
              reference.ownership match {
                case OwnT | PointerT | WeakT => true
                case ShareT => false
              }
            }
          }
        }
      })
    val mutability = if (isMutable) MutableT else ImmutableT

    val nearTemplateName = LambdaCitizenTemplateNameT(NameTranslator.translateCodeLocation(functionA.range.begin))
    val nearName = nearTemplateName.makeCitizenName(Vector())
    val fullName = containingFunctionEnv.fullName.addStep(nearName)

    val structTT = StructTT(fullName)

    // We declare the function into the environment that we use to compile the
    // struct, so that those who use the struct can reach into its environment
    // and see the function and use it.
    // See CSFMSEO and SAFHE.
    val structEnv =
      CitizenEnvironment(
        containingFunctionEnv.globalEnv,
        containingFunctionEnv,
        fullName,
        TemplatasStore(fullName, Map(), Map())
          .addEntries(
            Vector(
              FunctionTemplateNameT(CallTemplar.CALL_FUNCTION_NAME, functionA.range.begin) ->
                FunctionEnvEntry(functionA),
              FunctionTemplateNameT(CallTemplar.DROP_FUNCTION_NAME, functionA.range.begin) ->
                FunctionEnvEntry(
                  containingFunctionEnv.globalEnv.structDropMacro.makeImplicitDropFunction(
                    FunctionNameS(CallTemplar.DROP_FUNCTION_NAME, functionA.range.begin), functionA.range)),
              nearName -> TemplataEnvEntry(KindTemplata(structTT)),
              SelfNameT() -> TemplataEnvEntry(KindTemplata(structTT))) ++
              (if (mutability == ImmutableT) {
                Vector(
                  FreeTemplateNameT(functionA.range.begin) ->
                    FunctionEnvEntry(
                      containingFunctionEnv.globalEnv.structFreeMacro.makeImplicitFreeFunction(
                        FreeDeclarationNameS(functionA.range.begin), functionA.range)))
              } else {
                Vector()
              })))
    // We return this from the function in case we want to eagerly compile it (which we do
    // if it's not a template).
    val functionTemplata =
        FunctionTemplata(
          structEnv,
          functionA)

    temputs.declareKind(structTT);
    temputs.declareCitizenMutability(structTT, mutability)
    temputs.declareKindEnv(structTT, structEnv);


    val closureStructDefinition = StructDefinitionT(fullName, Vector.empty, false, mutability, members, true);
    temputs.add(closureStructDefinition)

    val closuredVarsStructRef = closureStructDefinition.getRef;

    (closuredVarsStructRef, mutability, functionTemplata)
  }
}
