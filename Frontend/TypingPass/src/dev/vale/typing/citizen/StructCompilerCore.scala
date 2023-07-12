package dev.vale.typing.citizen

import dev.vale.highertyping.{FunctionA, InterfaceA, StructA}
import dev.vale._
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
import dev.vale.typing.env.{CitizenEnvironmentT, FunctionEnvEntry, IInDenizenEnvironmentT, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.typing.ast._

import scala.collection.immutable.List

class StructCompilerCore(
  opts: TypingPassOptions,
  interner: Interner,
  keywords: Keywords,
  nameTranslator: NameTranslator,
  delegate: IStructCompilerDelegate) {

  def compileStruct(
    outerEnv: IInDenizenEnvironmentT,
    structRunesEnv: CitizenEnvironmentT[IStructNameT, IStructTemplateNameT],
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    structA: StructA):
  Unit = {
    val templateArgs = structRunesEnv.id.localName.templateArgs
    val templateIdT = structRunesEnv.templateId
    val templateNameT = templateIdT.localName
    val placeholderedNameT = templateNameT.makeStructName(interner, templateArgs)
    val placeholderedIdT = templateIdT.copy(localName = placeholderedNameT)

    // Usually when we make a StructTT we put the instantiation bounds into the coutputs,
    // but this isn't really an instantiation, so we don't here.
    val placeholderedStructTT = interner.intern(StructTT(placeholderedIdT))

    val attributesWithoutExportOrMacros =
      structA.attributes.filter({
        case ExportS(_) => false
        case MacroCallS(range, dontCall, macroName) => false
        case _ => true
      })

    val mutability =
      structRunesEnv.lookupNearestWithImpreciseName(
        interner.intern(RuneNameS(structA.mutabilityRune.rune)),
        Set(TemplataLookupContext)).toList match {
        case List(m) => ITemplataT.expectMutability(m)
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
      CitizenEnvironmentT(
        structRunesEnv.globalEnv,
        structRunesEnv,
        templateIdT,
        placeholderedIdT,
        TemplatasStore(placeholderedIdT, Map(), Map()))

    val members = makeStructMembers(structInnerEnv, coutputs, structA.members)

    if (mutability == MutabilityTemplataT(ImmutableT)) {
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
            outerEnv.id.addStep(name),
            (coutputs) => {
              delegate.evaluateGenericFunctionFromNonCallForHeader(
                coutputs, parentRanges, callLocation, FunctionTemplataT(outerEnv, functionA), true)
            }))
      }
      case _ => vcurious()
    })

    val runeToFunctionBound = TemplataCompiler.assembleRuneToFunctionBound(structRunesEnv.templatas)
    val runeToImplBound = TemplataCompiler.assembleRuneToImplBound(structRunesEnv.templatas)

    val structDefT =
      StructDefinitionT(
        templateIdT,
        placeholderedStructTT,
        translateCitizenAttributes(attributesWithoutExportOrMacros),
        structA.weakable,
        mutability,
        members,
        false,
        runeToFunctionBound,
        runeToImplBound)

    coutputs.addStruct(structDefT);
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
    outerEnv: IInDenizenEnvironmentT,
    interfaceRunesEnv: CitizenEnvironmentT[IInterfaceNameT, IInterfaceTemplateNameT],
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    interfaceA: InterfaceA):
  (InterfaceDefinitionT) = {
    val templateArgs = interfaceRunesEnv.id.localName.templateArgs
    val templateIdT = interfaceRunesEnv.templateId
    val templateNameT = templateIdT.localName
    val placeholderedNameT = templateNameT.makeInterfaceName(interner, templateArgs)
    val placeholderedIdT = templateIdT.copy(localName = placeholderedNameT)

    // Usually when we make a StructTT we put the instantiation bounds into the coutputs,
    // but this isn't really an instantiation, so we don't here.
    val placeholderedInterfaceTT = interner.intern(InterfaceTT(placeholderedIdT))

    val attributesWithoutExportOrMacros =
      interfaceA.attributes.filter({
        case ExportS(_) => false
        case MacroCallS(range, dontCall, macroName) => false
        case _ => true
      })
    val maybeExport =
      interfaceA.attributes.collectFirst { case e@ExportS(_) => e }


    val mutability =
      ITemplataT.expectMutability(
        vassertSome(
          interfaceRunesEnv.lookupNearestWithImpreciseName(
            interner.intern(RuneNameS(interfaceA.mutabilityRune.rune)),
            Set(TemplataLookupContext))))

    val internalMethods =
      outerEnv.templatas.entriesByNameT.collect({
        case (name, FunctionEnvEntry(functionA)) => {
          val header =
            delegate.evaluateGenericFunctionFromNonCallForHeader(
              coutputs, parentRanges, callLocation, FunctionTemplataT(outerEnv, functionA), true)
          header.toPrototype -> vassertSome(header.getVirtualIndex)
        }
      }).toVector

    val runeToFunctionBound = TemplataCompiler.assembleRuneToFunctionBound(interfaceRunesEnv.templatas)
    val runeToImplBound = TemplataCompiler.assembleRuneToImplBound(interfaceRunesEnv.templatas)

    val interfaceDef2 =
      InterfaceDefinitionT(
        templateIdT,
        placeholderedInterfaceTT,
        interner.intern(placeholderedInterfaceTT),
        translateCitizenAttributes(attributesWithoutExportOrMacros),
        interfaceA.weakable,
        mutability,
        runeToFunctionBound,
        runeToImplBound,
        internalMethods)
    coutputs.addInterface(interfaceDef2)

    (interfaceDef2)
  }

  private def makeStructMembers(
    env: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    members: Vector[IStructMemberS]):
  Vector[IStructMemberT] = {
    members.map(makeStructMember(env, coutputs, _))
  }

  private def makeStructMember(
    env: IInDenizenEnvironmentT,
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
        val CoordTemplataT(coord) = typeTemplata
        NormalStructMemberT(
          interner.intern(CodeVarNameT(name)),
          variabilityT,
          ReferenceMemberTypeT(coord))
      }
      case VariadicStructMemberS(_, variability, coordListRune) => {
        val placeholderTemplata =
          env.lookupNearestWithName(interner.intern(RuneNameT(coordListRune.rune)), Set(TemplataLookupContext)) match {
            case Some(PlaceholderTemplataT(idT, PackTemplataType(CoordTemplataType()))) => {
              PlaceholderTemplataT(idT, PackTemplataType(CoordTemplataType()))
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
    containingFunctionEnv: NodeEnvironmentT,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    name: IFunctionDeclarationNameS,
    functionA: FunctionA,
    members: Vector[NormalStructMemberT]):
  (StructTT, MutabilityT, FunctionTemplataT) = {
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
    val understructTemplatedId =
      containingFunctionEnv.id
        .addStep(understructTemplateNameT)

    val understructInstantiatedNameT =
      understructTemplateNameT.makeStructName(interner, Vector())
    val understructInstantiatedId =
      containingFunctionEnv.id.addStep(understructInstantiatedNameT)

    // Lambdas have no bounds, so we just supply Map()
    coutputs.addInstantiationBounds(understructInstantiatedId, InstantiationBoundArgumentsT(Map(), Map()))
    val understructStructTT = interner.intern(StructTT(understructInstantiatedId))

    val dropFuncNameT =
      interner.intern(FunctionTemplateNameT(keywords.drop, functionA.range.begin))

    // We declare the function into the environment that we use to compile the
    // struct, so that those who use the struct can reach into its environment
    // and see the function and use it.
    // See CSFMSEO and SAFHE.
    val structOuterEnv =
      CitizenEnvironmentT(
        containingFunctionEnv.globalEnv,
        containingFunctionEnv,
        understructTemplatedId,
        understructTemplatedId,
        TemplatasStore(understructTemplatedId, Map(), Map())
          .addEntries(
            interner,
            Vector(
              interner.intern(FunctionTemplateNameT(keywords.underscoresCall, functionA.range.begin)) ->
                env.FunctionEnvEntry(functionA),
              dropFuncNameT ->
                FunctionEnvEntry(
                  containingFunctionEnv.globalEnv.structDropMacro.makeImplicitDropFunction(
                    interner.intern(FunctionNameS(keywords.drop, functionA.range.begin)), functionA.range)),
              understructInstantiatedNameT -> TemplataEnvEntry(KindTemplataT(understructStructTT)),
              interner.intern(SelfNameT()) -> TemplataEnvEntry(KindTemplataT(understructStructTT)))))

    val structInnerEnv =
      CitizenEnvironmentT(
        structOuterEnv.globalEnv,
        structOuterEnv,
        understructTemplatedId,
        understructInstantiatedId,
        TemplatasStore(understructInstantiatedId, Map(), Map())
          // There are no inferences we'd need to add, because it's a lambda and they don't have
          // any rules or anything.
          .addEntries(interner, Vector()))

    // We return this from the function in case we want to eagerly compile it (which we do
    // if it's not a template).
    val functionTemplata = FunctionTemplataT(structInnerEnv, functionA)

    coutputs.declareType(understructTemplatedId)
    coutputs.declareTypeOuterEnv(understructTemplatedId, structOuterEnv)
    coutputs.declareTypeInnerEnv(understructTemplatedId, structInnerEnv)
    coutputs.declareTypeMutability(understructTemplatedId, MutabilityTemplataT(mutability))

    val closureStructDefinition =
      StructDefinitionT(
        understructTemplatedId,
        understructStructTT,
        Vector.empty,
        false,
        MutabilityTemplataT(mutability),
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
        callLocation,
        structInnerEnv.lookupNearestWithName(dropFuncNameT, Set(ExpressionLookupContext)) match {
          case Some(ft@FunctionTemplataT(_, _)) => ft
          case _ => throw CompileErrorExceptionT(RangedInternalErrorT(functionA.range :: parentRanges, "Couldn't find closure drop function we just added!"))
        },
        true)
//    }

    (closuredVarsStructRef, mutability, functionTemplata)
  }
}
