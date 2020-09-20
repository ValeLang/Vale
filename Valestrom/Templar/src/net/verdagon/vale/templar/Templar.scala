package net.verdagon.vale.templar;

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{CodeLocationS, ITemplexS, RangeS}
import net.verdagon.vale.templar.EdgeTemplar.{FoundFunction, NeededOverride}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.citizen.{AncestorHelper, IAncestorHelperDelegate, IStructTemplarDelegate, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.function.{BuiltInFunctions, DestructorTemplar, DropHelper, FunctionTemplar, FunctionTemplarCore, IFunctionTemplarDelegate, VirtualTemplar}
import net.verdagon.vale.templar.infer.IInfererDelegate

import scala.collection.immutable.{List, ListMap, Map, Set}

trait IFunctionGenerator {
  def generate(
    // These serve as the API that a function generator can use.
    // TODO: Give a trait with a reduced API.
    // Maybe this functionTemplarCore can be a lambda we can use to finalize and add *this* function.
    functionTemplarCore: FunctionTemplarCore,
    structTemplar: StructTemplar,
    destructorTemplar: DestructorTemplar,
    env: FunctionEnvironment,
    temputs: TemputsBox,
    callRange: RangeS,
    // We might be able to move these all into the function environment... maybe....
    originFunction: Option[FunctionA],
    paramCoords: List[Parameter2],
    maybeRetCoord: Option[Coord]):
  (FunctionHeader2)
}

case class TemplarOptions(
  functionGeneratorByName: Map[String, IFunctionGenerator],

  debugOut: String => Unit,
  verboseErrors: Boolean
)

class Templar(debugOut: (String) => Unit, verbose: Boolean) {
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
    }).toMap

  val opts = TemplarOptions(generatorsById, debugOut, verbose)



  val templataTemplar =
    new TemplataTemplar(
      opts,
      new ITemplataTemplarDelegate {
        override def getAncestorInterfaceDistance(temputs: TemputsBox, descendantCitizenRef: CitizenRef2, ancestorInterfaceRef: InterfaceRef2): Option[Int] = {
          ancestorHelper.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }

        override def getStructRef(temputs: TemputsBox, callRange: RangeS,structTemplata: StructTemplata, uncoercedTemplateArgs: List[ITemplata]): StructRef2 = {
          structTemplar.getStructRef(temputs, callRange, structTemplata, uncoercedTemplateArgs)
        }

        override def getInterfaceRef(temputs: TemputsBox, callRange: RangeS,interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: List[ITemplata]): InterfaceRef2 = {
          structTemplar.getInterfaceRef(temputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
        }

        override def makeArraySequenceType(env: IEnvironment, temputs: TemputsBox, mutability: Mutability, size: Int, type2: Coord): KnownSizeArrayT2 = {
          arrayTemplar.makeArraySequenceType(env, temputs, mutability, size, type2)
        }

        override def getTupleKind(env: IEnvironment, state: TemputsBox, elements: List[Coord]): TupleT2 = {
          val (tuple, mutability) = sequenceTemplar.makeTupleType(env, state, elements)
          tuple
        }
      })
  val inferTemplar: InferTemplar =
    new InferTemplar(
      opts,
      new IInfererDelegate[IEnvironment, TemputsBox] {
        override def evaluateType(
          env: IEnvironment,
          temputs: TemputsBox,
          type1: ITemplexA
        ): (ITemplata) = {
          templataTemplar.evaluateTemplex(env, temputs, type1)
        }

        override def lookupMemberTypes(state: TemputsBox, kind: Kind, expectedNumMembers: Int): Option[List[Coord]] = {
          val underlyingStructRef2 =
            kind match {
              case sr @ StructRef2(_) => sr
              case TupleT2(_, underlyingStruct) => underlyingStruct
              case PackT2(_, underlyingStruct) => underlyingStruct
              case _ => return None
            }
          val structDef2 = state.lookupStruct(underlyingStructRef2)
          val structMemberTypes = structDef2.members.map(_.tyype.reference)
          Some(structMemberTypes)
        }

        override def getMutability(state: TemputsBox, kind: Kind): Mutability = {
          Templar.getMutability(state, kind)
        }

        override def lookupTemplata(env: IEnvironment, name: IName2): ITemplata = {
          // We can only ever lookup types by name in expression context,
          // otherwise we have no idea what List<Str> means; it could
          // mean a list of strings or a list of the Str(:Int)Str function.
          env.getNearestTemplataWithAbsoluteName2(name, Set[ILookupContext](TemplataLookupContext)) match {
            case None => vfail("Couldn't find anything with name: " + name)
            case Some(x) => x
          }
        }

        override def lookupTemplataImprecise(env: IEnvironment, name: IImpreciseNameStepA): ITemplata = {
          env.getNearestTemplataWithName(name, Set[ILookupContext](TemplataLookupContext)) match {
            case None => vfail("Couldn't find anything with name: " + name)
            case Some(x) => x
          }
        }

        //      override def getPackKind(env: IEnvironment, state: TemputsBox, members: List[Coord]): (PackT2, Mutability) = {
        //        PackTemplar.makePackType(env.globalEnv, state, members)
        //      }

        override def getArraySequenceKind(env: IEnvironment, state: TemputsBox, mutability: Mutability, size: Int, element: Coord): (KnownSizeArrayT2) = {
          arrayTemplar.makeArraySequenceType(env, state, mutability, size, element)
        }

        override def getTupleKind(env: IEnvironment, state: TemputsBox, elements: List[Coord]): TupleT2 = {
          val (tuple, mutability) = sequenceTemplar.makeTupleType(env, state, elements)
          tuple
        }

        override def evaluateInterfaceTemplata(
          state: TemputsBox,
          callRange: RangeS,
          templata: InterfaceTemplata,
          templateArgs: List[ITemplata]):
        (Kind) = {
          structTemplar.getInterfaceRef(state, callRange, templata, templateArgs)
        }

        override def evaluateStructTemplata(
          state: TemputsBox,
          callRange: RangeS,
          templata: StructTemplata,
          templateArgs: List[ITemplata]):
        (Kind) = {
          structTemplar.getStructRef(state, callRange, templata, templateArgs)
        }

        override def getAncestorInterfaceDistance(temputs: TemputsBox, descendantCitizenRef: CitizenRef2, ancestorInterfaceRef: InterfaceRef2):
        (Option[Int]) = {
          ancestorHelper.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }

        override def getAncestorInterfaces(temputs: TemputsBox, descendantCitizenRef: CitizenRef2): (Set[InterfaceRef2]) = {
          ancestorHelper.getAncestorInterfaces(temputs, descendantCitizenRef)
        }

        override def getMemberCoords(state: TemputsBox, structRef: StructRef2): List[Coord] = {
          structTemplar.getMemberCoords(state, structRef)
        }

        override def citizenIsFromTemplate(state: TemputsBox, citizen: CitizenRef2, template: ITemplata): (Boolean) = {
          structTemplar.citizenIsFromTemplate(state, citizen, template)
        }


        override def getInterfaceTemplataType(it: InterfaceTemplata): ITemplataType = {
          it.originInterface.tyype
        }

        override def getStructTemplataType(st: StructTemplata): ITemplataType = {
          st.originStruct.tyype
        }

        override def structIsClosure(state: TemputsBox, structRef: StructRef2): Boolean = {
          val structDef = state.structDefsByRef(structRef)
          structDef.isClosure
        }

        // A simple interface is one that has only one method
        def getSimpleInterfaceMethod(state: TemputsBox, interfaceRef: InterfaceRef2): Prototype2 = {
          val interfaceDef2 = state.lookupInterface(interfaceRef)
          if (interfaceDef2.internalMethods.size != 1) {
            vfail("Interface is not simple!")
          }
          interfaceDef2.internalMethods.head.toPrototype
        }

        override def resolveExactSignature(env: IEnvironment, state: TemputsBox, range: RangeS, name: String, coords: List[Coord]): Prototype2 = {
          overloadTemplar.scoutExpectedFunctionForPrototype(env, state, range, GlobalFunctionFamilyNameA(name), List(), coords.map(ParamFilter(_, None)), List(), true) match {
            case sef @ ScoutExpectedFunctionFailure(humanName, args, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction) => {
              throw new CompileErrorExceptionT(CouldntFindFunctionToCallT(range, sef))
              vfail(sef.toString)
            }
            case ScoutExpectedFunctionSuccess(prototype) => prototype
          }
        }
      })
  val convertHelper =
    new ConvertHelper(
      opts,
      new IConvertHelperDelegate {
        override def isAncestor(temputs: TemputsBox, descendantCitizenRef: CitizenRef2, ancestorInterfaceRef: InterfaceRef2): Boolean = {
          ancestorHelper.isAncestor(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }
      })

  val arrayTemplar =
    new ArrayTemplar(
      opts,
      new IArrayTemplarDelegate {
        def getArrayDestructor(
          env: IEnvironment,
          temputs: TemputsBox,
          type2: Coord):
        (Prototype2) = {
          destructorTemplar.getArrayDestructor(env, temputs, type2)
        }
      })

  val ancestorHelper =
    new AncestorHelper(opts, inferTemplar, new IAncestorHelperDelegate {
      override def getInterfaceRef(temputs: TemputsBox, callRange: RangeS, interfaceTemplata: InterfaceTemplata, uncoercedTemplateArgs: List[ITemplata]): InterfaceRef2 = {
        structTemplar.getInterfaceRef(temputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
      }
    })

  val structTemplar: StructTemplar =
    new StructTemplar(
      opts,
      inferTemplar,
      ancestorHelper,
      new IStructTemplarDelegate {
        override def evaluateOrdinaryFunctionFromNonCallForHeader(temputs: TemputsBox, callRange: RangeS,functionTemplata: FunctionTemplata): FunctionHeader2 = {
          functionTemplar.evaluateOrdinaryFunctionFromNonCallForHeader(temputs, callRange, functionTemplata)
        }

        override def scoutExpectedFunctionForPrototype(
          env: IEnvironment, temputs: TemputsBox, callRange: RangeS, functionName: IImpreciseNameStepA, explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS], args: List[ParamFilter], extraEnvsToLookIn: List[IEnvironment], exact: Boolean):
        OverloadTemplar.IScoutExpectedFunctionResult = {
          overloadTemplar.scoutExpectedFunctionForPrototype(env, temputs, callRange, functionName, explicitlySpecifiedTemplateArgTemplexesS, args, extraEnvsToLookIn, exact)
        }

        override def makeImmConcreteDestructor(temputs: TemputsBox, env: IEnvironment, structRef2: StructRef2): Unit = {
          destructorTemplar.getImmConcreteDestructor(temputs, env, structRef2)
        }

        override def getImmInterfaceDestructorOverride(temputs: TemputsBox, env: IEnvironment, structRef2: StructRef2, implementedInterfaceRefT: InterfaceRef2): Prototype2 = {
          destructorTemplar.getImmInterfaceDestructorOverride(temputs, env, structRef2, implementedInterfaceRefT)
        }

        override def getImmInterfaceDestructor(temputs: TemputsBox, env: IEnvironment, interfaceRef2: InterfaceRef2): Prototype2 = {
          destructorTemplar.getImmInterfaceDestructor(temputs, env, interfaceRef2)
        }

        override def getImmConcreteDestructor(temputs: TemputsBox, env: IEnvironment, structRef2: StructRef2): Prototype2 = {
          destructorTemplar.getImmConcreteDestructor(temputs, env, structRef2)
        }
      })

  val functionTemplar: FunctionTemplar = new FunctionTemplar(opts, templataTemplar, inferTemplar, convertHelper, structTemplar, new IFunctionTemplarDelegate {
    override def evaluateBlockStatements(temputs: TemputsBox, startingFate: FunctionEnvironment, fate: FunctionEnvironmentBox, exprs: List[IExpressionAE]): (List[ReferenceExpression2], Set[Coord]) = {
      expressionTemplar.evaluateBlockStatements(temputs, startingFate, fate, exprs)
    }

    override def nonCheckingTranslateList(temputs: TemputsBox, fate: FunctionEnvironmentBox, patterns1: List[AtomAP], patternInputExprs2: List[ReferenceExpression2]): List[ReferenceExpression2] = {
      expressionTemplar.nonCheckingTranslateList(temputs, fate, patterns1, patternInputExprs2)
    }

    override def evaluateParent(env: IEnvironment, temputs: TemputsBox, sparkHeader: FunctionHeader2): Unit = {
      virtualTemplar.evaluateParent(env, temputs, sparkHeader)
    }

    override def generateFunction(
      functionTemplarCore: FunctionTemplarCore,
      generator: IFunctionGenerator,
      fullEnv: FunctionEnvironment,
      temputs: TemputsBox,
      callRange: RangeS,
      originFunction: Option[FunctionA],
      paramCoords: List[Parameter2],
      maybeRetCoord: Option[Coord]):
    FunctionHeader2 = {
      generator.generate(
        functionTemplarCore, structTemplar, destructorTemplar, fullEnv, temputs, callRange, originFunction, paramCoords, maybeRetCoord)
    }
  })
  val overloadTemplar: OverloadTemplar = new OverloadTemplar(opts, templataTemplar, inferTemplar, functionTemplar)
  val destructorTemplar: DestructorTemplar = new DestructorTemplar(opts, structTemplar, overloadTemplar)
  val dropHelper = new DropHelper(opts, destructorTemplar)

  val virtualTemplar = new VirtualTemplar(opts, overloadTemplar)

  val sequenceTemplar = new SequenceTemplar(opts, arrayTemplar, structTemplar, destructorTemplar)

  val expressionTemplar =
    new ExpressionTemplar(
      opts,
      templataTemplar,
      inferTemplar,
      arrayTemplar,
      structTemplar,
      sequenceTemplar,
      overloadTemplar,
      dropHelper,
      convertHelper,
      new IExpressionTemplarDelegate {
        override def evaluateTemplatedFunctionFromCallForPrototype(temputs: TemputsBox, callRange: RangeS, functionTemplata: FunctionTemplata, explicitTemplateArgs: List[ITemplata], args: List[ParamFilter]): FunctionTemplar.IEvaluateFunctionResult[Prototype2] = {
          functionTemplar.evaluateTemplatedFunctionFromCallForPrototype(temputs, callRange, functionTemplata, explicitTemplateArgs, args)
        }

        override def evaluateClosureStruct(temputs: TemputsBox, containingFunctionEnv: FunctionEnvironment, callRange: RangeS, name: LambdaNameA, function1: BFunctionA): StructRef2 = {
          functionTemplar.evaluateClosureStruct(temputs, containingFunctionEnv, callRange, name, function1)
        }
      })

  def evaluate(program: ProgramA): Result[Temputs, ICompileErrorT] = {
    try {
      val ProgramA(structsA, interfacesA, impls1, functions1) = program;

      val env0 =
        NamespaceEnvironment(
          None,
          FullName2(List(), GlobalNamespaceName2()),
          Map(
            PrimitiveName2("int") -> List(TemplataEnvEntry(KindTemplata(Int2()))),
            PrimitiveName2("Array") -> List(TemplataEnvEntry(ArrayTemplateTemplata())),
            PrimitiveName2("bool") -> List(TemplataEnvEntry(KindTemplata(Bool2()))),
            PrimitiveName2("float") -> List(TemplataEnvEntry(KindTemplata(Float2()))),
            PrimitiveName2("__Never") -> List(TemplataEnvEntry(KindTemplata(Never2()))),
            PrimitiveName2("str") -> List(TemplataEnvEntry(KindTemplata(Str2()))),
            PrimitiveName2("void") -> List(TemplataEnvEntry(KindTemplata(Void2())))))
      val env1b =
        BuiltInFunctions.builtIns.foldLeft(env0)({
          case (env1a, builtIn) => {
            env1a.addUnevaluatedFunction(builtIn)
          }
        })
      val env3 =
        generatedFunctions.foldLeft(env1b)({
          case (env2, (generatedFunction, generator)) => {
            env2.addUnevaluatedFunction(generatedFunction)
          }
        })

      // This has to come before the structs and interfaces because part of evaluating a
      // struct or interface is figuring out what it extends.
      val env5 =
      impls1.foldLeft(env3)({
        case (env4, impl1) => env4.addEntry(NameTranslator.translateImplName(impl1.name), ImplEnvEntry(impl1))
      })
      val env7 =
        structsA.foldLeft(env5)({
          case (env6, s) => env6.addEntries(makeStructEnvironmentEntries(s))
        })
      val env9 =
        interfacesA.foldLeft(env7)({
          case (env8, interfaceA) => env8.addEntries(makeInterfaceEnvironmentEntries(interfaceA))
        })

      val env11 =
        functions1.foldLeft(env9)({
          case (env10, functionS) => {
            env10.addUnevaluatedFunction(functionS)
          }
        })

      val temputs =
        TemputsBox(
          Temputs(
            Set(),
            Map(),
            List(),
            ListMap(),
            Set(),
            Map(),
            Set(),
            ListMap(),
            Map(),
            Set(),
            ListMap(),
            Map(),
            List(),
            Map(),
            Map(),
            Map()))

      structTemplar.addBuiltInStructs(env11, temputs)

      functions1.foreach({
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

      stampNeededOverridesUntilSettled(env11, temputs)

      Ok(temputs.temputs)
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
    functionA.attributes.contains(ExportA)
  }

  // Returns whether we should eagerly compile this and anything it depends on.
  def isRootStruct(structA: StructA): Boolean = {
    structA.attributes.contains(ExportA)
  }

  // Returns whether we should eagerly compile this and anything it depends on.
  def isRootInterface(interfaceA: InterfaceA): Boolean = {
    interfaceA.attributes.contains(ExportA)
  }

  // (Once we add namespaces, this will probably change)
  def makeInterfaceEnvironmentEntries(
    interfaceA: InterfaceA
  ): Map[IName2, List[IEnvEntry]] = {
    val interfaceEnvEntry = InterfaceEnvEntry(interfaceA)

    val TopLevelCitizenDeclarationNameA(humanName, codeLocationS) = interfaceA.name
    val name = CitizenTemplateName2(humanName, NameTranslator.translateCodeLocation(codeLocationS))

    val env0 = Map[IName2, List[IEnvEntry]]()
    val env1 = EnvironmentUtils.addEntry(env0, name, interfaceEnvEntry)
    val env2 =
      EnvironmentUtils.addUnevaluatedFunction(
        env1,
        structTemplar.getInterfaceConstructor(interfaceA))

    // Once we have sub-interfaces and sub-structs, we could recursively call this function.
    // We'll put our interfaceA onto the top of the list of every entry from the sub-struct/sub-interface.

    env2
  }

  // (Once we add namespaces, this will probably change)
  def makeStructEnvironmentEntries(
    structA: StructA
  ): Map[IName2, List[IEnvEntry]] = {
    val interfaceEnvEntry = StructEnvEntry(structA)

    val env0 = Map[IName2, List[IEnvEntry]]()
    val env1 = EnvironmentUtils.addEntry(env0, NameTranslator.translateNameStep(structA.name), interfaceEnvEntry)
    val env2 =
      EnvironmentUtils.addUnevaluatedFunction(
        env1,
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

  def stampNeededOverridesUntilSettled(env: NamespaceEnvironment[IName2], temputs: TemputsBox): Unit = {
    val partialEdges =
      EdgeTemplar.assemblePartialEdges(
        temputs.functions, temputs.getAllInterfaces(), temputs.impls)
    val neededOverrides =
      partialEdges.flatMap(e => e.methods.flatMap({
        case n@NeededOverride(_, _) => List(n)
        case FoundFunction(_) => List()
      }))
    if (neededOverrides.isEmpty) {
      return temputs
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
            vfail("Couldn't find function for vtable!\n" + seff.toString)
          }
          case (ScoutExpectedFunctionSuccess(_)) =>
        }
      }
    })

    stampNeededOverridesUntilSettled(env, temputs)
  }
}

object Templar {
  def getMutabilities(temputs: TemputsBox, concreteValues2: List[Kind]):
  List[Mutability] = {
    concreteValues2.map(concreteValue2 => getMutability(temputs, concreteValue2))
  }

  def getMutability(temputs: TemputsBox, concreteValue2: Kind):
  Mutability = {
    concreteValue2 match {
      case Never2() => Immutable
      case Int2() => Immutable
      case Float2() => Immutable
      case Bool2() => Immutable
      case Str2() => Immutable
      case Void2() => Immutable
      case UnknownSizeArrayT2(RawArrayT2(_, mutability)) => mutability
      case KnownSizeArrayT2(_, RawArrayT2(_, mutability)) => mutability
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
}
