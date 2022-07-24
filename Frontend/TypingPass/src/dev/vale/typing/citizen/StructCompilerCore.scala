package dev.vale.typing.citizen

import dev.vale.highertyping.{FunctionA, InterfaceA, StructA}
import dev.vale.{Interner, Keywords, vassertOne, vcurious, vfail, vimpl, vwat, _}
import dev.vale.parsing.ast.{CallMacroP, DontCallMacroP}
import dev.vale.postparsing.rules.RuneUsage
import dev.vale.postparsing.{ExportS, FreeDeclarationNameS, FunctionNameS, ICitizenAttributeS, IFunctionDeclarationNameS, IStructMemberS, MacroCallS, NormalStructMemberS, RuneNameS, SealedS, TopLevelCitizenDeclarationNameS, VariadicStructMemberS}
import dev.vale.typing.expression.CallCompiler
import dev.vale.highertyping._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing._
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.ast.{ICitizenAttributeT, SealedT}
import dev.vale.typing.{CompileErrorExceptionT, CompilerOutputs, ImmStructCantHaveVaryingMember, RangedInternalErrorT, TypingPassOptions, env}
import dev.vale.typing.{ast, _}
import dev.vale.typing.env._
import dev.vale.typing.function.FunctionCompiler
import dev.vale.parsing.ast.DontCallMacroP
import dev.vale.typing.env.{CitizenEnvironment, FunctionEnvEntry, IEnvironment, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, CodeVarNameT, FreeTemplateNameT, FunctionTemplateNameT, INameT, LambdaCitizenTemplateNameT, NameTranslator, RuneNameT, SelfNameT}
import dev.vale.typing.templata.{Conversions, CoordListTemplata, CoordTemplata, FunctionTemplata, ITemplata, KindTemplata, MutabilityTemplata}
import dev.vale.typing.types.{AddressMemberTypeT, BorrowT, ImmutableT, InterfaceDefinitionT, InterfaceTT, MutabilityT, MutableT, OwnT, ReferenceMemberTypeT, ShareT, StructDefinitionT, StructMemberT, StructTT, VaryingT, WeakT}
import dev.vale.typing.ast._
import dev.vale.typing.names.AnonymousSubstructImplNameT

import scala.collection.immutable.List

class StructCompilerCore(
  opts: TypingPassOptions,
  interner: Interner,
  keywords: Keywords,
  nameTranslator: NameTranslator,
  ancestorHelper: AncestorHelper,
  delegate: IStructCompilerDelegate) {

  def makeStruct(
    // The environment that the struct was defined in.
    structRunesEnv: CitizenEnvironment[INameT],
    coutputs: CompilerOutputs,
    structA: StructA,
    coercedFinalTemplateArgs: Vector[ITemplata]):
  (StructDefinitionT) = {
    val templateNameT = nameTranslator.translateCitizenName(structA.name)
    val structNameT = templateNameT.makeCitizenName(interner, coercedFinalTemplateArgs)
    val fullNameT = structRunesEnv.fullName.addStep(structNameT)
    val temporaryStructRef = interner.intern(StructTT(fullNameT))

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

        interner.intern(RuneNameS(structA.mutabilityRune.rune)),
        Set(TemplataLookupContext)).toList match {
        case List(MutabilityTemplata(m)) => m
        case _ => vwat()
      }

    val defaultCalledMacros =
      Vector(
        MacroCallS(structA.range, CallMacroP, keywords.DeriveStructDrop),
        MacroCallS(structA.range, CallMacroP, keywords.DeriveStructFree),
        MacroCallS(structA.range, CallMacroP, keywords.DeriveImplFree))
    val macrosToCall =
      structA.attributes.foldLeft(defaultCalledMacros)({
        case (macrosToCall, mc @ MacroCallS(range, CallMacroP, macroName)) => {
          if (macrosToCall.exists(_.macroName == macroName)) {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Calling macro twice: " + macroName))
          }
          macrosToCall :+ mc
        }
        case (macrosToCall, MacroCallS(_, DontCallMacroP, macroName)) => macrosToCall.filter(_.macroName != macroName)
        case (macrosToCall, _) => macrosToCall
      })

    val envEntriesFromMacros =
      macrosToCall.flatMap({ case MacroCallS(range, CallMacroP, macroName) =>
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
          .addEntries(interner, envEntriesFromMacros))

    coutputs.declareKindEnv(temporaryStructRef, structInnerEnv)

    val members = makeStructMembers(structInnerEnv, coutputs, structA.members)

    if (mutability == ImmutableT) {
      members.zipWithIndex.foreach({ case (member, index) =>
      if (member.variability == VaryingT) {
          throw CompileErrorExceptionT(
            ImmStructCantHaveVaryingMember(
              structA.members(index).range,
              structA.name,
              structA.members(index) match {
                case NormalStructMemberS(range, name, variability, typeRune) => name.str
                case VariadicStructMemberS(range, variability, typeRune) => "(unnamed)"
              }))
        }
      })
    }

    val structDefT =
      StructDefinitionT(
        fullNameT,
        interner.intern(StructTT(fullNameT)),
        translateCitizenAttributes(attributesWithoutExportOrMacros),
        structA.weakable,
        mutability,
        members,
        false)

    coutputs.add(structDefT);

    maybeExport match {
      case None =>
      case Some(exportPackageCoord) => {
        val exportedName =
          fullNameT.last match {
            case CitizenNameT(CitizenTemplateNameT(humanName), _) => humanName
            case _ => vfail("Can't export something that doesn't have a human readable name!")
          }
        coutputs.addKindExport(
          structA.range,
          structDefT.getRef,
          exportPackageCoord.packageCoordinate,
          exportedName)
      }
    }

    val ancestorImplsAndInterfaces =
      ancestorHelper.getAncestorInterfaces(coutputs, temporaryStructRef)

    ancestorImplsAndInterfaces.foreach({
      case (ancestorInterface, implTemplata) => {
        val interfaceDefinition2 = coutputs.lookupInterface(ancestorInterface)
        if (structDefT.weakable != interfaceDefinition2.weakable) {
          throw WeakableImplingMismatch(structDefT.weakable, interfaceDefinition2.weakable)
        }
        coutputs.addImpl(temporaryStructRef, ancestorInterface)
      }
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
    coutputs: CompilerOutputs,
    interfaceA: InterfaceA,
    coercedFinalTemplateArgs2: Vector[ITemplata]):
  (InterfaceDefinitionT) = {
    val TopLevelCitizenDeclarationNameS(humanName, codeLocation) = interfaceA.name
    val fullNameT = interfaceRunesEnv.fullName.addStep(interner.intern(CitizenNameT(interner.intern(CitizenTemplateNameT(humanName)), coercedFinalTemplateArgs2)))
    val temporaryInferfaceRef = interner.intern(InterfaceTT(fullNameT))

    val attributesWithoutExportOrMacros =
      interfaceA.attributes.filter({
        case ExportS(_) => false
        case MacroCallS(range, dontCall, macroName) => false
        case _ => true
      })
    val maybeExport =
      interfaceA.attributes.collectFirst { case e@ExportS(_) => e }


    val mutability =
      interfaceRunesEnv.lookupNearestWithImpreciseName(

        interner.intern(RuneNameS(interfaceA.mutabilityRune.rune)),
        Set(TemplataLookupContext)).toList match {
        case List(MutabilityTemplata(m)) => m
        case _ => vwat()
      }

    val defaultCalledMacros =
      Vector(
        MacroCallS(interfaceA.range, CallMacroP, keywords.DeriveInterfaceDrop),
        MacroCallS(interfaceA.range, CallMacroP, keywords.DeriveInterfaceFree))
    val macrosToCall =
      interfaceA.attributes.foldLeft(defaultCalledMacros)({
        case (macrosToCall, mc @ MacroCallS(_, CallMacroP, _)) => macrosToCall :+ mc
        case (macrosToCall, MacroCallS(_, DontCallMacroP, macroName)) => macrosToCall.filter(_.macroName != macroName)
        case (macrosToCall, _) => macrosToCall
      })

    val envEntriesFromMacros =
      macrosToCall.flatMap({ case MacroCallS(range, CallMacroP, macroName) =>
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
          .addEntries(interner, envEntriesFromMacros)
          .addEntries(
            interner,
            interfaceA.identifyingRunes.zip(coercedFinalTemplateArgs2)
              .map({ case (rune, templata) => (interner.intern(RuneNameT(rune.rune)), TemplataEnvEntry(templata)) }))
          .addEntries(
            interner,
            Vector(interner.intern(SelfNameT()) -> TemplataEnvEntry(KindTemplata(temporaryInferfaceRef))))
          .addEntries(
            interner,
            interfaceA.internalMethods
              .map(internalMethod => {
                val functionName = nameTranslator.translateFunctionNameToTemplateName(internalMethod.name)
                (functionName -> FunctionEnvEntry(internalMethod))
              })))

    coutputs
      .declareKindEnv(
        temporaryInferfaceRef,
        interfaceInnerEnv)

    val internalMethods2 =
      interfaceA.internalMethods.map(internalMethod => {
        if (internalMethod.isTemplate) {
          delegate.evaluateTemplatedFunctionFromNonCallForHeader(
            coutputs,
            FunctionTemplata(
              interfaceInnerEnv,
              internalMethod))
        } else {
          delegate.evaluateOrdinaryFunctionFromNonCallForHeader(
            coutputs,
            FunctionTemplata(
              interfaceInnerEnv,
              internalMethod))
        }
      })

    val interfaceDef2 =
      InterfaceDefinitionT(
        fullNameT,
        interner.intern(InterfaceTT(fullNameT)),
        translateCitizenAttributes(attributesWithoutExportOrMacros),
        interfaceA.weakable,
        mutability,
        internalMethods2)
    coutputs.add(interfaceDef2)

    maybeExport match {
      case None =>
      case Some(exportPackageCoord) => {
        val exportedName =
          fullNameT.last match {
            case CitizenNameT(CitizenTemplateNameT(humanName), _) => humanName
            case _ => vfail("Can't export something that doesn't have a human readable name!")
          }
        coutputs.addKindExport(
          interfaceA.range,
          interfaceDef2.getRef,
          exportPackageCoord.packageCoordinate,
          exportedName)
      }
    }

    val _ = ancestorHelper.getParentInterfaces(coutputs, temporaryInferfaceRef)

    (interfaceDef2)
  }

  private def makeStructMembers(env: IEnvironment, coutputs: CompilerOutputs, members: Vector[IStructMemberS]): (Vector[StructMemberT]) = {
    members.flatMap(makeStructMember(env, coutputs, _))
  }

  private def makeStructMember(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    member: IStructMemberS):
  Vector[StructMemberT] = {
    val typeTemplata =
      vassertOne(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(member.typeRune.rune)), Set(TemplataLookupContext)))
    val variabilityT = Conversions.evaluateVariability(member.variability)
    member match {
      case NormalStructMemberS(_, name, _, _) => {
        val CoordTemplata(coord) = typeTemplata
        Vector(StructMemberT(interner.intern(CodeVarNameT(name)), variabilityT, ReferenceMemberTypeT(coord)))
      }
      case VariadicStructMemberS(_, _, _) => {
        val CoordListTemplata(coords) = typeTemplata
        coords.zipWithIndex.map({ case (coord, index) =>
          StructMemberT(interner.intern(CodeVarNameT(interner.intern(StrI(index.toString)))), variabilityT, ReferenceMemberTypeT(coord))
        })
      }
    }
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    coutputs: CompilerOutputs,
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
                case OwnT | BorrowT | WeakT => true
                case ShareT => false
              }
            }
          }
        }
      })
    val mutability = if (isMutable) MutableT else ImmutableT

    val nearTemplateName = interner.intern(LambdaCitizenTemplateNameT(nameTranslator.translateCodeLocation(functionA.range.begin)))
    val nearName = nearTemplateName.makeCitizenName(interner, Vector())
    val fullName = containingFunctionEnv.fullName.addStep(nearName)

    val structTT = interner.intern(StructTT(fullName))

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
            interner,
            Vector(
              interner.intern(FunctionTemplateNameT(keywords.underscoresCall, functionA.range.begin)) ->
                env.FunctionEnvEntry(functionA),
              interner.intern(FunctionTemplateNameT(keywords.drop, functionA.range.begin)) ->
                FunctionEnvEntry(
                  containingFunctionEnv.globalEnv.structDropMacro.makeImplicitDropFunction(
                    interner.intern(FunctionNameS(keywords.drop, functionA.range.begin)), functionA.range)),
              nearName -> TemplataEnvEntry(KindTemplata(structTT)),
              interner.intern(SelfNameT()) -> TemplataEnvEntry(KindTemplata(structTT))) ++
              (if (mutability == ImmutableT) {
                Vector(
                  interner.intern(FreeTemplateNameT(functionA.range.begin)) ->
                    FunctionEnvEntry(
                      containingFunctionEnv.globalEnv.structFreeMacro.makeImplicitFreeFunction(
                        interner.intern(FreeDeclarationNameS(functionA.range.begin)), functionA.range)))
              } else {
                Vector()
              })))
    // We return this from the function in case we want to eagerly compile it (which we do
    // if it's not a template).
    val functionTemplata =
        FunctionTemplata(
          structEnv,
          functionA)

    coutputs.declareKind(structTT);
    coutputs.declareCitizenMutability(structTT, mutability)
    coutputs.declareKindEnv(structTT, structEnv);


    val closureStructDefinition =
      StructDefinitionT(
        fullName,
        interner.intern(StructTT(fullName)),
        Vector.empty, false, mutability, members, true);
    coutputs.add(closureStructDefinition)

    val closuredVarsStructRef = closureStructDefinition.getRef;

    (closuredVarsStructRef, mutability, functionTemplata)
  }
}
