package dev.vale.typing.macros.citizen

import dev.vale.highertyping._
import dev.vale.postparsing.patterns.{AbstractSP, AtomSP, CaptureS}
import dev.vale.postparsing.rules.{CallSR, CoerceToCoordSR, CoordComponentsSR, EqualsSR, IRulexSR, LookupSR, RuneUsage}
import dev.vale.{Accumulator, Interner, Keywords, RangeS, StrI, vimpl, vwat}
import dev.vale.postparsing._
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, DestroyTE, DiscardTE, FunctionHeaderT, FunctionDefinitionT, LocationInFunctionEnvironment, ParameterT, ReturnTE, UnletTE, VoidLiteralTE}
import dev.vale.typing.env.{FunctionEnvEntry, FunctionEnvironment, FunctionEnvironmentBox, ReferenceLocalVariableT}
import dev.vale.typing.{Compiler, CompilerOutputs, OverloadResolver, TemplataCompiler, InheritBoundsFromTypeItself, ast, env}
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.macros.{IFunctionBodyMacro, IOnStructDefinedMacro}
import dev.vale.typing.names.{IdT, INameT, NameTranslator}
import dev.vale.typing.types._
import dev.vale.typing.ast._
import dev.vale.typing.macros.IOnStructDefinedMacro
import dev.vale.typing.names.INameT
import dev.vale.typing.types._
import dev.vale.typing.templata.{ITemplata, MutabilityTemplata, PlaceholderTemplata}

import scala.collection.mutable

class StructDropMacro(
  interner: Interner,
  keywords: Keywords,
  nameTranslator: NameTranslator,
  destructorCompiler: DestructorCompiler
) extends IOnStructDefinedMacro with IFunctionBodyMacro {

  val macroName: StrI = keywords.DeriveStructDrop

  val dropGeneratorId: StrI = keywords.dropGenerator

  override def getStructSiblingEntries(
    structName: IdT[INameT], structA: StructA):
  Vector[(IdT[INameT], FunctionEnvEntry)] = {
    def range(n: Int) = RangeS.internal(interner, n)
    def use(n: Int, rune: IRuneS) = RuneUsage(range(n), rune)

    val rules = new Accumulator[IRulexSR]()
    // Use the same rules as the original struct, see MDSFONARFO.
    structA.headerRules.foreach(r => rules.add(r))
    val runeToType = mutable.HashMap[IRuneS, ITemplataType]()
    // Use the same runes as the original struct, see MDSFONARFO.
    structA.headerRuneToType.foreach(runeToType += _)

    val vooid = MacroVoidRuneS()
    runeToType.put(vooid, CoordTemplataType())
    rules.add(LookupSR(range(-1672147),use(-64002, vooid),interner.intern(CodeNameS(keywords.void))))

    val structNameRune = StructNameRuneS(structA.name)
    runeToType += (structNameRune -> structA.tyype)

    val self = MacroSelfRuneS()
    runeToType += (self -> CoordTemplataType())
    rules.add(
      LookupSR(
        structA.name.range,
        RuneUsage(structA.name.range, structNameRune),
        structA.name.getImpreciseName(interner)))
    rules.add(
      CallSR(
        structA.name.range,
        use(-64002, self),
        RuneUsage(structA.name.range, structNameRune),
        structA.genericParameters.map(_.rune).toVector))

    // Use the same generic parameters as the struct
    val functionGenericParameters = structA.genericParameters

    val functionTemplataType =
      TemplateTemplataType(
        functionGenericParameters.map(_.rune.rune).map(runeToType),
        FunctionTemplataType())

    val nameS = interner.intern(FunctionNameS(keywords.drop, structA.range.begin))
    val dropFunctionA =
      FunctionA(
        structA.range,
        nameS,
        Vector(),
        functionTemplataType,
        functionGenericParameters,
        runeToType.toMap,
        Vector(
          ParameterS(
            AtomSP(
              range(-1340),
              Some(CaptureS(interner.intern(CodeVarNameS(keywords.thiss)))),
              None,
              Some(use(-64002, self)), None))),
        Some(use(-64002, vooid)),
        rules.buildArray().toVector,
        GeneratedBodyS(dropGeneratorId))

    val dropNameT = structName.addStep(nameTranslator.translateGenericFunctionName(dropFunctionA.name))
    Vector((dropNameT, FunctionEnvEntry(dropFunctionA)))
  }

  override def getStructChildEntries(
    macroName: StrI, structName: IdT[INameT], structA: StructA, mutability: ITemplata[MutabilityTemplataType]):
  Vector[(IdT[INameT], FunctionEnvEntry)] = {
    Vector()
  }

  // Implicit drop is one made for closures, arrays, or anything else that's not explicitly
  // defined by the user.
  def makeImplicitDropFunction(
    dropOrFreeFunctionNameS: IFunctionDeclarationNameS,
    structRange: RangeS):
  FunctionA = {
    FunctionA(
      structRange,
      dropOrFreeFunctionNameS,
      Vector(),
      FunctionTemplataType(),
      Vector(),
      Map(
        CodeRuneS(keywords.DropP1) -> CoordTemplataType(),
        CodeRuneS(keywords.DropV) -> CoordTemplataType()),
      Vector(
        ParameterS(AtomSP(RangeS.internal(interner, -1342), Some(CaptureS(interner.intern(CodeVarNameS(keywords.x)))), None, Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.DropP1))), None))),
      Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.DropV))),
      Vector(
        LookupSR(
          RangeS.internal(interner, -1672161),
          RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.DropP1)),
          interner.intern(SelfNameS())),
        LookupSR(RangeS.internal(interner, -1672162), RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.DropV)), interner.intern(CodeNameS(keywords.void)))),
      GeneratedBodyS(dropGeneratorId))
  }

  override def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironment,
    callRange: List[RangeS],
    originFunction1: Option[FunctionA],
    params2: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT, ReferenceExpressionTE) = {
    val bodyEnv = FunctionEnvironmentBox(env)

    val structTT =
      params2.head.tyype.kind match {
        case structTT @ StructTT(_) => structTT
        case other => vwat(other)
      }
    val structDef = coutputs.lookupStruct(structTT)
    val structOwnership =
      structDef.mutability match {
        case MutabilityTemplata(MutableT) => OwnT
        case MutabilityTemplata(ImmutableT) => ShareT
        case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => OwnT
      }
    val structType = CoordT(structOwnership, structTT)

    val ret = CoordT(ShareT, VoidT())
    val header = ast.FunctionHeaderT(env.fullName, Vector.empty, params2, ret, Some(env.templata))

    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val body =
      BlockTE(
        Compiler.consecutive(
          Vector(
            structDef.mutability match {
              case MutabilityTemplata(ImmutableT) => DiscardTE(ArgLookupTE(0, structType))
              case MutabilityTemplata(MutableT) | PlaceholderTemplata(_, _) => {
                val memberLocalVariables =
                  structDef.members.flatMap({
                    case NormalStructMemberT(name, _, ReferenceMemberTypeT(unsubstitutedReference)) => {
                      val substituter =
                        TemplataCompiler.getPlaceholderSubstituter(
                          interner, keywords, structTT.fullName,
                          // We received an instance of this type, so we can use the bounds from it.
                          InheritBoundsFromTypeItself)
                      val reference = substituter.substituteForCoord(coutputs, unsubstitutedReference)
                      Vector(ReferenceLocalVariableT(env.fullName.addStep(name), FinalT, reference))
                    }
                    case NormalStructMemberT(_, _, AddressMemberTypeT(_)) => {
                      // See Destructure2 and its handling of addressible members for why
                      // we don't include these in the destination variables.
                      Vector.empty
                    }
                    case VariadicStructMemberT(name, tyype) => vimpl()
                  })

                Compiler.consecutive(
                  Vector(DestroyTE(ArgLookupTE(0, structType), structTT, memberLocalVariables)) ++
                    memberLocalVariables.map(v => {
                      destructorCompiler.drop(
                        bodyEnv,
                        coutputs,
                        originFunction1.map(_.range).toList ++ callRange,
                        UnletTE(v))
                    }))
              }
            },
            ReturnTE(VoidLiteralTE()))))
    (header, body)
  }
}
