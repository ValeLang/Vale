package dev.vale.typing

import dev.vale.{Err, Interner, Keywords, Ok, PackageCoordinate, PackageCoordinateMap, Profiler, RangeS, Result, vassert, vassertOne, vcurious, vfail, vimpl, vwat, _}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.UseP
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing.{BlockSE, CodeNameS, ExportS, ExternS, FunctionNameS, IFunctionDeclarationNameS, IImpreciseNameS, IRuneS, ITemplataType, SealedS}
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.citizen.{AncestorHelper, IAncestorHelperDelegate, IStructCompilerDelegate, StructCompiler}
import dev.vale.typing.expression.{ExpressionCompiler, IExpressionCompilerDelegate}
import dev.vale.typing.function.{DestructorCompiler, FunctionCompiler, FunctionCompilerCore, IFunctionCompilerDelegate, VirtualCompiler}
import dev.vale.typing.infer.IInfererDelegate
import dev.vale.typing.types.{BoolT, CitizenRefT, CoordT, FloatT, ImmutableT, IntT, InterfaceTT, KindT, MutabilityT, NeverT, OverloadSetT, ParamFilter, RuntimeSizedArrayTT, ShareT, StaticSizedArrayTT, StrT, StructMemberT, StructTT, VariabilityT, VoidT}
import dev.vale.highertyping._
import dev.vale.postparsing.ICompileErrorS
import OverloadResolver.FindFunctionFailure
import dev.vale
import dev.vale.highertyping.{ExportAsA, FunctionA, InterfaceA, ProgramA, StructA}
import dev.vale.typing.ast.{ConsecutorTE, EdgeT, FunctionHeaderT, LocationInFunctionEnvironment, ParameterT, PrototypeT, ReferenceExpressionTE, VoidLiteralTE}
import dev.vale.typing.env.{FunctionEnvEntry, FunctionEnvironment, GlobalEnvironment, IEnvEntry, IEnvironment, ImplEnvEntry, InterfaceEnvEntry, NodeEnvironment, NodeEnvironmentBox, PackageEnvironment, StructEnvEntry, TemplataEnvEntry, TemplatasStore}
import dev.vale.typing.macros.{AbstractBodyMacro, AnonymousInterfaceMacro, AsSubtypeMacro, FunctorHelper, LockWeakMacro, SameInstanceMacro, StructConstructorMacro}
import dev.vale.typing.macros.citizen.{ImplDropMacro, ImplFreeMacro, InterfaceDropMacro, InterfaceFreeMacro, StructDropMacro, StructFreeMacro}
import dev.vale.typing.macros.rsa.{RSADropIntoMacro, RSAFreeMacro, RSAImmutableNewMacro, RSALenMacro, RSAMutableCapacityMacro, RSAMutableNewMacro, RSAMutablePopMacro, RSAMutablePushMacro}
import dev.vale.typing.macros.ssa.{SSADropIntoMacro, SSAFreeMacro, SSALenMacro}
import dev.vale.typing.names.{FullNameT, INameT, NameTranslator, PackageTopLevelNameT, PrimitiveNameT}
import dev.vale.typing.templata.{FunctionTemplata, ITemplata, InterfaceTemplata, KindTemplata, PrototypeTemplata, RuntimeSizedArrayTemplateTemplata, StaticSizedArrayTemplateTemplata, StructTemplata}
import dev.vale.typing.ast._
import dev.vale.typing.citizen.AncestorHelper
import dev.vale.typing.env._
import dev.vale.typing.expression.LocalHelper
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.macros.citizen.StructDropMacro
import dev.vale.typing.macros.rsa.RSALenMacro
import dev.vale.typing.macros.ssa.SSALenMacro
import dev.vale.typing.macros.SameInstanceMacro
import dev.vale.typing.names.CitizenTemplateNameT

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable
import scala.util.control.Breaks._

trait IFunctionGenerator {
  def generate(
    // These serve as the API that a function generator can use.
    // TODO: Give a trait with a reduced API.
    // Maybe this functionCompilerCore can be a lambda we can use to finalize and add &This* function.

    functionCompilerCore: FunctionCompilerCore,
    structCompiler: StructCompiler,
    destructorCompiler: DestructorCompiler,
    arrayCompiler: ArrayCompiler,
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    // We might be able to move these all into the function environment... maybe....
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT)
}

object DefaultPrintyThing {
  def print(x: => Object) = {
//    println("###: " + x)
  }
}

case class TypingPassOptions(
  debugOut: (=> String) => Unit = DefaultPrintyThing.print,
  globalOptions: GlobalOptions
) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
}



class Compiler(
    debugOut: (=> String) => Unit,

    interner: Interner,
    keywords: Keywords,
    globalOptions: GlobalOptions) {
  val opts = TypingPassOptions(debugOut, globalOptions)

  val nameTranslator = new NameTranslator(interner)

  val templataCompiler =
    new TemplataCompiler(
      opts,

      nameTranslator,
      new ITemplataCompilerDelegate {
        override def isAncestor(coutputs: CompilerOutputs, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): Boolean = {
          ancestorHelper.isAncestor(coutputs, descendantCitizenRef, ancestorInterfaceRef).nonEmpty
        }

        override def getStructRef(coutputs: CompilerOutputs, callRange: RangeS,structTemplata: StructTemplata, uncoercedTemplateArgs: Vector[ITemplata]): StructTT = {
          structCompiler.getStructRef(coutputs, callRange, structTemplata, uncoercedTemplateArgs)
        }

        override def getInterfaceRef(coutputs: CompilerOutputs, callRange: RangeS,interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: Vector[ITemplata]): InterfaceTT = {
          structCompiler.getInterfaceRef(coutputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
        }

        override def getStaticSizedArrayKind(
            env: IEnvironment,
            coutputs: CompilerOutputs,
            mutability: MutabilityT,
            variability: VariabilityT,
            size: Int,
            type2: CoordT
        ): StaticSizedArrayTT = {
          arrayCompiler.getStaticSizedArrayKind(env.globalEnv, coutputs, mutability, variability, size, type2)
        }

        override def getRuntimeSizedArrayKind(env: IEnvironment, state: CompilerOutputs, element: CoordT, arrayMutability: MutabilityT): RuntimeSizedArrayTT = {
          arrayCompiler.getRuntimeSizedArrayKind(env.globalEnv, state, element, arrayMutability)
        }
      })
  val inferCompiler: InferCompiler =
    new InferCompiler(
      opts,

      new IInfererDelegate[IEnvironment, CompilerOutputs] {
        override def lookupTemplata(
          env: IEnvironment,
          coutputs: CompilerOutputs,
          range: RangeS,
          name: INameT):
        ITemplata = {
          templataCompiler.lookupTemplata(env, coutputs, range, name)
        }

        override def isDescendant(
          env: IEnvironment,
          coutputs: CompilerOutputs,
          kind: KindT):
        Boolean = {
          kind match {
            case RuntimeSizedArrayTT(_, _) => false
            case OverloadSetT(_, _) => false
            case StaticSizedArrayTT(_, _, _, _) => false
            case s @ StructTT(_) => ancestorHelper.getAncestorInterfaces(coutputs, s).nonEmpty
            case i @ InterfaceTT(_) => ancestorHelper.getAncestorInterfaces(coutputs, i).nonEmpty
            case IntT(_) | BoolT() | FloatT() | StrT() | VoidT() => false
          }
        }

        override def isAncestor(
          env: IEnvironment,
          coutputs: CompilerOutputs,
          kind: KindT):
        Boolean = {
          kind match {
            case InterfaceTT(_) => true
            case _ => false
          }
        }

        def coerce(env: IEnvironment, state: CompilerOutputs, range: RangeS, toType: ITemplataType, templata: ITemplata): ITemplata = {
          templataCompiler.coerce(state, range, templata, toType)
        }

        override def lookupTemplataImprecise(env: IEnvironment, state: CompilerOutputs, range: RangeS, name: IImpreciseNameS): Option[ITemplata] = {
          templataCompiler.lookupTemplata(env, state, range, name)
        }

        override def lookupMemberTypes(state: CompilerOutputs, kind: KindT, expectedNumMembers: Int): Option[Vector[CoordT]] = {
            val underlyingstructTT =
              kind match {
                case sr@StructTT(_) => sr
                case _ => return None
              }
            val structDefT = state.lookupStruct(underlyingstructTT)
            val structMemberTypes = structDefT.members.map(_.tyype.reference)
            Some(structMemberTypes)
        }

        override def getMutability(state: CompilerOutputs, kind: KindT): MutabilityT = {
            Compiler.getMutability(state, kind)
        }

        override def getStaticSizedArrayKind(env: IEnvironment, state: CompilerOutputs, mutability: MutabilityT, variability: VariabilityT, size: Int, element: CoordT): (StaticSizedArrayTT) = {
            arrayCompiler.getStaticSizedArrayKind(env.globalEnv, state, mutability, variability, size, element)
        }

        override def getRuntimeSizedArrayKind(env: IEnvironment, state: CompilerOutputs, element: CoordT, arrayMutability: MutabilityT): RuntimeSizedArrayTT = {
            arrayCompiler.getRuntimeSizedArrayKind(env.globalEnv, state, element, arrayMutability)
        }

        override def evaluateInterfaceTemplata(
          state: CompilerOutputs,
          callRange: RangeS,
          templata: InterfaceTemplata,
          templateArgs: Vector[ITemplata]):
        (KindT) = {
            structCompiler.getInterfaceRef(state, callRange, templata, templateArgs)
        }

        override def evaluateStructTemplata(
          state: CompilerOutputs,
          callRange: RangeS,
          templata: StructTemplata,
          templateArgs: Vector[ITemplata]):
        (KindT) = {
          structCompiler.getStructRef(state, callRange, templata, templateArgs)
        }

        override def kindIsFromTemplate(
          coutputs: CompilerOutputs,
          actualCitizenRef: KindT,
          expectedCitizenTemplata: ITemplata):
        Boolean = {
          actualCitizenRef match {
            case s : CitizenRefT => templataCompiler.citizenIsFromTemplate(s, expectedCitizenTemplata)
            case RuntimeSizedArrayTT(_, _) => (expectedCitizenTemplata == RuntimeSizedArrayTemplateTemplata())
            case StaticSizedArrayTT(_, _, _, _) => (expectedCitizenTemplata == StaticSizedArrayTemplateTemplata())
            case _ => false
          }
        }

        override def getAncestors(coutputs: CompilerOutputs, descendant: KindT, includeSelf: Boolean): Set[KindT] = {
            (if (includeSelf) Set[KindT](descendant) else Set[KindT]()) ++
              (descendant match {
                case s : CitizenRefT => ancestorHelper.getAncestorInterfaces(coutputs, s).keys
                case _ => Set()
              })
        }

        override def getMemberCoords(state: CompilerOutputs, structTT: StructTT): Vector[CoordT] = {
            structCompiler.getMemberCoords(state, structTT)

        }


        override def getInterfaceTemplataType(it: InterfaceTemplata): ITemplataType = {
            it.originInterface.tyype

        }

        override def getStructTemplataType(st: StructTemplata): ITemplataType = {
            st.originStruct.tyype
        }

        override def structIsClosure(state: CompilerOutputs, structTT: StructTT): Boolean = {
            val structDef = state.getStructDefForRef(structTT)
            structDef.isClosure
        }

        override def resolveExactSignature(env: IEnvironment, state: CompilerOutputs, range: RangeS, name: String, coords: Vector[CoordT]): Result[PrototypeT, FindFunctionFailure] = {
            overloadCompiler.findFunction(env, state, range, interner.intern(CodeNameS(interner.intern(StrI(name)))), Vector.empty, Array.empty, coords.map(ParamFilter(_, None)), Vector.empty, true)
        }
      })
  val convertHelper =
    new ConvertHelper(
      opts,
      new IConvertHelperDelegate {
        override def isAncestor(coutputs: CompilerOutputs, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): Boolean = {
          ancestorHelper.isAncestor(coutputs, descendantCitizenRef, ancestorInterfaceRef).nonEmpty
        }
      })

  val ancestorHelper: AncestorHelper =
    new AncestorHelper(opts, interner, inferCompiler, new IAncestorHelperDelegate {
      override def getInterfaceRef(coutputs: CompilerOutputs, callRange: RangeS, interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: Vector[ITemplata]): InterfaceTT = {
        structCompiler.getInterfaceRef(coutputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
      }
    })

  val structCompiler: StructCompiler =
    new StructCompiler(
      opts,
      interner,
      keywords,
      nameTranslator,
      inferCompiler,
      ancestorHelper,
      new IStructCompilerDelegate {
        override def evaluateOrdinaryFunctionFromNonCallForHeader(coutputs: CompilerOutputs, functionTemplata: FunctionTemplata): FunctionHeaderT = {
          functionCompiler.evaluateOrdinaryFunctionFromNonCallForHeader(coutputs, functionTemplata)
        }

        override def evaluateTemplatedFunctionFromNonCallForHeader(coutputs: CompilerOutputs, functionTemplata: FunctionTemplata): FunctionHeaderT = {
          functionCompiler.evaluateTemplatedFunctionFromNonCallForHeader(coutputs, functionTemplata)
        }

        override def scoutExpectedFunctionForPrototype(
          env: IEnvironment, coutputs: CompilerOutputs, callRange: RangeS, functionName: IImpreciseNameS,
          explicitTemplateArgRulesS: Vector[IRulexSR],
          explicitTemplateArgRunesS: Array[IRuneS],
          args: Vector[ParamFilter], extraEnvsToLookIn: Vector[IEnvironment], exact: Boolean):
        PrototypeT = {
          overloadCompiler.findFunction(env, coutputs, callRange, functionName,
            explicitTemplateArgRulesS,
            explicitTemplateArgRunesS, args, extraEnvsToLookIn, exact) match {
            case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
            case Ok(x) => x
          }
        }
      })

  val functionCompiler: FunctionCompiler =
    new FunctionCompiler(opts, interner, keywords, nameTranslator, templataCompiler, inferCompiler, convertHelper, structCompiler,
      new IFunctionCompilerDelegate {
    override def evaluateBlockStatements(
        coutputs: CompilerOutputs,
        startingNenv: NodeEnvironment,
        nenv: NodeEnvironmentBox,
        life: LocationInFunctionEnvironment,
        exprs: BlockSE
    ): (ReferenceExpressionTE, Set[CoordT]) = {
      expressionCompiler.evaluateBlockStatements(coutputs, startingNenv, nenv, life, exprs)
    }

    override def translatePatternList(
      coutputs: CompilerOutputs,
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      patterns1: Vector[AtomSP],
      patternInputExprs2: Vector[ReferenceExpressionTE]
    ): ReferenceExpressionTE = {
      expressionCompiler.translatePatternList(coutputs, nenv, life, patterns1, patternInputExprs2)
    }

//    override def evaluateParent(env: IEnvironment, coutputs: CompilerOutputs, callRange: RangeS, sparkHeader: FunctionHeaderT): Unit = {
//      virtualCompiler.evaluateParent(env, coutputs, callRange, sparkHeader)
//    }

    override def generateFunction(
      functionCompilerCore: FunctionCompilerCore,
      generator: IFunctionGenerator,
      fullEnv: FunctionEnvironment,
      coutputs: CompilerOutputs,
      life: LocationInFunctionEnvironment,
      callRange: RangeS,
      originFunction: Option[FunctionA],
      paramCoords: Vector[ParameterT],
      maybeRetCoord: Option[CoordT]):
    FunctionHeaderT = {
      generator.generate(

        functionCompilerCore, structCompiler, destructorCompiler, arrayCompiler, fullEnv, coutputs, life, callRange, originFunction, paramCoords, maybeRetCoord)
    }
  })
  val overloadCompiler: OverloadResolver = new OverloadResolver(opts, interner, keywords, templataCompiler, inferCompiler, functionCompiler)
  val destructorCompiler: DestructorCompiler = new DestructorCompiler(opts, interner, keywords, structCompiler, overloadCompiler)

  val virtualCompiler = new VirtualCompiler(opts, interner, overloadCompiler)

  val sequenceCompiler = new SequenceCompiler(opts, interner, keywords, structCompiler, templataCompiler)

  val arrayCompiler =
    new ArrayCompiler(
      opts,
      interner,
      keywords,
      inferCompiler,
      overloadCompiler)

  val expressionCompiler: ExpressionCompiler =
    new ExpressionCompiler(
      opts,
      interner,
      keywords,
      nameTranslator,
      templataCompiler,
      inferCompiler,
      arrayCompiler,
      structCompiler,
      ancestorHelper,
      sequenceCompiler,
      overloadCompiler,
      destructorCompiler,
      convertHelper,
      new IExpressionCompilerDelegate {
        override def evaluateTemplatedFunctionFromCallForPrototype(coutputs: CompilerOutputs, callRange: RangeS, functionTemplata: FunctionTemplata, explicitTemplateArgs: Vector[ITemplata], args: Vector[ParamFilter]): FunctionCompiler.IEvaluateFunctionResult[PrototypeT] = {
          functionCompiler.evaluateTemplatedFunctionFromCallForPrototype(coutputs, callRange, functionTemplata, explicitTemplateArgs, args)
        }

        override def evaluateClosureStruct(coutputs: CompilerOutputs, containingNodeEnv: NodeEnvironment, callRange: RangeS, name: IFunctionDeclarationNameS, function1: FunctionA): StructTT = {
          functionCompiler.evaluateClosureStruct(coutputs, containingNodeEnv, callRange, name, function1)
        }
      })

  val edgeCompiler = new EdgeCompiler(interner, overloadCompiler)

  val functorHelper = new FunctorHelper(interner, keywords, structCompiler)
  val structConstructorMacro = new StructConstructorMacro(opts, interner, keywords, nameTranslator)
  val structDropMacro = new StructDropMacro(interner, keywords, nameTranslator, destructorCompiler)
  val structFreeMacro = new StructFreeMacro(interner, keywords, nameTranslator, destructorCompiler)
  val interfaceFreeMacro = new InterfaceFreeMacro(interner, keywords, overloadCompiler)
  val asSubtypeMacro = new AsSubtypeMacro(keywords, ancestorHelper, expressionCompiler)
  val rsaLenMacro = new RSALenMacro(keywords)
  val rsaMutNewMacro = new RSAMutableNewMacro(interner, keywords)
  val rsaImmNewMacro = new RSAImmutableNewMacro(interner, keywords)
  val rsaPushMacro = new RSAMutablePushMacro(interner, keywords)
  val rsaPopMacro = new RSAMutablePopMacro(interner, keywords)
  val rsaCapacityMacro = new RSAMutableCapacityMacro(interner, keywords)
  val ssaLenMacro = new SSALenMacro(keywords)
  val rsaDropMacro = new RSADropIntoMacro(keywords, arrayCompiler)
  val ssaDropMacro = new SSADropIntoMacro(keywords, arrayCompiler)
  val rsaFreeMacro = new RSAFreeMacro(keywords, arrayCompiler, destructorCompiler)
  val ssaFreeMacro = new SSAFreeMacro(keywords, arrayCompiler, destructorCompiler)
//  val ssaLenMacro = new SSALenMacro(keywords)
  val implDropMacro = new ImplDropMacro(interner, nameTranslator)
  val implFreeMacro = new ImplFreeMacro(interner, keywords, nameTranslator)
  val interfaceDropMacro = new InterfaceDropMacro(interner, keywords, nameTranslator)
  val abstractBodyMacro = new AbstractBodyMacro(keywords)
  val lockWeakMacro = new LockWeakMacro(keywords, expressionCompiler)
  val sameInstanceMacro = new SameInstanceMacro(keywords)
  val anonymousInterfaceMacro =
    new AnonymousInterfaceMacro(
      opts, interner, nameTranslator, overloadCompiler, structCompiler, structConstructorMacro, structDropMacro, structFreeMacro, interfaceFreeMacro, implDropMacro)


  def evaluate(packageToProgramA: PackageCoordinateMap[ProgramA]): Result[Hinputs, ICompileErrorT] = {
    try {
      Profiler.frame(() => {
        val fullNameAndEnvEntry: Vector[(FullNameT[INameT], IEnvEntry)] =
          packageToProgramA.flatMap({ case (coord, programA) =>
            val packageName = FullNameT(coord, Vector(), interner.intern(PackageTopLevelNameT()))
            programA.structs.map(structA => {
              val structNameT = packageName.addStep(nameTranslator.translateNameStep(structA.name))
              Vector((structNameT, StructEnvEntry(structA))) ++
              structConstructorMacro.getStructSiblingEntries(structConstructorMacro.macroName, structNameT, structA)
            }) ++
            programA.interfaces.map(interfaceA => {
              val interfaceNameT = packageName.addStep(nameTranslator.translateNameStep(interfaceA.name))
              Vector((interfaceNameT, InterfaceEnvEntry(interfaceA))) ++
                (if (interfaceA.attributes.contains(SealedS)) {
                  Vector()
                } else {
                  anonymousInterfaceMacro.getInterfaceSiblingEntries(interfaceNameT, interfaceA)
                })
            }) ++
            programA.impls.map(implA => {
              val implNameT = packageName.addStep(nameTranslator.translateImplName(implA.name))
              Vector((implNameT, ImplEnvEntry(implA))) ++
              implDropMacro.getImplSiblingEntries(implNameT, implA)
            }) ++
            programA.functions.map(functionA => {
              val functionNameT = packageName.addStep(nameTranslator.translateFunctionNameToTemplateName(functionA.name))
              Vector((functionNameT, FunctionEnvEntry(functionA)))
            })
          }).flatten.flatten.toVector

        val namespaceNameToTemplatas =
          fullNameAndEnvEntry
            .map({
              case (name, envEntry) => {
                (name.copy(last = interner.intern(PackageTopLevelNameT())), name.last, envEntry)
              }
            })
            .groupBy(_._1)
            .map({ case (namespaceFullName, envEntries) =>
              namespaceFullName ->
              TemplatasStore(namespaceFullName, Map(), Map())
                .addEntries(interner, envEntries.map({ case (_, b, c) => (b, c) }))
             }).toMap

        val globalEnv =
          GlobalEnvironment(
            functorHelper,
            structConstructorMacro,
            structDropMacro,
            structFreeMacro,
            interfaceDropMacro,
            interfaceFreeMacro,
            anonymousInterfaceMacro,
            Map(
              structDropMacro.macroName -> structDropMacro,
              structFreeMacro.macroName -> structFreeMacro,
              implFreeMacro.macroName -> implFreeMacro),
            Map(
              interfaceDropMacro.macroName -> interfaceDropMacro,
              interfaceFreeMacro.macroName -> interfaceFreeMacro),
            Map(),
            Map(
              abstractBodyMacro.generatorId -> abstractBodyMacro,
              structConstructorMacro.generatorId -> structConstructorMacro,
              structFreeMacro.freeGeneratorId -> structFreeMacro,
//              interfaceFreeMacro.generatorId -> interfaceFreeMacro,
              structDropMacro.dropGeneratorId -> structDropMacro,
              rsaLenMacro.generatorId -> rsaLenMacro,
              rsaMutNewMacro.generatorId -> rsaMutNewMacro,
              rsaImmNewMacro.generatorId -> rsaImmNewMacro,
              rsaPushMacro.generatorId -> rsaPushMacro,
              rsaPopMacro.generatorId -> rsaPopMacro,
              rsaCapacityMacro.generatorId -> rsaCapacityMacro,
              ssaLenMacro.generatorId -> ssaLenMacro,
              rsaDropMacro.generatorId -> rsaDropMacro,
              ssaDropMacro.generatorId -> ssaDropMacro,
              rsaFreeMacro.generatorId -> rsaFreeMacro,
              ssaFreeMacro.generatorId -> ssaFreeMacro,
              lockWeakMacro.generatorId -> lockWeakMacro,
              sameInstanceMacro.generatorId -> sameInstanceMacro,
              asSubtypeMacro.generatorId -> asSubtypeMacro),
            namespaceNameToTemplatas,
            // Bulitins
            env.TemplatasStore(FullNameT(PackageCoordinate.BUILTIN(interner, keywords), Vector(), interner.intern(PackageTopLevelNameT())), Map(), Map()).addEntries(
              interner,
              Vector[(INameT, IEnvEntry)](
                interner.intern(PrimitiveNameT(keywords.int)) -> TemplataEnvEntry(KindTemplata(IntT.i32)),
                interner.intern(PrimitiveNameT(keywords.i64)) -> TemplataEnvEntry(KindTemplata(IntT.i64)),
                interner.intern(PrimitiveNameT(keywords.Array)) -> TemplataEnvEntry(RuntimeSizedArrayTemplateTemplata()),
                interner.intern(PrimitiveNameT(keywords.bool)) -> TemplataEnvEntry(KindTemplata(BoolT())),
                interner.intern(PrimitiveNameT(keywords.float)) -> TemplataEnvEntry(KindTemplata(FloatT())),
                interner.intern(PrimitiveNameT(keywords.__Never)) -> TemplataEnvEntry(KindTemplata(NeverT(false))),
                interner.intern(PrimitiveNameT(keywords.str)) -> TemplataEnvEntry(KindTemplata(StrT())),
                interner.intern(PrimitiveNameT(keywords.void)) -> TemplataEnvEntry(KindTemplata(VoidT())))))

        val coutputs = CompilerOutputs()

//        val emptyTupleStruct =
//          sequenceCompiler.makeTupleKind(
//            PackageEnvironment.makeTopLevelEnvironment(
//              globalEnv, FullNameT(PackageCoordinate.BUILTIN, Vector(), PackageTopLevelNameT())),
//            coutputs,
//            Vector())
//        val emptyTupleStructRef =
//          sequenceCompiler.makeTupleCoord(
//            PackageEnvironment.makeTopLevelEnvironment(
//              globalEnv, FullNameT(PackageCoordinate.BUILTIN, Vector(), PackageTopLevelNameT())),
//            coutputs,
//            Vector())

        globalEnv.nameToTopLevelEnvironment.foreach({ case (namespaceCoord, templatas) =>
          val env = PackageEnvironment.makeTopLevelEnvironment(globalEnv, namespaceCoord)

          templatas.entriesByNameT.map({ case (name, entry) =>
            entry match {
              case TemplataEnvEntry(_) =>
              case FunctionEnvEntry(functionA) => {
                if (functionA.isTemplate) {
                  // Do nothing, it's a template
                } else {
                  if (isRootFunction(functionA)) {
                    val _ =
                      functionCompiler.evaluateOrdinaryFunctionFromNonCallForPrototype(
                        coutputs,
                        RangeS.internal(interner, -177),
                        FunctionTemplata(env, functionA))
                  }
                }
              }
              case StructEnvEntry(structA) => {
                if (structA.isTemplate) {
                  // Do nothing, it's a template
                } else {
                  if (isRootStruct(structA)) {
                    val templata = StructTemplata(env, structA)
                    val _ = structCompiler.getStructRef(coutputs, structA.range, templata, Vector.empty)
                  }
                }
              }
              case InterfaceEnvEntry(interfaceA) => {
                if (interfaceA.isTemplate) {
                  // Do nothing, it's a template
                } else {
                  if (isRootInterface(interfaceA)) {
                    val templata = InterfaceTemplata(env, interfaceA)
                    val _ = structCompiler.getInterfaceRef(coutputs, interfaceA.range, templata, Vector.empty)
                  }
                }
              }
              case ImplEnvEntry(impl) => {
                if (impl.isTemplate) {
                  // Do nothing, it's a template
                } else {

                }
              }
            }
          })
        })

        packageToProgramA.flatMap({ case (packageCoord, programA) =>
          val env =
            PackageEnvironment.makeTopLevelEnvironment(
              globalEnv, FullNameT(packageCoord, Vector(), interner.intern(PackageTopLevelNameT())))

          programA.exports.foreach({ case ExportAsA(range, exportedName, rules, runeToType, typeRuneA) =>
            val typeRuneT = typeRuneA

            val templataByRune =
              inferCompiler.solveExpectComplete(env, coutputs, rules, runeToType, range, Vector(), Vector())
            val kind =
              templataByRune.get(typeRuneT.rune) match {
                case Some(KindTemplata(kind)) => {
                  coutputs.addKindExport(range, kind, range.file.packageCoordinate, exportedName)
                }
                case Some(PrototypeTemplata(prototype)) => {
                  vimpl()
                }
                case _ => vfail()
              }
          })
        })

        breakable {
          while (true) {
            val denizensAtStart = coutputs.countDenizens()

            coutputs.getAllStructs().foreach(struct => {
              if (struct.mutability == ImmutableT) {
                destructorCompiler.getDropFunction(globalEnv, coutputs, RangeS.internal(interner, -1663), CoordT(ShareT, struct.getRef))
                destructorCompiler.getFreeFunction(globalEnv, coutputs, RangeS.internal(interner, -1663), CoordT(ShareT, struct.getRef))
              }
            })
            coutputs.getAllInterfaces().foreach(interface => {
              if (interface.mutability == ImmutableT) {
                destructorCompiler.getDropFunction(globalEnv, coutputs, RangeS.internal(interner, -1663), CoordT(ShareT, interface.getRef))
                destructorCompiler.getFreeFunction(globalEnv, coutputs, RangeS.internal(interner, -1663), CoordT(ShareT, interface.getRef))
              }
            })
            coutputs.getAllRuntimeSizedArrays().foreach(rsa => {
              if (rsa.mutability == ImmutableT) {
                destructorCompiler.getDropFunction(globalEnv, coutputs, RangeS.internal(interner, -1663), types.CoordT(ShareT, rsa))
                destructorCompiler.getFreeFunction(globalEnv, coutputs, RangeS.internal(interner, -1663), types.CoordT(ShareT, rsa))
              }
            })
            coutputs.getAllStaticSizedArrays().foreach(ssa => {
              if (ssa.mutability == ImmutableT) {
                destructorCompiler.getDropFunction(globalEnv, coutputs, RangeS.internal(interner, -1663), types.CoordT(ShareT, ssa))
                destructorCompiler.getFreeFunction(globalEnv, coutputs, RangeS.internal(interner, -1663), types.CoordT(ShareT, ssa))
              }
            })

              Profiler.frame(() => {
//                val env =
//                  PackageEnvironment.makeTopLevelEnvironment(
//                    globalEnv, FullNameT(PackageCoordinate.BUILTIN, Vector(), interner.intern(PackageTopLevelNameT())))

                // Returns the number of overrides stamped
                // This doesnt actually stamp *all* overrides, just the ones we can immediately
                // see missing. We don't know if, in the process of stamping these, we'll find more.
                // Also note, these don't stamp them right now, they defer them for later evaluating.
                edgeCompiler.compileITables(coutputs)
              })

            var deferredFunctionsEvaluated = 0
            while (coutputs.peekNextDeferredEvaluatingFunction().nonEmpty) {
              val nextDeferredEvaluatingFunction = coutputs.peekNextDeferredEvaluatingFunction().get
              deferredFunctionsEvaluated += 1
              // No, IntelliJ, I assure you this has side effects
              (nextDeferredEvaluatingFunction.call) (coutputs)
              coutputs.markDeferredFunctionEvaluated(nextDeferredEvaluatingFunction.prototypeT)
            }


            val denizensAtEnd = coutputs.countDenizens()
            if (denizensAtStart == denizensAtEnd)
              break
          }
        }

        val (interfaceEdgeBlueprints, interfaceToStructToMethods) = edgeCompiler.compileITables(coutputs)
        val edges =
          interfaceToStructToMethods.flatMap({ case (interface, structToMethods) =>
            structToMethods.map({ case (struct, methods) =>
              EdgeT(struct, interface, methods)
            })
          })

//        // NEVER ZIP TWO SETS TOGETHER
//        val edgeBlueprintsAsList = edgeBlueprints.toVector
//        val edgeBlueprintsByInterface = edgeBlueprintsAsList.map(_.interface).zip(edgeBlueprintsAsList).toMap;
//
//        edgeBlueprintsByInterface.foreach({ case (interfaceTT, edgeBlueprint) =>
//          vassert(edgeBlueprint.interface == interfaceTT)
//        })

        ensureDeepExports(coutputs)

        Profiler.frame(() => {
          val reachables = Reachability.findReachables(coutputs, interfaceEdgeBlueprints, interfaceToStructToMethods)

          val categorizedFunctions = coutputs.getAllFunctions().groupBy(f => reachables.functions.contains(f.header.toSignature))
          val reachableFunctions = categorizedFunctions.getOrElse(true, Vector.empty)
          val unreachableFunctions = categorizedFunctions.getOrElse(false, Vector.empty)
          unreachableFunctions.foreach(f => debugOut("Shaking out unreachable: " + f.header.fullName))
          reachableFunctions.foreach(f => debugOut("Including: " + f.header.fullName))

          val categorizedSSAs = coutputs.getAllStaticSizedArrays().groupBy(f => reachables.staticSizedArrays.contains(f))
          val reachableSSAs = categorizedSSAs.getOrElse(true, Vector.empty)
          val unreachableSSAs = categorizedSSAs.getOrElse(false, Vector.empty)
          unreachableSSAs.foreach(f => debugOut("Shaking out unreachable: " + f))
          reachableSSAs.foreach(f => debugOut("Including: " + f))

          val categorizedRSAs = coutputs.getAllRuntimeSizedArrays().groupBy(f => reachables.runtimeSizedArrays.contains(f))
          val reachableRSAs = categorizedRSAs.getOrElse(true, Vector.empty)
          val unreachableRSAs = categorizedRSAs.getOrElse(false, Vector.empty)
          unreachableRSAs.foreach(f => debugOut("Shaking out unreachable: " + f))
          reachableRSAs.foreach(f => debugOut("Including: " + f))

          val categorizedStructs = coutputs.getAllStructs().groupBy(f => reachables.structs.contains(f.getRef))
          val reachableStructs = categorizedStructs.getOrElse(true, Vector.empty)
          val unreachableStructs = categorizedStructs.getOrElse(false, Vector.empty)
          unreachableStructs.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
          reachableStructs.foreach(f => debugOut("Including: " + f.fullName))

          val categorizedInterfaces = coutputs.getAllInterfaces().groupBy(f => reachables.interfaces.contains(f.getRef))
          val reachableInterfaces = categorizedInterfaces.getOrElse(true, Vector.empty)
          val unreachableInterfaces = categorizedInterfaces.getOrElse(false, Vector.empty)
          unreachableInterfaces.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
          reachableInterfaces.foreach(f => debugOut("Including: " + f.fullName))

          val categorizedEdges =
            edges.groupBy(f => reachables.edges.contains(f))
          val reachableEdges = categorizedEdges.getOrElse(true, Vector.empty)
          val unreachableEdges = categorizedEdges.getOrElse(false, Vector.empty)
          unreachableEdges.foreach(f => debugOut("Shaking out unreachable: " + f))
          reachableEdges.foreach(f => debugOut("Including: " + f))

          val allKinds =
            reachableStructs.map(_.getRef) ++ reachableInterfaces.map(_.getRef) ++ reachableSSAs ++ reachableRSAs
          val reachableImmKinds: Vector[KindT] =
            allKinds
              .filter({
                case s@StructTT(_) => coutputs.lookupMutability(s) == ImmutableT
                case i@InterfaceTT(_) => coutputs.lookupMutability(i) == ImmutableT
                case StaticSizedArrayTT(_, m, _, _) => m == ImmutableT
                case RuntimeSizedArrayTT(m, _) => m == ImmutableT
                case _ => true
              })
              .toVector
          val reachableImmKindToDestructor = reachableImmKinds.zip(reachableImmKinds.map(coutputs.findImmDestructor)).toMap

          val hinputs =
            vale.typing.Hinputs(
              reachableInterfaces.toVector,
              reachableStructs.toVector,
              reachableFunctions.toVector,
              reachableImmKindToDestructor,
              interfaceEdgeBlueprints.groupBy(_.interface).mapValues(vassertOne(_)),
              edges.toVector,
              coutputs.getKindExports,
              coutputs.getFunctionExports,
              coutputs.getKindExterns,
              coutputs.getFunctionExterns)

          vassert(reachableFunctions.toVector.map(_.header.fullName).distinct.size == reachableFunctions.toVector.map(_.header.fullName).size)

          Ok(hinputs)
        })
      })
    } catch {
      case CompileErrorExceptionT(err) => Err(err)
    }
  }

  def ensureDeepExports(coutputs: CompilerOutputs): Unit = {
    val packageToKindToExport =
      coutputs.getKindExports
        .map(kindExport => (kindExport.packageCoordinate, kindExport.tyype, kindExport))
        .groupBy(_._1)
        .mapValues(
          _.map(x => (x._2, x._3))
            .groupBy(_._1)
            .mapValues({
              case Vector() => vwat()
              case Vector(only) => only
              case multiple => {
                val exports = multiple.map(_._2)
                throw CompileErrorExceptionT(
                  TypeExportedMultipleTimes(
                    exports.head.range,
                    exports.head.packageCoordinate,
                    exports))
              }
            }))

    coutputs.getFunctionExports.foreach(funcExport => {
      val exportedKindToExport = packageToKindToExport.getOrElse(funcExport.packageCoordinate, Map())
      (Vector(funcExport.prototype.returnType) ++ funcExport.prototype.paramTypes)
        .foreach(paramType => {
          if (!Compiler.isPrimitive(paramType.kind) && !exportedKindToExport.contains(paramType.kind)) {
            throw CompileErrorExceptionT(
              ExportedFunctionDependedOnNonExportedKind(
                funcExport.range, funcExport.packageCoordinate, funcExport.prototype.toSignature, paramType.kind))
          }
        })
    })
    coutputs.getFunctionExterns.foreach(functionExtern => {
      val exportedKindToExport = packageToKindToExport.getOrElse(functionExtern.packageCoordinate, Map())
      (Vector(functionExtern.prototype.returnType) ++ functionExtern.prototype.paramTypes)
        .foreach(paramType => {
          if (!Compiler.isPrimitive(paramType.kind) && !exportedKindToExport.contains(paramType.kind)) {
            throw CompileErrorExceptionT(
              ExternFunctionDependedOnNonExportedKind(
                functionExtern.range, functionExtern.packageCoordinate, functionExtern.prototype.toSignature, paramType.kind))
          }
        })
    })
    packageToKindToExport.foreach({ case (packageCoord, exportedKindToExport) =>
      exportedKindToExport.foreach({ case (exportedKind, (kind, export)) =>
        exportedKind match {
          case sr@StructTT(_) => {
            val structDef = coutputs.getStructDefForRef(sr)
            structDef.members.foreach({ case StructMemberT(_, _, member) =>
              val CoordT(_, memberKind) = member.reference
              if (structDef.mutability == ImmutableT && !Compiler.isPrimitive(memberKind) && !exportedKindToExport.contains(memberKind)) {
                throw CompileErrorExceptionT(
                  vale.typing.ExportedImmutableKindDependedOnNonExportedKind(
                    export.range, packageCoord, exportedKind, memberKind))
              }
            })
          }
          case StaticSizedArrayTT(_, mutability, _, CoordT(_, elementKind)) => {
            if (mutability == ImmutableT && !Compiler.isPrimitive(elementKind) && !exportedKindToExport.contains(elementKind)) {
              throw CompileErrorExceptionT(
                vale.typing.ExportedImmutableKindDependedOnNonExportedKind(
                  export.range, packageCoord, exportedKind, elementKind))
            }
          }
          case RuntimeSizedArrayTT(mutability, CoordT(_, elementKind)) => {
            if (mutability == ImmutableT && !Compiler.isPrimitive(elementKind) && !exportedKindToExport.contains(elementKind)) {
              throw CompileErrorExceptionT(
                vale.typing.ExportedImmutableKindDependedOnNonExportedKind(
                  export.range, packageCoord, exportedKind, elementKind))
            }
          }
          case InterfaceTT(_) =>
        }
      })
    })
  }

  // Returns whether we should eagerly compile this and anything it depends on.
  def isRootFunction(functionA: FunctionA): Boolean = {
    functionA.name match {
      case FunctionNameS(StrI("main"), _) => return true
      case _ =>
    }
    functionA.attributes.exists({
      case ExportS(_) => true
      case ExternS(_) => true
      case _ => false
    })
  }

  // Returns whether we should eagerly compile this and anything it depends on.
  def isRootStruct(structA: StructA): Boolean = {
    structA.attributes.exists({ case ExportS(_) => true case _ => false })
  }

  // Returns whether we should eagerly compile this and anything it depends on.
  def isRootInterface(interfaceA: InterfaceA): Boolean = {
    interfaceA.attributes.exists({ case ExportS(_) => true case _ => false })
  }
}


object Compiler {
  // Flattens any nested ConsecutorTEs
  def consecutive(exprs: Vector[ReferenceExpressionTE]): ReferenceExpressionTE = {
    exprs match {
      case Vector() => vwat("Shouldn't have zero-element consecutors!")
      case Vector(only) => only
      case _ => {
        val flattened =
          exprs.flatMap({
            case ConsecutorTE(exprs) => exprs
            case other => Vector(other)
          })

        val withoutInitVoids =
          flattened.init
            .filter({ case VoidLiteralTE() => false case _ => true }) :+
            flattened.last

        withoutInitVoids match {
          case Vector() => vwat("Shouldn't have zero-element consecutors!")
          case Vector(only) => only
          case _ => ConsecutorTE(withoutInitVoids)
        }
      }
    }
  }

  def isPrimitive(kind: KindT): Boolean = {
    kind match {
      case VoidT() | IntT(_) | BoolT() | StrT() | NeverT(_) | FloatT() => true
//      case TupleTT(_, understruct) => isPrimitive(understruct)
      case StructTT(_) => false
      case InterfaceTT(_) => false
      case StaticSizedArrayTT(_, _, _, _) => false
      case RuntimeSizedArrayTT(_, _) => false
    }
  }

  def getMutabilities(coutputs: CompilerOutputs, concreteValues2: Vector[KindT]):
  Vector[MutabilityT] = {
    concreteValues2.map(concreteValue2 => getMutability(coutputs, concreteValue2))
  }

  def getMutability(coutputs: CompilerOutputs, concreteValue2: KindT):
  MutabilityT = {
    concreteValue2 match {
      case NeverT(_) => ImmutableT
      case IntT(_) => ImmutableT
      case FloatT() => ImmutableT
      case BoolT() => ImmutableT
      case StrT() => ImmutableT
      case VoidT() => ImmutableT
      case RuntimeSizedArrayTT(mutability, _) => mutability
      case StaticSizedArrayTT(_, mutability, _, _) => mutability
      case sr @ StructTT(_) => coutputs.lookupMutability(sr)
      case ir @ InterfaceTT(_) => coutputs.lookupMutability(ir)
//      case PackTT(_, sr) => coutputs.lookupMutability(sr)
//      case TupleTT(_, sr) => coutputs.lookupMutability(sr)
      case OverloadSetT(_, _) => {
        // Just like FunctionT2
        ImmutableT
      }
    }
  }
}
