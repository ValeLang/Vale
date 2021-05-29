package net.verdagon.vale.templar;

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.scout.{CodeLocationS, ICompileErrorS, ITemplexS, ProgramS, RangeS}
import net.verdagon.vale.templar.EdgeTemplar.{FoundFunction, NeededOverride, PartialEdge2}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.citizen.{AncestorHelper, IAncestorHelperDelegate, IStructTemplarDelegate, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.{ExpressionTemplar, IExpressionTemplarDelegate}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.function.{BuiltInFunctions, DestructorTemplar, DropHelper, FunctionTemplar, FunctionTemplarCore, IFunctionTemplarDelegate, VirtualTemplar}
import net.verdagon.vale.templar.infer.IInfererDelegate

import scala.collection.immutable.{List, ListMap, Map, Set}
import scala.collection.mutable

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
    paramCoords: List[Parameter2],
    maybeRetCoord: Option[Coord]):
  (FunctionHeader2)
}

case class TemplarOptions(
  functionGeneratorByName: Map[String, IFunctionGenerator],
  debugOut: String => Unit = (x => {
    println("###: " + x)
  }),
  verboseErrors: Boolean = false,
  useOptimization: Boolean = false,
)



class Templar(debugOut: (String) => Unit, verbose: Boolean, profiler: IProfiler, useOptimization: Boolean) {
  val generatedFunctions =
    List(
      DestructorTemplar.addConcreteDestructor(Mutable),
      DestructorTemplar.addConcreteDestructor(Immutable),
      DestructorTemplar.addInterfaceDestructor(Mutable),
      DestructorTemplar.addInterfaceDestructor(Immutable),
      DestructorTemplar.addImplDestructor(Mutable),
      DestructorTemplar.addImplDestructor(Immutable),
      DestructorTemplar.addDrop(Mutable),
      DestructorTemplar.addDrop(Immutable))
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
          paramCoords: List[Parameter2],
          maybeReturnType2: Option[Coord]):
        (FunctionHeader2) = {
          val header =
            FunctionHeader2(namedEnv.fullName, List(), paramCoords, maybeReturnType2.get, maybeOriginFunction1)
          temputs.declareFunctionReturnType(header.toSignature, header.returnType)
          temputs.addFunction(
            Function2(
              header,
              List(),
              Block2(List(Return2(IsSameInstance2(ArgLookup2(0, paramCoords(0).tyype), ArgLookup2(1, paramCoords(1).tyype)))))))
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
          paramCoords: List[Parameter2],
          maybeReturnType2: Option[Coord]):
        (FunctionHeader2) = {
          val header =
            FunctionHeader2(namedEnv.fullName, List(), paramCoords, maybeReturnType2.get, maybeOriginFunction1)
          temputs.declareFunctionReturnType(header.toSignature, header.returnType)

          val sourceKind = vassertSome(paramCoords.headOption).tyype.referend
          val KindTemplata(targetKind) = vassertSome(namedEnv.fullName.last.templateArgs.headOption)

          val sourceCitizen =
            sourceKind match {
              case c : CitizenRef2 => c
              case _ => throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
            }

          val targetCitizen =
            targetKind match {
              case c : CitizenRef2 => c
              case _ => throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
            }

          // We dont support downcasting to interfaces yet
          val targetStruct =
            targetCitizen match {
              case sr @ StructRef2(_) => sr
              case ir @ InterfaceRef2(_) => throw CompileErrorExceptionT(CantDowncastToInterface(callRange, ir))
              case _ => vfail()
            }


          val incomingCoord = paramCoords(0).tyype
          val incomingSubkind = incomingCoord.referend
          val targetCoord = incomingCoord.copy(referend = targetKind)
          val (resultCoord, okConstructor, errConstructor) =
            expressionTemplar.getResult(temputs, namedEnv, callRange, targetCoord, incomingCoord)
          val asSubtypeExpr: ReferenceExpression2 =
            sourceCitizen match {
              case sourceInterface @ InterfaceRef2(_) => {
                if (ancestorHelper.isAncestor(temputs, targetStruct, sourceInterface)) {
                  AsSubtype2(
                    ArgLookup2(0, incomingCoord),
                    targetKind,
                    resultCoord,
                    okConstructor,
                    errConstructor)
                } else {
                  throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
                }
              }
              case sourceStruct @ StructRef2(_) => {
                if (sourceStruct == targetStruct) {
                  FunctionCall2(
                    okConstructor,
                    List(ArgLookup2(0, incomingCoord)))
                } else {
                  throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
                }
              }
            }

          temputs.addFunction(Function2(header, List(), Block2(List(Return2(asSubtypeExpr)))))
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
        override def getAncestorInterfaceDistance(temputs: Temputs, descendantCitizenRef: CitizenRef2, ancestorInterfaceRef: InterfaceRef2): Option[Int] = {
          ancestorHelper.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }

        override def getStructRef(temputs: Temputs, callRange: RangeS,structTemplata: StructTemplata, uncoercedTemplateArgs: List[ITemplata]): StructRef2 = {
          structTemplar.getStructRef(temputs, callRange, structTemplata, uncoercedTemplateArgs)
        }

        override def getInterfaceRef(temputs: Temputs, callRange: RangeS,interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: List[ITemplata]): InterfaceRef2 = {
          structTemplar.getInterfaceRef(temputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
        }

        override def makeArraySequenceType(
            env: IEnvironment,
            temputs: Temputs,
            mutability: Mutability,
            variability: Variability,
            size: Int,
            type2: Coord
        ): KnownSizeArrayT2 = {
          arrayTemplar.makeArraySequenceType(env, temputs, mutability, variability, size, type2)
        }

        override def makeUnknownSizeArrayType(env: IEnvironment, state: Temputs, element: Coord, arrayMutability: Mutability, arrayVariability: Variability): UnknownSizeArrayT2 = {
          arrayTemplar.makeUnknownSizeArrayType(env, state, element, arrayMutability, arrayVariability)
        }

        override def getTupleKind(env: IEnvironment, state: Temputs, elements: List[Coord]): TupleT2 = {
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

        override def lookupMemberTypes(state: Temputs, kind: Kind, expectedNumMembers: Int): Option[List[Coord]] = {
          profiler.childFrame("InferTemplarDelegate.lookupMemberTypes", () => {
            val underlyingStructRef2 =
              kind match {
                case sr@StructRef2(_) => sr
                case TupleT2(_, underlyingStruct) => underlyingStruct
                case PackT2(_, underlyingStruct) => underlyingStruct
                case _ => return None
              }
            val structDef2 = state.lookupStruct(underlyingStructRef2)
            val structMemberTypes = structDef2.members.map(_.tyype.reference)
            Some(structMemberTypes)
          })
        }

        override def getMutability(state: Temputs, kind: Kind): Mutability = {
          profiler.childFrame("InferTemplarDelegate.getMutability", () => {
            Templar.getMutability(state, kind)
          })
        }

        override def lookupTemplata(env: IEnvironment, range: RangeS, name: IName2): ITemplata = {
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

        override def getArraySequenceKind(env: IEnvironment, state: Temputs, mutability: Mutability, variability: Variability, size: Int, element: Coord): (KnownSizeArrayT2) = {
          profiler.childFrame("InferTemplarDelegate.getArraySequenceKind", () => {
            arrayTemplar.makeArraySequenceType(env, state, mutability, variability, size, element)
          })
        }

        override def makeUnknownSizeArrayType(env: IEnvironment, state: Temputs, element: Coord, arrayMutability: Mutability, arrayVariability: Variability): UnknownSizeArrayT2 = {
          profiler.childFrame("InferTemplarDelegate.makeUnknownSizeArrayType", () => {
            arrayTemplar.makeUnknownSizeArrayType(env, state, element, arrayMutability, arrayVariability)
          })
        }

        override def getTupleKind(env: IEnvironment, state: Temputs, elements: List[Coord]): TupleT2 = {
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
        (Kind) = {
          profiler.childFrame("InferTemplarDelegate.evaluateInterfaceTemplata", () => {
            structTemplar.getInterfaceRef(state, callRange, templata, templateArgs)
          })
        }

        override def evaluateStructTemplata(
          state: Temputs,
          callRange: RangeS,
          templata: StructTemplata,
          templateArgs: List[ITemplata]):
        (Kind) = {
          profiler.childFrame("InferTemplarDelegate.evaluateStructTemplata", () => {
            structTemplar.getStructRef(state, callRange, templata, templateArgs)
          })
        }

        override def getAncestorInterfaceDistance(temputs: Temputs, descendantCitizenRef: CitizenRef2, ancestorInterfaceRef: InterfaceRef2):
        (Option[Int]) = {
          profiler.childFrame("InferTemplarDelegate.getAncestorInterfaceDistance", () => {
            ancestorHelper.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
          })
        }

        override def getAncestorInterfaces(temputs: Temputs, descendantCitizenRef: CitizenRef2): (Set[InterfaceRef2]) = {
          profiler.childFrame("InferTemplarDelegate.getAncestorInterfaces", () => {
            ancestorHelper.getAncestorInterfaces(temputs, descendantCitizenRef)
          })
        }

        override def getMemberCoords(state: Temputs, structRef: StructRef2): List[Coord] = {
          profiler.childFrame("InferTemplarDelegate.getMemberCoords", () => {
            structTemplar.getMemberCoords(state, structRef)
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

        override def structIsClosure(state: Temputs, structRef: StructRef2): Boolean = {
          profiler.childFrame("InferTemplarDelegate.structIsClosure", () => {
            val structDef = state.getStructDefForRef(structRef)
            structDef.isClosure
          })
        }

        override def resolveExactSignature(env: IEnvironment, state: Temputs, range: RangeS, name: String, coords: List[Coord]): Prototype2 = {
          profiler.childFrame("InferTemplarDelegate.resolveExactSignature", () => {
            overloadTemplar.scoutExpectedFunctionForPrototype(env, state, range, GlobalFunctionFamilyNameA(name), List(), coords.map(ParamFilter(_, None)), List(), true) match {
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
        override def isAncestor(temputs: Temputs, descendantCitizenRef: CitizenRef2, ancestorInterfaceRef: InterfaceRef2): Boolean = {
          ancestorHelper.isAncestor(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }
      })

  val ancestorHelper: AncestorHelper =
    new AncestorHelper(opts, profiler, inferTemplar, new IAncestorHelperDelegate {
      override def getInterfaceRef(temputs: Temputs, callRange: RangeS, interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: List[ITemplata]): InterfaceRef2 = {
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
        override def evaluateOrdinaryFunctionFromNonCallForHeader(temputs: Temputs, callRange: RangeS,functionTemplata: FunctionTemplata): FunctionHeader2 = {
          functionTemplar.evaluateOrdinaryFunctionFromNonCallForHeader(temputs, callRange, functionTemplata)
        }

        override def scoutExpectedFunctionForPrototype(
          env: IEnvironment, temputs: Temputs, callRange: RangeS, functionName: IImpreciseNameStepA, explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS], args: List[ParamFilter], extraEnvsToLookIn: List[IEnvironment], exact: Boolean):
        OverloadTemplar.IScoutExpectedFunctionResult = {
          overloadTemplar.scoutExpectedFunctionForPrototype(env, temputs, callRange, functionName, explicitlySpecifiedTemplateArgTemplexesS, args, extraEnvsToLookIn, exact)
        }

        override def makeImmConcreteDestructor(temputs: Temputs, env: IEnvironment, structRef2: StructRef2): Unit = {
          destructorTemplar.getImmConcreteDestructor(temputs, env, structRef2)
        }

        override def getImmInterfaceDestructorOverride(temputs: Temputs, env: IEnvironment, structRef2: StructRef2, implementedInterfaceRefT: InterfaceRef2): Prototype2 = {
          destructorTemplar.getImmInterfaceDestructorOverride(temputs, env, structRef2, implementedInterfaceRefT)
        }

        override def getImmInterfaceDestructor(temputs: Temputs, env: IEnvironment, interfaceRef2: InterfaceRef2): Prototype2 = {
          destructorTemplar.getImmInterfaceDestructor(temputs, env, interfaceRef2)
        }

        override def getImmConcreteDestructor(temputs: Temputs, env: IEnvironment, structRef2: StructRef2): Prototype2 = {
          destructorTemplar.getImmConcreteDestructor(temputs, env, structRef2)
        }
      })

  val functionTemplar: FunctionTemplar =
    new FunctionTemplar(opts, profiler, newTemplataStore, templataTemplar, inferTemplar, convertHelper, structTemplar,
      new IFunctionTemplarDelegate {
    override def evaluateBlockStatements(temputs: Temputs, startingFate: FunctionEnvironment, fate: FunctionEnvironmentBox, exprs: List[IExpressionAE]): (List[ReferenceExpression2], Set[Coord]) = {
      expressionTemplar.evaluateBlockStatements(temputs, startingFate, fate, exprs)
    }

    override def nonCheckingTranslateList(temputs: Temputs, fate: FunctionEnvironmentBox, patterns1: List[AtomAP], patternInputExprs2: List[ReferenceExpression2]): List[ReferenceExpression2] = {
      expressionTemplar.nonCheckingTranslateList(temputs, fate, patterns1, patternInputExprs2)
    }

    override def evaluateParent(env: IEnvironment, temputs: Temputs, sparkHeader: FunctionHeader2): Unit = {
      virtualTemplar.evaluateParent(env, temputs, sparkHeader)
    }

    override def generateFunction(
      functionTemplarCore: FunctionTemplarCore,
      generator: IFunctionGenerator,
      fullEnv: FunctionEnvironment,
      temputs: Temputs,
      callRange: RangeS,
      originFunction: Option[FunctionA],
      paramCoords: List[Parameter2],
      maybeRetCoord: Option[Coord]):
    FunctionHeader2 = {
      generator.generate(
        functionTemplarCore, structTemplar, destructorTemplar, fullEnv, temputs, callRange, originFunction, paramCoords, maybeRetCoord)
    }
  })
  val overloadTemplar: OverloadTemplar = new OverloadTemplar(opts, profiler, templataTemplar, inferTemplar, functionTemplar)
  val destructorTemplar: DestructorTemplar = new DestructorTemplar(opts, structTemplar, overloadTemplar)
  val dropHelper = new DropHelper(opts, destructorTemplar)

  val virtualTemplar = new VirtualTemplar(opts, overloadTemplar)

  val sequenceTemplar = new SequenceTemplar(opts, structTemplar, destructorTemplar)

  val arrayTemplar =
    new ArrayTemplar(
      opts,
      new IArrayTemplarDelegate {
        def getArrayDestructor(
          env: IEnvironment,
          temputs: Temputs,
          type2: Coord):
        (Prototype2) = {
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
      dropHelper,
      convertHelper,
      new IExpressionTemplarDelegate {
        override def evaluateTemplatedFunctionFromCallForPrototype(temputs: Temputs, callRange: RangeS, functionTemplata: FunctionTemplata, explicitTemplateArgs: List[ITemplata], args: List[ParamFilter]): FunctionTemplar.IEvaluateFunctionResult[Prototype2] = {
          functionTemplar.evaluateTemplatedFunctionFromCallForPrototype(temputs, callRange, functionTemplata, explicitTemplateArgs, args)
        }

        override def evaluateClosureStruct(temputs: Temputs, containingFunctionEnv: FunctionEnvironment, callRange: RangeS, name: LambdaNameA, function1: BFunctionA): StructRef2 = {
          functionTemplar.evaluateClosureStruct(temputs, containingFunctionEnv, callRange, name, function1)
        }
      })

  def evaluate(packageToProgramA: PackageCoordinateMap[ProgramA]): Result[Hinputs, ICompileErrorT] = {
    try {
      profiler.newProfile("Templar.evaluate", "", () => {
        val programsA = packageToProgramA.moduleToPackagesToFilenameToContents.values.flatMap(_.values)

        val structsA = programsA.flatMap(_.structs)
        val interfacesA = programsA.flatMap(_.interfaces)
        val implsA = programsA.flatMap(_.impls)
        val functionsA = programsA.flatMap(_.functions)
        val exportsA = programsA.flatMap(_.exports)

        val env0 =
          PackageEnvironment(
            None,
            FullName2(List(), GlobalPackageName2()),
            newTemplataStore()
                .addEntries(
                  opts.useOptimization,
                  Map(
                    PrimitiveName2("int") -> List(TemplataEnvEntry(KindTemplata(Int2()))),
                    PrimitiveName2("Array") -> List(TemplataEnvEntry(ArrayTemplateTemplata())),
                    PrimitiveName2("bool") -> List(TemplataEnvEntry(KindTemplata(Bool2()))),
                    PrimitiveName2("float") -> List(TemplataEnvEntry(KindTemplata(Float2()))),
                    PrimitiveName2("__Never") -> List(TemplataEnvEntry(KindTemplata(Never2()))),
                    PrimitiveName2("str") -> List(TemplataEnvEntry(KindTemplata(Str2()))),
                    PrimitiveName2("void") -> List(TemplataEnvEntry(KindTemplata(Void2()))))))
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
                    temputs, RangeS.internal(-177), FunctionTemplata(env11, functionS))
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
                    temputs, structS.range, StructTemplata(env11, structS), List())
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
                    temputs, interfaceS.range, InterfaceTemplata(env11, interfaceS), List())
              }
            }
          }
        })

        exportsA.foreach({ case ExportAsA(range, exportedName, rules, typeByRune, typeRuneA) =>
          val typeRuneT = NameTranslator.translateRune(typeRuneA)
          val templataByRune =
            inferTemplar.inferOrdinaryRules(env11, temputs, rules, typeByRune, Set(typeRuneA))
          val referend =
            templataByRune.get(typeRuneT) match {
              case Some(KindTemplata(referend)) => referend
              case _ => vfail()
            }
          temputs.addExport(referend, range.file.packageCoordinate, exportedName)
        })

        profiler.newProfile("StampOverridesUntilSettledProbe", "", () => {
//          Split.enter(classOf[ValeSplitProbe], "stamp needed overrides")
          stampNeededOverridesUntilSettled(env11, temputs)
//          Split.exit()
        })

//        // Should get a conflict if there are more than one.
//        val (maybeNoArgMain, _, _, _) =
//          overloadTemplar.scoutMaybeFunctionForPrototype(
//            env11, temputs, RangeS.internal(-1398), GlobalFunctionFamilyNameA("main"), List(), List(), List(), true)


        val edgeBlueprints = EdgeTemplar.makeInterfaceEdgeBlueprints(temputs)
        val partialEdges = EdgeTemplar.assemblePartialEdges(temputs)
        val edges =
          partialEdges.map({ case PartialEdge2(struct, interface, methods) =>
            Edge2(
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

        edgeBlueprintsByInterface.foreach({ case (interfaceRef, edgeBlueprint) =>
          vassert(edgeBlueprint.interface == interfaceRef)
        })



        val reachables = Reachability.findReachables(temputs, edgeBlueprintsAsList, edges)

        val categorizedFunctions = temputs.getAllFunctions().groupBy(f => reachables.functions.contains(f.header.toSignature))
        val reachableFunctions = categorizedFunctions.getOrElse(true, List())
        val unreachableFunctions = categorizedFunctions.getOrElse(false, List())
        unreachableFunctions.foreach(f => debugOut("Shaking out unreachable: " + f.header.fullName))
        reachableFunctions.foreach(f => debugOut("Including: " + f.header.fullName))

        val categorizedStructs = temputs.getAllStructs().groupBy(f => reachables.structs.contains(f.getRef))
        val reachableStructs = categorizedStructs.getOrElse(true, List())
        val unreachableStructs = categorizedStructs.getOrElse(false, List())
        unreachableStructs.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
        reachableStructs.foreach(f => debugOut("Including: " + f.fullName))

        val categorizedInterfaces = temputs.getAllInterfaces().groupBy(f => reachables.interfaces.contains(f.getRef))
        val reachableInterfaces = categorizedInterfaces.getOrElse(true, List())
        val unreachableInterfaces = categorizedInterfaces.getOrElse(false, List())
        unreachableInterfaces.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
        reachableInterfaces.foreach(f => debugOut("Including: " + f.fullName))

        val categorizedEdges = edges.groupBy(f => reachables.edges.contains(f))
        val reachableEdges = categorizedEdges.getOrElse(true, List())
        val unreachableEdges = categorizedEdges.getOrElse(false, List())
        unreachableEdges.foreach(f => debugOut("Shaking out unreachable: " + f))
        reachableEdges.foreach(f => debugOut("Including: " + f))

        val hinputs =
          Hinputs(
            reachableInterfaces.toList,
            reachableStructs.toList,
            Program2.emptyTupleStructRef,
            reachableFunctions.toList,
            temputs.getExports,
            temputs.getExternPrototypes,
            edgeBlueprintsByInterface,
            edges)

        Ok(hinputs)
      })
    } catch {
      case CompileErrorExceptionT(err) => Err(err)
    }
  }

  // Returns whether we should eagerly compile this and anything it depends on.
  def isRootFunction(functionA: FunctionA): Boolean = {
    functionA.name match {
      case FunctionNameA("main", _) => return true
      case _ =>
    }
    functionA.attributes.exists({ case ExportA(_) => true case _ => false })
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
    env0: PackageEnvironment[GlobalPackageName2],
    interfaceA: InterfaceA
  ): PackageEnvironment[GlobalPackageName2] = {
    val interfaceEnvEntry = InterfaceEnvEntry(interfaceA)

    val TopLevelCitizenDeclarationNameA(humanName, codeLocationS) = interfaceA.name
    val name = CitizenTemplateName2(humanName, NameTranslator.translateCodeLocation(codeLocationS))

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
    env0: PackageEnvironment[GlobalPackageName2],
    structA: StructA
  ): PackageEnvironment[GlobalPackageName2] = {
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

  def stampNeededOverridesUntilSettled(env: PackageEnvironment[IName2], temputs: Temputs): Unit = {
    val neededOverrides =
      profiler.childFrame("assemble partial edges", () => {
        val partialEdges = EdgeTemplar.assemblePartialEdges(temputs)
        partialEdges.flatMap(e => e.methods.flatMap({
          case n @ NeededOverride(_, _) => List(n)
          case FoundFunction(_) => List()
        }))
      })
    if (neededOverrides.isEmpty) {
      return
    }

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
          List(), // No explicitly specified ones. It has to be findable just by param filters.
          neededOverride.paramFilters,
          List(),
          true) match {
          case (seff@ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
            throw CompileErrorExceptionT(RangedInternalErrorT(RangeS.internal(-1674), "Couldn't find function for vtable!\n" + seff.toString))
          }
          case (ScoutExpectedFunctionSuccess(_)) =>
        }
      }
    })

    stampNeededOverridesUntilSettled(env, temputs)
  }
}

object Templar {
  def getMutabilities(temputs: Temputs, concreteValues2: List[Kind]):
  List[Mutability] = {
    concreteValues2.map(concreteValue2 => getMutability(temputs, concreteValue2))
  }

  def getMutability(temputs: Temputs, concreteValue2: Kind):
  Mutability = {
    concreteValue2 match {
      case Never2() => Immutable
      case Int2() => Immutable
      case Float2() => Immutable
      case Bool2() => Immutable
      case Str2() => Immutable
      case Void2() => Immutable
      case UnknownSizeArrayT2(RawArrayT2(_, mutability, _)) => mutability
      case KnownSizeArrayT2(_, RawArrayT2(_, mutability, _)) => mutability
      case sr @ StructRef2(_) => temputs.lookupMutability(sr)
      case ir @ InterfaceRef2(_) => temputs.lookupMutability(ir)
      case PackT2(_, sr) => temputs.lookupMutability(sr)
      case TupleT2(_, sr) => temputs.lookupMutability(sr)
      case OverloadSet(_, _, _) => {
        // Just like FunctionT2
        Immutable
      }
    }
  }

  def intersectPermission(a: Permission, b: Permission): Permission = {
    (a, b) match {
      case (Readonly, Readonly) => Readonly
      case (Readonly, Readwrite) => Readonly
      case (Readwrite, Readonly) => Readonly
      case (Readwrite, Readwrite) => Readwrite
    }
  }

  def factorVariabilityAndPermission(
    containerPermission: Permission,
    memberVariability: Variability,
    memberPermission: Permission):
  (Variability, Permission) = {
    val effectiveVariability =
      (containerPermission, memberVariability) match {
        case (Readonly, Final) => Final
        case (Readwrite, Final) => Final
        case (Readonly, Varying) => Final
        case (Readwrite, Varying) => Varying
      }

    val targetPermission = Templar.intersectPermission(containerPermission, memberPermission)
    (effectiveVariability, targetPermission)
  }
}
