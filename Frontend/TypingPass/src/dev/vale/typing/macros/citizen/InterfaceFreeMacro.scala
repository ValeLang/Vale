//package dev.vale.typing.macros.citizen
//
//import dev.vale.highertyping.{FunctionA, InterfaceA}
//import dev.vale.postparsing.patterns.{AbstractSP, AtomSP, CaptureS}
//import dev.vale.postparsing.rules.{CallSR, IRulexSR, LookupSR, RuneUsage}
//import dev.vale.{Accumulator, Err, Interner, Keywords, RangeS, StrI}
//import dev.vale.postparsing._
//import dev.vale.typing.OverloadResolver
//import dev.vale.typing.env.FunctionEnvEntry
//import dev.vale.typing.expression.CallCompiler
//import dev.vale.typing.macros.IOnInterfaceDefinedMacro
//import dev.vale.typing.names.{FreeTemplateNameT, FullNameT, INameT}
//import dev.vale.typing.types._
//import dev.vale.highertyping.FunctionA
//import dev.vale.parsing.ast.ImmutableRuneAttributeP
//import dev.vale.postparsing._
//import dev.vale.postparsing.patterns.AbstractSP
//import dev.vale.typing.ast._
//import dev.vale.typing.env.IEnvironment
//import dev.vale.typing.macros.IOnInterfaceDefinedMacro
//import dev.vale.typing.names._
//import dev.vale.typing.types._
//import dev.vale.typing.env
//
//import scala.collection.mutable
//
//class InterfaceFreeMacro(interner: Interner, keywords: Keywords, nameTranslator: NameTranslator) extends IOnInterfaceDefinedMacro {
//
//  val generatorId: StrI = keywords.interfaceFreeGenerator
//
//  val macroName: StrI = keywords.DeriveInterfaceFree
//
//  override def getInterfaceSiblingEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA): Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
//    def range(n: Int) = RangeS.internal(interner, n)
//    def use(n: Int, rune: IRuneS) = RuneUsage(range(n), rune)
//
//    val interfaceRange = interfaceA.range
//
//    val rules = new Accumulator[IRulexSR]()
//    // Use the same rules as the original interface, see MDSFONARFO.
//    interfaceA.rules.foreach(r => rules.add(r))
//    val runeToType = mutable.HashMap[IRuneS, ITemplataType]()
//    // Use the same runes as the original interface, see MDSFONARFO.
//    interfaceA.runeToType.foreach(runeToType += _)
//
//    val vooid = MacroVoidRuneS()
//    runeToType.put(vooid, CoordTemplataType())
//    rules.add(LookupSR(range(-1672147),use(-64002, vooid),interner.intern(CodeNameS(keywords.void))))
//
//    val interfaceNameRune = StructNameRuneS(interfaceA.name)
//    runeToType += (interfaceNameRune -> interfaceA.tyype)
//
//
//    val self = MacroSelfRuneS()
//    runeToType += (self -> CoordTemplataType())
//    rules.add(
//      LookupSR(
//        interfaceA.name.range,
//        RuneUsage(interfaceA.name.range, interfaceNameRune),
//        interfaceA.name.getImpreciseName(interner)))
//    rules.add(
//      CallSR(
//        interfaceA.name.range,
//        use(-64002, self),
//        RuneUsage(interfaceA.name.range, interfaceNameRune),
//        interfaceA.genericParameters.map(_.rune).toVector))
//
//    val functionGenericParameters = interfaceA.genericParameters
//
//    val functionTemplataType =
//      TemplateTemplataType(
//        functionGenericParameters.map(_.rune.rune).map(runeToType),
//        FunctionTemplataType())
//
//    val nameS = interner.intern(FreeDeclarationNameS(interfaceRange.begin))
//    val freeFunctionA =
//      FunctionA(
//        interfaceRange,
//        nameS,
//        Vector(),
//        functionTemplataType,
//        functionGenericParameters,
//        runeToType.toMap,
//        Vector(
//          ParameterS(
//            AtomSP(
//              range(-1340),
//              Some(CaptureS(interner.intern(CodeVarNameS(keywords.thiss)))),
//              Some(AbstractSP(range(-454092), false)),
//              Some(use(-64002, self)), None))),
//        Some(use(-64002, vooid)),
//        rules.buildArray().toVector,
//        AbstractBodyS)
//
//    val freeNameT = interfaceName.addStep(nameTranslator.translateGenericFunctionName(freeFunctionA.name))
//    Vector((freeNameT, FunctionEnvEntry(freeFunctionA)))
//  }
//
////  override def getInterfaceChildEntries(
////    interfaceName: FullNameT[INameT],
////    interfaceA: InterfaceA,
////    mutability: MutabilityT):
////  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
////    Vector()
////  }
//
////  override def generateFunctionBody(
////    env: FunctionEnvironment,
////    coutputs: CompilerOutputs,
////    generatorId: StrI,
////    life: LocationInFunctionEnvironment,
////    callRange: List[RangeS],
////    originFunction1: Option[FunctionA],
////    params2: Vector[ParameterT],
////    maybeRetCoord: Option[CoordT]):
////  FunctionHeaderT = {
////    val Vector(paramCoord @ CoordT(ShareT, InterfaceTT(_))) = params2.map(_.tyype)
////
////    val ret = CoordT(ShareT, VoidT())
////    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, originFunction1)
////
////    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)
////
////    val virtualFreePrototype =
////      overloadCompiler.findFunction(
////        env, coutputs, callRange, interner.intern(VirtualFreeImpreciseNameS()), Vector(), Vector(),
////        Vector(ParamFilter(paramCoord, None)), Vector(), true) match {
////        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
////        case Ok(x) => x
////      }
////
////    val expr = FunctionCallTE(virtualFreePrototype, Vector(ArgLookupTE(0, paramCoord)))
////
////    val function2 = FunctionT(header, BlockTE(Compiler.consecutive(Vector(expr, ReturnTE(VoidLiteralTE())))))
////    coutputs.addFunction(function2)
////    function2.header}
//}
