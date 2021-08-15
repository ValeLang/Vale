package net.verdagon.vale.templar;

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.scout.{CodeLocationS, ICompileErrorS, ITemplexS, ProgramS, RangeS}
import net.verdagon.vale.templar.EdgeTemplar.{FoundFunction, NeededOverride, PartialEdgeT}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.citizen.{AncestorHelper, IAncestorHelperDelegate, IStructTemplarDelegate, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.{ExpressionTemplar, IExpressionTemplarDelegate}
import net.verdagon.vale.templar.types.{CoordT, _}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.function.{BuiltInFunctions, DestructorTemplar, FunctionTemplar, FunctionTemplarCore, IFunctionTemplarDelegate, VirtualTemplar}
import net.verdagon.vale.templar.infer.IInfererDelegate

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable
import scala.util.control.Breaks._
trait IFunctionGenerator {
  def generate(
    // These serve as the API that a function generator can use.
    // TODO: Give a trait with a reduced API.
    // Maybe this functionTemplarCore can be a lambda we can use to finalize and add *this* function.
    functionTemplarCore: FunctionTemplarCore,
    structTemplar: StructTemplar,
    destructorTemplar: DestructorTemplar,
    env: FunctionEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    // We might be able to move these all into the function environment... maybe....
    originFunction: Option[FunctionA],
    paramCoords: List[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT)
}

case class TemplarOptions(
  functionGeneratorByName: Map[String, IFunctionGenerator],
  debugOut: String => Unit = (x => {
    println("###: " + x)
  }),
  verboseErrors: Boolean = false,
  useOptimization: Boolean = false,
) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}



class Templar(debugOut: (String) => Unit, verbose: Boolean, profiler: IProfiler, useOptimization: Boolean) {
  val generatedFunctions =
    List(
      DestructorTemplar.addConcreteDestructor(MutableT),
      DestructorTemplar.addConcreteDestructor(ImmutableT),
      DestructorTemplar.addInterfaceDestructor(MutableT),
      DestructorTemplar.addInterfaceDestructor(ImmutableT),
      DestructorTemplar.addImplDestructor(MutableT),
      DestructorTemplar.addImplDestructor(ImmutableT),
      DestructorTemplar.addDrop(MutableT),
      DestructorTemplar.addDrop(ImmutableT))
  val generatorsById =
    StructTemplar.getFunctionGenerators() ++
    generatedFunctions.map({ case (functionA, generator) =>
      val id =
        functionA.body match {
          case GeneratedBodyA(generatorId) => generatorId
          case _ => vfail()
        }
      (id, generator)
    }).toMap +
    ("vale_same_instance" ->
      new IFunctionGenerator {
        override def generate(
          functionTemplarCore: FunctionTemplarCore,
          structTemplar: StructTemplar,
          destructorTemplar: DestructorTemplar,
          namedEnv: FunctionEnvironment,
          temputs: Temputs,
          callRange: RangeS,
          maybeOriginFunction1: Option[FunctionA],
          paramCoords: List[ParameterT],
          maybeReturnType2: Option[CoordT]):
        (FunctionHeaderT) = {
          val header =
            FunctionHeaderT(namedEnv.fullName, List.empty, paramCoords, maybeReturnType2.get, maybeOriginFunction1)
          temputs.declareFunctionReturnType(header.toSignature, header.returnType)
          temputs.addFunction(
            FunctionT(
              header,
              BlockTE(ReturnTE(IsSameInstanceTE(ArgLookupTE(0, paramCoords(0).tyype), ArgLookupTE(1, paramCoords(1).tyype))))))
          header
        }
      }) +
    ("vale_as_subtype" ->
      new IFunctionGenerator {
        override def generate(
          functionTemplarCore: FunctionTemplarCore,
          structTemplar: StructTemplar,
          destructorTemplar: DestructorTemplar,
          namedEnv: FunctionEnvironment,
          temputs: Temputs,
          callRange: RangeS,
          maybeOriginFunction1: Option[FunctionA],
          paramCoords: List[ParameterT],
          maybeReturnType2: Option[CoordT]):
        (FunctionHeaderT) = {
          val header =
            FunctionHeaderT(namedEnv.fullName, List.empty, paramCoords, maybeReturnType2.get, maybeOriginFunction1)
          temputs.declareFunctionReturnType(header.toSignature, header.returnType)

          val sourceKind = vassertSome(paramCoords.headOption).tyype.kind
          val KindTemplata(targetKind) = vassertSome(namedEnv.fullName.last.templateArgs.headOption)

          val sourceCitizen =
            sourceKind match {
              case c : CitizenRefT => c
              case _ => throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
            }

          val targetCitizen =
            targetKind match {
              case c : CitizenRefT => c
              case _ => throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
            }

          // We dont support downcasting to interfaces yet
          val targetStruct =
            targetCitizen match {
              case sr @ StructTT(_) => sr
              case ir @ InterfaceTT(_) => throw CompileErrorExceptionT(CantDowncastToInterface(callRange, ir))
              case _ => vfail()
            }


          val incomingCoord = paramCoords(0).tyype
          val incomingSubkind = incomingCoord.kind
          val targetCoord = incomingCoord.copy(kind = targetKind)
          val (resultCoord, okConstructor, errConstructor) =
            expressionTemplar.getResult(temputs, namedEnv, callRange, targetCoord, incomingCoord)
          val asSubtypeExpr: ReferenceExpressionTE =
            sourceCitizen match {
              case sourceInterface @ InterfaceTT(_) => {
                if (ancestorHelper.isAncestor(temputs, targetStruct, sourceInterface)) {
                  AsSubtypeTE(
                    ArgLookupTE(0, incomingCoord),
                    targetKind,
                    resultCoord,
                    okConstructor,
                    errConstructor)
                } else {
                  throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
                }
              }
              case sourceStruct @ StructTT(_) => {
                if (sourceStruct == targetStruct) {
                  FunctionCallTE(
                    okConstructor,
                    List(ArgLookupTE(0, incomingCoord)))
                } else {
                  throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
                }
              }
            }

          temputs.addFunction(FunctionT(header, BlockTE(ReturnTE(asSubtypeExpr))))

          header
        }
      })

  val opts = TemplarOptions(generatorsById, debugOut, verbose, useOptimization)

  val newTemplataStore: () => TemplatasStore =
    () => {
//      if (opts.useOptimization) {
        TemplatasStore(Map(), Map())
//      } else {
//        SimpleTemplatasIndex(Map())
//      }
    }

  val templataTemplar =
    new TemplataTemplar(
      opts,
      new ITemplataTemplarDelegate {
        override def getAncestorInterfaceDistance(temputs: Temputs, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): Option[Int] = {
          ancestorHelper.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }

        override def getStructRef(temputs: Temputs, callRange: RangeS,structTemplata: StructTemplata, uncoercedTemplateArgs: List[ITemplata]): StructTT = {
          structTemplar.getStructRef(temputs, callRange, structTemplata, uncoercedTemplateArgs)
        }

        override def getInterfaceRef(temputs: Temputs, callRange: RangeS,interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: List[ITemplata]): InterfaceTT = {
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
          arrayTemplar.getStaticSizedArrayKind(env, temputs, mutability, variability, size, type2)
        }

        override def getRuntimeSizedArrayKind(env: IEnvironment, state: Temputs, element: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT): RuntimeSizedArrayTT = {
          arrayTemplar.getRuntimeSizedArrayKind(env, state, element, arrayMutability, arrayVariability)
        }

        override def getTupleKind(env: IEnvironment, state: Temputs, elements: List[CoordT]): TupleTT = {
          val (tuple, mutability) = sequenceTemplar.makeTupleType(env, state, elements)
          tuple
        }
      })
  val inferTemplar: InferTemplar =
    new InferTemplar(
      opts,
      profiler,
      new IInfererDelegate[IEnvironment, Temputs] {
        override def evaluateType(
          env: IEnvironment,
          temputs: Temputs,
          type1: ITemplexA
        ): (ITemplata) = {
          profiler.childFrame("InferTemplarDelegate.evaluateType", () => {
            templataTemplar.evaluateTemplex(env, temputs, type1)
          })
        }

        override def lookupMemberTypes(state: Temputs, kind: KindT, expectedNumMembers: Int): Option[List[CoordT]] = {
          profiler.childFrame("InferTemplarDelegate.lookupMemberTypes", () => {
            val underlyingstructTT =
              kind match {
                case sr@StructTT(_) => sr
                case TupleTT(_, underlyingStruct) => underlyingStruct
                case PackTT(_, underlyingStruct) => underlyingStruct
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

        override def lookupTemplata(env: IEnvironment, range: RangeS, name: INameT): ITemplata = {
          profiler.childFrame("InferTemplarDelegate.lookupTemplata", () => {
            // We can only ever lookup types by name in expression context,
            // otherwise we have no idea what List<Str> means; it could
            // mean a list of strings or a list of the Str(:Int)Str function.
            env.getNearestTemplataWithAbsoluteName2(name, Set[ILookupContext](TemplataLookupContext)) match {
              case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't find anything with name: " + name))
              case Some(x) => x
            }
          })
        }

        override def lookupTemplataImprecise(env: IEnvironment, range: RangeS, name: IImpreciseNameStepA): ITemplata = {
          profiler.childFrame("InferTemplarDelegate.lookupTemplataImprecise", () => {
            env.getNearestTemplataWithName(name, Set[ILookupContext](TemplataLookupContext)) match {
              case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't find anything with name: " + name))
              case Some(x) => x
            }
          })
        }

        //      override def getPackKind(env: IEnvironment, state: Temputs, members: List[Coord]): (PackT2, Mutability) = {
        //        PackTemplar.makePackType(env.globalEnv, state, members)
        //      }

        override def getStaticSizedArrayKind(env: IEnvironment, state: Temputs, mutability: MutabilityT, variability: VariabilityT, size: Int, element: CoordT): (StaticSizedArrayTT) = {
          profiler.childFrame("InferTemplarDelegate.getStaticSizedArrayKind", () => {
            arrayTemplar.getStaticSizedArrayKind(env, state, mutability, variability, size, element)
          })
        }

        override def getRuntimeSizedArrayKind(env: IEnvironment, state: Temputs, element: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT): RuntimeSizedArrayTT = {
          profiler.childFrame("InferTemplarDelegate.getRuntimeSizedArrayKind", () => {
            arrayTemplar.getRuntimeSizedArrayKind(env, state, element, arrayMutability, arrayVariability)
          })
        }

        override def getTupleKind(env: IEnvironment, state: Temputs, elements: List[CoordT]): TupleTT = {
          profiler.childFrame("InferTemplarDelegate.getTupleKind", () => {
            val (tuple, mutability) = sequenceTemplar.makeTupleType(env, state, elements)
            tuple
          })
        }

        override def evaluateInterfaceTemplata(
          state: Temputs,
          callRange: RangeS,
          templata: InterfaceTemplata,
          templateArgs: List[ITemplata]):
        (KindT) = {
          profiler.childFrame("InferTemplarDelegate.evaluateInterfaceTemplata", () => {
            structTemplar.getInterfaceRef(state, callRange, templata, templateArgs)
          })
        }

        override def evaluateStructTemplata(
          state: Temputs,
          callRange: RangeS,
          templata: StructTemplata,
          templateArgs: List[ITemplata]):
        (KindT) = {
          profiler.childFrame("InferTemplarDelegate.evaluateStructTemplata", () => {
            structTemplar.getStructRef(state, callRange, templata, templateArgs)
          })
        }

        override def getAncestorInterfaceDistance(temputs: Temputs, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT):
        (Option[Int]) = {
          profiler.childFrame("InferTemplarDelegate.getAncestorInterfaceDistance", () => {
            ancestorHelper.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
          })
        }

        override def getAncestorInterfaces(temputs: Temputs, descendantCitizenRef: CitizenRefT): (Set[InterfaceTT]) = {
          profiler.childFrame("InferTemplarDelegate.getAncestorInterfaces", () => {
            ancestorHelper.getAncestorInterfaces(temputs, descendantCitizenRef)
          })
        }

        override def getMemberCoords(state: Temputs, structTT: StructTT): List[CoordT] = {
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

        override def resolveExactSignature(env: IEnvironment, state: Temputs, range: RangeS, name: String, coords: List[CoordT]): PrototypeT = {
          profiler.childFrame("InferTemplarDelegate.resolveExactSignature", () => {
            overloadTemplar.scoutExpectedFunctionForPrototype(env, state, range, GlobalFunctionFamilyNameA(name), List.empty, coords.map(ParamFilter(_, None)), List.empty, true) match {
              case sef@ScoutExpectedFunctionFailure(humanName, args, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction) => {
                throw new CompileErrorExceptionT(CouldntFindFunctionToCallT(range, sef))
                throw CompileErrorExceptionT(RangedInternalErrorT(range, sef.toString))
              }
              case ScoutExpectedFunctionSuccess(prototype) => prototype
            }
          })
        }
      })
  val convertHelper =
    new ConvertHelper(
      opts,
      new IConvertHelperDelegate {
        override def isAncestor(temputs: Temputs, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): Boolean = {
          ancestorHelper.isAncestor(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }
      })

  val ancestorHelper: AncestorHelper =
    new AncestorHelper(opts, profiler, inferTemplar, new IAncestorHelperDelegate {
      override def getInterfaceRef(temputs: Temputs, callRange: RangeS, interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: List[ITemplata]): InterfaceTT = {
        structTemplar.getInterfaceRef(temputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
      }
    })

  val structTemplar: StructTemplar =
    new StructTemplar(
      opts,
      profiler,
      newTemplataStore,
      inferTemplar,
      ancestorHelper,
      new IStructTemplarDelegate {
        override def evaluateOrdinaryFunctionFromNonCallForHeader(temputs: Temputs, callRange: RangeS,functionTemplata: FunctionTemplata): FunctionHeaderT = {
          functionTemplar.evaluateOrdinaryFunctionFromNonCallForHeader(temputs, callRange, functionTemplata)
        }

        override def scoutExpectedFunctionForPrototype(
          env: IEnvironment, temputs: Temputs, callRange: RangeS, functionName: IImpreciseNameStepA, explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS], args: List[ParamFilter], extraEnvsToLookIn: List[IEnvironment], exact: Boolean):
        OverloadTemplar.IScoutExpectedFunctionResult = {
          overloadTemplar.scoutExpectedFunctionForPrototype(env, temputs, callRange, functionName, explicitlySpecifiedTemplateArgTemplexesS, args, extraEnvsToLookIn, exact)
        }

        override def makeImmConcreteDestructor(temputs: Temputs, env: IEnvironment, structTT: StructTT): PrototypeT = {
          destructorTemplar.getImmConcreteDestructor(temputs, env, structTT)
        }

        override def getImmInterfaceDestructorOverride(temputs: Temputs, env: IEnvironment, structTT: StructTT, implementedInterfaceRefT: InterfaceTT): PrototypeT = {
          destructorTemplar.getImmInterfaceDestructorOverride(temputs, env, structTT, implementedInterfaceRefT)
        }

        override def getImmInterfaceDestructor(temputs: Temputs, env: IEnvironment, interfaceTT: InterfaceTT): PrototypeT = {
          destructorTemplar.getImmInterfaceDestructor(temputs, env, interfaceTT)
        }

        override def getImmConcreteDestructor(temputs: Temputs, env: IEnvironment, structTT: StructTT): PrototypeT = {
          destructorTemplar.getImmConcreteDestructor(temputs, env, structTT)
        }
      })

  val functionTemplar: FunctionTemplar =
    new FunctionTemplar(opts, profiler, newTemplataStore, templataTemplar, inferTemplar, convertHelper, structTemplar,
      new IFunctionTemplarDelegate {
    override def evaluateBlockStatements(
        temputs: Temputs,
        startingFate: FunctionEnvironment,
        fate: FunctionEnvironmentBox,
        exprs: List[IExpressionAE]
    ): (ReferenceExpressionTE, Set[CoordT]) = {
      expressionTemplar.evaluateBlockStatements(temputs, startingFate, fate, exprs)
    }

    override def translatePatternList(temputs: Temputs, fate: FunctionEnvironmentBox, patterns1: List[AtomAP], patternInputExprs2: List[ReferenceExpressionTE]): ReferenceExpressionTE = {
      expressionTemplar.translatePatternList(temputs, fate, patterns1, patternInputExprs2)
    }

    override def evaluateParent(env: IEnvironment, temputs: Temputs, sparkHeader: FunctionHeaderT): Unit = {
      virtualTemplar.evaluateParent(env, temputs, sparkHeader)
    }

    override def generateFunction(
      functionTemplarCore: FunctionTemplarCore,
      generator: IFunctionGenerator,
      fullEnv: FunctionEnvironment,
      temputs: Temputs,
      callRange: RangeS,
      originFunction: Option[FunctionA],
      paramCoords: List[ParameterT],
      maybeRetCoord: Option[CoordT]):
    FunctionHeaderT = {
      generator.generate(
        functionTemplarCore, structTemplar, destructorTemplar, fullEnv, temputs, callRange, originFunction, paramCoords, maybeRetCoord)
    }
  })
  val overloadTemplar: OverloadTemplar = new OverloadTemplar(opts, profiler, templataTemplar, inferTemplar, functionTemplar)
  val destructorTemplar: DestructorTemplar = new DestructorTemplar(opts, structTemplar, overloadTemplar)

  val virtualTemplar = new VirtualTemplar(opts, overloadTemplar)

  val sequenceTemplar = new SequenceTemplar(opts, structTemplar, destructorTemplar)

  val arrayTemplar =
    new ArrayTemplar(
      opts,
      new IArrayTemplarDelegate {
        def getArrayDestructor(
          env: IEnvironment,
          temputs: Temputs,
          type2: CoordT):
        (PrototypeT) = {
          destructorTemplar.getArrayDestructor(env, temputs, type2)
        }
      },
      inferTemplar,
      overloadTemplar)

  val expressionTemplar: ExpressionTemplar =
    new ExpressionTemplar(
      opts,
      profiler,
      newTemplataStore,
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
        override def evaluateTemplatedFunctionFromCallForPrototype(temputs: Temputs, callRange: RangeS, functionTemplata: FunctionTemplata, explicitTemplateArgs: List[ITemplata], args: List[ParamFilter]): FunctionTemplar.IEvaluateFunctionResult[PrototypeT] = {
          functionTemplar.evaluateTemplatedFunctionFromCallForPrototype(temputs, callRange, functionTemplata, explicitTemplateArgs, args)
        }

        override def evaluateClosureStruct(temputs: Temputs, containingFunctionEnv: FunctionEnvironment, callRange: RangeS, name: LambdaNameA, function1: BFunctionA): StructTT = {
          functionTemplar.evaluateClosureStruct(temputs, containingFunctionEnv, callRange, name, function1)
        }
      })

  def evaluate(packageToProgramA: PackageCoordinateMap[ProgramA]): Result[Hinputs, ICompileErrorT] = {
    try {
      profiler.newProfile("Templar.evaluate", "", () => {
        val programsA = packageToProgramA.moduleToPackagesToContents.values.flatMap(_.values)

        val structsA = programsA.flatMap(_.structs)
        val interfacesA = programsA.flatMap(_.interfaces)
        val implsA = programsA.flatMap(_.impls)
        val functionsA = programsA.flatMap(_.functions)
        val exportsA = programsA.flatMap(_.exports)

        val env0 =
          PackageEnvironment(
            None,
            FullNameT(PackageCoordinate.BUILTIN, List.empty, PackageTopLevelNameT()),
            newTemplataStore()
                .addEntries(
                  opts.useOptimization,
                  Map(
                    PrimitiveNameT("int") -> List(TemplataEnvEntry(KindTemplata(IntT.i32))),
                    PrimitiveNameT("i64") -> List(TemplataEnvEntry(KindTemplata(IntT.i64))),
                    PrimitiveNameT("Array") -> List(TemplataEnvEntry(ArrayTemplateTemplata())),
                    PrimitiveNameT("bool") -> List(TemplataEnvEntry(KindTemplata(BoolT()))),
                    PrimitiveNameT("float") -> List(TemplataEnvEntry(KindTemplata(FloatT()))),
                    PrimitiveNameT("__Never") -> List(TemplataEnvEntry(KindTemplata(NeverT()))),
                    PrimitiveNameT("str") -> List(TemplataEnvEntry(KindTemplata(StrT()))),
                    PrimitiveNameT("void") -> List(TemplataEnvEntry(KindTemplata(VoidT()))))))
        val env1b =
          BuiltInFunctions.builtIns.foldLeft(env0)({
            case (env1a, builtIn) => {
              env1a.addUnevaluatedFunction(opts.useOptimization, builtIn)
            }
          })
        val env3 =
          generatedFunctions.foldLeft(env1b)({
            case (env2, (generatedFunction, generator)) => {
              env2.addUnevaluatedFunction(opts.useOptimization, generatedFunction)
            }
          })

        // This has to come before the structs and interfaces because part of evaluating a
        // struct or interface is figuring out what it extends.
        val env5 =
        implsA.foldLeft(env3)({
          case (env4, impl1) => env4.addEntry(opts.useOptimization, NameTranslator.translateImplName(impl1.name), ImplEnvEntry(impl1))
        })
        val env7 =
          structsA.foldLeft(env5)({
            case (env6, s) => makeStructEnvironmentEntries(env6, s)
          })
        val env9 =
          interfacesA.foldLeft(env7)({
            case (env8, interfaceA) => addInterfaceEnvironmentEntries(env8, interfaceA)
          })

        val env11 =
          functionsA.foldLeft(env9)({
            case (env10, functionS) => {
              env10.addUnevaluatedFunction(opts.useOptimization, functionS)
            }
          })

        val temputs = Temputs()

        structTemplar.addBuiltInStructs(env11, temputs)

        functionsA.foreach({
          case (functionS) => {
            if (functionS.isTemplate) {
              // Do nothing, it's a template
            } else {
              if (isRootFunction(functionS)) {
                val _ =
                  functionTemplar.evaluateOrdinaryFunctionFromNonCallForPrototype(
                    temputs, RangeS.internal(-177), FunctionTemplata.make(env11, functionS))
              }
            }
          }
        })

        structsA.foreach({
          case (structS) => {
            if (structS.isTemplate) {
              // Do nothing, it's a template
            } else {
              if (isRootStruct(structS)) {
                val _ =
                  structTemplar.getStructRef(
                    temputs, structS.range, StructTemplata.make(env11, structS), List.empty)
              }
            }
          }
        })

        interfacesA.foreach({
          case (interfaceS) => {
            if (interfaceS.isTemplate) {
              // Do nothing, it's a template
            } else {
              if (isRootInterface(interfaceS)) {
                val _ =
                  structTemplar.getInterfaceRef(
                    temputs, interfaceS.range, InterfaceTemplata.make(env11, interfaceS), List.empty)
              }
            }
          }
        })

        exportsA.foreach({ case ExportAsA(range, exportedName, rules, typeByRune, typeRuneA) =>
          val typeRuneT = NameTranslator.translateRune(typeRuneA)
          val templataByRune =
            inferTemplar.inferOrdinaryRules(env11, temputs, rules, typeByRune, Set(typeRuneA))
          val kind =
            templataByRune.get(typeRuneT) match {
              case Some(KindTemplata(kind)) => {
                temputs.addKindExport(range, kind, range.file.packageCoordinate, exportedName)
              }
              case Some(PrototypeTemplata(prototype)) => {
                vimpl()
              }
              case _ => vfail()
            }
        })

        breakable {
          while (true) {
            temputs.getAllStructs().foreach(struct => {
              if (struct.mutability == ImmutableT && struct.getRef != Program2.emptyTupleStructRef) {
                temputs.getDestructor(struct.getRef)
              }
            })
            temputs.getAllInterfaces().foreach(interface => {
              if (interface.mutability == ImmutableT) {
                temputs.getDestructor(interface.getRef)
              }
            })
            temputs.getAllRuntimeSizedArrays().foreach(rsa => {
              if (rsa.array.mutability == ImmutableT) {
                temputs.getDestructor(rsa)
              }
            })
            temputs.getAllStaticSizedArrays().foreach(ssa => {
              if (ssa.array.mutability == ImmutableT) {
                temputs.getDestructor(ssa)
              }
            })

            val stampedOverrides =
              profiler.newProfile("StampOverridesUntilSettledProbe", "", () => {
                stampKnownNeededOverrides(env11, temputs)
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
        val edgeBlueprintsAsList = edgeBlueprints.toList
        val edgeBlueprintsByInterface = edgeBlueprintsAsList.map(_.interface).zip(edgeBlueprintsAsList).toMap;

        edgeBlueprintsByInterface.foreach({ case (interfaceTT, edgeBlueprint) =>
          vassert(edgeBlueprint.interface == interfaceTT)
        })

        ensureDeepExports(temputs)

        val reachables = Reachability.findReachables(temputs, edgeBlueprintsAsList, edges)

        val categorizedFunctions = temputs.getAllFunctions().groupBy(f => reachables.functions.contains(f.header.toSignature))
        val reachableFunctions = categorizedFunctions.getOrElse(true, List.empty)
        val unreachableFunctions = categorizedFunctions.getOrElse(false, List.empty)
        unreachableFunctions.foreach(f => debugOut("Shaking out unreachable: " + f.header.fullName))
        reachableFunctions.foreach(f => debugOut("Including: " + f.header.fullName))

        val categorizedSSAs = temputs.getAllStaticSizedArrays().groupBy(f => reachables.staticSizedArrays.contains(f))
        val reachableSSAs = categorizedSSAs.getOrElse(true, List.empty)
        val unreachableSSAs = categorizedSSAs.getOrElse(false, List.empty)
        unreachableSSAs.foreach(f => debugOut("Shaking out unreachable: " + f))
        reachableSSAs.foreach(f => debugOut("Including: " + f))

        val categorizedRSAs = temputs.getAllRuntimeSizedArrays().groupBy(f => reachables.runtimeSizedArrays.contains(f))
        val reachableRSAs = categorizedRSAs.getOrElse(true, List.empty)
        val unreachableRSAs = categorizedRSAs.getOrElse(false, List.empty)
        unreachableRSAs.foreach(f => debugOut("Shaking out unreachable: " + f))
        reachableRSAs.foreach(f => debugOut("Including: " + f))

        val categorizedStructs = temputs.getAllStructs().groupBy(f => reachables.structs.contains(f.getRef))
        val reachableStructs = categorizedStructs.getOrElse(true, List.empty)
        val unreachableStructs = categorizedStructs.getOrElse(false, List.empty)
        unreachableStructs.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
        reachableStructs.foreach(f => debugOut("Including: " + f.fullName))

        val categorizedInterfaces = temputs.getAllInterfaces().groupBy(f => reachables.interfaces.contains(f.getRef))
        val reachableInterfaces = categorizedInterfaces.getOrElse(true, List.empty)
        val unreachableInterfaces = categorizedInterfaces.getOrElse(false, List.empty)
        unreachableInterfaces.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
        reachableInterfaces.foreach(f => debugOut("Including: " + f.fullName))

        val categorizedEdges = edges.groupBy(f => reachables.edges.contains(f))
        val reachableEdges = categorizedEdges.getOrElse(true, List.empty)
        val unreachableEdges = categorizedEdges.getOrElse(false, List.empty)
        unreachableEdges.foreach(f => debugOut("Shaking out unreachable: " + f))
        reachableEdges.foreach(f => debugOut("Including: " + f))

        val reachableKinds = (reachableStructs.map(_.getRef) ++ reachableInterfaces.map(_.getRef) ++ reachableSSAs ++ reachableRSAs).toSet[KindT]
        val categorizedDestructors = temputs.getKindToDestructorMap().groupBy({ case (kind, destructor) => reachableKinds.contains(kind) })
        val reachableDestructors = categorizedDestructors.getOrElse(true, List.empty)
        val unreachableDestructors = categorizedDestructors.getOrElse(false, List.empty)
        unreachableDestructors.foreach(f => debugOut("Shaking out unreachable: " + f))
        reachableDestructors.foreach(f => debugOut("Including: " + f))

        val hinputs =
          Hinputs(
            reachableInterfaces.toList,
            reachableStructs.toList,
            Program2.emptyTupleStructRef,
            reachableFunctions.toList,
            reachableDestructors.toMap,
            edgeBlueprintsByInterface,
            edges,
            temputs.getKindExports,
            temputs.getFunctionExports,
            temputs.getKindExterns,
            temputs.getFunctionExterns)

        Ok(hinputs)
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
              case Nil => vwat()
              case List(only) => only
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
      (funcExport.prototype.returnType :: funcExport.prototype.paramTypes)
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
      (functionExtern.prototype.returnType :: functionExtern.prototype.paramTypes)
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
          case StaticSizedArrayTT(_, RawArrayTT(CoordT(_, _, elementKind), mutability, _)) => {
            if (mutability == ImmutableT && !Templar.isPrimitive(elementKind) && !exportedKindToExport.contains(elementKind)) {
              throw CompileErrorExceptionT(
                ExportedImmutableKindDependedOnNonExportedKind(
                  export.range, packageCoord, exportedKind, elementKind))
            }
          }
          case RuntimeSizedArrayTT(RawArrayTT(CoordT(_, _, elementKind), mutability, _)) => {
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
      case FunctionNameA("main", _) => return true
      case _ =>
    }
    functionA.attributes.exists({
      case ExportA(_) => true
      case ExternA(_) => true
      case _ => false
    })
  }

  // Returns whether we should eagerly compile this and anything it depends on.
  def isRootStruct(structA: StructA): Boolean = {
    structA.attributes.exists({ case ExportA(_) => true case _ => false })
  }

  // Returns whether we should eagerly compile this and anything it depends on.
  def isRootInterface(interfaceA: InterfaceA): Boolean = {
    interfaceA.attributes.exists({ case ExportA(_) => true case _ => false })
  }

  // (Once we add packages, this will probably change)
  def addInterfaceEnvironmentEntries(
    env0: PackageEnvironment[PackageTopLevelNameT],
    interfaceA: InterfaceA
  ): PackageEnvironment[PackageTopLevelNameT] = {
    val interfaceEnvEntry = InterfaceEnvEntry(interfaceA)

    val TopLevelCitizenDeclarationNameA(humanName, codeLocationS) = interfaceA.name
    val name = CitizenTemplateNameT(humanName, NameTranslator.translateCodeLocation(codeLocationS))

    val env1 = env0.addEntry(opts.useOptimization, name, interfaceEnvEntry)
    val env2 =
      env1.addUnevaluatedFunction(
        opts.useOptimization,
        structTemplar.getInterfaceConstructor(interfaceA))

    // Once we have sub-interfaces and sub-structs, we could recursively call this function.
    // We'll put our interfaceA onto the top of the list of every entry from the sub-struct/sub-interface.

    env2
  }

  // (Once we add packages, this will probably change)
  def makeStructEnvironmentEntries(
    env0: PackageEnvironment[PackageTopLevelNameT],
    structA: StructA
  ): PackageEnvironment[PackageTopLevelNameT] = {
    val interfaceEnvEntry = StructEnvEntry(structA)

    val env1 = env0.addEntry(opts.useOptimization, NameTranslator.translateNameStep(structA.name), interfaceEnvEntry)
    val env2 =
      env1.addUnevaluatedFunction(
        opts.useOptimization,
        structTemplar.getConstructor(structA))

    // To add once we have methods inside structs:
    //    val env4 =
    //      structA.internalMethods.foldLeft(env2)({
    //        case (env3, internalMethodA) => EnvironmentUtils.addFunction(env3, Some(interfaceEnvEntry), internalMethodA)
    //      })

    // Once we have sub-interfaces and sub-structs, we could recursively call this function.
    // We'll put our structA onto the top of the list of every entry from the sub-struct/sub-interface.

    env2
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
          case n @ NeededOverride(_, _) => List(n)
          case FoundFunction(_) => List.empty
        }))
      })

    // right now we're just assuming global env, but it might not be there...
    // perhaps look in the struct's env and the function's env? cant think of where else to look.
    opts.debugOut("which envs do we look in?")

    neededOverrides.foreach({
      case (neededOverride) => {
        overloadTemplar.scoutExpectedFunctionForPrototype(
          env,
          temputs,
          RangeS.internal(-1900),
          neededOverride.name,
          List.empty, // No explicitly specified ones. It has to be findable just by param filters.
          neededOverride.paramFilters,
          List.empty,
          true) match {
          case (seff@ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
            throw CompileErrorExceptionT(RangedInternalErrorT(RangeS.internal(-1674), "Couldn't find function for vtable!\n" + seff.toString))
          }
          case (ScoutExpectedFunctionSuccess(_)) =>
        }
      }
    })

    neededOverrides.size
  }
}

object Templar {
  // Flattens any nested ConsecutorTEs
  def consecutive(exprs: List[ReferenceExpressionTE]): ReferenceExpressionTE = {
    exprs match {
      case Nil => vwat("Shouldn't have zero-element consecutors!")
      case List(only) => only
      case _ => {
        ConsecutorTE(
          exprs.flatMap({
            case ConsecutorTE(exprs) => exprs
            case other => List(other)
          }))
      }
    }
  }

  def isPrimitive(kind: KindT): Boolean = {
    kind match {
      case VoidT() | IntT(_) | BoolT() | StrT() | NeverT() | FloatT() => true
      case TupleTT(_, understruct) => isPrimitive(understruct)
      case sr @ StructTT(_) => sr == Program2.emptyTupleStructRef
      case InterfaceTT(_) => false
      case StaticSizedArrayTT(_, _) => false
      case RuntimeSizedArrayTT(_) => false
    }
  }

  def getMutabilities(temputs: Temputs, concreteValues2: List[KindT]):
  List[MutabilityT] = {
    concreteValues2.map(concreteValue2 => getMutability(temputs, concreteValue2))
  }

  def getMutability(temputs: Temputs, concreteValue2: KindT):
  MutabilityT = {
    concreteValue2 match {
      case NeverT() => ImmutableT
      case IntT(_) => ImmutableT
      case FloatT() => ImmutableT
      case BoolT() => ImmutableT
      case StrT() => ImmutableT
      case VoidT() => ImmutableT
      case RuntimeSizedArrayTT(RawArrayTT(_, mutability, _)) => mutability
      case StaticSizedArrayTT(_, RawArrayTT(_, mutability, _)) => mutability
      case sr @ StructTT(_) => temputs.lookupMutability(sr)
      case ir @ InterfaceTT(_) => temputs.lookupMutability(ir)
      case PackTT(_, sr) => temputs.lookupMutability(sr)
      case TupleTT(_, sr) => temputs.lookupMutability(sr)
      case OverloadSet(_, _, _) => {
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
