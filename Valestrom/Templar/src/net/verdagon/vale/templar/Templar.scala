package net.verdagon.vale.templar;

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.ast.UseP
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.rules.IRulexSR
import net.verdagon.vale.scout.{BlockSE, CodeNameS, ExportS, ExternS, FunctionNameS, GeneratedBodyS, GlobalFunctionFamilyNameS, ICompileErrorS, IExpressionSE, IFunctionDeclarationNameS, IImpreciseNameS, INameS, IRuneS, ITemplataType, ProgramS, SealedS, TopLevelCitizenDeclarationNameS}
import net.verdagon.vale.templar.EdgeTemplar.{FoundFunction, NeededOverride, PartialEdgeT}
import net.verdagon.vale.templar.OverloadTemplar.FindFunctionFailure
import net.verdagon.vale.templar.ast.{ArgLookupTE, ArrayLengthTE, AsSubtypeTE, BlockTE, ConsecutorTE, EdgeT, FunctionCallTE, FunctionHeaderT, FunctionT, IsSameInstanceTE, LocationInFunctionEnvironment, LockWeakTE, ParameterT, ProgramT, PrototypeT, ReferenceExpressionTE, ReturnTE, VoidLiteralTE}
import net.verdagon.vale.templar.citizen.{AncestorHelper, IAncestorHelperDelegate, IStructTemplarDelegate, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.{ExpressionTemplar, IExpressionTemplarDelegate, LocalHelper}
import net.verdagon.vale.templar.types.{CoordT, _}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar, FunctionTemplarCore, IFunctionTemplarDelegate, VirtualTemplar}
import net.verdagon.vale.templar.infer.IInfererDelegate
import net.verdagon.vale.templar.macros.citizen.{ImplDropMacro, ImplFreeMacro, InterfaceDropMacro, InterfaceFreeMacro, StructDropMacro, StructFreeMacro}
import net.verdagon.vale.templar.macros.rsa.{RSADropIntoMacro, RSAFreeMacro, RSAImmutableNewMacro, RSALenMacro, RSAMutableCapacityMacro, RSAMutableNewMacro, RSAMutablePopMacro, RSAMutablePushMacro}
import net.verdagon.vale.templar.macros.ssa.{SSADropIntoMacro, SSAFreeMacro, SSALenMacro}
import net.verdagon.vale.templar.macros.{AbstractBodyMacro, AnonymousInterfaceMacro, AsSubtypeMacro, FunctorHelper, LockWeakMacro, SameInstanceMacro, StructConstructorMacro}
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, INameT, NameTranslator, PackageTopLevelNameT, PrimitiveNameT}

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable
import scala.util.control.Breaks._

trait IFunctionGenerator {
  def generate(
    // These serve as the API that a function generator can use.
    // TODO: Give a trait with a reduced API.
    // Maybe this functionTemplarCore can be a lambda we can use to finalize and add *this* function.
    profiler: IProfiler,
    functionTemplarCore: FunctionTemplarCore,
    structTemplar: StructTemplar,
    destructorTemplar: DestructorTemplar,
    arrayTemplar: ArrayTemplar,
    env: FunctionEnvironment,
    temputs: Temputs,
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

case class TemplarOptions(
  debugOut: (=> String) => Unit = DefaultPrintyThing.print,
  globalOptions: GlobalOptions
) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}



class Templar(debugOut: (=> String) => Unit, profiler: IProfiler, globalOptions: GlobalOptions) {
  val opts = TemplarOptions(debugOut, globalOptions)

  val templataTemplar =
    new TemplataTemplar(
      opts,
      profiler,
      new ITemplataTemplarDelegate {
        override def isAncestor(temputs: Temputs, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): Boolean = {
          ancestorHelper.isAncestor(temputs, descendantCitizenRef, ancestorInterfaceRef).nonEmpty
        }

        override def getStructRef(temputs: Temputs, callRange: RangeS,structTemplata: StructTemplata, uncoercedTemplateArgs: Vector[ITemplata]): StructTT = {
          structTemplar.getStructRef(temputs, callRange, structTemplata, uncoercedTemplateArgs)
        }

        override def getInterfaceRef(temputs: Temputs, callRange: RangeS,interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: Vector[ITemplata]): InterfaceTT = {
          structTemplar.getInterfaceRef(temputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
        }

        override def getStaticSizedArrayKind(
            env: IEnvironment,
            temputs: Temputs,
            mutability: MutabilityT,
            variability: VariabilityT,
            size: Int,
            type2: CoordT
        ): StaticSizedArrayTT = {
          arrayTemplar.getStaticSizedArrayKind(env.globalEnv, temputs, mutability, variability, size, type2)
        }

        override def getRuntimeSizedArrayKind(env: IEnvironment, state: Temputs, element: CoordT, arrayMutability: MutabilityT): RuntimeSizedArrayTT = {
          arrayTemplar.getRuntimeSizedArrayKind(env.globalEnv, state, element, arrayMutability)
        }
      })
  val inferTemplar: InferTemplar =
    new InferTemplar(
      opts,
      profiler,
      new IInfererDelegate[IEnvironment, Temputs] {
        override def lookupTemplata(
          env: IEnvironment,
          temputs: Temputs,
          range: RangeS,
          name: INameT):
        ITemplata = {
          templataTemplar.lookupTemplata(env, temputs, range, name)
        }

        override def isDescendant(
          env: IEnvironment,
          temputs: Temputs,
          kind: KindT):
        Boolean = {
          kind match {
            case RuntimeSizedArrayTT(_, _) => false
            case OverloadSet(_, _) => false
            case StaticSizedArrayTT(_, _, _, _) => false
            case s @ StructTT(_) => ancestorHelper.getAncestorInterfaces(temputs, s).nonEmpty
            case i @ InterfaceTT(_) => ancestorHelper.getAncestorInterfaces(temputs, i).nonEmpty
            case IntT(_) | BoolT() | StrT() | VoidT() => false
          }
        }

        override def isAncestor(
          env: IEnvironment,
          temputs: Temputs,
          kind: KindT):
        Boolean = {
          kind match {
            case InterfaceTT(_) => true
            case _ => false
          }
        }

        def coerce(env: IEnvironment, state: Temputs, range: RangeS, toType: ITemplataType, templata: ITemplata): ITemplata = {
          templataTemplar.coerce(state, range, templata, toType)
        }

        override def lookupTemplataImprecise(env: IEnvironment, state: Temputs, range: RangeS, name: IImpreciseNameS): Option[ITemplata] = {
          templataTemplar.lookupTemplata(env, state, range, name)
        }

        override def lookupMemberTypes(state: Temputs, kind: KindT, expectedNumMembers: Int): Option[Vector[CoordT]] = {
          profiler.childFrame("InferTemplarDelegate.lookupMemberTypes", () => {
            val underlyingstructTT =
              kind match {
                case sr@StructTT(_) => sr
                case _ => return None
              }
            val structDefT = state.lookupStruct(underlyingstructTT)
            val structMemberTypes = structDefT.members.map(_.tyype.reference)
            Some(structMemberTypes)
          })
        }

        override def getMutability(state: Temputs, kind: KindT): MutabilityT = {
          profiler.childFrame("InferTemplarDelegate.getMutability", () => {
            Templar.getMutability(state, kind)
          })
        }

        override def getStaticSizedArrayKind(env: IEnvironment, state: Temputs, mutability: MutabilityT, variability: VariabilityT, size: Int, element: CoordT): (StaticSizedArrayTT) = {
          profiler.childFrame("InferTemplarDelegate.getStaticSizedArrayKind", () => {
            arrayTemplar.getStaticSizedArrayKind(env.globalEnv, state, mutability, variability, size, element)
          })
        }

        override def getRuntimeSizedArrayKind(env: IEnvironment, state: Temputs, element: CoordT, arrayMutability: MutabilityT): RuntimeSizedArrayTT = {
          profiler.childFrame("InferTemplarDelegate.getRuntimeSizedArrayKind", () => {
            arrayTemplar.getRuntimeSizedArrayKind(env.globalEnv, state, element, arrayMutability)
          })
        }

        override def evaluateInterfaceTemplata(
          state: Temputs,
          callRange: RangeS,
          templata: InterfaceTemplata,
          templateArgs: Vector[ITemplata]):
        (KindT) = {
          profiler.childFrame("InferTemplarDelegate.evaluateInterfaceTemplata", () => {
            structTemplar.getInterfaceRef(state, callRange, templata, templateArgs)
          })
        }

        override def evaluateStructTemplata(
          state: Temputs,
          callRange: RangeS,
          templata: StructTemplata,
          templateArgs: Vector[ITemplata]):
        (KindT) = {
          profiler.childFrame("InferTemplarDelegate.evaluateStructTemplata", () => {
            structTemplar.getStructRef(state, callRange, templata, templateArgs)
          })
        }

        override def kindIsFromTemplate(
          temputs: Temputs,
          actualCitizenRef: KindT,
          expectedCitizenTemplata: ITemplata):
        Boolean = {
          actualCitizenRef match {
            case s : CitizenRefT => templataTemplar.citizenIsFromTemplate(s, expectedCitizenTemplata)
            case RuntimeSizedArrayTT(_, _) => (expectedCitizenTemplata == RuntimeSizedArrayTemplateTemplata())
            case StaticSizedArrayTT(_, _, _, _) => (expectedCitizenTemplata == StaticSizedArrayTemplateTemplata())
            case _ => false
          }
        }

        override def getAncestors(temputs: Temputs, descendant: KindT, includeSelf: Boolean): Set[KindT] = {
          profiler.childFrame("InferTemplarDelegate.getAncestorInterfaces", () => {
            (if (includeSelf) Set[KindT](descendant) else Set[KindT]()) ++
              (descendant match {
                case s : CitizenRefT => ancestorHelper.getAncestorInterfaces(temputs, s).keys
                case _ => Set()
              })
          })
        }

        override def getMemberCoords(state: Temputs, structTT: StructTT): Vector[CoordT] = {
          profiler.childFrame("InferTemplarDelegate.getMemberCoords", () => {
            structTemplar.getMemberCoords(state, structTT)
          })
        }


        override def getInterfaceTemplataType(it: InterfaceTemplata): ITemplataType = {
          profiler.childFrame("InferTemplarDelegate.getInterfaceTemplataType", () => {
            it.originInterface.tyype
          })
        }

        override def getStructTemplataType(st: StructTemplata): ITemplataType = {
          profiler.childFrame("InferTemplarDelegate.getStructTemplataType", () => {
            st.originStruct.tyype
          })
        }

        override def structIsClosure(state: Temputs, structTT: StructTT): Boolean = {
          profiler.childFrame("InferTemplarDelegate.structIsClosure", () => {
            val structDef = state.getStructDefForRef(structTT)
            structDef.isClosure
          })
        }

        override def resolveExactSignature(env: IEnvironment, state: Temputs, range: RangeS, name: String, coords: Vector[CoordT]): PrototypeT = {
          profiler.childFrame("InferTemplarDelegate.resolveExactSignature", () => {
            overloadTemplar.findFunction(env, state, range, CodeNameS(name), Vector.empty, Array.empty, coords.map(ParamFilter(_, None)), Vector.empty, true)
          })
        }
      })
  val convertHelper =
    new ConvertHelper(
      opts,
      new IConvertHelperDelegate {
        override def isAncestor(temputs: Temputs, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): Boolean = {
          ancestorHelper.isAncestor(temputs, descendantCitizenRef, ancestorInterfaceRef).nonEmpty
        }
      })

  val ancestorHelper: AncestorHelper =
    new AncestorHelper(opts, profiler, inferTemplar, new IAncestorHelperDelegate {
      override def getInterfaceRef(temputs: Temputs, callRange: RangeS, interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: Vector[ITemplata]): InterfaceTT = {
        structTemplar.getInterfaceRef(temputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
      }
    })

  val structTemplar: StructTemplar =
    new StructTemplar(
      opts,
      profiler,
      inferTemplar,
      ancestorHelper,
      new IStructTemplarDelegate {
        override def evaluateOrdinaryFunctionFromNonCallForHeader(temputs: Temputs, functionTemplata: FunctionTemplata): FunctionHeaderT = {
          functionTemplar.evaluateOrdinaryFunctionFromNonCallForHeader(temputs, functionTemplata)
        }

        override def evaluateTemplatedFunctionFromNonCallForHeader(temputs: Temputs, functionTemplata: FunctionTemplata): FunctionHeaderT = {
          functionTemplar.evaluateTemplatedFunctionFromNonCallForHeader(temputs, functionTemplata)
        }

        override def scoutExpectedFunctionForPrototype(
          env: IEnvironment, temputs: Temputs, callRange: RangeS, functionName: IImpreciseNameS,
          explicitTemplateArgRulesS: Vector[IRulexSR],
          explicitTemplateArgRunesS: Array[IRuneS],
          args: Vector[ParamFilter], extraEnvsToLookIn: Vector[IEnvironment], exact: Boolean):
        PrototypeT = {
          overloadTemplar.findFunction(env, temputs, callRange, functionName,
            explicitTemplateArgRulesS,
            explicitTemplateArgRunesS, args, extraEnvsToLookIn, exact)
        }
      })

  val functionTemplar: FunctionTemplar =
    new FunctionTemplar(opts, profiler, templataTemplar, inferTemplar, convertHelper, structTemplar,
      new IFunctionTemplarDelegate {
    override def evaluateBlockStatements(
        temputs: Temputs,
        startingNenv: NodeEnvironment,
        nenv: NodeEnvironmentBox,
        life: LocationInFunctionEnvironment,
        exprs: BlockSE
    ): (ReferenceExpressionTE, Set[CoordT]) = {
      expressionTemplar.evaluateBlockStatements(temputs, startingNenv, nenv, life, exprs)
    }

    override def translatePatternList(
      temputs: Temputs,
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      patterns1: Vector[AtomSP],
      patternInputExprs2: Vector[ReferenceExpressionTE]
    ): ReferenceExpressionTE = {
      expressionTemplar.translatePatternList(temputs, nenv, life, patterns1, patternInputExprs2)
    }

    override def evaluateParent(env: IEnvironment, temputs: Temputs, sparkHeader: FunctionHeaderT): Unit = {
      virtualTemplar.evaluateParent(env, temputs, sparkHeader)
    }

    override def generateFunction(
      functionTemplarCore: FunctionTemplarCore,
      generator: IFunctionGenerator,
      fullEnv: FunctionEnvironment,
      temputs: Temputs,
      life: LocationInFunctionEnvironment,
      callRange: RangeS,
      originFunction: Option[FunctionA],
      paramCoords: Vector[ParameterT],
      maybeRetCoord: Option[CoordT]):
    FunctionHeaderT = {
      generator.generate(
        profiler,
        functionTemplarCore, structTemplar, destructorTemplar, arrayTemplar, fullEnv, temputs, life, callRange, originFunction, paramCoords, maybeRetCoord)
    }
  })
  val overloadTemplar: OverloadTemplar = new OverloadTemplar(opts, profiler, templataTemplar, inferTemplar, functionTemplar)
  val destructorTemplar: DestructorTemplar = new DestructorTemplar(opts, structTemplar, overloadTemplar)

  val virtualTemplar = new VirtualTemplar(opts, overloadTemplar)

  val sequenceTemplar = new SequenceTemplar(opts, profiler, structTemplar, templataTemplar)

  val arrayTemplar =
    new ArrayTemplar(
      opts,
      profiler,
      inferTemplar,
      overloadTemplar)

  val expressionTemplar: ExpressionTemplar =
    new ExpressionTemplar(
      opts,
      profiler,
      templataTemplar,
      inferTemplar,
      arrayTemplar,
      structTemplar,
      ancestorHelper,
      sequenceTemplar,
      overloadTemplar,
      destructorTemplar,
      convertHelper,
      new IExpressionTemplarDelegate {
        override def evaluateTemplatedFunctionFromCallForPrototype(temputs: Temputs, callRange: RangeS, functionTemplata: FunctionTemplata, explicitTemplateArgs: Vector[ITemplata], args: Vector[ParamFilter]): FunctionTemplar.IEvaluateFunctionResult[PrototypeT] = {
          functionTemplar.evaluateTemplatedFunctionFromCallForPrototype(temputs, callRange, functionTemplata, explicitTemplateArgs, args)
        }

        override def evaluateClosureStruct(temputs: Temputs, containingNodeEnv: NodeEnvironment, callRange: RangeS, name: IFunctionDeclarationNameS, function1: FunctionA): StructTT = {
          functionTemplar.evaluateClosureStruct(temputs, containingNodeEnv, callRange, name, function1)
        }
      })

  val functorHelper = new FunctorHelper(profiler, structTemplar)
  val structConstructorMacro = new StructConstructorMacro(opts, profiler)
  val structDropMacro = new StructDropMacro(overloadTemplar, destructorTemplar)
  val structFreeMacro = new StructFreeMacro(overloadTemplar, destructorTemplar)
  val interfaceFreeMacro = new InterfaceFreeMacro(overloadTemplar)
  val asSubtypeMacro = new AsSubtypeMacro(ancestorHelper, expressionTemplar)
  val rsaLenMacro = new RSALenMacro()
  val rsaMutNewMacro = new RSAMutableNewMacro(profiler)
  val rsaImmNewMacro = new RSAImmutableNewMacro(profiler)
  val rsaPushMacro = new RSAMutablePushMacro(profiler)
  val rsaPopMacro = new RSAMutablePopMacro(profiler)
  val rsaCapacityMacro = new RSAMutableCapacityMacro(profiler)
  val ssaLenMacro = new SSALenMacro()
  val rsaDropMacro = new RSADropIntoMacro(arrayTemplar)
  val ssaDropMacro = new SSADropIntoMacro(arrayTemplar)
  val rsaFreeMacro = new RSAFreeMacro(arrayTemplar, destructorTemplar)
  val ssaFreeMacro = new SSAFreeMacro(arrayTemplar, destructorTemplar)
//  val ssaLenMacro = new SSALenMacro()
  val implDropMacro = new ImplDropMacro()
  val implFreeMacro = new ImplFreeMacro(overloadTemplar)
  val interfaceDropMacro = new InterfaceDropMacro(overloadTemplar)
  val abstractBodyMacro = new AbstractBodyMacro()
  val lockWeakMacro = new LockWeakMacro(expressionTemplar)
  val sameInstanceMacro = new SameInstanceMacro(profiler)
  val anonymousInterfaceMacro =
    new AnonymousInterfaceMacro(
      opts, profiler, overloadTemplar, structTemplar, structConstructorMacro, structDropMacro, structFreeMacro, interfaceFreeMacro, implDropMacro)


  def evaluate(packageToProgramA: PackageCoordinateMap[ProgramA]): Result[Hinputs, ICompileErrorT] = {
    try {
      profiler.newProfile("Templar.evaluate", "", () => {
        val fullNameAndEnvEntry: Vector[(FullNameT[INameT], IEnvEntry)] =
          packageToProgramA.flatMap({ case (coord, programA) =>
            val packageName = FullNameT(coord, Vector(), PackageTopLevelNameT())
            programA.structs.map(structA => {
              val structNameT = packageName.addStep(NameTranslator.translateNameStep(structA.name))
              Vector((structNameT, StructEnvEntry(structA))) ++
              structConstructorMacro.getStructSiblingEntries(structConstructorMacro.macroName, structNameT, structA)
            }) ++
            programA.interfaces.map(interfaceA => {
              val interfaceNameT = packageName.addStep(NameTranslator.translateNameStep(interfaceA.name))
              Vector((interfaceNameT, InterfaceEnvEntry(interfaceA))) ++
                (if (interfaceA.attributes.contains(SealedS)) {
                  Vector()
                } else {
                  anonymousInterfaceMacro.getInterfaceSiblingEntries(interfaceNameT, interfaceA)
                })
            }) ++
            programA.impls.map(implA => {
              val implNameT = packageName.addStep(NameTranslator.translateImplName(implA.name))
              Vector((implNameT, ImplEnvEntry(implA))) ++
              implDropMacro.getImplSiblingEntries(implNameT, implA)
            }) ++
            programA.functions.map(functionA => {
              val functionNameT = packageName.addStep(NameTranslator.translateFunctionNameToTemplateName(functionA.name))
              Vector((functionNameT, FunctionEnvEntry(functionA)))
            })
          }).flatten.flatten.toVector

        val namespaceNameToTemplatas =
          fullNameAndEnvEntry
            .map({
              case (name, envEntry) => {
                (name.copy(last = PackageTopLevelNameT()), name.last, envEntry)
              }
            })
            .groupBy(_._1)
            .map({ case (namespaceFullName, envEntries) =>
              namespaceFullName ->
              TemplatasStore(namespaceFullName, Map(), Map())
                .addEntries(envEntries.map({ case (_, b, c) => (b, c) }))
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
              interfaceFreeMacro.generatorId -> interfaceFreeMacro,
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
            TemplatasStore(FullNameT(PackageCoordinate.BUILTIN, Vector(), PackageTopLevelNameT()), Map(), Map()).addEntries(
              Vector[(INameT, IEnvEntry)](
                PrimitiveNameT("int") -> TemplataEnvEntry(KindTemplata(IntT.i32)),
                PrimitiveNameT("i64") -> TemplataEnvEntry(KindTemplata(IntT.i64)),
                PrimitiveNameT("Array") -> TemplataEnvEntry(RuntimeSizedArrayTemplateTemplata()),
                PrimitiveNameT("bool") -> TemplataEnvEntry(KindTemplata(BoolT())),
                PrimitiveNameT("float") -> TemplataEnvEntry(KindTemplata(FloatT())),
                PrimitiveNameT("__Never") -> TemplataEnvEntry(KindTemplata(NeverT(false))),
                PrimitiveNameT("str") -> TemplataEnvEntry(KindTemplata(StrT())),
                PrimitiveNameT("void") -> TemplataEnvEntry(KindTemplata(VoidT())))))

        val temputs = Temputs()

//        val emptyTupleStruct =
//          sequenceTemplar.makeTupleKind(
//            PackageEnvironment.makeTopLevelEnvironment(
//              globalEnv, FullNameT(PackageCoordinate.BUILTIN, Vector(), PackageTopLevelNameT())),
//            temputs,
//            Vector())
//        val emptyTupleStructRef =
//          sequenceTemplar.makeTupleCoord(
//            PackageEnvironment.makeTopLevelEnvironment(
//              globalEnv, FullNameT(PackageCoordinate.BUILTIN, Vector(), PackageTopLevelNameT())),
//            temputs,
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
                      functionTemplar.evaluateOrdinaryFunctionFromNonCallForPrototype(
                        temputs,
                        RangeS.internal(-177),
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
                    val _ = structTemplar.getStructRef(temputs, structA.range, templata, Vector.empty)
                  }
                }
              }
              case InterfaceEnvEntry(interfaceA) => {
                if (interfaceA.isTemplate) {
                  // Do nothing, it's a template
                } else {
                  if (isRootInterface(interfaceA)) {
                    val templata = InterfaceTemplata(env, interfaceA)
                    val _ = structTemplar.getInterfaceRef(temputs, interfaceA.range, templata, Vector.empty)
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
              globalEnv, FullNameT(packageCoord, Vector(), PackageTopLevelNameT()))

          programA.exports.foreach({ case ExportAsA(range, exportedName, rules, runeToType, typeRuneA) =>
            val typeRuneT = typeRuneA

            val templataByRune =
              inferTemplar.solveExpectComplete(env, temputs, rules, runeToType, range, Vector(), Vector())
            val kind =
              templataByRune.get(typeRuneT.rune) match {
                case Some(KindTemplata(kind)) => {
                  temputs.addKindExport(range, kind, range.file.packageCoordinate, exportedName)
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
            temputs.getAllStructs().foreach(struct => {
              if (struct.mutability == ImmutableT) {
                destructorTemplar.getDropFunction(globalEnv, temputs, CoordT(ShareT, ReadonlyT, struct.getRef))
                destructorTemplar.getFreeFunction(globalEnv, temputs, CoordT(ShareT, ReadonlyT, struct.getRef))
              }
            })
            temputs.getAllInterfaces().foreach(interface => {
              if (interface.mutability == ImmutableT) {
                destructorTemplar.getDropFunction(globalEnv, temputs, CoordT(ShareT, ReadonlyT, interface.getRef))
                destructorTemplar.getFreeFunction(globalEnv, temputs, CoordT(ShareT, ReadonlyT, interface.getRef))
              }
            })
            temputs.getAllRuntimeSizedArrays().foreach(rsa => {
              if (rsa.mutability == ImmutableT) {
                destructorTemplar.getDropFunction(globalEnv, temputs, CoordT(ShareT, ReadonlyT, rsa))
                destructorTemplar.getFreeFunction(globalEnv, temputs, CoordT(ShareT, ReadonlyT, rsa))
              }
            })
            temputs.getAllStaticSizedArrays().foreach(ssa => {
              if (ssa.mutability == ImmutableT) {
                destructorTemplar.getDropFunction(globalEnv, temputs, CoordT(ShareT, ReadonlyT, ssa))
                destructorTemplar.getFreeFunction(globalEnv, temputs, CoordT(ShareT, ReadonlyT, ssa))
              }
            })

            val stampedOverrides =
              profiler.newProfile("StampOverridesUntilSettledProbe", "", () => {

                // right now we're just assuming global env, but it might not be there...
                // perhaps look in the struct's env and the function's env? cant think of where else to look.
                opts.debugOut("which envs do we look in?")
                val env =
                  PackageEnvironment.makeTopLevelEnvironment(
                    globalEnv, FullNameT(PackageCoordinate.BUILTIN, Vector(), PackageTopLevelNameT()))
                stampKnownNeededOverrides(env, temputs)
              })

            var deferredFunctionsEvaluated = 0
            while (temputs.peekNextDeferredEvaluatingFunction().nonEmpty) {
              val nextDeferredEvaluatingFunction = temputs.peekNextDeferredEvaluatingFunction().get
              deferredFunctionsEvaluated += 1
              // No, IntelliJ, I assure you this has side effects
              (nextDeferredEvaluatingFunction.call) (temputs)
              temputs.markDeferredFunctionEvaluated(nextDeferredEvaluatingFunction.prototypeT)
            }

            if (stampedOverrides + deferredFunctionsEvaluated == 0)
              break
          }
        }

        val edgeBlueprints = EdgeTemplar.makeInterfaceEdgeBlueprints(temputs)
        val partialEdges = EdgeTemplar.assemblePartialEdges(temputs)
        val edges =
          partialEdges.map({ case PartialEdgeT(struct, interface, methods) =>
            EdgeT(
              struct,
              interface,
              methods.map({
                case FoundFunction(prototype) => prototype
                case NeededOverride(_, _) => vwat()
              })
            )
          })

        // NEVER ZIP TWO SETS TOGETHER
        val edgeBlueprintsAsList = edgeBlueprints.toVector
        val edgeBlueprintsByInterface = edgeBlueprintsAsList.map(_.interface).zip(edgeBlueprintsAsList).toMap;

        edgeBlueprintsByInterface.foreach({ case (interfaceTT, edgeBlueprint) =>
          vassert(edgeBlueprint.interface == interfaceTT)
        })

        ensureDeepExports(temputs)

        profiler.newProfile("Reachability", "", () => {
          val reachables = Reachability.findReachables(temputs, edgeBlueprintsAsList, edges)

          val categorizedFunctions = temputs.getAllFunctions().groupBy(f => reachables.functions.contains(f.header.toSignature))
          val reachableFunctions = categorizedFunctions.getOrElse(true, Vector.empty)
          val unreachableFunctions = categorizedFunctions.getOrElse(false, Vector.empty)
          unreachableFunctions.foreach(f => debugOut("Shaking out unreachable: " + f.header.fullName))
          reachableFunctions.foreach(f => debugOut("Including: " + f.header.fullName))

          val categorizedSSAs = temputs.getAllStaticSizedArrays().groupBy(f => reachables.staticSizedArrays.contains(f))
          val reachableSSAs = categorizedSSAs.getOrElse(true, Vector.empty)
          val unreachableSSAs = categorizedSSAs.getOrElse(false, Vector.empty)
          unreachableSSAs.foreach(f => debugOut("Shaking out unreachable: " + f))
          reachableSSAs.foreach(f => debugOut("Including: " + f))

          val categorizedRSAs = temputs.getAllRuntimeSizedArrays().groupBy(f => reachables.runtimeSizedArrays.contains(f))
          val reachableRSAs = categorizedRSAs.getOrElse(true, Vector.empty)
          val unreachableRSAs = categorizedRSAs.getOrElse(false, Vector.empty)
          unreachableRSAs.foreach(f => debugOut("Shaking out unreachable: " + f))
          reachableRSAs.foreach(f => debugOut("Including: " + f))

          val categorizedStructs = temputs.getAllStructs().groupBy(f => reachables.structs.contains(f.getRef))
          val reachableStructs = categorizedStructs.getOrElse(true, Vector.empty)
          val unreachableStructs = categorizedStructs.getOrElse(false, Vector.empty)
          unreachableStructs.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
          reachableStructs.foreach(f => debugOut("Including: " + f.fullName))

          val categorizedInterfaces = temputs.getAllInterfaces().groupBy(f => reachables.interfaces.contains(f.getRef))
          val reachableInterfaces = categorizedInterfaces.getOrElse(true, Vector.empty)
          val unreachableInterfaces = categorizedInterfaces.getOrElse(false, Vector.empty)
          unreachableInterfaces.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
          reachableInterfaces.foreach(f => debugOut("Including: " + f.fullName))

          val categorizedEdges = edges.groupBy(f => reachables.edges.contains(f))
          val reachableEdges = categorizedEdges.getOrElse(true, Vector.empty)
          val unreachableEdges = categorizedEdges.getOrElse(false, Vector.empty)
          unreachableEdges.foreach(f => debugOut("Shaking out unreachable: " + f))
          reachableEdges.foreach(f => debugOut("Including: " + f))

          val allKinds =
            reachableStructs.map(_.getRef) ++ reachableInterfaces.map(_.getRef) ++ reachableSSAs ++ reachableRSAs
          val reachableImmKinds: Vector[KindT] =
            allKinds
              .filter({
                case s@StructTT(_) => temputs.lookupMutability(s) == ImmutableT
                case i@InterfaceTT(_) => temputs.lookupMutability(i) == ImmutableT
                case StaticSizedArrayTT(_, m, _, _) => m == ImmutableT
                case RuntimeSizedArrayTT(m, _) => m == ImmutableT
                case _ => true
              })
              .toVector
          val reachableImmKindToDestructor = reachableImmKinds.zip(reachableImmKinds.map(temputs.findImmDestructor)).toMap

          val hinputs =
            Hinputs(
              reachableInterfaces.toVector,
              reachableStructs.toVector,
              reachableFunctions.toVector,
              reachableImmKindToDestructor,
              edgeBlueprintsByInterface,
              edges,
              temputs.getKindExports,
              temputs.getFunctionExports,
              temputs.getKindExterns,
              temputs.getFunctionExterns)

          vassert(reachableFunctions.toVector.map(_.header.fullName).distinct.size == reachableFunctions.toVector.map(_.header.fullName).size)

          Ok(hinputs)
        })
      })
    } catch {
      case CompileErrorExceptionT(err) => Err(err)
    }
  }

  def ensureDeepExports(temputs: Temputs): Unit = {
    val packageToKindToExport =
      temputs.getKindExports
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

    temputs.getFunctionExports.foreach(funcExport => {
      val exportedKindToExport = packageToKindToExport.getOrElse(funcExport.packageCoordinate, Map())
      (Vector(funcExport.prototype.returnType) ++ funcExport.prototype.paramTypes)
        .foreach(paramType => {
          if (!Templar.isPrimitive(paramType.kind) && !exportedKindToExport.contains(paramType.kind)) {
            throw CompileErrorExceptionT(
              ExportedFunctionDependedOnNonExportedKind(
                funcExport.range, funcExport.packageCoordinate, funcExport.prototype.toSignature, paramType.kind))
          }
        })
    })
    temputs.getFunctionExterns.foreach(functionExtern => {
      val exportedKindToExport = packageToKindToExport.getOrElse(functionExtern.packageCoordinate, Map())
      (Vector(functionExtern.prototype.returnType) ++ functionExtern.prototype.paramTypes)
        .foreach(paramType => {
          if (!Templar.isPrimitive(paramType.kind) && !exportedKindToExport.contains(paramType.kind)) {
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
            val structDef = temputs.getStructDefForRef(sr)
            structDef.members.foreach({ case StructMemberT(_, _, member) =>
              val CoordT(_, _, memberKind) = member.reference
              if (structDef.mutability == ImmutableT && !Templar.isPrimitive(memberKind) && !exportedKindToExport.contains(memberKind)) {
                throw CompileErrorExceptionT(
                  ExportedImmutableKindDependedOnNonExportedKind(
                    export.range, packageCoord, exportedKind, memberKind))
              }
            })
          }
          case StaticSizedArrayTT(_, mutability, _, CoordT(_, _, elementKind)) => {
            if (mutability == ImmutableT && !Templar.isPrimitive(elementKind) && !exportedKindToExport.contains(elementKind)) {
              throw CompileErrorExceptionT(
                ExportedImmutableKindDependedOnNonExportedKind(
                  export.range, packageCoord, exportedKind, elementKind))
            }
          }
          case RuntimeSizedArrayTT(mutability, CoordT(_, _, elementKind)) => {
            if (mutability == ImmutableT && !Templar.isPrimitive(elementKind) && !exportedKindToExport.contains(elementKind)) {
              throw CompileErrorExceptionT(
                ExportedImmutableKindDependedOnNonExportedKind(
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
      case FunctionNameS("main", _) => return true
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

  // Returns the number of overrides stamped
  // This doesnt actually stamp *all* overrides, just the ones we can immediately
  // see missing. We don't know if, in the process of stamping these, we'll find more.
  // Also note, these don't stamp them right now, they defer them for later evaluating.
  def stampKnownNeededOverrides(env: PackageEnvironment[INameT], temputs: Temputs): Int = {
    val neededOverrides =
      profiler.childFrame("assemble partial edges", () => {
        val partialEdges = EdgeTemplar.assemblePartialEdges(temputs)
        partialEdges.flatMap(e => e.methods.flatMap({
          case n @ NeededOverride(_, _) => Vector(n)
          case FoundFunction(_) => Vector.empty
        }))
      })

    neededOverrides.foreach({
      case (neededOverride) => {
        overloadTemplar.findFunction(
          env,
          temputs,
          RangeS.internal(-1900),
          neededOverride.name,
          Vector.empty, // No explicitly specified ones. It has to be findable just by param filters.
          Array.empty,
          neededOverride.paramFilters,
          Vector.empty,
          true)
      }
    })

    neededOverrides.size
  }
}


object Templar {
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
          flattened.init.filter(_ != VoidLiteralTE()) :+ flattened.last

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

  def getMutabilities(temputs: Temputs, concreteValues2: Vector[KindT]):
  Vector[MutabilityT] = {
    concreteValues2.map(concreteValue2 => getMutability(temputs, concreteValue2))
  }

  def getMutability(temputs: Temputs, concreteValue2: KindT):
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
      case sr @ StructTT(_) => temputs.lookupMutability(sr)
      case ir @ InterfaceTT(_) => temputs.lookupMutability(ir)
//      case PackTT(_, sr) => temputs.lookupMutability(sr)
//      case TupleTT(_, sr) => temputs.lookupMutability(sr)
      case OverloadSet(_, _) => {
        // Just like FunctionT2
        ImmutableT
      }
    }
  }

  def intersectPermission(a: PermissionT, b: PermissionT): PermissionT = {
    (a, b) match {
      case (ReadonlyT, ReadonlyT) => ReadonlyT
      case (ReadonlyT, ReadwriteT) => ReadonlyT
      case (ReadwriteT, ReadonlyT) => ReadonlyT
      case (ReadwriteT, ReadwriteT) => ReadwriteT
    }
  }

  def factorVariabilityAndPermission(
    containerPermission: PermissionT,
    memberVariability: VariabilityT,
    memberPermission: PermissionT):
  (VariabilityT, PermissionT) = {
    val effectiveVariability =
      (containerPermission, memberVariability) match {
        case (ReadonlyT, FinalT) => FinalT
        case (ReadwriteT, FinalT) => FinalT
        case (ReadonlyT, VaryingT) => FinalT
        case (ReadwriteT, VaryingT) => VaryingT
      }

    val targetPermission = Templar.intersectPermission(containerPermission, memberPermission)
    (effectiveVariability, targetPermission)
  }
}
