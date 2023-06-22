package dev.vale.typing.macros

import dev.vale.highertyping.{FunctionA, StructA}
import dev.vale.postparsing.patterns.{AtomSP, CaptureS}
import dev.vale.postparsing.rules.{CallSR, CoerceToCoordSR, IRulexSR, LookupSR, RuneUsage}
import dev.vale.postparsing._
import dev.vale.typing.{ArrayCompiler, CompileErrorExceptionT, CompilerOutputs, CouldntFindFunctionToCallT, InheritBoundsFromTypeItself, OverloadResolver, TemplataCompiler, TypingPassOptions, UseBoundsFromContainer, ast}
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, ConstructTE, FunctionDefinitionT, FunctionHeaderT, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.{FunctionEnvEntry, FunctionEnvironment}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, FunctionNameT, ICitizenNameT, ICitizenTemplateNameT, IFunctionNameT, IFunctionTemplateNameT, INameT, ITemplateNameT, IdT, NameTranslator, PlaceholderNameT}
import dev.vale.{Err, Interner, Keywords, Ok, PackageCoordinate, Profiler, RangeS, StrI, vassert, vassertSome, vcurious, vimpl}
import dev.vale.typing.types._
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.ConstructorNameS
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.ast._
import dev.vale.typing.env.PackageEnvironment
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.function.FunctionCompiler.EvaluateFunctionSuccess
import dev.vale.typing.function.{DestructorCompiler, FunctionCompilerCore}
import dev.vale.typing.infer.CouldntFindFunction
import dev.vale.typing.templata.ITemplata.expectMutability
import dev.vale.typing.templata.{CoordTemplata, ITemplata, KindTemplata, MutabilityTemplata, PlaceholderTemplata}
import dev.vale.typing.types.InterfaceTT

import scala.collection.mutable

class StructConstructorMacro(
  opts: TypingPassOptions,
  interner: Interner,
  keywords: Keywords,
  nameTranslator: NameTranslator,
  destructorCompiler: DestructorCompiler,
) extends IOnStructDefinedMacro with IFunctionBodyMacro {

  val generatorId: StrI = keywords.structConstructorGenerator

  val macroName: StrI = keywords.DeriveStructConstructor

  override def getStructSiblingEntries(structName: IdT[INameT], structA: StructA):
  Vector[(IdT[INameT], FunctionEnvEntry)] = {
    if (structA.members.collect({ case VariadicStructMemberS(_, _, _) => }).nonEmpty) {
      // Dont generate constructors for variadic structs, not supported yet.
      // Only one we have right now is tuple, which has its own special syntax for constructing.
      return Vector()
    }
    val runeToType = mutable.HashMap[IRuneS, ITemplataType]()
    runeToType ++= structA.headerRuneToType
    runeToType ++= structA.membersRuneToType

    val rules = mutable.ArrayBuffer[IRulexSR]()
    rules ++= structA.headerRules
    rules ++= structA.memberRules

    val retRune = RuneUsage(structA.name.range, ReturnRuneS())
    runeToType += (retRune.rune -> CoordTemplataType())
    val structNameRange = structA.name.range
//    if (structA.isTemplate) {
      val structGenericRune = StructNameRuneS(structA.name)
      runeToType += (structGenericRune -> structA.tyype)
      rules += LookupSR(structNameRange, RuneUsage(structNameRange, structGenericRune), structA.name.getImpreciseName(interner))

      val structKindRune = RuneUsage(structNameRange, ImplicitCoercionKindRuneS(structNameRange, structGenericRune))
      runeToType += (structKindRune.rune -> KindTemplataType())
      rules += CallSR(structNameRange, structKindRune, RuneUsage(structNameRange, structGenericRune), structA.genericParameters.map(_.rune).toVector)

      rules += CoerceToCoordSR(structNameRange, retRune, structKindRune)
//    } else {
//      vcurious()
//      rules += LookupSR(structNameRange, retRune, structA.name.getImpreciseName(interner))
//    }

    val params =
      structA.members.zipWithIndex.flatMap({
        case (NormalStructMemberS(range, name, variability, typeRune), index) => {
          val capture = CaptureS(interner.intern(CodeVarNameS(name)), false)
          Vector(ParameterS(range, None, false, AtomSP(range, Some(capture), Some(typeRune), None)))
        }
        case (VariadicStructMemberS(range, variability, typeRune), index) => {
          Vector()
        }
      })

    val functionA =
      FunctionA(
        structA.range,
        interner.intern(ConstructorNameS(structA.name)),
        Vector(),
        TemplateTemplataType(structA.tyype.paramTypes, FunctionTemplataType()),
        structA.genericParameters,
        runeToType.toMap,
        params,
        Some(retRune),
        rules.toVector,
        GeneratedBodyS(generatorId))

    Vector(
      structName.copy(localName = nameTranslator.translateNameStep(functionA.name)) ->
        FunctionEnvEntry(functionA))
  }


  override def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironment,
    callRange: List[RangeS],
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT, ReferenceExpressionTE) = {
    val Some(CoordT(_, structTT @ StructTT(_))) = maybeRetCoord
    val definition = coutputs.lookupStruct(structTT.fullName)
    val placeholderSubstituter =
      TemplataCompiler.getPlaceholderSubstituter(
        interner,
        keywords,
        structTT.fullName,
        // We only know about this struct from the return type, we don't get to inherit any of its
        // bounds or guarantees from. Satisfy them from our environment instead.
        UseBoundsFromContainer(
          definition.runeToFunctionBound,
          definition.runeToImplBound,
          vassertSome(coutputs.getInstantiationBounds(structTT.fullName))))
    val members =
      definition.members.map({
        case NormalStructMemberT(name, _, ReferenceMemberTypeT(tyype)) => {
          (name, placeholderSubstituter.substituteForCoord(coutputs, tyype))
        }
        case NormalStructMemberT(name, variability, AddressMemberTypeT(tyype)) => vcurious()
        case VariadicStructMemberT(name, tyype) => vimpl()
      })

    val constructorFullName = env.fullName
    vassert(constructorFullName.localName.parameters.size == members.size)
    val constructorParams =
      members.map({ case (name, coord) => ParameterT(name, None, coord) })
    val mutability =
      StructCompiler.getMutability(
        interner, keywords, coutputs, structTT,
        // Not entirely sure if this is right, but it's consistent with using it for the return kind
        // and its the more conservative option so we'll go with it for now.
        UseBoundsFromContainer(
          definition.runeToFunctionBound,
          definition.runeToImplBound,
          vassertSome(coutputs.getInstantiationBounds(structTT.fullName))))
    val constructorReturnOwnership =
      mutability match {
        case MutabilityTemplata(MutableT) => OwnT
        case MutabilityTemplata(ImmutableT) => ShareT
        case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => OwnT
      }
    val constructorReturnType = CoordT(constructorReturnOwnership, structTT)

    // not virtual because how could a constructor be virtual
    val header =
      ast.FunctionHeaderT(
        constructorFullName,
        Vector.empty,
        constructorParams,
        constructorReturnType,
        Some(env.templata))

    val body =
      BlockTE(
        ReturnTE(
          ConstructTE(
            structTT,
            constructorReturnType,
            constructorParams.zipWithIndex.map({ case (p, index) => ArgLookupTE(index, p.tyype) }))))
    (header, body)
  }
}
