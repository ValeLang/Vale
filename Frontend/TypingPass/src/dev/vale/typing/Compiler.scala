package dev.vale.typing

import dev.vale._
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.{CallMacroP, DontCallMacroP, UseP}
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing._
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.citizen._
import dev.vale.typing.expression.{ExpressionCompiler, IExpressionCompilerDelegate}
import dev.vale.typing.function._
import dev.vale.typing.infer.IInfererDelegate
import dev.vale.typing.types._
import dev.vale.highertyping._
import OverloadResolver.FindFunctionFailure
import dev.vale.typing.function._
import dev.vale
import dev.vale.highertyping.{ExportAsA, FunctionA, InterfaceA, ProgramA, StructA}
import dev.vale.typing.Compiler.isPrimitive
import dev.vale.typing.ast.{ConsecutorTE, EdgeT, FunctionHeaderT, LocationInFunctionEnvironmentT, ParameterT, PrototypeT, ReferenceExpressionTE, VoidLiteralTE}
import dev.vale.typing.env._
import dev.vale.typing.macros.{AbstractBodyMacro, AnonymousInterfaceMacro, AsSubtypeMacro, FunctorHelper, IOnImplDefinedMacro, IOnInterfaceDefinedMacro, IOnStructDefinedMacro, LockWeakMacro, SameInstanceMacro, StructConstructorMacro}
import dev.vale.typing.macros.citizen._
import dev.vale.typing.macros.rsa.{RSADropIntoMacro, RSAImmutableNewMacro, RSALenMacro, RSAMutableCapacityMacro, RSAMutableNewMacro, RSAMutablePopMacro, RSAMutablePushMacro}
import dev.vale.typing.macros.ssa.{SSADropIntoMacro, SSALenMacro}
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.ast._
import dev.vale.typing.citizen.ImplCompiler
import dev.vale.typing.env._
import dev.vale.typing.expression.LocalHelper
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.macros.citizen.StructDropMacro
import dev.vale.typing.macros.ssa.SSALenMacro

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
    env: FunctionEnvironmentT,
    coutputs: CompilerOutputs,
    life: LocationInFunctionEnvironmentT,
    callRange: List[RangeS],
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



class Compiler(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords) {
  val debugOut = opts.debugOut
  val globalOptions = opts.globalOptions

  val nameTranslator = new NameTranslator(interner)

  val templataCompiler =
    new TemplataCompiler(
      interner,
      opts,
      nameTranslator,
      new ITemplataCompilerDelegate {
        override def isParent(
          coutputs: CompilerOutputs,
          callingEnv: IInDenizenEnvironmentT,
          parentRanges: List[RangeS],
          callLocation: LocationInDenizen,
          subKindTT: ISubKindTT,
          superKindTT: ISuperKindTT):
        IsParentResult = {
          implCompiler.isParent(coutputs, callingEnv, parentRanges, callLocation, subKindTT, superKindTT)
        }

        override def resolveStruct(
          coutputs: CompilerOutputs,
          callingEnv: IInDenizenEnvironmentT,
          callRange: List[RangeS],
          callLocation: LocationInDenizen,
          structTemplata: StructDefinitionTemplataT,
          uncoercedTemplateArgs: Vector[ITemplataT[ITemplataType]]):
        IResolveOutcome[StructTT] = {
          structCompiler.resolveStruct(
            coutputs, callingEnv, callRange, callLocation, structTemplata, uncoercedTemplateArgs)
        }

        override def resolveInterface(
            coutputs: CompilerOutputs,
            callingEnv: IInDenizenEnvironmentT, // See CSSNCE
            callRange: List[RangeS],
            callLocation: LocationInDenizen,
            interfaceTemplata: InterfaceDefinitionTemplataT,
            uncoercedTemplateArgs: Vector[ITemplataT[ITemplataType]]):
        IResolveOutcome[InterfaceTT] = {
          structCompiler.resolveInterface(
            coutputs, callingEnv, callRange, callLocation, interfaceTemplata, uncoercedTemplateArgs)
        }

      })
  val inferCompiler: InferCompiler =
    new InferCompiler(
      opts,
      interner,
      keywords,
      nameTranslator,
      new IInfererDelegate {
        def getPlaceholdersInId(accum: Accumulator[IdT[INameT]], id: IdT[INameT]): Unit = {
          id.localName match {
            case KindPlaceholderNameT(_) => accum.add(id)
            case KindPlaceholderTemplateNameT(_, _) => accum.add(id)
            case _ =>
          }
        }

        def getPlaceholdersInTemplata(accum: Accumulator[IdT[INameT]], templata: ITemplataT[ITemplataType]): Unit = {
          templata match {
            case KindTemplataT(kind) => getPlaceholdersInKind(accum, kind)
            case CoordTemplataT(CoordT(_, _, kind)) => getPlaceholdersInKind(accum, kind)
            case CoordTemplataT(CoordT(_, _, _)) =>
            case PlaceholderTemplataT(id, _) => accum.add(id)
            case IntegerTemplataT(_) =>
            case BooleanTemplataT(_) =>
            case StringTemplataT(_) =>
            case RuntimeSizedArrayTemplateTemplataT() =>
            case StaticSizedArrayTemplateTemplataT() =>
            case VariabilityTemplataT(_) =>
            case OwnershipTemplataT(_) =>
            case MutabilityTemplataT(_) =>
            case InterfaceDefinitionTemplataT(_,_) =>
            case StructDefinitionTemplataT(_,_) =>
            case ImplDefinitionTemplataT(_,_) =>
            case CoordListTemplataT(coords) => coords.foreach(c => getPlaceholdersInKind(accum, c.kind))
            case PrototypeTemplataT(_, prototype) => {
              getPlaceholdersInId(accum, prototype.id)
              prototype.paramTypes.foreach(c => getPlaceholdersInKind(accum, c.kind))
              getPlaceholdersInKind(accum, prototype.returnType.kind)
            }
            case IsaTemplataT(_, _, subKind, superKind) => {
              getPlaceholdersInKind(accum, subKind)
              getPlaceholdersInKind(accum, superKind)
            }
            case other => vimpl(other)
          }
        }

        def getPlaceholdersInKind(accum: Accumulator[IdT[INameT]], kind: KindT): Unit = {
          kind match {
            case IntT(_) =>
            case BoolT() =>
            case FloatT() =>
            case VoidT() =>
            case NeverT(_) =>
            case StrT() =>
            case contentsRuntimeSizedArrayTT(mutability, elementType, selfRegion) => {
              getPlaceholdersInTemplata(accum, mutability)
              getPlaceholdersInKind(accum, elementType.kind)
            }
            case contentsStaticSizedArrayTT(size, mutability, variability, elementType, selfRegion) => {
              getPlaceholdersInTemplata(accum, size)
              getPlaceholdersInTemplata(accum, mutability)
              getPlaceholdersInTemplata(accum, variability)
              getPlaceholdersInKind(accum, elementType.kind)
            }
            case StructTT(IdT(_,_,name)) => name.templateArgs.foreach(getPlaceholdersInTemplata(accum, _))
            case InterfaceTT(IdT(_,_,name)) => name.templateArgs.foreach(getPlaceholdersInTemplata(accum, _))
            case KindPlaceholderT(id) => accum.add(id)
            case OverloadSetT(env, name) =>
            case other => vimpl(other)
          }
        }

        override def sanityCheckConclusion(env: InferEnv, state: CompilerOutputs, rune: IRuneS, templata: ITemplataT[ITemplataType]): Unit = {
          val accum = new Accumulator[IdT[INameT]]()
          getPlaceholdersInTemplata(accum, templata)

          if (accum.elementsReversed.nonEmpty) {
            val rootDenizenEnv = env.originalCallingEnv.rootCompilingDenizenEnv
            val originalCallingEnvTemplateName =
              rootDenizenEnv.id match {
                case IdT(packageCoord, initSteps, x: ITemplateNameT) => {
                  IdT(packageCoord, initSteps, x)
                }
                // When we compile a generic function, we populate some placeholders for its template
                // args. Then, we start compiling its body expressions. At that point, we're in an
                // environment that has a FullName with placeholders in it.
                // That's what we'll see in this case.
                case IdT(packageCoord, initSteps, x: IInstantiationNameT) => {
                  IdT(packageCoord, initSteps, x.template)
                }
                case other => vfail(other)
              }
            accum.elementsReversed.foreach(placeholderName => {
              // There should only ever be placeholders from the original calling environment, we should
              // *never* mix placeholders from two environments.
              // If this assert trips, that means we're not correctly phrasing everything in terms of
              // placeholders from this top level denizen.
              // See OWPFRD.
              vassert(placeholderName.steps.startsWith(originalCallingEnvTemplateName.steps))
            })
          }
        }

        override def lookupTemplata(
          envs: InferEnv,
          coutputs: CompilerOutputs,
          range: List[RangeS],
          name: INameT):
        ITemplataT[ITemplataType] = {
          templataCompiler.lookupTemplata(envs.selfEnv, coutputs, range, name)
        }

        override def isDescendant(
          envs: InferEnv,
          coutputs: CompilerOutputs,
          kind: KindT):
        Boolean = {
          kind match {
            case p @ KindPlaceholderT(_) => implCompiler.isDescendant(coutputs, envs.parentRanges, envs.callLocation, envs.originalCallingEnv, p, false)
            case contentsRuntimeSizedArrayTT(_, _, _) => false
            case OverloadSetT(_, _) => false
            case NeverT(fromBreak) => true
            case contentsStaticSizedArrayTT(_, _, _, _, _) => false
            case s @ StructTT(_) => implCompiler.isDescendant(coutputs, envs.parentRanges, envs.callLocation, envs.originalCallingEnv, s, false)
            case i @ InterfaceTT(_) => implCompiler.isDescendant(coutputs, envs.parentRanges, envs.callLocation, envs.originalCallingEnv, i, false)
            case IntT(_) | BoolT() | FloatT() | StrT() | VoidT() => false
          }
        }

        override def isAncestor(
          envs: InferEnv,
          coutputs: CompilerOutputs,
          kind: KindT):
        Boolean = {
          kind match {
            case InterfaceTT(_) => true
            case _ => false
          }
        }

        override def isParent(
          env: InferEnv,
          coutputs: CompilerOutputs,
          parentRanges: List[RangeS],
          subKindTT: ISubKindTT,
          superKindTT: ISuperKindTT,
          includeSelf: Boolean):
        Option[ITemplataT[ImplTemplataType]] = {
          implCompiler.isParent(coutputs, env.originalCallingEnv, parentRanges, env.callLocation, subKindTT, superKindTT) match {
            case IsParent(implTemplata, _, _) => Some(implTemplata)
            case IsntParent(candidates) => None
          }
        }

        def coerceToCoord(
          envs: InferEnv,
          state: CompilerOutputs,
          range: List[RangeS],
          templata: ITemplataT[ITemplataType],
          region: RegionT):
        ITemplataT[ITemplataType] = {
          templataCompiler.coerceToCoord(state, envs.originalCallingEnv, range, templata, region)
        }

        override def lookupTemplataImprecise(envs: InferEnv, state: CompilerOutputs, range: List[RangeS], name: IImpreciseNameS): Option[ITemplataT[ITemplataType]] = {
          templataCompiler.lookupTemplata(envs.selfEnv, state, range, name)
        }

        override def getMutability(state: CompilerOutputs, kind: KindT): ITemplataT[MutabilityTemplataType] = {
            Compiler.getMutability(state, kind)
        }

        override def predictStaticSizedArrayKind(
          envs: InferEnv,
          state: CompilerOutputs,
          mutability: ITemplataT[MutabilityTemplataType],
          variability: ITemplataT[VariabilityTemplataType],
          size: ITemplataT[IntegerTemplataType],
          element: CoordT,
          region: RegionT):
        StaticSizedArrayTT = {
          arrayCompiler.resolveStaticSizedArray(mutability, variability, size, element, region)
        }

        override def predictRuntimeSizedArrayKind(
          envs: InferEnv,
          state: CompilerOutputs,
          element: CoordT,
          arrayMutability: ITemplataT[MutabilityTemplataType],
          region: RegionT):
        RuntimeSizedArrayTT = {
            arrayCompiler.resolveRuntimeSizedArray(element, arrayMutability, region)
        }

        override def predictInterface(
          env: InferEnv,
          state: CompilerOutputs,
          templata: InterfaceDefinitionTemplataT,
          templateArgs: Vector[ITemplataT[ITemplataType]]):
        (KindT) = {
            structCompiler.predictInterface(
              state, env.originalCallingEnv, env.parentRanges, env.callLocation, templata, templateArgs)
        }

        override def predictStruct(
          env: InferEnv,
          state: CompilerOutputs,
          templata: StructDefinitionTemplataT,
          templateArgs: Vector[ITemplataT[ITemplataType]]):
        (KindT) = {
          structCompiler.predictStruct(
            state, env.originalCallingEnv, env.parentRanges, env.callLocation, templata, templateArgs)
        }

        override def kindIsFromTemplate(
          coutputs: CompilerOutputs,
          actualCitizenRef: KindT,
          expectedCitizenTemplata: ITemplataT[ITemplataType]):
        Boolean = {
          actualCitizenRef match {
            case s : ICitizenTT => templataCompiler.citizenIsFromTemplate(s, expectedCitizenTemplata)
            case contentsRuntimeSizedArrayTT(_, _, _) => (expectedCitizenTemplata == RuntimeSizedArrayTemplateTemplataT())
            case contentsStaticSizedArrayTT(_, _, _, _, _) => (expectedCitizenTemplata == StaticSizedArrayTemplateTemplataT())
            case _ => false
          }
        }

        override def getAncestors(
          envs: InferEnv,
          coutputs: CompilerOutputs,
          descendant: KindT,
          includeSelf: Boolean):
        Set[KindT] = {
            (if (includeSelf) {
              Set[KindT](descendant)
            } else {
              Set[KindT]()
            }) ++
              (descendant match {
                case s : ISubKindTT => implCompiler.getParents(coutputs, envs.parentRanges, envs.callLocation, envs.originalCallingEnv, s, true)
                case _ => Vector[KindT]()
              })
        }

        override def structIsClosure(state: CompilerOutputs, structTT: StructTT): Boolean = {
            val structDef = state.lookupStruct(structTT.id)
            structDef.isClosure
        }

        def predictFunction(
          envs: InferEnv,
          state: CompilerOutputs,
          functionRange: RangeS,
          name: StrI,
          paramCoords: Vector[CoordT],
          returnCoord: CoordT):
        PrototypeTemplataT = {
          PrototypeTemplataT(
            functionRange,
            PrototypeT(
              envs.selfEnv.id.addStep(
                interner.intern(
                  FunctionNameT(
                    interner.intern(FunctionTemplateNameT(name, functionRange.begin)),
                    Vector(),
                    paramCoords))),
              returnCoord))
        }

        override def assemblePrototype(
            envs: InferEnv,
          state: CompilerOutputs,
            range: RangeS,
            name: StrI,
            coords: Vector[CoordT],
            returnType: CoordT):
        PrototypeT = {
          val result =
            PrototypeT(
              envs.selfEnv.id.addStep(
                interner.intern(FunctionBoundNameT(
                  interner.intern(FunctionBoundTemplateNameT(name, range.begin)), Vector(), coords))),
              returnType)

          // This is a function bound, and there's no such thing as a function bound with function bounds.
          state.addInstantiationBounds(result.id, InstantiationBoundArgumentsT(Map(), Map()))

          result
        }

        override def assembleImpl(env: InferEnv, range: RangeS, subKind: KindT, superKind: KindT): IsaTemplataT = {
          IsaTemplataT(
            range,
            env.selfEnv.id.addStep(
              interner.intern(
                ImplBoundNameT(
                  interner.intern(ImplBoundTemplateNameT(range.begin)),
                  Vector()))),
            subKind,
            superKind)
        }
      },
      new IInferCompilerDelegate {
        override def resolveInterface(
          callingEnv: IInDenizenEnvironmentT,
          state: CompilerOutputs,
          callRange: List[RangeS],
          callLocation: LocationInDenizen,
          templata: InterfaceDefinitionTemplataT,
          templateArgs: Vector[ITemplataT[ITemplataType]],
          verifyConclusions: Boolean):
        IResolveOutcome[InterfaceTT] = {
          vassert(verifyConclusions) // If we dont want to be verifying, we shouldnt be calling this func
          structCompiler.resolveInterface(state, callingEnv, callRange, callLocation, templata, templateArgs)
        }

        override def resolveStruct(
          callingEnv: IInDenizenEnvironmentT,
          state: CompilerOutputs,
          callRange: List[RangeS],
          callLocation: LocationInDenizen,
          templata: StructDefinitionTemplataT,
          templateArgs: Vector[ITemplataT[ITemplataType]],
          verifyConclusions: Boolean):
        IResolveOutcome[StructTT] = {
          vassert(verifyConclusions) // If we dont want to be verifying, we shouldnt be calling this func
          structCompiler.resolveStruct(state, callingEnv, callRange,callLocation, templata, templateArgs)
        }

        override def resolveFunction(
          callingEnv: IInDenizenEnvironmentT,
          state: CompilerOutputs,
          range: List[RangeS],
          callLocation: LocationInDenizen,
          name: StrI,
          coords: Vector[CoordT],
          contextRegion: RegionT,
          verifyConclusions: Boolean):
        Result[StampFunctionSuccess, FindFunctionFailure] = {
          overloadResolver.findFunction(
            callingEnv,
            state,
            range,
            callLocation,
            interner.intern(CodeNameS(interner.intern(name))),
            Vector.empty,
            Vector.empty,
            contextRegion,
            coords,
            Vector.empty,
            true,
            verifyConclusions)
        }

        override def resolveStaticSizedArrayKind(
          coutputs: CompilerOutputs,
          mutability: ITemplataT[MutabilityTemplataType],
          variability: ITemplataT[VariabilityTemplataType],
          size: ITemplataT[IntegerTemplataType],
          element: CoordT,
          region: RegionT):
        StaticSizedArrayTT = {
          arrayCompiler.resolveStaticSizedArray(mutability, variability, size, element, region)
        }

        override def resolveRuntimeSizedArrayKind(
          coutputs: CompilerOutputs,
          element: CoordT,
          arrayMutability: ITemplataT[MutabilityTemplataType],
          region: RegionT):
        RuntimeSizedArrayTT = {
          arrayCompiler.resolveRuntimeSizedArray(element, arrayMutability, region)
        }

        override def resolveImpl(
          callingEnv: IInDenizenEnvironmentT,
          state: CompilerOutputs,
          range: List[RangeS],
          callLocation: LocationInDenizen,
          subKind: ISubKindTT,
          superKind: ISuperKindTT):
        IsParentResult = {
          implCompiler.isParent(state, callingEnv, range, callLocation, subKind, superKind)
        }
      })
  val convertHelper =
    new ConvertHelper(
      opts,
      new IConvertHelperDelegate {
        override def isParent(
          coutputs: CompilerOutputs,
          callingEnv: IInDenizenEnvironmentT,
          parentRanges: List[RangeS],
          callLocation: LocationInDenizen,
          descendantCitizenRef: ISubKindTT,
          ancestorInterfaceRef: ISuperKindTT):
        IsParentResult = {
          implCompiler.isParent(
            coutputs, callingEnv, parentRanges, callLocation, descendantCitizenRef, ancestorInterfaceRef)
        }
      })

  val structCompiler: StructCompiler =
    new StructCompiler(
      opts,
      interner,
      keywords,
      nameTranslator,
      templataCompiler,
      inferCompiler,
      new IStructCompilerDelegate {
        override def evaluateGenericFunctionFromNonCallForHeader(
          coutputs: CompilerOutputs,
          parentRanges: List[RangeS],
          callLocation: LocationInDenizen,
          functionTemplata: FunctionTemplataT,
          verifyConclusions: Boolean):
        FunctionHeaderT = {
          functionCompiler.evaluateGenericFunctionFromNonCall(
            coutputs, parentRanges, callLocation, functionTemplata, verifyConclusions)
        }

        override def scoutExpectedFunctionForPrototype(
          env: IInDenizenEnvironmentT,
          coutputs: CompilerOutputs,
          callRange: List[RangeS],
          callLocation: LocationInDenizen,
          functionName: IImpreciseNameS,
          explicitTemplateArgRulesS: Vector[IRulexSR],
          explicitTemplateArgRunesS: Vector[IRuneS],
          contextRegion: RegionT,
          args: Vector[CoordT],
          extraEnvsToLookIn: Vector[IInDenizenEnvironmentT],
          exact: Boolean,
          verifyConclusions: Boolean):
        StampFunctionSuccess = {
          overloadResolver.findFunction(
            env,
            coutputs,
            callRange,
            callLocation,
            functionName,
            explicitTemplateArgRulesS,
            explicitTemplateArgRunesS,
            contextRegion,
            args,
            extraEnvsToLookIn,
            exact,
            verifyConclusions) match {
            case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
            case Ok(x) => x
          }
        }
      })

  val implCompiler: ImplCompiler =
    new ImplCompiler(opts, interner, nameTranslator, structCompiler, templataCompiler, inferCompiler)

  val functionCompiler: FunctionCompiler =
    new FunctionCompiler(opts, interner, keywords, nameTranslator, templataCompiler, inferCompiler, convertHelper, structCompiler,
      new IFunctionCompilerDelegate {
    override def evaluateBlockStatements(
        coutputs: CompilerOutputs,
        startingNenv: NodeEnvironmentT,
        nenv: NodeEnvironmentBox,
        life: LocationInFunctionEnvironmentT,
      ranges: List[RangeS],
      callLocation: LocationInDenizen,
        region: RegionT,
        exprs: BlockSE
    ): (ReferenceExpressionTE, Set[CoordT]) = {
      expressionCompiler.evaluateBlockStatements(
        coutputs, startingNenv, nenv, life, ranges, callLocation, region, exprs)
    }

    override def translatePatternList(
      coutputs: CompilerOutputs,
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironmentT,
      ranges: List[RangeS],
      callLocation: LocationInDenizen,
      region: RegionT,
      patterns1: Vector[AtomSP],
      patternInputExprs2: Vector[ReferenceExpressionTE]
    ): ReferenceExpressionTE = {
      expressionCompiler.translatePatternList(coutputs, nenv, life, ranges, callLocation, patterns1, patternInputExprs2, region)
    }

//    override def evaluateParent(env: IEnvironment, coutputs: CompilerOutputs, callRange: List[RangeS], sparkHeader: FunctionHeaderT): Unit = {
//      virtualCompiler.evaluateParent(env, coutputs, callRange, sparkHeader)
//    }

    override def generateFunction(
      functionCompilerCore: FunctionCompilerCore,
      generator: IFunctionGenerator,
      fullEnv: FunctionEnvironmentT,
      coutputs: CompilerOutputs,
      life: LocationInFunctionEnvironmentT,
      callRange: List[RangeS],
      originFunction: Option[FunctionA],
      paramCoords: Vector[ParameterT],
      maybeRetCoord: Option[CoordT]):
    FunctionHeaderT = {
      generator.generate(

        functionCompilerCore, structCompiler, destructorCompiler, arrayCompiler, fullEnv, coutputs, life, callRange, originFunction, paramCoords, maybeRetCoord)
    }
  })
  val overloadResolver: OverloadResolver = new OverloadResolver(opts, interner, keywords, templataCompiler, inferCompiler, functionCompiler, implCompiler)
  val destructorCompiler: DestructorCompiler = new DestructorCompiler(opts, interner, keywords, structCompiler, overloadResolver)

  val virtualCompiler = new VirtualCompiler(opts, interner, overloadResolver)

  val sequenceCompiler = new SequenceCompiler(opts, interner, keywords, structCompiler, templataCompiler)

  val arrayCompiler: ArrayCompiler =
    new ArrayCompiler(
      opts,
      interner,
      keywords,
      inferCompiler,
      overloadResolver,
      destructorCompiler,
      templataCompiler)

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
      implCompiler,
      sequenceCompiler,
      overloadResolver,
      destructorCompiler,
      implCompiler,
      convertHelper,
      new IExpressionCompilerDelegate {
        override def evaluateTemplatedFunctionFromCallForPrototype(
            coutputs: CompilerOutputs,
            callingEnv: IInDenizenEnvironmentT, // See CSSNCE
            callRange: List[RangeS],
          callLocation: LocationInDenizen,
            functionTemplata: FunctionTemplataT,
            explicitTemplateArgs: Vector[ITemplataT[ITemplataType]],
          contextRegion: RegionT,
            args: Vector[CoordT]):
        IEvaluateFunctionResult = {
          functionCompiler.evaluateTemplatedFunctionFromCallForPrototype(
            coutputs, callRange, callLocation, callingEnv, functionTemplata, explicitTemplateArgs, contextRegion, args, true)
        }

        override def evaluateGenericFunctionFromCallForPrototype(
          coutputs: CompilerOutputs,
          callingEnv: IInDenizenEnvironmentT, // See CSSNCE
          callRange: List[RangeS],
          callLocation: LocationInDenizen,
          functionTemplata: FunctionTemplataT,
          explicitTemplateArgs: Vector[ITemplataT[ITemplataType]],
          contextRegion: RegionT,
          args: Vector[CoordT]):
        IEvaluateFunctionResult = {
          functionCompiler.evaluateGenericLightFunctionFromCallForPrototype(
            coutputs, callRange, callLocation, callingEnv, functionTemplata, explicitTemplateArgs, contextRegion, args)
        }

        override def evaluateClosureStruct(
            coutputs: CompilerOutputs,
            containingNodeEnv: NodeEnvironmentT,
            callRange: List[RangeS],
          callLocation: LocationInDenizen,
            name: IFunctionDeclarationNameS,
            function1: FunctionA):
        StructTT = {
          functionCompiler.evaluateClosureStruct(coutputs, containingNodeEnv, callRange, callLocation, name, function1, true)
        }
      })

  val edgeCompiler = new EdgeCompiler(interner, keywords, functionCompiler, overloadResolver, implCompiler)

  val functorHelper = new FunctorHelper(interner, keywords)
  val structConstructorMacro = new StructConstructorMacro(opts, interner, keywords, nameTranslator, destructorCompiler)
  val structDropMacro = new StructDropMacro(interner, keywords, nameTranslator, destructorCompiler)
//  val structFreeMacro = new StructFreeMacro(interner, keywords, nameTranslator, destructorCompiler)
//  val interfaceFreeMacro = new InterfaceFreeMacro(interner, keywords, nameTranslator)
  val asSubtypeMacro = new AsSubtypeMacro(keywords, implCompiler, expressionCompiler, destructorCompiler)
  val rsaLenMacro = new RSALenMacro(keywords)
  val rsaMutNewMacro = new RSAMutableNewMacro(interner, keywords, arrayCompiler, destructorCompiler)
  val rsaImmNewMacro = new RSAImmutableNewMacro(interner, keywords, overloadResolver, arrayCompiler, destructorCompiler)
  val rsaPushMacro = new RSAMutablePushMacro(interner, keywords)
  val rsaPopMacro = new RSAMutablePopMacro(interner, keywords)
  val rsaCapacityMacro = new RSAMutableCapacityMacro(interner, keywords)
  val ssaLenMacro = new SSALenMacro(keywords)
  val rsaDropMacro = new RSADropIntoMacro(keywords, arrayCompiler)
  val ssaDropMacro = new SSADropIntoMacro(keywords, arrayCompiler)
//  val ssaLenMacro = new SSALenMacro(keywords)
//  val implDropMacro = new ImplDropMacro(interner, nameTranslator)
//  val implFreeMacro = new ImplFreeMacro(interner, keywords, nameTranslator)
  val interfaceDropMacro = new InterfaceDropMacro(interner, keywords, nameTranslator)
  val abstractBodyMacro = new AbstractBodyMacro(interner, keywords, overloadResolver)
  val lockWeakMacro = new LockWeakMacro(keywords, expressionCompiler)
  val sameInstanceMacro = new SameInstanceMacro(keywords)
  val anonymousInterfaceMacro =
    new AnonymousInterfaceMacro(
      opts, interner, keywords, nameTranslator, overloadResolver, structCompiler, structConstructorMacro, structDropMacro)


  def evaluate(packageToProgramA: PackageCoordinateMap[ProgramA]): Result[HinputsT, ICompileErrorT] = {
    try {
      Profiler.frame(() => {
        println("Using overload index? " + opts.globalOptions.useOverloadIndex)

        val nameToStructDefinedMacro =
          Map(
            structConstructorMacro.macroName -> structConstructorMacro,
            structDropMacro.macroName -> structDropMacro)//,
//            structFreeMacro.macroName -> structFreeMacro,
//            implFreeMacro.macroName -> implFreeMacro)
        val nameToInterfaceDefinedMacro =
          Map(
            interfaceDropMacro.macroName -> interfaceDropMacro,
//            interfaceFreeMacro.macroName -> interfaceFreeMacro,
            anonymousInterfaceMacro.macroName -> anonymousInterfaceMacro)
        val nameToImplDefinedMacro = Map[StrI, IOnImplDefinedMacro]()
        val nameToFunctionBodyMacro =
          Map(
            abstractBodyMacro.generatorId -> abstractBodyMacro,
            structConstructorMacro.generatorId -> structConstructorMacro,
//            structFreeMacro.freeGeneratorId -> structFreeMacro,
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
            lockWeakMacro.generatorId -> lockWeakMacro,
            sameInstanceMacro.generatorId -> sameInstanceMacro,
            asSubtypeMacro.generatorId -> asSubtypeMacro)

        val idAndEnvEntry: Vector[(IdT[INameT], IEnvEntry)] =
          packageToProgramA.flatMap({ case (coord, programA) =>
            val packageName = IdT(coord, Vector(), interner.intern(PackageTopLevelNameT()))
            programA.structs.map(structA => {
              val structNameT = packageName.addStep(nameTranslator.translateNameStep(structA.name))
              Vector((structNameT, StructEnvEntry(structA))) ++
                preprocessStruct(nameToStructDefinedMacro, structNameT, structA)
            }) ++
            programA.interfaces.map(interfaceA => {
              val interfaceNameT = packageName.addStep(nameTranslator.translateNameStep(interfaceA.name))
              Vector((interfaceNameT, InterfaceEnvEntry(interfaceA))) ++
                preprocessInterface(nameToInterfaceDefinedMacro, interfaceNameT, interfaceA)
            }) ++
            programA.impls.map(implA => {
              val implNameT = packageName.addStep(nameTranslator.translateImplName(implA.name))
              Vector((implNameT, ImplEnvEntry(implA)))
            }) ++
            programA.functions.map(functionA => {
              val functionNameT = packageName.addStep(nameTranslator.translateGenericFunctionName(functionA.name))
              Vector((functionNameT, FunctionEnvEntry(functionA)))
            })
          }).flatten.flatten.toVector

        val namespaceNameToTemplatas =
          idAndEnvEntry
            .map({ case (name, envEntry) =>
              (name.copy(localName = interner.intern(PackageTopLevelNameT())), name.localName, envEntry)
            })
            .groupBy(_._1)
            .map({ case (packageId, envEntries) =>
              packageId ->
                TemplatasStore(packageId, Map(), Map())
                  .addEntries(interner, envEntries.map({ case (_, b, c) => (b, c) }))
             }).toMap

        val globalEnv =
          GlobalEnvironment(
            functorHelper,
            structConstructorMacro,
            structDropMacro,
//            structFreeMacro,
            interfaceDropMacro,
//            interfaceFreeMacro,
            anonymousInterfaceMacro,
            nameToStructDefinedMacro,
            nameToInterfaceDefinedMacro,
            nameToImplDefinedMacro,
            nameToFunctionBodyMacro,
            namespaceNameToTemplatas,
            // Bulitins
            env.TemplatasStore(IdT(PackageCoordinate.BUILTIN(interner, keywords), Vector(), interner.intern(PackageTopLevelNameT())), Map(), Map()).addEntries(
              interner,
              Vector[(INameT, IEnvEntry)](
                interner.intern(PrimitiveNameT(keywords.int)) -> TemplataEnvEntry(KindTemplataT(IntT.i32)),
                interner.intern(PrimitiveNameT(keywords.i64)) -> TemplataEnvEntry(KindTemplataT(IntT.i64)),
                interner.intern(PrimitiveNameT(keywords.Array)) -> TemplataEnvEntry(RuntimeSizedArrayTemplateTemplataT()),
                interner.intern(PrimitiveNameT(keywords.StaticArray)) -> TemplataEnvEntry(StaticSizedArrayTemplateTemplataT()),
                interner.intern(PrimitiveNameT(keywords.bool)) -> TemplataEnvEntry(KindTemplataT(BoolT())),
                interner.intern(PrimitiveNameT(keywords.float)) -> TemplataEnvEntry(KindTemplataT(FloatT())),
                interner.intern(PrimitiveNameT(keywords.__Never)) -> TemplataEnvEntry(KindTemplataT(NeverT(false))),
                interner.intern(PrimitiveNameT(keywords.str)) -> TemplataEnvEntry(KindTemplataT(StrT())),
                interner.intern(PrimitiveNameT(keywords.void)) -> TemplataEnvEntry(KindTemplataT(VoidT())))))

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

        arrayCompiler.compileStaticSizedArray(globalEnv, coutputs)
        arrayCompiler.compileRuntimeSizedArray(globalEnv, coutputs)


        // Indexing phase

        globalEnv.nameToTopLevelEnvironment.foreach({ case (packageId, templatas) =>
          val env = PackageEnvironmentT.makeTopLevelEnvironment(globalEnv, packageId)
          templatas.entriesByNameT.map({ case (name, entry) =>
            entry match {
              case StructEnvEntry(structA) => {
                val templata = StructDefinitionTemplataT(env, structA)
                structCompiler.precompileStruct(coutputs, templata)
              }
              case InterfaceEnvEntry(interfaceA) => {
                val templata = InterfaceDefinitionTemplataT(env, interfaceA)
                structCompiler.precompileInterface(coutputs, templata)
              }
              case _ =>
            }
          })
        })

        // Compiling phase

        globalEnv.nameToTopLevelEnvironment.foreach({ case (packageId, templatas) =>
          val packageEnv = PackageEnvironmentT.makeTopLevelEnvironment(globalEnv, packageId)
          templatas.entriesByNameT.map({ case (name, entry) =>
            entry match {
              case StructEnvEntry(structA) => {
                val templata = StructDefinitionTemplataT(packageEnv, structA)
                structCompiler.compileStruct(coutputs, List(), LocationInDenizen(Vector()), templata)

                val maybeExport =
                  structA.attributes.collectFirst { case e@ExportS(_) => e }
                maybeExport match {
                  case None =>
                  case Some(ExportS(packageCoordinate)) => {
                    val templateName = interner.intern(ExportTemplateNameT(structA.range.begin))
                    val templateId = IdT(packageId.packageCoord, Vector(), templateName)
                    val exportOuterEnv =
                      ExportEnvironmentT(
                        globalEnv, packageEnv, templateId, TemplatasStore(templateId, Map(), Map()))

                    val regionPlaceholder = RegionT()

                    val placeholderedExportName = interner.intern(ExportNameT(templateName, RegionT()))
                    val placeholderedExportId = templateId.copy(localName = placeholderedExportName)
                    val exportEnv =
                      ExportEnvironmentT(
                        globalEnv, packageEnv, placeholderedExportId, TemplatasStore(placeholderedExportId, Map(), Map()))

                    val exportPlaceholderedStruct =
                      structCompiler.resolveStruct(
                        coutputs, exportEnv, List(structA.range), LocationInDenizen(Vector()), templata, Vector()) match {
                        case ResolveSuccess(kind) => kind
                        case ResolveFailure(range, reason) => {
                          throw CompileErrorExceptionT(CouldntEvaluateStruct(range, reason))
                        }
                      }

                    val exportName =
                      structA.name match {
                        case TopLevelCitizenDeclarationNameS(name, range) => name
                        case other => vwat(other)
                      }

                    coutputs.addKindExport(
                      structA.range, exportPlaceholderedStruct, placeholderedExportId, exportName)
                  }
                }
              }
              case InterfaceEnvEntry(interfaceA) => {
                val templata = InterfaceDefinitionTemplataT(packageEnv, interfaceA)
                structCompiler.compileInterface(coutputs, List(), LocationInDenizen(Vector()), templata)

                val maybeExport =
                  interfaceA.attributes.collectFirst { case e@ExportS(_) => e }
                maybeExport match {
                  case None =>
                  case Some(ExportS(packageCoordinate)) => {
                    val templateName = interner.intern(ExportTemplateNameT(interfaceA.range.begin))
                    val templateId = IdT(packageId.packageCoord, Vector(), templateName)
                    val exportOuterEnv =
                      ExportEnvironmentT(
                        globalEnv, packageEnv, templateId, TemplatasStore(templateId, Map(), Map()))

                    val placeholderedExportName = interner.intern(ExportNameT(templateName, RegionT()))
                    val placeholderedExportId = templateId.copy(localName = placeholderedExportName)
                    val exportEnv =
                      ExportEnvironmentT(
                        globalEnv, packageEnv, placeholderedExportId, TemplatasStore(placeholderedExportId, Map(), Map()))

                    val exportPlaceholderedKind =
                      structCompiler.resolveInterface(
                        coutputs, exportEnv, List(interfaceA.range), LocationInDenizen(Vector()), templata, Vector()) match {
                        case ResolveSuccess(kind) => kind
                        case ResolveFailure(range, reason) => {
                          throw CompileErrorExceptionT(CouldntEvaluateInterface(range, reason))
                        }
                      }

                    val exportName =
                      interfaceA.name match {
                        case TopLevelCitizenDeclarationNameS(name, range) => name
                        case other => vwat(other)
                      }

                    coutputs.addKindExport(
                      interfaceA.range, exportPlaceholderedKind, placeholderedExportId, exportName)
                  }
                }
              }
              case _ =>
            }
          })
        })

        globalEnv.nameToTopLevelEnvironment.foreach({ case (packageId, templatas) =>
          val env = PackageEnvironmentT.makeTopLevelEnvironment(globalEnv, packageId)
          templatas.entriesByNameT.map({ case (name, entry) =>
            entry match {
              case ImplEnvEntry(impl) => {
                implCompiler.compileImpl(coutputs, LocationInDenizen(Vector()), ImplDefinitionTemplataT(env, impl))
              }
              case _ =>
            }
          })
        })

        globalEnv.nameToTopLevelEnvironment.foreach({
          // Anything in global scope should be compiled
          case (packageId @ IdT(_, Vector(), PackageTopLevelNameT()), templatas) => {
            val packageEnv = PackageEnvironmentT.makeTopLevelEnvironment(globalEnv, packageId)
            templatas.entriesByNameT.map({ case (name, entry) =>
              entry match {
                case FunctionEnvEntry(functionA) => {
                  val templata = FunctionTemplataT(packageEnv, functionA)
                  val header =
                  functionCompiler.evaluateGenericFunctionFromNonCall(
                      coutputs, List(), LocationInDenizen(Vector()), templata, true)

                  functionA.attributes.collectFirst({ case e @ ExternS(_) => e }) match {
                    case None =>
                    case Some(ExternS(packageCoord)) => {
                      val externName =
                        functionA.name match {
                          case FunctionNameS(name, range) => name
                          case other => vwat(other)
                        }

                      val templateName = interner.intern(ExternTemplateNameT(functionA.range.begin))
                      val templateId = IdT(packageId.packageCoord, Vector(), templateName)

                      val regionPlaceholder = RegionT()
                      val placeholderedExternName = interner.intern(ExternNameT(templateName, RegionT()))
                      val placeholderedExternId = templateId.copy(localName = placeholderedExternName)
                      val externEnv =
                        ExternEnvironmentT(
                          globalEnv, packageEnv, placeholderedExternId, TemplatasStore(placeholderedExternId, Map(), Map()))
                      // We evaluate this and then don't do anything for it on purpose, we just do
                      // this to cause the compiler to make instantiation bounds for all the types
                      // in terms of this extern. That way, further below, when we do the
                      // substituting templatas, the bounds are already made for these types.
                      val externPlaceholderedWrapperPrototype =
                        functionCompiler.evaluateGenericLightFunctionFromCallForPrototype(
                          coutputs,
                          List(functionA.range),
                          LocationInDenizen(Vector()),
                          externEnv,
                          templata,
                          Vector(),
                          regionPlaceholder,
                          Vector()) match {
                          case EvaluateFunctionSuccess(prototype, inferences) => prototype.prototype
                          case EvaluateFunctionFailure(reason) => {
                            throw CompileErrorExceptionT(CouldntEvaluateFunction(List(functionA.range), reason))
                          }
                        }

//                      val externPerspectivedParams =
//                        header.params.map(_.tyype).map(typeT => {
//                          TemplataCompiler.substituteTemplatasInCoord(
//                            coutputs,
//                            interner,
//                            keywords,
//                            TemplataCompiler.getTemplate(header.id),
//                            Vector(regionPlaceholder),
//                            InheritBoundsFromTypeItself,
//                            typeT)
//                        })
//                      val externPerspectivedReturn =
//                        TemplataCompiler.substituteTemplatasInCoord(
//                          coutputs,
//                          interner,
//                          keywords,
//                          TemplataCompiler.getTemplate(header.id),
//                          Vector(regionPlaceholder),
//                          InheritBoundsFromTypeItself,
//                          header.returnType)
//                      val externFunctionId =
//                        IdT(
//                          packageCoord,
//                          Vector.empty,
//                          interner.intern(ExternFunctionNameT(
//                            externName, externPerspectivedParams)))
//                      val externPrototype = PrototypeT(externFunctionId, externPerspectivedReturn)

                      // We don't actually want to call the wrapper function, we want to call the extern.
                      // The extern's prototype is always similar to the wrapper function, so we do
                      // a straightforward replace of the names.
                      // We don't have to worry about placeholders, they're already phrased in terms
                      // of the calling FunctionExternT.
                      val externPrototype =
                        externPlaceholderedWrapperPrototype match {
                          case PrototypeT(IdT(packageCoord, steps, FunctionNameT(FunctionTemplateNameT(humanName, codeLocation), templateArgs, params)), returnType) => {
                            PrototypeT(
                              IdT(
                                packageCoord,
                                steps,
                                interner.intern(ExternFunctionNameT(humanName, params))),
                              returnType)
                          }
                          case other => vwat(other)
                        }
                      // Though, we do need to add some instantiation bounds for this new IdT we
                      // just made.
                      coutputs.addInstantiationBounds(
                        externPrototype.id,
                        vassertSome(coutputs.getInstantiationBounds(externPlaceholderedWrapperPrototype.id)))

                      coutputs.addFunctionExtern(
                        functionA.range, placeholderedExternId, externPrototype, externName)
                    }
                  }

                  val maybeExport =
                    functionA.attributes.collectFirst { case e@ExportS(_) => e }
                  maybeExport match {
                    case None =>
                    case Some(ExportS(packageCoordinate)) => {
                      val templateName = interner.intern(ExportTemplateNameT(functionA.range.begin))
                      val templateId = IdT(packageId.packageCoord, Vector(), templateName)
                      val exportOuterEnv =
                        ExportEnvironmentT(
                          globalEnv, packageEnv, templateId, TemplatasStore(templateId, Map(), Map()))

                      val regionPlaceholder = RegionT()

                      val placeholderedExportName = interner.intern(ExportNameT(templateName, regionPlaceholder))
                      val placeholderedExportId = templateId.copy(localName = placeholderedExportName)
                      val exportEnv =
                        ExportEnvironmentT(
                          globalEnv, packageEnv, placeholderedExportId, TemplatasStore(placeholderedExportId, Map(), Map()))

                      val exportPlaceholderedPrototype =
                        functionCompiler.evaluateGenericLightFunctionFromCallForPrototype(
                          coutputs, List(functionA.range), LocationInDenizen(Vector()), exportEnv, templata, Vector(), regionPlaceholder, Vector()) match {
                          case EvaluateFunctionSuccess(prototype, inferences) => prototype.prototype
                          case EvaluateFunctionFailure(reason) => {
                            throw CompileErrorExceptionT(CouldntEvaluateFunction(List(functionA.range), reason))
                          }
                        }

                      val exportName =
                        functionA.name match {
                          case FunctionNameS(name, range) => name
                          case other => vwat(other)
                        }

                      coutputs.addFunctionExport(
                        functionA.range, exportPlaceholderedPrototype, placeholderedExportId, exportName)
                    }
                  }
                }
                case _ =>
              }
            })
          }
          // Anything underneath something else should be skipped, we'll evaluate those later on.
          case (IdT(_, anythingElse, PackageTopLevelNameT()), _) =>
        })

        packageToProgramA.flatMap({ case (packageCoord, programA) =>
          val packageEnv =
            PackageEnvironmentT.makeTopLevelEnvironment(
              globalEnv, IdT(packageCoord, Vector(), interner.intern(PackageTopLevelNameT())))

          programA.exports.foreach({ case ExportAsA(range, exportedName, rules, runeToType, typeRuneA) =>
            val typeRuneT = typeRuneA

            val templateName = interner.intern(ExportTemplateNameT(range.begin))
            val templateId = IdT(packageCoord, Vector(), templateName)
            val exportOuterEnv =
              ExportEnvironmentT(
                globalEnv, packageEnv, templateId, TemplatasStore(templateId, Map(), Map()))

            val regionPlaceholder = RegionT()

            val placeholderedExportName = interner.intern(ExportNameT(templateName, regionPlaceholder))
            val placeholderedExportId = templateId.copy(localName = placeholderedExportName)
            val exportEnv =
              ExportEnvironmentT(
                globalEnv, packageEnv, placeholderedExportId, TemplatasStore(placeholderedExportId, Map(), Map()))

            val CompleteCompilerSolve(_, templataByRune, _, Vector(), Vector()) =
              inferCompiler.solveExpectComplete(
                InferEnv(exportEnv, List(range), LocationInDenizen(Vector()), exportEnv, regionPlaceholder),
                coutputs, rules, runeToType, List(range),
                LocationInDenizen(Vector()), Vector(), Vector(), true, true, Vector())
              templataByRune.get(typeRuneT.rune) match {
                case Some(KindTemplataT(kind)) => {
                coutputs.addKindExport(range, kind, placeholderedExportId, exportedName)
                }
                case Some(prototype) => {
                  vimpl()
                }
                case _ => vfail()
              }
          })
        })

//        breakable {
//          while (true) {
        val denizensAtStart = coutputs.countDenizens()

        val builtinPackageCoord = PackageCoordinate.BUILTIN(interner, keywords)
        val rootPackageEnv =
          PackageEnvironmentT.makeTopLevelEnvironment(
            globalEnv,
            IdT(builtinPackageCoord, Vector(), interner.intern(PackageTopLevelNameT())))

//        val freeImpreciseName = interner.intern(FreeImpreciseNameS())
//        val dropImpreciseName = interner.intern(CodeNameS(keywords.drop))

//        val immutableKinds =
//          coutputs.getAllStructs().filter(_.mutability == MutabilityTemplata(ImmutableT)).map(_.templateName) ++
//            coutputs.getAllInterfaces().filter(_.mutability == ImmutableT).map(_.templateName) ++
//            coutputs.getAllRuntimeSizedArrays().filter(_.mutability == MutabilityTemplata(ImmutableT)) ++
//            coutputs.getAllStaticSizedArrays().filter(_.mutability == MutabilityTemplata(ImmutableT))
//        immutableKinds.foreach(kind => {
//          val kindEnv = coutputs.getEnvForTemplate(kind)
//          functionCompiler.evaluateGenericFunctionFromNonCall(
//            coutputs,
//            kindEnv.lookupNearestWithImpreciseName(freeImpreciseName, Set(ExpressionLookupContext)) match {
//              case Some(ft@FunctionTemplata(_, _)) => ft
//              case _ => throw CompileErrorExceptionT(RangedInternalErrorT(RangeS.internal(interner, -1663), "Couldn't find free for immutable struct!"))
//            })
//          functionCompiler.evaluateGenericFunctionFromNonCall(
//            coutputs,
//            kindEnv.lookupNearestWithImpreciseName(dropImpreciseName, Set(ExpressionLookupContext)) match {
//              case Some(ft@FunctionTemplata(_, _)) => ft
//              case _ => throw CompileErrorExceptionT(RangedInternalErrorT(RangeS.internal(interner, -1663), "Couldn't find free for immutable struct!"))
//            })
//        })

        val (interfaceEdgeBlueprints, interfaceToSubCitizenToEdge) =
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

        while (coutputs.peekNextDeferredFunctionBodyCompile().nonEmpty || coutputs.peekNextDeferredFunctionCompile().nonEmpty) {
          while (coutputs.peekNextDeferredFunctionCompile().nonEmpty) {
            val nextDeferredEvaluatingFunction = coutputs.peekNextDeferredFunctionCompile().get
            // No, IntelliJ, I assure you this has side effects
            (nextDeferredEvaluatingFunction.call) (coutputs)
            coutputs.markDeferredFunctionCompiled(nextDeferredEvaluatingFunction.name)
          }

          // No particular reason for this if/while mismatch, it just feels a bit better to get started on more before
          // we finish any.
          if (coutputs.peekNextDeferredFunctionBodyCompile().nonEmpty) {
            val nextDeferredEvaluatingFunctionBody = coutputs.peekNextDeferredFunctionBodyCompile().get
            // No, IntelliJ, I assure you this has side effects
            (nextDeferredEvaluatingFunctionBody.call) (coutputs)
            coutputs.markDeferredFunctionBodyCompiled(nextDeferredEvaluatingFunctionBody.prototypeT)
          }
        }


//            val denizensAtEnd = coutputs.countDenizens()
//            if (denizensAtStart == denizensAtEnd)
//              break
//          }
//        }

//        // NEVER ZIP TWO SETS TOGETHER
//        val edgeBlueprintsAsList = edgeBlueprints.toVector
//        val interfaceToEdgeBlueprints = edgeBlueprintsAsList.map(_.interface).zip(edgeBlueprintsAsList).toMap;
//
//        interfaceToEdgeBlueprints.foreach({ case (interfaceTT, edgeBlueprint) =>
//          vassert(edgeBlueprint.interface == interfaceTT)
//        })

        ensureDeepExports(coutputs)

        val (
          reachableInterfaces,
          reachableStructs,
//          reachableSSAs,
//          reachableRSAs,
          reachableFunctions) =
//        if (opts.treeShakingEnabled) {
//          Profiler.frame(() => {
//            val reachables = Reachability.findReachables(coutputs, interfaceEdgeBlueprints, interfaceToStructToMethods)
//
//            val categorizedFunctions = coutputs.getAllFunctions().groupBy(f => reachables.functions.contains(f.header.toSignature))
//            val reachableFunctions = categorizedFunctions.getOrElse(true, Vector.empty)
//            val unreachableFunctions = categorizedFunctions.getOrElse(false, Vector.empty)
//            unreachableFunctions.foreach(f => debugOut("Shaking out unreachable: " + f.header.fullName))
//            reachableFunctions.foreach(f => debugOut("Including: " + f.header.fullName))
//
//            val categorizedSSAs = coutputs.getAllStaticSizedArrays().groupBy(f => reachables.staticSizedArrays.contains(f))
//            val reachableSSAs = categorizedSSAs.getOrElse(true, Vector.empty)
//            val unreachableSSAs = categorizedSSAs.getOrElse(false, Vector.empty)
//            unreachableSSAs.foreach(f => debugOut("Shaking out unreachable: " + f))
//            reachableSSAs.foreach(f => debugOut("Including: " + f))
//
//            val categorizedRSAs = coutputs.getAllRuntimeSizedArrays().groupBy(f => reachables.runtimeSizedArrays.contains(f))
//            val reachableRSAs = categorizedRSAs.getOrElse(true, Vector.empty)
//            val unreachableRSAs = categorizedRSAs.getOrElse(false, Vector.empty)
//            unreachableRSAs.foreach(f => debugOut("Shaking out unreachable: " + f))
//            reachableRSAs.foreach(f => debugOut("Including: " + f))
//
//            val categorizedStructs = coutputs.getAllStructs().groupBy(f => reachables.structs.contains(f.getRef))
//            val reachableStructs = categorizedStructs.getOrElse(true, Vector.empty)
//            val unreachableStructs = categorizedStructs.getOrElse(false, Vector.empty)
//            unreachableStructs.foreach(f => {
//              debugOut("Shaking out unreachable: " + f.fullName)
//            })
//            reachableStructs.foreach(f => debugOut("Including: " + f.fullName))
//
//            val categorizedInterfaces = coutputs.getAllInterfaces().groupBy(f => reachables.interfaces.contains(f.getRef))
//            val reachableInterfaces = categorizedInterfaces.getOrElse(true, Vector.empty)
//            val unreachableInterfaces = categorizedInterfaces.getOrElse(false, Vector.empty)
//            unreachableInterfaces.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
//            reachableInterfaces.foreach(f => debugOut("Including: " + f.fullName))
//
//            val categorizedEdges =
//              edges.groupBy(f => reachables.edges.contains(f))
//            val reachableEdges = categorizedEdges.getOrElse(true, Vector.empty)
//            val unreachableEdges = categorizedEdges.getOrElse(false, Vector.empty)
//            unreachableEdges.foreach(f => debugOut("Shaking out unreachable: " + f))
//            reachableEdges.foreach(f => debugOut("Including: " + f))
//
//            (reachableInterfaces, reachableStructs, reachableSSAs, reachableRSAs, reachableFunctions)
//          })
//        } else {
          (
            coutputs.getAllInterfaces(),
            coutputs.getAllStructs(),
//            coutputs.getAllStaticSizedArrays(),
//            coutputs.getAllRuntimeSizedArrays(),
            coutputs.getAllFunctions())
//        }

//      val allKinds =
//        reachableStructs.map(_.place) ++ reachableInterfaces.map(_.getRef) ++ reachableSSAs ++ reachableRSAs
//      val reachableImmKinds: Vector[KindT] =
//        allKinds
//          .filter({
//            case s@StructTT(_) => coutputs.lookupMutability(s) == ImmutableT
//            case i@InterfaceTT(_) => coutputs.lookupMutability(i) == ImmutableT
//            case contentsStaticSizedArrayTT(_, m, _, _) => m == ImmutableT
//            case contentsRuntimeSizedArrayTT(m, _) => m == ImmutableT
//            case _ => true
//          })
//          .toVector
//      val reachableImmKindToDestructor = reachableImmKinds.zip(reachableImmKinds.map(coutputs.findImmDestructor)).toMap

      val hinputs =
          vale.typing.HinputsT(
            reachableInterfaces.toVector,
            reachableStructs.toVector,
            reachableFunctions.toVector,
//            Map(), // Will be populated by instantiator
            interfaceEdgeBlueprints.groupBy(_.interface).mapValues(vassertOne(_)),
            interfaceToSubCitizenToEdge,
            coutputs.getInstantiationNameToFunctionBoundToRune(),
            coutputs.getKindExports,
            coutputs.getFunctionExports,
            coutputs.getKindExterns,
            coutputs.getFunctionExterns)

        vassert(reachableFunctions.toVector.map(_.header.id).distinct.size == reachableFunctions.toVector.map(_.header.id).size)

        Ok(hinputs)
      })
    } catch {
      case CompileErrorExceptionT(err) => Err(err)
    }
  }

  private def preprocessStruct(
    nameToStructDefinedMacro: Map[StrI, IOnStructDefinedMacro],
    structNameT: IdT[INameT],
    structA: StructA): Vector[(IdT[INameT], IEnvEntry)] = {
    val defaultCalledMacros =
      Vector(
        MacroCallS(structA.range, CallMacroP, keywords.DeriveStructConstructor),
        MacroCallS(structA.range, CallMacroP, keywords.DeriveStructDrop))//,
//        MacroCallS(structA.range, CallMacroP, keywords.DeriveStructFree),
//        MacroCallS(structA.range, CallMacroP, keywords.DeriveImplFree))
    determineMacrosToCall(nameToStructDefinedMacro, defaultCalledMacros, List(structA.range), structA.attributes)
      .flatMap(_.getStructSiblingEntries(structNameT, structA))
  }

  private def preprocessInterface(
    nameToInterfaceDefinedMacro: Map[StrI, IOnInterfaceDefinedMacro],
    interfaceNameT: IdT[INameT],
    interfaceA: InterfaceA):
  Vector[(IdT[INameT], IEnvEntry)] = {
    val defaultCalledMacros =
      Vector(
        MacroCallS(interfaceA.range, CallMacroP, keywords.DeriveInterfaceDrop),
//        MacroCallS(interfaceA.range, CallMacroP, keywords.DeriveInterfaceFree),
        MacroCallS(interfaceA.range, CallMacroP, keywords.DeriveAnonymousSubstruct))
    val macrosToCall =
      determineMacrosToCall(nameToInterfaceDefinedMacro, defaultCalledMacros, List(interfaceA.range), interfaceA.attributes)
    vpass()
    val results =
      macrosToCall.flatMap(_.getInterfaceSiblingEntries(interfaceNameT, interfaceA))
    vpass()
    results
  }

  private def determineMacrosToCall[T](
    nameToMacro: Map[StrI, T],
    defaultCalledMacros: Vector[MacroCallS],
    parentRanges: List[RangeS],
    attributes: Vector[ICitizenAttributeS]):
  Vector[T] = {
    attributes.foldLeft(defaultCalledMacros)({
      case (macrosToCall, mc@MacroCallS(range, CallMacroP, macroName)) => {
        if (macrosToCall.exists(_.macroName == macroName)) {
          throw CompileErrorExceptionT(RangedInternalErrorT(range :: parentRanges, "Calling macro twice: " + macroName))
        }
        macrosToCall :+ mc
      }
      case (macrosToCall, MacroCallS(_, DontCallMacroP, macroName)) => macrosToCall.filter(_.macroName != macroName)
      case (macrosToCall, _) => macrosToCall
    }).map(macroCall => {
      nameToMacro.get(macroCall.macroName) match {
        case None => {
          throw CompileErrorExceptionT(RangedInternalErrorT(macroCall.range :: parentRanges, "Macro not found: " + macroCall.macroName))
        }
        case Some(m) => m
      }
    })
  }

  def ensureDeepExports(coutputs: CompilerOutputs): Unit = {
    val packageToKindToExport =
      coutputs.getKindExports
        .map(kindExport => (kindExport.id.packageCoord, kindExport.tyype, kindExport))
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
                    List(exports.head.range),
                    exports.head.id.packageCoord,
                    exports))
              }
            }))

    coutputs.getFunctionExports.foreach(funcExport => {
      val exportedKindToExport = packageToKindToExport.getOrElse(funcExport.exportId.packageCoord, Map())
      (Vector(funcExport.prototype.returnType) ++ funcExport.prototype.paramTypes)
        .foreach(paramType => {
          if (!Compiler.isPrimitive(paramType.kind) && !exportedKindToExport.contains(paramType.kind)) {
            throw CompileErrorExceptionT(
              ExportedFunctionDependedOnNonExportedKind(
                List(funcExport.range), funcExport.exportId.packageCoord, funcExport.prototype.toSignature, paramType.kind))
          }
        })
    })
    coutputs.getFunctionExterns.foreach(functionExtern => {
      val exportedKindToExport = packageToKindToExport.getOrElse(functionExtern.externPlaceholderedId.packageCoord, Map())
      (Vector(functionExtern.prototype.returnType) ++ functionExtern.prototype.paramTypes)
        .foreach(paramType => {
          if (!Compiler.isPrimitive(paramType.kind) && !exportedKindToExport.contains(paramType.kind)) {
            throw CompileErrorExceptionT(
              ExternFunctionDependedOnNonExportedKind(
                List(functionExtern.range), functionExtern.externPlaceholderedId.packageCoord, functionExtern.prototype.toSignature, paramType.kind))
          }
        })
    })
    packageToKindToExport.foreach({ case (packageCoord, exportedKindToExport) =>
      exportedKindToExport.foreach({ case (exportedKind, (kind, export)) =>
        exportedKind match {
          case sr@StructTT(_) => {
            val structDef = coutputs.lookupStruct(sr.id)

            val substituter =
              TemplataCompiler.getPlaceholderSubstituter(
                interner,
                keywords,
                sr.id,
                InheritBoundsFromTypeItself)

            structDef.members.foreach({
              case VariadicStructMemberT(name, tyype) => {
                vimpl()
              }
              case NormalStructMemberT(name, variability, AddressMemberTypeT(reference)) => {
                vimpl()
              }
              case NormalStructMemberT(_, _, ReferenceMemberTypeT(unsubstitutedMemberCoord)) => {
                val memberCoord = substituter.substituteForCoord(coutputs, unsubstitutedMemberCoord)
                val memberKind = memberCoord.kind
                if (structDef.mutability == MutabilityTemplataT(ImmutableT) && !Compiler.isPrimitive(memberKind) && !exportedKindToExport.contains(memberKind)) {
                  throw CompileErrorExceptionT(
                    vale.typing.ExportedImmutableKindDependedOnNonExportedKind(
                      List(export.range), packageCoord, exportedKind, memberKind))
                }
              }
            })
          }
          case contentsStaticSizedArrayTT(_, mutability, _, CoordT(_, _, elementKind), _) => {
            if (mutability == MutabilityTemplataT(ImmutableT) && !Compiler.isPrimitive(elementKind) && !exportedKindToExport.contains(elementKind)) {
              throw CompileErrorExceptionT(
                vale.typing.ExportedImmutableKindDependedOnNonExportedKind(
                  List(export.range), packageCoord, exportedKind, elementKind))
            }
          }
          case contentsRuntimeSizedArrayTT(mutability, CoordT(_, _, elementKind), _) => {
            if (mutability == MutabilityTemplataT(ImmutableT) && !Compiler.isPrimitive(elementKind) && !exportedKindToExport.contains(elementKind)) {
              throw CompileErrorExceptionT(
                vale.typing.ExportedImmutableKindDependedOnNonExportedKind(
                  List(export.range), packageCoord, exportedKind, elementKind))
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
            .filter({ case VoidLiteralTE(_) => false case _ => true }) :+
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
      case KindPlaceholderT(_) => false
      case StructTT(_) => false
      case InterfaceTT(_) => false
      case contentsStaticSizedArrayTT(_, _, _, _, _) => false
      case contentsRuntimeSizedArrayTT(_, _, _) => false
    }
  }

  def getMutabilities(coutputs: CompilerOutputs, concreteValues2: Vector[KindT]):
  Vector[ITemplataT[MutabilityTemplataType]] = {
    concreteValues2.map(concreteValue2 => getMutability(coutputs, concreteValue2))
  }

  def getMutability(coutputs: CompilerOutputs, concreteValue2: KindT):
  ITemplataT[MutabilityTemplataType] = {
    concreteValue2 match {
      case KindPlaceholderT(id) => coutputs.lookupMutability(TemplataCompiler.getPlaceholderTemplate(id))
      case NeverT(_) => MutabilityTemplataT(ImmutableT)
      case IntT(_) => MutabilityTemplataT(ImmutableT)
      case FloatT() => MutabilityTemplataT(ImmutableT)
      case BoolT() => MutabilityTemplataT(ImmutableT)
      case StrT() => MutabilityTemplataT(ImmutableT)
      case VoidT() => MutabilityTemplataT(ImmutableT)
      case contentsRuntimeSizedArrayTT(mutability, _, _) => mutability
      case contentsStaticSizedArrayTT(_, mutability, _, _, _) => mutability
      case sr @ StructTT(name) => coutputs.lookupMutability(TemplataCompiler.getStructTemplate(name))
      case ir @ InterfaceTT(name) => coutputs.lookupMutability(TemplataCompiler.getInterfaceTemplate(name))
//      case PackTT(_, sr) => coutputs.lookupMutability(sr)
//      case TupleTT(_, sr) => coutputs.lookupMutability(sr)
      case OverloadSetT(_, _) => {
        // Just like FunctionT2
        MutabilityTemplataT(ImmutableT)
      }
    }
  }
}
