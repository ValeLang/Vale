//package dev.vale.typing.macros.citizen
//
//import dev.vale.highertyping.{FunctionA, StructA}
//import dev.vale.postparsing.patterns.{AtomSP, CaptureS}
//import dev.vale.postparsing.rules.{CallSR, CoerceToCoordSR, EqualsSR, IRulexSR, LookupSR, RuneUsage}
//import dev.vale.{Accumulator, Interner, Keywords, RangeS, StrI, vimpl, vwat}
//import dev.vale.postparsing._
//import dev.vale.typing.{Compiler, CompilerOutputs, OverloadResolver, TemplataCompiler, ast, env}
//import dev.vale.typing.ast.{ArgLookupTE, BlockTE, DestroyTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE, UnletTE, VoidLiteralTE}
//import dev.vale.typing.env.{FunctionEnvEntry, FunctionEnvironment, FunctionEnvironmentBox, ReferenceLocalVariableT}
//import dev.vale.typing.expression.CallCompiler
//import dev.vale.typing.function.DestructorCompiler
//import dev.vale.typing.macros.{IFunctionBodyMacro, IOnStructDefinedMacro}
//import dev.vale.typing.names.{FullNameT, INameT, NameTranslator}
//import dev.vale.typing.types._
//import dev.vale.highertyping.FunctionA
//import dev.vale.postparsing.patterns.AtomSP
//import dev.vale.typing.ast._
//import dev.vale.typing.macros.IOnStructDefinedMacro
//import dev.vale.typing.names.INameT
//import dev.vale.typing.types._
//import dev.vale.typing.templata.{ITemplata, MutabilityTemplata, PlaceholderTemplata}
//
//import scala.collection.mutable
//
//class StructFreeMacro(
//  interner: Interner,
//  keywords: Keywords,
//  nameTranslator: NameTranslator,
//  destructorCompiler: DestructorCompiler
//) extends IOnStructDefinedMacro with IFunctionBodyMacro {
//
//  val macroName: StrI = keywords.DeriveStructFree
//
//  val freeGeneratorId: StrI = keywords.freeGenerator
//
//  override def getStructChildEntries(
//    macroName: StrI, structName: FullNameT[INameT], structA: StructA, mutability: ITemplata[MutabilityTemplataType]):
//  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
//    Vector()
//  }
//
//  override def getStructSiblingEntries(structName: FullNameT[INameT], structA: StructA):
//  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
//    def range(n: Int) = RangeS.internal(interner, n)
//    def use(n: Int, rune: IRuneS) = RuneUsage(range(n), rune)
//
//    val structRange = structA.range
//
//    val rules = new Accumulator[IRulexSR]()
//    // Use the same rules as the original struct, see MDSFONARFO.
//    structA.headerRules.foreach(r => rules.add(r))
//    val runeToType = mutable.HashMap[IRuneS, ITemplataType]()
//    // Use the same runes as the original struct, see MDSFONARFO.
//    structA.headerRuneToType.foreach(runeToType += _)
//
//    val vooid = MacroVoidRuneS()
//    runeToType.put(vooid, CoordTemplataType())
//    rules.add(LookupSR(range(-1672147),use(-64002, vooid),interner.intern(CodeNameS(keywords.void))))
//
//    val structNameRune = StructNameRuneS(structA.name)
//    runeToType += (structNameRune -> structA.tyype)
//
//
//    val self = MacroSelfRuneS()
//    runeToType += (self -> CoordTemplataType())
//    rules.add(
//      LookupSR(
//        structA.name.range,
//        RuneUsage(structA.name.range, structNameRune),
//        structA.name.getImpreciseName(interner)))
//    rules.add(
//      CallSR(
//        structA.name.range,
//        use(-64002, self),
//        RuneUsage(structA.name.range, structNameRune),
//        structA.genericParameters.map(_.rune).toVector))
//
//    val functionGenericParameters = structA.genericParameters
//
//    val functionTemplataType =
//      TemplateTemplataType(
//        functionGenericParameters.map(_.rune.rune).map(runeToType),
//        FunctionTemplataType())
//
//    val nameS = interner.intern(FreeDeclarationNameS(structRange.begin))
//    val freeFunctionA =
//    FunctionA(
//      structRange,
//      nameS,
//      Vector(),
//      functionTemplataType,
//      functionGenericParameters,
//      runeToType.toMap,
//      Vector(
//        ParameterS(
//          AtomSP(
//            range(-1340),
//            Some(CaptureS(interner.intern(CodeVarNameS(keywords.thiss)))),
//            None,
//            Some(use(-64002, self)), None))),
//      Some(use(-64002, vooid)),
//      rules.buildArray().toVector,
//      GeneratedBodyS(freeGeneratorId))
//
//    val freeNameT = structName.addStep(nameTranslator.translateGenericFunctionName(freeFunctionA.name))
//    Vector((freeNameT, FunctionEnvEntry(freeFunctionA)))
//  }
//
//  // Implicit drop is one made for closures, arrays, or anything else that's not explicitly
//  // defined by the user.
//  def makeImplicitFreeFunction(
//    dropOrFreeFunctionNameS: IFunctionDeclarationNameS,
//    structRange: RangeS):
//  FunctionA = {
//    FunctionA(
//      structRange,
//      dropOrFreeFunctionNameS,
//      Vector(),
//      FunctionTemplataType(),
//      Vector(),
//      Map(
//        CodeRuneS(keywords.FreeP1) -> CoordTemplataType(),
//        CodeRuneS(keywords.FreeV) -> CoordTemplataType()),
//      Vector(
//        ParameterS(
//          AtomSP(
//            RangeS.internal(interner, -1342),
//            Some(CaptureS(interner.intern(CodeVarNameS(keywords.x)))),
//            None,
//            Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.FreeP1))), None))),
//      Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.FreeV))),
//      Vector(
//        LookupSR(
//          RangeS.internal(interner, -1672165),
//          RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.FreeP1)),
//          interner.intern(SelfNameS())),
//        LookupSR(RangeS.internal(interner, -1672166), RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.FreeV)), interner.intern(CodeNameS(keywords.void)))),
//      GeneratedBodyS(freeGeneratorId))
//  }
//
//  override def generateFunctionBody(
//    env: FunctionEnvironment,
//    coutputs: CompilerOutputs,
//    generatorId: StrI,
//    life: LocationInFunctionEnvironment,
//    callRange: List[RangeS],
//    originFunction1: Option[FunctionA],
//    params2: Vector[ParameterT],
//    maybeRetCoord: Option[CoordT]):
//  (FunctionHeaderT, ReferenceExpressionTE) = {
//    val bodyEnv = FunctionEnvironmentBox(env)
//
//    val structTT =
//      params2.head.tyype.kind match {
//        case structTT @ StructTT(_) => structTT
//        case other => vwat(other)
//      }
//    val structDef = coutputs.lookupStruct(structTT)
//    val structOwnership =
//      structDef.mutability match {
//        case MutabilityTemplata(MutableT) => OwnT
//        case MutabilityTemplata(ImmutableT) => ShareT
//        case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => vimpl()
//      }
//    val structType = CoordT(structOwnership, structTT)
//
//    val ret = CoordT(ShareT, VoidT())
//    val header = ast.FunctionHeaderT(env.fullName, Vector.empty, params2, ret, Some(env.templata))
//
//    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)
//
//    val substituter =
//      TemplataCompiler.getPlaceholderSubstituter(interner, keywords, structTT.fullName)
//
//    val memberLocalVariables =
//      structDef.members.flatMap({
//        case NormalStructMemberT(name, _, ReferenceMemberTypeT(reference)) => {
//          Vector(ReferenceLocalVariableT(env.fullName.addStep(name), FinalT, substituter.substituteForCoord(coutputs, reference)))
//        }
//        case NormalStructMemberT(_, _, AddressMemberTypeT(_)) => {
//          // See Destructure2 and its handling of addressible members for why
//          // we don't include these in the destination variables.
//          Vector.empty
//        }
//        case VariadicStructMemberT(name, tyype) => vimpl()
//      })
//    val expr =
//      structDef.mutability match {
//        case PlaceholderTemplata(fullNameT, tyype) => vimpl()
//        case MutabilityTemplata(ImmutableT) => {
//          Compiler.consecutive(
//            Vector(DestroyTE(ArgLookupTE(0, structType), structTT, memberLocalVariables)) ++
//              memberLocalVariables.map(v => {
//                destructorCompiler.drop(bodyEnv, coutputs, callRange, UnletTE(v))
//              }))
//        }
//        case MutabilityTemplata(MutableT) => {
//          // Right now we make a free for mutables, but only because we don't really know how to
//          // turn off the free macro for mutable structs.
//          // Just do nothing here.
//          VoidLiteralTE()
//        }
//      }
//
//    val body = BlockTE(Compiler.consecutive(Vector(expr, ReturnTE(VoidLiteralTE()))))
//
//    (header, body)
//  }
//}
