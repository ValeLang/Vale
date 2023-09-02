package dev.vale.typing.macros

import dev.vale.highertyping.{FunctionA, StructA}
import dev.vale.postparsing.patterns.{AtomSP, CaptureS}
import dev.vale.postparsing.rules._
import dev.vale.postparsing._
import dev.vale.typing.{ArrayCompiler, CompileErrorExceptionT, CompilerOutputs, CouldntFindFunctionToCallT, InheritBoundsFromTypeItself, OverloadResolver, TemplataCompiler, TypingPassOptions, UseBoundsFromContainer, ast}
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, ConstructTE, FunctionDefinitionT, FunctionHeaderT, LocationInFunctionEnvironmentT, ParameterT, ReturnTE}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.{FunctionEnvEntry, FunctionEnvironmentT}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, FunctionNameT, ICitizenNameT, ICitizenTemplateNameT, IFunctionNameT, IFunctionTemplateNameT, INameT, ITemplateNameT, IdT, NameTranslator, KindPlaceholderNameT}
import dev.vale.{Err, Interner, Keywords, Ok, PackageCoordinate, Profiler, RangeS, StrI, vassert, vassertSome, vcurious, vimpl}
import dev.vale.typing.types._
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.ConstructorNameS
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.ast._
import dev.vale.typing.env.PackageEnvironmentT
import dev.vale.typing.expression.CallCompiler

import dev.vale.typing.function.{DestructorCompiler, FunctionCompilerCore}
import dev.vale.typing.infer.CouldntFindFunction
import dev.vale.typing.templata.ITemplataT.expectMutability
import dev.vale.typing.templata._
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
    val rules = mutable.ArrayBuffer[IRulexSR]()

    // We dont need these, they really just contain bounds and stuff, which we'd inherit from our parameters anyway.
    // However, if we leave it out, then this (from an IRAGP test):
    //   struct Bork<T, Y> where T = Y { t T; y Y; }
    // thing's constructor would be:
    //   func Bork<T, Y>(t T, y Y) Bork<T, Y> { ... }
    // and it fails to resolve that return type there because it doesn't meet the struct's conditions, because it didn't
    // repeat the rules from the struct's header, specifically the T = Y rule.
    // So, we just include all the rules from the constructor's header.
    // If we ever need to drop that functionality (the T = Y nonsense) then we can probably take out the inheriting of
    // the header rules.
    runeToType ++= structA.headerRuneToType
    rules ++= structA.headerRules

    // We include these because they become our parameters. If a struct contains a Opt<^MyNode<T>> we want those two
    // CallSRs in our function rules too.
    runeToType ++= structA.membersRuneToType
    rules ++= structA.memberRules

    val defaultRegionRune = structA.regionRune

    val retRune = RuneUsage(structA.name.range, ReturnRuneS())
    runeToType += (retRune.rune -> CoordTemplataType())
    val structNameRange = structA.name.range
    val structGenericRune = StructNameRuneS(structA.name)
    runeToType += (structGenericRune -> structA.tyype)
    rules += LookupSR(structNameRange, RuneUsage(structNameRange, structGenericRune), structA.name.getImpreciseName(interner))

      val structKindRune = RuneUsage(structNameRange, ImplicitCoercionKindRuneS(structNameRange, structGenericRune))
      runeToType += (structKindRune.rune -> KindTemplataType())
      rules += CallSR(structNameRange, structKindRune, RuneUsage(structNameRange, structGenericRune), structA.genericParameters.map(_.rune).toVector)

      rules += CoerceToCoordSR(structNameRange, retRune, RuneUsage(structNameRange, defaultRegionRune), structKindRune)

    val params =
      structA.members.zipWithIndex.flatMap({
        case (NormalStructMemberS(range, name, variability, typeRune), index) => {
          val capture = CaptureS(interner.intern(CodeVarNameS(name)), false)
          Vector(ParameterS(range, None, false, typeRune.rune, AtomSP(range, Some(capture), Some(typeRune), None)))
        }
        case (VariadicStructMemberS(range, variability, typeRune), index) => {
          Vector()
        }
      })
    runeToType ++= params.flatMap(_.pattern.coordRune.map(_.rune)).map(_ -> CoordTemplataType())

    val functionA =
      FunctionA(
        structA.range,
        interner.intern(ConstructorNameS(structA.name)),
        // Struct constructors cant be pure, that would require the temporary struct inside the
        // constructor to have members that could point to a different, immutable region.
        Vector(),
        TemplateTemplataType(structA.tyype.paramTypes, FunctionTemplataType()),
        structA.genericParameters,
        runeToType.toMap,
        params,
        Some(retRune),
        defaultRegionRune,
        rules.toVector,
        GeneratedBodyS(generatorId))

    Vector(
      structName.copy(localName = nameTranslator.translateNameStep(functionA.name)) ->
        FunctionEnvEntry(functionA))
  }


  override def generateFunctionBody(
    env: FunctionEnvironmentT,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironmentT,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT, ReferenceExpressionTE) = {
    val Some(CoordT(_, _, structTT @ StructTT(_))) = maybeRetCoord
    val definition = coutputs.lookupStruct(structTT.id)
    val placeholderSubstituter =
      TemplataCompiler.getPlaceholderSubstituter(
        opts.globalOptions.sanityCheck,
        interner,
        keywords,
        env.denizenTemplateId,
        structTT.id,
//        Vector((definition.defaultRegion, env.defaultRegion)),
        // We only know about this struct from the return type, we don't get to inherit any of its
        // bounds or guarantees from. Satisfy them from our environment instead.
        UseBoundsFromContainer(
          definition.instantiationBoundParams,
          vassertSome(coutputs.getInstantiationBounds(structTT.id))))
    val members =
      definition.members.map({
        case NormalStructMemberT(name, _, ReferenceMemberTypeT(tyype)) => {
          (name, placeholderSubstituter.substituteForCoord(coutputs, tyype))
        }
        case NormalStructMemberT(name, variability, AddressMemberTypeT(tyype)) => vcurious()
        case VariadicStructMemberT(name, tyype) => vimpl()
      })

    val constructorId = env.id
    vassert(constructorId.localName.parameters.size == members.size)
    val constructorParams =
      members.map({ case (name, coord) => ParameterT(name, None, false, coord) })
//    val mutability =
//      StructCompiler.getMutability(
//        opts.globalOptions.sanityCheck,
//        interner, keywords, coutputs, env.denizenTemplateId, RegionT(), structTT,
//        // Not entirely sure if this is right, but it's consistent with using it for the return kind
//        // and its the more conservative option so we'll go with it for now.
//        UseBoundsFromContainer(
//          definition.instantiationBoundParams,
//          vassertSome(coutputs.getInstantiationBounds(structTT.id))))
//    val constructorReturnOwnership =
//      mutability match {
//        case MutabilityTemplataT(MutableT) => OwnT
//        case MutabilityTemplataT(ImmutableT) => ShareT
//        case PlaceholderTemplataT(idT, MutabilityTemplataType()) => OwnT
//      }
    val constructorReturnType = vassertSome(maybeRetCoord)

    // not virtual because how could a constructor be virtual
    val header =
      ast.FunctionHeaderT(
        constructorId,
        Vector(PureT),
//        Vector(RegionT(env.defaultRegion.localName, true)),
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
