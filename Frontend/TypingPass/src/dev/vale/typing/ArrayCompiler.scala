package dev.vale.typing

import dev.vale.parsing.ast.MutableP
import dev.vale.postparsing.{TemplateTemplataType, _}
import dev.vale.postparsing.rules.{IRulexSR, RuneParentEnvLookupSR, RuneUsage}
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.types._
import dev.vale.{CodeLocationS, Err, Interner, Keywords, Ok, PackageCoordinate, Profiler, RangeS, StrI, vassert, vassertOne, vassertSome, vimpl}
import dev.vale.typing.types._
import dev.vale.typing.templata.{ITemplata, _}
import OverloadResolver.FindFunctionFailure
import dev.vale.typing.ast.{DestroyImmRuntimeSizedArrayTE, DestroyStaticSizedArrayIntoFunctionTE, FunctionCallTE, NewImmRuntimeSizedArrayTE, ReferenceExpressionTE, RuntimeSizedArrayLookupTE, StaticArrayFromCallableTE, StaticArrayFromValuesTE, StaticSizedArrayLookupTE}
import dev.vale.typing.env.{CitizenEnvironment, FunctionEnvironmentBox, GlobalEnvironment, IEnvironment, NodeEnvironment, NodeEnvironmentBox, PackageEnvironment, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import dev.vale.typing.names.{IdT, RawArrayNameT, RuneNameT, RuntimeSizedArrayNameT, RuntimeSizedArrayTemplateNameT, SelfNameT, StaticSizedArrayNameT, StaticSizedArrayTemplateNameT}
import dev.vale.typing.templata._
import dev.vale.typing.ast._
import dev.vale.typing.citizen.StructCompilerCore
import dev.vale.typing.function.FunctionCompiler.EvaluateFunctionSuccess
import dev.vale.typing.types._
import dev.vale.typing.templata._

import scala.collection.immutable.{List, Set}

class ArrayCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    inferCompiler: InferCompiler,
    overloadResolver: OverloadResolver,
    destructorCompiler: DestructorCompiler,
    templataCompiler: TemplataCompiler) {

  val runeTypeSolver = new RuneTypeSolver(interner)

  vassert(overloadResolver != null)

  def evaluateStaticSizedArrayFromCallable(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment,
    range: List[RangeS],
    rulesA: Vector[IRulexSR],
    maybeElementTypeRuneA: Option[IRuneS],
    sizeRuneA: IRuneS,
    mutabilityRune: IRuneS,
    variabilityRune: IRuneS,
    callableTE: ReferenceExpressionTE,
    verifyConclusions: Boolean):
  StaticArrayFromCallableTE = {
//    val builtinNamespaceCoord =
//      interner.intern(PackageCoordinate(keywords.emptyString, Vector.empty))
//    val declaringEnv =
//      PackageEnvironment.makeTopLevelEnvironment(callingEnv.globalEnv, builtinNamespaceCoord)
    val runeToType =
      runeTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        (nameS: IImpreciseNameS) => vassertOne(callingEnv.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext))).tyype,
        range,
        false,
        rulesA,
        List(),
        true,
        Map()) match {
        case Ok(r) => r
        case Err(e) => throw CompileErrorExceptionT(HigherTypingInferError(range, e))
      }
    val CompleteCompilerSolve(_, templatas, _, Vector()) =
      inferCompiler.solveExpectComplete(
        InferEnv(callingEnv, range, callingEnv),
        coutputs,
        rulesA,
        runeToType,
        range,
        Vector(),
        Vector(),
        true,
        true,
        Vector())

    val size = ITemplata.expectInteger(vassertSome(templatas.get(sizeRuneA)))
    val mutability = ITemplata.expectMutability(vassertSome(templatas.get(mutabilityRune)))
    val variability = ITemplata.expectVariability(vassertSome(templatas.get(variabilityRune)))
    val prototype = overloadResolver.getArrayGeneratorPrototype(coutputs, callingEnv, range, callableTE, true)
    val ssaMT = resolveStaticSizedArray(mutability, variability, size, prototype.returnType)

    maybeElementTypeRuneA.foreach(elementTypeRuneA => {
      val expectedElementType = getArrayElementType(templatas, elementTypeRuneA)
      if (prototype.returnType != expectedElementType) {
        throw CompileErrorExceptionT(UnexpectedArrayElementType(range, expectedElementType, prototype.returnType))
      }
    })

    val resultCoord =
      CoordT(
        mutability match {
          case MutabilityTemplata(MutableT) => OwnT
          case MutabilityTemplata(ImmutableT) => ShareT
          case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => vimpl()
        },
        ssaMT)

    val expr2 = ast.StaticArrayFromCallableTE(ssaMT, callableTE, prototype)
    expr2
  }

  def evaluateRuntimeSizedArrayFromCallable(
    coutputs: CompilerOutputs,
    callingEnv: NodeEnvironment,
    range: List[RangeS],
    rulesA: Vector[IRulexSR],
    maybeElementTypeRune: Option[IRuneS],
    mutabilityRune: IRuneS,
    sizeTE: ReferenceExpressionTE,
    maybeCallableTE: Option[ReferenceExpressionTE],
    verifyConclusions: Boolean):
  ReferenceExpressionTE = {
    val runeToType =
      runeTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        nameS => vassertOne(callingEnv.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext))).tyype,
        range,
        false,
        rulesA,
        List(),
        true,
        Map(mutabilityRune -> MutabilityTemplataType()) ++
          maybeElementTypeRune.map(_ -> CoordTemplataType())) match {
        case Ok(r) => r
        case Err(e) => throw CompileErrorExceptionT(HigherTypingInferError(range, e))
      }
    val CompleteCompilerSolve(_, templatas, _, Vector()) =
      inferCompiler.solveExpectComplete(
        InferEnv(callingEnv, range, callingEnv), coutputs, rulesA, runeToType, range, Vector(), Vector(), true, true, Vector())
    val mutability = ITemplata.expectMutability(vassertSome(templatas.get(mutabilityRune)))

//    val variability = getArrayVariability(templatas, variabilityRune)

    if (maybeElementTypeRune.isEmpty) {
      // Temporary until we can figure out MSAE.
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Must specify element for arrays."))
    }

    mutability match {
      case PlaceholderTemplata(_, MutabilityTemplataType()) => vimpl()
      case MutabilityTemplata(ImmutableT) => {
        val callableTE =
          maybeCallableTE match {
            case None => {
              throw CompileErrorExceptionT(NewImmRSANeedsCallable(range))
            }
            case Some(c) => c
          }

        val prototype =
          overloadResolver.getArrayGeneratorPrototype(
            coutputs, callingEnv, range, callableTE, true)
        val rsaMT = resolveRuntimeSizedArray(prototype.returnType, mutability)

        maybeElementTypeRune.foreach(elementTypeRuneA => {
          val expectedElementType = getArrayElementType(templatas, elementTypeRuneA)
          if (prototype.returnType != expectedElementType) {
            throw CompileErrorExceptionT(UnexpectedArrayElementType(range, expectedElementType, prototype.returnType))
          }
        })

        val resultCoord =
          CoordT(
            mutability match {
              case MutabilityTemplata(MutableT) => OwnT
              case MutabilityTemplata(ImmutableT) => ShareT
              case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => vimpl()
            },
            rsaMT)

        NewImmRuntimeSizedArrayTE(rsaMT, sizeTE, callableTE, prototype)
      }
      case MutabilityTemplata(MutableT) => {
        val EvaluateFunctionSuccess(prototype, conclusions) =
          overloadResolver.findFunction(
            callingEnv
              .addEntries(
                interner,
                Vector(
                  (interner.intern(RuneNameT(CodeRuneS(keywords.M))), TemplataEnvEntry(MutabilityTemplata(MutableT)))) ++
              maybeElementTypeRune.map(e => {
                (interner.intern(RuneNameT(e)), TemplataEnvEntry(CoordTemplata(getArrayElementType(templatas, e))))
              })),
            coutputs,
            range,
            interner.intern(CodeNameS(keywords.Array)),
            Vector(
              RuneParentEnvLookupSR(range.head, RuneUsage(range.head, CodeRuneS(keywords.M)))) ++
            maybeElementTypeRune.map(e => {
              RuneParentEnvLookupSR(range.head, RuneUsage(range.head, e))
            }),
            Vector(CodeRuneS(keywords.M)) ++ maybeElementTypeRune,
            Vector(sizeTE.result.coord) ++
              maybeCallableTE.map(c => c.result.coord),
            Vector(),
            true,
            true) match {
            case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
            case Ok(x) => x
          }

        val elementType =
          prototype.prototype.returnType.kind match {
            case RuntimeSizedArrayTT(IdT(_, _, RuntimeSizedArrayNameT(_, RawArrayNameT(mutability, elementType)))) => {
              if (mutability != MutabilityTemplata(MutableT)) {
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Array function returned wrong mutability!"))
              }
              elementType
            }
            case _ => {
              throw CompileErrorExceptionT(RangedInternalErrorT(range, "Array function returned wrong type!"))
            }
          }
        maybeElementTypeRune.foreach(elementTypeRuneA => {
          val expectedElementType = getArrayElementType(templatas, elementTypeRuneA)
          if (elementType != expectedElementType) {
            throw CompileErrorExceptionT(
              UnexpectedArrayElementType(range, expectedElementType, prototype.prototype.returnType))
          }
        })
        vassert(coutputs.getInstantiationBounds(prototype.prototype.fullName).nonEmpty)
        val callTE =
          FunctionCallTE(prototype.prototype, Vector(sizeTE) ++ maybeCallableTE)
        callTE
        //        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't construct a mutable runtime array from a callable!"))
      }
    }
  }

  def evaluateStaticSizedArrayFromValues(
      coutputs: CompilerOutputs,
      callingEnv: IEnvironment,
      range: List[RangeS],
      rulesA: Vector[IRulexSR],
      maybeElementTypeRuneA: Option[IRuneS],
      sizeRuneA: IRuneS,
      mutabilityRuneA: IRuneS,
      variabilityRuneA: IRuneS,
      exprs2: Vector[ReferenceExpressionTE],
      verifyConclusions: Boolean):
   StaticArrayFromValuesTE = {
    val runeToType =
      runeTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        nameS => vassertOne(callingEnv.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext))).tyype,
        range,
        false,
        rulesA,
        List(),
        true,
        Map[IRuneS, ITemplataType](
          sizeRuneA -> IntegerTemplataType(),
          mutabilityRuneA -> MutabilityTemplataType(),
          variabilityRuneA -> VariabilityTemplataType()) ++
          (maybeElementTypeRuneA match {
            case Some(rune) => Map(rune -> CoordTemplataType())
            case None => Map()
          })) match {
        case Ok(r) => r
        case Err(e) => throw CompileErrorExceptionT(HigherTypingInferError(range, e))
      }
    val memberTypes = exprs2.map(_.result.coord).toSet
    if (memberTypes.size > 1) {
      throw CompileErrorExceptionT(ArrayElementsHaveDifferentTypes(range, memberTypes))
    }
    val memberType = memberTypes.head

    val CompleteCompilerSolve(_, templatas, _, Vector()) =
      inferCompiler.solveExpectComplete(
        InferEnv(callingEnv, range, callingEnv), coutputs, rulesA, runeToType, range, Vector(), Vector(), true, true, Vector())
    maybeElementTypeRuneA.foreach(elementTypeRuneA => {
      val expectedElementType = getArrayElementType(templatas, elementTypeRuneA)
      if (memberType != expectedElementType) {
        throw CompileErrorExceptionT(UnexpectedArrayElementType(range, expectedElementType, memberType))
      }
    })

    val size = getArraySize(templatas, sizeRuneA)
    val mutability = ITemplata.expectMutability(vassertSome(templatas.get(mutabilityRuneA)))
    val variability = ITemplata.expectVariability(vassertSome(templatas.get(variabilityRuneA)))

        if (size != exprs2.size) {
          throw CompileErrorExceptionT(InitializedWrongNumberOfElements(range, size, exprs2.size))
        }

    val staticSizedArrayType = resolveStaticSizedArray(mutability, variability, IntegerTemplata(exprs2.size), memberType)
    val ownership =
      staticSizedArrayType.mutability match {
        case MutabilityTemplata(MutableT) => OwnT
        case MutabilityTemplata(ImmutableT) => ShareT
        case PlaceholderTemplata(_, MutabilityTemplataType()) => OwnT
      }

    val ssaCoord = CoordT(ownership, staticSizedArrayType)

    val finalExpr =
      StaticArrayFromValuesTE(
        exprs2, ssaCoord, staticSizedArrayType)
    (finalExpr)
  }

  def evaluateDestroyStaticSizedArrayIntoCallable(
    coutputs: CompilerOutputs,
    fate: FunctionEnvironmentBox,
    range: List[RangeS],
    arrTE: ReferenceExpressionTE,
    callableTE: ReferenceExpressionTE):
  DestroyStaticSizedArrayIntoFunctionTE = {
    val arrayTT =
      arrTE.result.coord match {
        case CoordT(_, s @ contentsStaticSizedArrayTT(_, _, _, _)) => s
        case other => {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Destroying a non-array with a callable! Destroying: " + other))
        }
      }

    val prototype =
      overloadResolver.getArrayConsumerPrototype(
        coutputs, fate, range, callableTE, arrayTT.elementType, true)

    ast.DestroyStaticSizedArrayIntoFunctionTE(
      arrTE,
      arrayTT,
      callableTE,
      prototype)
  }

  def evaluateDestroyRuntimeSizedArrayIntoCallable(
    coutputs: CompilerOutputs,
    fate: FunctionEnvironmentBox,
    range: List[RangeS],
    arrTE: ReferenceExpressionTE,
    callableTE: ReferenceExpressionTE):
  DestroyImmRuntimeSizedArrayTE = {
    val arrayTT =
      arrTE.result.coord match {
        case CoordT(_, s @ contentsRuntimeSizedArrayTT(_, _)) => s
        case other => {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Destroying a non-array with a callable! Destroying: " + other))
        }
      }

    arrayTT.mutability match {
      case PlaceholderTemplata(_, MutabilityTemplataType()) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't destroy an array whose mutability we don't know!"))
      }
      case MutabilityTemplata(ImmutableT) =>
      case MutabilityTemplata(MutableT) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't destroy a mutable array with a callable!"))
      }
    }

    val prototype =
      overloadResolver.getArrayConsumerPrototype(
        coutputs, fate, range, callableTE, arrayTT.elementType, true)

//    val freePrototype =
//      destructorCompiler.getFreeFunction(
//        coutputs, fate, range, arrTE.result.reference)
//        .function.prototype
//    vassert(coutputs.getInstantiationBounds(freePrototype.fullName).nonEmpty)

    ast.DestroyImmRuntimeSizedArrayTE(
      arrTE,
      arrayTT,
      callableTE,
      prototype)
  }

  def compileStaticSizedArray(globalEnv: GlobalEnvironment, coutputs: CompilerOutputs): Unit = {
    val builtinPackage = PackageCoordinate.BUILTIN(interner, keywords)
    val templateFullName =
      IdT(builtinPackage, Vector.empty, interner.intern(StaticSizedArrayTemplateNameT()))

    // We declare the function into the environment that we use to compile the
    // struct, so that those who use the struct can reach into its environment
    // and see the function and use it.
    // See CSFMSEO and SAFHE.
    val arrayOuterEnv =
      CitizenEnvironment(
        globalEnv,
        PackageEnvironment(globalEnv, templateFullName, globalEnv.nameToTopLevelEnvironment.values.toVector),
        templateFullName,
        templateFullName,
        TemplatasStore(templateFullName, Map(), Map()))
    coutputs.declareType(templateFullName)
    coutputs.declareTypeOuterEnv(templateFullName, arrayOuterEnv)

    val TemplateTemplataType(types, _) = StaticSizedArrayTemplateTemplata().tyype
    val Vector(IntegerTemplataType(), MutabilityTemplataType(), VariabilityTemplataType(), CoordTemplataType()) = types
    val sizePlaceholder =
      templataCompiler.createPlaceholderInner(
        coutputs, arrayOuterEnv, templateFullName, 0, CodeRuneS(interner.intern(StrI("N"))), IntegerTemplataType(), false, true)
    val mutabilityPlaceholder =
      templataCompiler.createPlaceholderInner(
        coutputs, arrayOuterEnv, templateFullName, 1, CodeRuneS(interner.intern(StrI("M"))), MutabilityTemplataType(), false, true)
    val variabilityPlaceholder =
      templataCompiler.createPlaceholderInner(
        coutputs, arrayOuterEnv, templateFullName, 2, CodeRuneS(interner.intern(StrI("V"))), VariabilityTemplataType(), false, true)
    val elementPlaceholder =
      templataCompiler.createPlaceholderInner(
        coutputs, arrayOuterEnv, templateFullName, 3, CodeRuneS(interner.intern(StrI("E"))), CoordTemplataType(), false, true)
    val placeholders =
      Vector(sizePlaceholder, mutabilityPlaceholder, variabilityPlaceholder, elementPlaceholder)

    val fullName = templateFullName.copy(localName = templateFullName.localName.makeCitizenName(interner, placeholders))
    vassert(TemplataCompiler.getTemplate(fullName) == templateFullName)

    val arrayInnerEnv =
      arrayOuterEnv.copy(
        fullName = fullName,
        templatas = arrayOuterEnv.templatas.copy(templatasStoreName = fullName))
    coutputs.declareTypeInnerEnv(templateFullName, arrayInnerEnv)
  }

  def resolveStaticSizedArray(
    mutability: ITemplata[MutabilityTemplataType],
    variability: ITemplata[VariabilityTemplataType],
    size: ITemplata[IntegerTemplataType],
    type2: CoordT):
  (StaticSizedArrayTT) = {
    interner.intern(StaticSizedArrayTT(
      IdT(
        PackageCoordinate.BUILTIN(interner, keywords),
        Vector(),
        interner.intern(StaticSizedArrayNameT(
          interner.intern(StaticSizedArrayTemplateNameT()),
          size,
          variability,
          interner.intern(RawArrayNameT(
            mutability, type2)))))))
  }

  def compileRuntimeSizedArray(globalEnv: GlobalEnvironment, coutputs: CompilerOutputs): Unit = {
    val builtinPackage = PackageCoordinate.BUILTIN(interner, keywords)
    val templateFullName =
      IdT(builtinPackage, Vector.empty, interner.intern(RuntimeSizedArrayTemplateNameT()))

    // We declare the function into the environment that we use to compile the
    // struct, so that those who use the struct can reach into its environment
    // and see the function and use it.
    // See CSFMSEO and SAFHE.
    val arrayOuterEnv =
      CitizenEnvironment(
        globalEnv,
        PackageEnvironment(globalEnv, templateFullName, globalEnv.nameToTopLevelEnvironment.values.toVector),
        templateFullName,
        templateFullName,
        TemplatasStore(templateFullName, Map(), Map()))
    coutputs.declareType(templateFullName)
    coutputs.declareTypeOuterEnv(templateFullName, arrayOuterEnv)



    val TemplateTemplataType(types, _) = RuntimeSizedArrayTemplateTemplata().tyype
    val Vector(MutabilityTemplataType(), CoordTemplataType()) = types
    val mutabilityPlaceholder =
      templataCompiler.createPlaceholderInner(
        coutputs, arrayOuterEnv, templateFullName, 0, CodeRuneS(interner.intern(StrI("M"))), MutabilityTemplataType(), false, true)
    val elementPlaceholder =
      templataCompiler.createPlaceholderInner(
        coutputs, arrayOuterEnv, templateFullName, 1, CodeRuneS(interner.intern(StrI("E"))), CoordTemplataType(), false, true)
    val placeholders =
      Vector(mutabilityPlaceholder, elementPlaceholder)

    val fullName = templateFullName.copy(localName = templateFullName.localName.makeCitizenName(interner, placeholders))

    val arrayInnerEnv =
      arrayOuterEnv.copy(
        fullName = fullName,
        templatas = arrayOuterEnv.templatas.copy(templatasStoreName = fullName))
    coutputs.declareTypeInnerEnv(templateFullName, arrayInnerEnv)
  }

  def resolveRuntimeSizedArray(type2: CoordT, mutability: ITemplata[MutabilityTemplataType]):
  (RuntimeSizedArrayTT) = {
    interner.intern(RuntimeSizedArrayTT(
      IdT(
        PackageCoordinate.BUILTIN(interner, keywords),
        Vector(),
        interner.intern(RuntimeSizedArrayNameT(
          interner.intern(RuntimeSizedArrayTemplateNameT()),
          interner.intern(RawArrayNameT(
            mutability, type2)))))))
  }

  private def getArraySize(templatas: Map[IRuneS, ITemplata[ITemplataType]], sizeRuneA: IRuneS): Int = {
    val IntegerTemplata(m) = vassertSome(templatas.get(sizeRuneA))
    m.toInt
  }
  private def getArrayElementType(templatas: Map[IRuneS, ITemplata[ITemplataType]], typeRuneA: IRuneS): CoordT = {
    val CoordTemplata(m) = vassertSome(templatas.get(typeRuneA))
    m
  }

  def lookupInStaticSizedArray(
      range: RangeS,
      containerExpr2: ReferenceExpressionTE,
      indexExpr2: ReferenceExpressionTE,
      at: StaticSizedArrayTT) = {
    val contentsStaticSizedArrayTT(size, mutability, variabilityTemplata, memberType) = at
    val variability =
      variabilityTemplata match {
        case PlaceholderTemplata(_, _) => FinalT
        case VariabilityTemplata(variability) => variability
      }
    StaticSizedArrayLookupTE(range, containerExpr2, at, indexExpr2, variability)
  }

  def lookupInUnknownSizedArray(
    parentRanges: List[RangeS],
    range: RangeS,
    containerExpr2: ReferenceExpressionTE,
    indexExpr2: ReferenceExpressionTE,
    rsa: RuntimeSizedArrayTT
  ): RuntimeSizedArrayLookupTE = {
    val contentsRuntimeSizedArrayTT(mutability, memberType) = rsa
    if (indexExpr2.result.coord != CoordT(ShareT, IntT(32))) {
      throw CompileErrorExceptionT(IndexedArrayWithNonInteger(range :: parentRanges, indexExpr2.result.coord))
    }
    val variability =
      mutability match {
        case PlaceholderTemplata(_, MutabilityTemplataType()) => FinalT
        case MutabilityTemplata(ImmutableT) => FinalT
        case MutabilityTemplata(MutableT) => VaryingT
      }
    RuntimeSizedArrayLookupTE(range, containerExpr2, rsa, indexExpr2, variability)
  }

}
