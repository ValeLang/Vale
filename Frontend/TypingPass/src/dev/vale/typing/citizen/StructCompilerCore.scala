package dev.vale.typing.citizen

import dev.vale.highertyping.{FunctionA, InterfaceA, StructA}
import dev.vale.{Interner, Keywords, vassertOne, vcurious, vfail, vimpl, vwat, _}
import dev.vale.parsing.ast.{CallMacroP, DontCallMacroP}
import dev.vale.postparsing.rules.RuneUsage
import dev.vale.postparsing._
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
import dev.vale.typing.names.{AnonymousSubstructImplNameT, CitizenNameT, CitizenTemplateNameT, CodeVarNameT, IdT, FunctionTemplateNameT, ICitizenTemplateNameT, IInterfaceNameT, IInterfaceTemplateNameT, INameT, IStructNameT, IStructTemplateNameT, InterfaceNameT, InterfaceTemplateNameT, LambdaCitizenTemplateNameT, NameTranslator, PackageTopLevelNameT, RuneNameT, SelfNameT, StructNameT, StructTemplateNameT}
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.typing.ast._
import dev.vale.typing.templata.ITemplata.expectMutabilityTemplata

import scala.collection.immutable.List

class StructCompilerCore(
  opts: TypingPassOptions,
  interner: Interner,
  keywords: Keywords,
  nameTranslator: NameTranslator,
  delegate: IStructCompilerDelegate) {

  def compileStruct(
    outerEnv: IEnvironment,
    structRunesEnv: CitizenEnvironment[IStructNameT, IStructTemplateNameT],
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    structA: StructA):
  Unit = {
    val templateArgs = structRunesEnv.fullName.localName.templateArgs
    val templateFullNameT = structRunesEnv.templateName
    val templateNameT = templateFullNameT.localName
    val placeholderedNameT = templateNameT.makeStructName(interner, templateArgs)
    val placeholderedFullNameT = templateFullNameT.copy(localName = placeholderedNameT)

    // Usually when we make a StructTT we put the instantiation bounds into the coutputs,
    // but this isn't really an instantiation, so we don't here.
    val placeholderedStructTT = interner.intern(StructTT(placeholderedFullNameT))

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
        case List(m) => ITemplata.expectMutability(m)
        case _ => vwat()
      }

    val defaultCalledMacros =
      Vector(
        MacroCallS(structA.range, CallMacroP, keywords.DeriveStructDrop))//,
//        MacroCallS(structA.range, CallMacroP, keywords.DeriveStructFree),
//        MacroCallS(structA.range, CallMacroP, keywords.DeriveImplFree))
    val macrosToCall =
      structA.attributes.foldLeft(defaultCalledMacros)({
        case (macrosToCall, mc @ MacroCallS(range, CallMacroP, macroName)) => {
          if (macrosToCall.exists(_.macroName == macroName)) {
            throw CompileErrorExceptionT(RangedInternalErrorT(range :: parentRanges, "Calling macro twice: " + macroName))
          }
          macrosToCall :+ mc
        }
        case (macrosToCall, MacroCallS(_, DontCallMacroP, macroName)) => macrosToCall.filter(_.macroName != macroName)
        case (macrosToCall, _) => macrosToCall
      })

    val structInnerEnv =
      CitizenEnvironment(
        structRunesEnv.globalEnv,
        structRunesEnv,
        templateFullNameT,
        placeholderedFullNameT,
        TemplatasStore(placeholderedFullNameT, Map(), Map()))

    val members = makeStructMembers(structInnerEnv, coutputs, structA.members)

    if (mutability == MutabilityTemplata(ImmutableT)) {
      members.zipWithIndex.foreach({
        case (VariadicStructMemberT(name, tyype), index) => {
          vimpl() // Dont have imm variadics yet
        }
        case (NormalStructMemberT(name, variability, tyype), index) => {
          if (variability == VaryingT) {
            throw CompileErrorExceptionT(
              ImmStructCantHaveVaryingMember(
                structA.members(index).range :: parentRanges,
                structA.name,
                structA.members(index) match {
                  case NormalStructMemberS(range, name, variability, typeRune) => name.str
                  case VariadicStructMemberS(range, variability, typeRune) => "(unnamed)"
                }))
          }

          if (tyype.reference.ownership != ShareT) {
            throw CompileErrorExceptionT(
              ImmStructCantHaveMutableMember(
                structA.members(index).range :: parentRanges,
                structA.name,
                structA.members(index) match {
                  case NormalStructMemberS(range, name, variability, typeRune) => name.str
                  case VariadicStructMemberS(range, variability, typeRune) => "(unnamed)"
                }))
          }
        }
      })
    }

    outerEnv.templatas.entriesByNameT.foreach({
      case (name, FunctionEnvEntry(functionA)) => {
        // These have to be delegated, otherwise some compiling functions won't have what we expect.
        // For example, MyShip.drop will expect to see the members of MyEngine, but we haven't compiled
        // MyEngine yet.
        // We need to defer all these functions until after the structs and interfaces are done.
        coutputs.deferEvaluatingFunction(
          DeferredEvaluatingFunction(
            outerEnv.fullName.addStep(name),
            (coutputs) => {
              delegate.evaluateGenericFunctionFromNonCallForHeader(
                coutputs, parentRanges, FunctionTemplata(outerEnv, functionA), true)
            }))
      }
      case _ => vcurious()
    })

    val runeToFunctionBound = TemplataCompiler.assembleRuneToFunctionBound(structRunesEnv.templatas)
    val runeToImplBound = TemplataCompiler.assembleRuneToImplBound(structRunesEnv.templatas)

    val structDefT =
      StructDefinitionT(
        templateFullNameT,
        placeholderedStructTT,
        translateCitizenAttributes(attributesWithoutExportOrMacros),
        structA.weakable,
        mutability,
        members,
        false,
        runeToFunctionBound,
        runeToImplBound)

    coutputs.addStruct(structDefT);

    maybeExport match {
      case None =>
      case Some(exportPackageCoord) => {
        val exportedName =
          placeholderedFullNameT.localName match {
            case StructNameT(StructTemplateNameT(humanName), _) => humanName
            case _ => vfail("Can't export something that doesn't have a human readable name!")
          }
        coutputs.addKindExport(
          structA.range,
          placeholderedStructTT,
          exportPackageCoord.packageCoordinate,
          exportedName)
      }
    }
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
  def compileInterface(
    containingEnv: IEnvironment,
    outerEnv: IEnvironment,
    interfaceRunesEnv: CitizenEnvironment[IInterfaceNameT, IInterfaceTemplateNameT],
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    interfaceA: InterfaceA):
  (InterfaceDefinitionT) = {
    val templateArgs = interfaceRunesEnv.fullName.localName.templateArgs
    val templateFullNameT = interfaceRunesEnv.templateName
    val templateNameT = templateFullNameT.localName
    val placeholderedNameT = templateNameT.makeInterfaceName(interner, templateArgs)
    val placeholderedFullNameT = templateFullNameT.copy(localName = placeholderedNameT)

    // Usually when we make a StructTT we put the instantiation bounds into the coutputs,
    // but this isn't really an instantiation, so we don't here.
    val placeholderedInterfaceTT = interner.intern(InterfaceTT(placeholderedFullNameT))

    val attributesWithoutExportOrMacros =
      interfaceA.attributes.filter({
        case ExportS(_) => false
        case MacroCallS(range, dontCall, macroName) => false
        case _ => true
      })
    val maybeExport =
      interfaceA.attributes.collectFirst { case e@ExportS(_) => e }


    val mutability =
      ITemplata.expectMutability(
        vassertSome(
          interfaceRunesEnv.lookupNearestWithImpreciseName(
            interner.intern(RuneNameS(interfaceA.mutabilityRune.rune)),
            Set(TemplataLookupContext))))

//    val defaultCalledMacros =
//      Vector(
//        MacroCallS(interfaceA.range, CallMacroP, keywords.DeriveInterfaceDrop),
//        MacroCallS(interfaceA.range, CallMacroP, keywords.DeriveInterfaceFree))
//    val macrosToCall =
//      interfaceA.attributes.foldLeft(defaultCalledMacros)({
//        case (macrosToCall, mc @ MacroCallS(_, CallMacroP, _)) => macrosToCall :+ mc
//        case (macrosToCall, MacroCallS(_, DontCallMacroP, macroName)) => macrosToCall.filter(_.macroName != macroName)
//        case (macrosToCall, _) => macrosToCall
//      })

//    val envEntriesFromMacros =
//      macrosToCall.flatMap({ case MacroCallS(range, CallMacroP, macroName) =>
//        val maacro =
//          interfaceRunesEnv.globalEnv.nameToInterfaceDefinedMacro.get(macroName) match {
//            case None => {
//              throw CompileErrorExceptionT(RangedInternalErrorT(range :: parentRanges, "Macro not found: " + macroName))
//            }
//            case Some(m) => m
//          }
//        val newEntriesList = maacro.getInterfaceSiblingEntries(placeholderedFullNameT, interfaceA)
//        val newEntries =
//          newEntriesList.map({ case (entryName, value) =>
//            vcurious(placeholderedFullNameT.steps.size + 1 == entryName.steps.size)
//            val last = entryName.last
//            last -> value
//          })
//        newEntries
//      })
//
//    val interfaceInnerEnv =
//      CitizenEnvironment(
//        interfaceRunesEnv.globalEnv,
//        interfaceRunesEnv,
//        templateFullNameT,
//        placeholderedFullNameT,
//        TemplatasStore(placeholderedFullNameT, Map(), Map())
//          .addEntries(interner, envEntriesFromMacros)
//          .addEntries(
//            interner,
//            interfaceA.genericParameters.zip(interfaceRunesEnv.fullName.last.templateArgs)
//              .map({ case (genericParam, templata) => (interner.intern(RuneNameT(genericParam.rune.rune)), TemplataEnvEntry(templata)) }))
//          .addEntries(
//            interner,
//            Vector(interner.intern(SelfNameT()) -> TemplataEnvEntry(KindTemplata(placeholderedInterfaceTT)))))

    val internalMethods =
      outerEnv.templatas.entriesByNameT.collect({
        case (name, FunctionEnvEntry(functionA)) => {
          val header =
            delegate.evaluateGenericFunctionFromNonCallForHeader(
              coutputs, parentRanges, FunctionTemplata(outerEnv, functionA), true)
          header.toPrototype -> vassertSome(header.getVirtualIndex)
        }
      }).toVector

    val runeToFunctionBound = TemplataCompiler.assembleRuneToFunctionBound(interfaceRunesEnv.templatas)
    val runeToImplBound = TemplataCompiler.assembleRuneToImplBound(interfaceRunesEnv.templatas)

    val interfaceDef2 =
      InterfaceDefinitionT(
        templateFullNameT,
        placeholderedInterfaceTT,
        interner.intern(placeholderedInterfaceTT),
        translateCitizenAttributes(attributesWithoutExportOrMacros),
        interfaceA.weakable,
        mutability,
        runeToFunctionBound,
        runeToImplBound,
        internalMethods)
    coutputs.addInterface(interfaceDef2)

    maybeExport match {
      case None =>
      case Some(exportPackageCoord) => {
        val exportedName =
          placeholderedFullNameT.localName match {
            case InterfaceNameT(InterfaceTemplateNameT(humanName), _) => humanName
            case _ => vfail("Can't export something that doesn't have a human readable name!")
          }
        coutputs.addKindExport(
          interfaceA.range,
          placeholderedInterfaceTT,
          exportPackageCoord.packageCoordinate,
          exportedName)
      }
    }

    (interfaceDef2)
  }

  private def makeStructMembers(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    members: Vector[IStructMemberS]):
  Vector[IStructMemberT] = {
    members.map(makeStructMember(env, coutputs, _))
  }

  private def makeStructMember(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    member: IStructMemberS):
  IStructMemberT = {
    val typeTemplata =
      vassertOne(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(member.typeRune.rune)), Set(TemplataLookupContext)))
    val variabilityT = Conversions.evaluateVariability(member.variability)
    member match {
      case NormalStructMemberS(_, name, _, _) => {
        val CoordTemplata(coord) = typeTemplata
        NormalStructMemberT(
          interner.intern(CodeVarNameT(name)),
          variabilityT,
          ReferenceMemberTypeT(coord))
      }
      case VariadicStructMemberS(_, variability, coordListRune) => {
        val placeholderTemplata =
          env.lookupNearestWithName(interner.intern(RuneNameT(coordListRune.rune)), Set(TemplataLookupContext)) match {
            case Some(PlaceholderTemplata(fullNameT, PackTemplataType(CoordTemplataType()))) => {
              PlaceholderTemplata(fullNameT, PackTemplataType(CoordTemplataType()))
            }
            case _ => vwat()
          }
        VariadicStructMemberT(
          interner.intern(CodeVarNameT(keywords.emptyString)),
          placeholderTemplata)
      }
    }
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: NodeEnvironment,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    name: IFunctionDeclarationNameS,
    functionA: FunctionA,
    members: Vector[NormalStructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
    val isMutable =
      members.exists({ case NormalStructMemberT(name, variability, tyype) =>
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

    val understructTemplateNameT =
      interner.intern(LambdaCitizenTemplateNameT(nameTranslator.translateCodeLocation(functionA.range.begin)))
    val understructTemplatedFullNameT =
      containingFunctionEnv.fullName
        .addStep(understructTemplateNameT)

    val understructInstantiatedNameT =
      understructTemplateNameT.makeStructName(interner, Vector())
    val understructInstantiatedFullNameT =
      containingFunctionEnv.fullName.addStep(understructInstantiatedNameT)

    // Lambdas have no bounds, so we just supply Map()
    coutputs.addInstantiationBounds(understructInstantiatedFullNameT, InstantiationBoundArguments(Map(), Map()))
    val understructStructTT = interner.intern(StructTT(understructInstantiatedFullNameT))

    val dropFuncNameT =
      interner.intern(FunctionTemplateNameT(keywords.drop, functionA.range.begin))

    // We declare the function into the environment that we use to compile the
    // struct, so that those who use the struct can reach into its environment
    // and see the function and use it.
    // See CSFMSEO and SAFHE.
    val structOuterEnv =
      CitizenEnvironment(
        containingFunctionEnv.globalEnv,
        containingFunctionEnv,
        understructTemplatedFullNameT,
        understructTemplatedFullNameT,
        TemplatasStore(understructTemplatedFullNameT, Map(), Map())
          .addEntries(
            interner,
            Vector(
              interner.intern(FunctionTemplateNameT(keywords.underscoresCall, functionA.range.begin)) ->
                env.FunctionEnvEntry(functionA),
              dropFuncNameT ->
                FunctionEnvEntry(
                  containingFunctionEnv.globalEnv.structDropMacro.makeImplicitDropFunction(
                    interner.intern(FunctionNameS(keywords.drop, functionA.range.begin)), functionA.range)),
              understructInstantiatedNameT -> TemplataEnvEntry(KindTemplata(understructStructTT)),
              interner.intern(SelfNameT()) -> TemplataEnvEntry(KindTemplata(understructStructTT)))))

    val structInnerEnv =
      CitizenEnvironment(
        structOuterEnv.globalEnv,
        structOuterEnv,
        understructTemplatedFullNameT,
        understructInstantiatedFullNameT,
        TemplatasStore(understructInstantiatedFullNameT, Map(), Map())
          // There are no inferences we'd need to add, because it's a lambda and they don't have
          // any rules or anything.
          .addEntries(interner, Vector()))

    // We return this from the function in case we want to eagerly compile it (which we do
    // if it's not a template).
    val functionTemplata = FunctionTemplata(structInnerEnv, functionA)

    coutputs.declareType(understructTemplatedFullNameT)
    coutputs.declareTypeOuterEnv(understructTemplatedFullNameT, structOuterEnv)
    coutputs.declareTypeInnerEnv(understructTemplatedFullNameT, structInnerEnv)
    coutputs.declareTypeMutability(understructTemplatedFullNameT, MutabilityTemplata(mutability))

    val closureStructDefinition =
      StructDefinitionT(
        understructTemplatedFullNameT,
        understructStructTT,
        Vector.empty,
        false,
        MutabilityTemplata(mutability),
        members,
        true,
        // Closures have no function bounds or impl bounds
        Map(),
        Map());
    coutputs.addStruct(closureStructDefinition)

    val closuredVarsStructRef = understructStructTT;

//    if (mutability == ImmutableT) {
      // Adds the free function to the coutputs
      // Free is indeed ordinary because it just takes in the lambda struct. The lambda struct
      // isn't templated. The lambda call function might be, but the struct isnt.

    // Always evaluate a drop, drops only capture borrows so there should always be a drop defined
    // on all members.
      delegate.evaluateGenericFunctionFromNonCallForHeader(
        coutputs,
        parentRanges,
        structInnerEnv.lookupNearestWithName(dropFuncNameT, Set(ExpressionLookupContext)) match {
          case Some(ft@FunctionTemplata(_, _)) => ft
          case _ => throw CompileErrorExceptionT(RangedInternalErrorT(functionA.range :: parentRanges, "Couldn't find closure drop function we just added!"))
        },
        true)
//    }

    (closuredVarsStructRef, mutability, functionTemplata)
  }
}
