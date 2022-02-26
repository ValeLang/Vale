package net.verdagon.vale.templar

import net.verdagon.vale.parser.ast.MutableP
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.rules.{IRulexSR, RuneParentEnvLookupSR, RuneUsage}
import net.verdagon.vale.scout.{CodeNameS, CodeRuneS, CoordTemplataType, IImpreciseNameS, INameS, IRuneS, MutabilityTemplataType, RuneTypeSolver, SelfNameS}
import net.verdagon.vale.templar.OverloadTemplar.FindFunctionFailure
import net.verdagon.vale.templar.ast.{DestroyImmRuntimeSizedArrayTE, DestroyStaticSizedArrayIntoFunctionTE, FunctionCallTE, NewImmRuntimeSizedArrayTE, ProgramT, PrototypeT, ReferenceExpressionTE, RuntimeSizedArrayLookupTE, StaticArrayFromCallableTE, StaticArrayFromValuesTE, StaticSizedArrayLookupTE}
import net.verdagon.vale.templar.citizen.{StructTemplar, StructTemplarCore}
import net.verdagon.vale.templar.env.{CitizenEnvironment, FunctionEnvEntry, FunctionEnvironmentBox, GlobalEnvironment, IEnvironment, IEnvironmentBox, NodeEnvironmentBox, PackageEnvironment, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.names.{FullNameT, FunctionNameT, FunctionTemplateNameT, PackageTopLevelNameT, RuneNameT, SelfNameT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.{CodeLocationS, Err, Profiler, Interner, Ok, RangeS, vassert, vassertOne, vassertSome, vimpl}

import scala.collection.immutable.{List, Set}

class ArrayTemplar(
    opts: TemplarOptions,

    interner: Interner,
    inferTemplar: InferTemplar,
    overloadTemplar: OverloadTemplar) {

  val runeTypeSolver = new RuneTypeSolver(interner)

  vassert(overloadTemplar != null)

  def evaluateStaticSizedArrayFromCallable(
    temputs: Temputs,
    fate: IEnvironment,
    range: RangeS,
    rulesA: Vector[IRulexSR],
    maybeElementTypeRuneA: Option[IRuneS],
    sizeRuneA: IRuneS,
    mutabilityRune: IRuneS,
    variabilityRune: IRuneS,
    callableTE: ReferenceExpressionTE):
  StaticArrayFromCallableTE = {
    val runeToType =
      runeTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        (nameS: IImpreciseNameS) => vassertOne(fate.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext))).tyype,
        range,
        false,
        rulesA,
        List(),
        true,
        Map()) match {
        case Ok(r) => r
        case Err(e) => throw CompileErrorExceptionT(InferAstronomerError(range, e))
      }
    val templatas =
      inferTemplar.solveExpectComplete(fate, temputs, rulesA, runeToType, range, Vector(), Vector())
    val IntegerTemplata(size) = vassertSome(templatas.get(sizeRuneA))
    val mutability = getArrayMutability(templatas, mutabilityRune)
    val variability = getArrayVariability(templatas, variabilityRune)
    val prototype = overloadTemplar.getArrayGeneratorPrototype(temputs, fate, range, callableTE)
    val ssaMT = getStaticSizedArrayKind(fate.globalEnv, temputs, mutability, variability, size.toInt, prototype.returnType)

    maybeElementTypeRuneA.foreach(elementTypeRuneA => {
      val expectedElementType = getArrayElementType(templatas, elementTypeRuneA)
      if (prototype.returnType != expectedElementType) {
        throw CompileErrorExceptionT(UnexpectedArrayElementType(range, expectedElementType, prototype.returnType))
      }
    })

    val expr2 = StaticArrayFromCallableTE(ssaMT, callableTE, prototype)
    expr2
  }

  def evaluateRuntimeSizedArrayFromCallable(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    range: RangeS,
    rulesA: Vector[IRulexSR],
    maybeElementTypeRune: Option[IRuneS],
    mutabilityRune: IRuneS,
    sizeTE: ReferenceExpressionTE,
    maybeCallableTE: Option[ReferenceExpressionTE]):
  ReferenceExpressionTE = {
    val runeToType =
      runeTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        nameS => vassertOne(nenv.functionEnvironment.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext))).tyype,
        range,
        false,
        rulesA,
        List(),
        true,
        Map(mutabilityRune -> MutabilityTemplataType) ++
          maybeElementTypeRune.map(_ -> CoordTemplataType)) match {
        case Ok(r) => r
        case Err(e) => throw CompileErrorExceptionT(InferAstronomerError(range, e))
      }
    val templatas =
      inferTemplar.solveExpectComplete(nenv.functionEnvironment, temputs, rulesA, runeToType, range, Vector(), Vector())
    val mutability = getArrayMutability(templatas, mutabilityRune)

//    val variability = getArrayVariability(templatas, variabilityRune)

    mutability match {
      case ImmutableT => {
        val callableTE =
          maybeCallableTE match {
            case None => {
              throw CompileErrorExceptionT(NewImmRSANeedsCallable(range))
            }
            case Some(c) => c
          }

        val prototype =
          overloadTemplar.getArrayGeneratorPrototype(
            temputs, nenv.functionEnvironment, range, callableTE)
        val rsaMT =
          getRuntimeSizedArrayKind(
            nenv.functionEnvironment.globalEnv, temputs, prototype.returnType, mutability)

        maybeElementTypeRune.foreach(elementTypeRuneA => {
          val expectedElementType = getArrayElementType(templatas, elementTypeRuneA)
          if (prototype.returnType != expectedElementType) {
            throw CompileErrorExceptionT(UnexpectedArrayElementType(range, expectedElementType, prototype.returnType))
          }
        })

        NewImmRuntimeSizedArrayTE(rsaMT, sizeTE, callableTE, prototype)
      }
      case MutableT => {
        val prototype =
          overloadTemplar.findFunction(
            nenv.functionEnvironment
              .addEntries(
                interner,
                Vector(
                  (interner.intern(RuneNameT(CodeRuneS("M"))), TemplataEnvEntry(MutabilityTemplata(MutableT)))) ++
              maybeElementTypeRune.map(e => {
                (interner.intern(RuneNameT(e)), TemplataEnvEntry(CoordTemplata(getArrayElementType(templatas, e))))
              })),
            temputs,
            range,
            interner.intern(CodeNameS("Array")),
            Vector(
              RuneParentEnvLookupSR(range, RuneUsage(range, CodeRuneS("M")))) ++
            maybeElementTypeRune.map(e => {
              RuneParentEnvLookupSR(range, RuneUsage(range, e))
            }),
            Array(CodeRuneS("M")) ++ maybeElementTypeRune,
            Vector(ParamFilter(sizeTE.result.reference, None)) ++
              maybeCallableTE.map(c => ParamFilter(c.result.reference, None)),
            Vector(),
            true)

        val elementType =
          prototype.returnType.kind match {
            case RuntimeSizedArrayTT(mutability, elementType) => {
              if (mutability != MutableT) {
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
            throw CompileErrorExceptionT(UnexpectedArrayElementType(range, expectedElementType, prototype.returnType))
          }
        })
        val callTE =
          FunctionCallTE(prototype, Vector(sizeTE) ++ maybeCallableTE)
        callTE
        //        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't construct a mutable runtime array from a callable!"))
      }
    }
  }

  def evaluateStaticSizedArrayFromValues(
      temputs: Temputs,
      fate: IEnvironment,
      range: RangeS,
      rulesA: Vector[IRulexSR],
    maybeElementTypeRuneA: Option[IRuneS],
    sizeRuneA: IRuneS,
    mutabilityRuneA: IRuneS,
    variabilityRuneA: IRuneS,
      exprs2: Vector[ReferenceExpressionTE]):
   StaticArrayFromValuesTE = {
    val runeToType =
      runeTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        nameS => vassertOne(fate.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext))).tyype,
        range,
        false,
        rulesA,
        List(),
        true,
        Map()) match {
        case Ok(r) => r
        case Err(e) => throw CompileErrorExceptionT(InferAstronomerError(range, e))
      }
    val memberTypes = exprs2.map(_.result.reference).toSet
    if (memberTypes.size > 1) {
      throw CompileErrorExceptionT(ArrayElementsHaveDifferentTypes(range, memberTypes))
    }
    val memberType = memberTypes.head

    val templatas =
      inferTemplar.solveExpectComplete(
        fate, temputs, rulesA, runeToType, range, Vector(), Vector())
    maybeElementTypeRuneA.foreach(elementTypeRuneA => {
      val expectedElementType = getArrayElementType(templatas, elementTypeRuneA)
      if (memberType != expectedElementType) {
        throw CompileErrorExceptionT(UnexpectedArrayElementType(range, expectedElementType, memberType))
      }
    })

    val size = getArraySize(templatas, sizeRuneA)
    val mutability = getArrayMutability(templatas, mutabilityRuneA)
    val variability = getArrayVariability(templatas, variabilityRuneA)

        if (size != exprs2.size) {
          throw CompileErrorExceptionT(InitializedWrongNumberOfElements(range, size, exprs2.size))
        }

    val staticSizedArrayType = getStaticSizedArrayKind(fate.globalEnv, temputs, mutability, variability, exprs2.size, memberType)
    val ownership = if (staticSizedArrayType.mutability == MutableT) OwnT else ShareT
    val permission = if (staticSizedArrayType.mutability == MutableT) ReadwriteT else ReadonlyT
    val finalExpr = StaticArrayFromValuesTE(exprs2, CoordT(ownership, permission, staticSizedArrayType), staticSizedArrayType)
    (finalExpr)
  }

  def evaluateDestroyStaticSizedArrayIntoCallable(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    arrTE: ReferenceExpressionTE,
    callableTE: ReferenceExpressionTE):
  DestroyStaticSizedArrayIntoFunctionTE = {
    val arrayTT =
      arrTE.result.reference match {
        case CoordT(_, _, s @ StaticSizedArrayTT(_, _, _, _)) => s
        case other => {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Destroying a non-array with a callable! Destroying: " + other))
        }
      }

    val prototype =
      overloadTemplar.getArrayConsumerPrototype(
        temputs, fate, range, callableTE, arrayTT.elementType)

    DestroyStaticSizedArrayIntoFunctionTE(
      arrTE,
      arrayTT,
      callableTE,
      prototype)
  }

  def evaluateDestroyRuntimeSizedArrayIntoCallable(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    arrTE: ReferenceExpressionTE,
    callableTE: ReferenceExpressionTE):
  DestroyImmRuntimeSizedArrayTE = {
    val arrayTT =
      arrTE.result.reference match {
        case CoordT(_, _, s @ RuntimeSizedArrayTT(_, _)) => s
        case other => {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Destroying a non-array with a callable! Destroying: " + other))
        }
      }

    arrayTT.mutability match {
      case ImmutableT =>
      case MutableT => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't destroy a mutable array with a callable!"))
      }
    }

    val prototype =
      overloadTemplar.getArrayConsumerPrototype(
        temputs, fate, range, callableTE, arrayTT.elementType)

    DestroyImmRuntimeSizedArrayTE(
      arrTE,
      arrayTT,
      callableTE,
      prototype)
  }

  def getStaticSizedArrayKind(
    globalEnv: GlobalEnvironment,
    temputs: Temputs,
    mutability: MutabilityT,
    variability: VariabilityT,
    size: Int,
    type2: CoordT):
  (StaticSizedArrayTT) = {
//    val rawArrayT2 = RawArrayTT()

    temputs.getStaticSizedArrayType(size, mutability, variability, type2) match {
      case Some(staticSizedArrayT2) => (staticSizedArrayT2)
      case None => {
        val staticSizedArrayType = interner.intern(StaticSizedArrayTT(size, mutability, variability, type2))
        temputs.addStaticSizedArray(staticSizedArrayType)
        val staticSizedArrayOwnership = if (mutability == MutableT) OwnT else ShareT
        val staticSizedArrayPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT
        val staticSizedArrayRefType2 = CoordT(staticSizedArrayOwnership, staticSizedArrayPermission, staticSizedArrayType)

        // We declare the function into the environment that we use to compile the
        // struct, so that those who use the struct can reach into its environment
        // and see the function and use it.
        // See CSFMSEO and SAFHE.
        val arrayEnv =
          CitizenEnvironment(
            globalEnv,
            PackageEnvironment(globalEnv, staticSizedArrayType.getName(interner), globalEnv.nameToTopLevelEnvironment.values.toVector),
            staticSizedArrayType.getName(interner),
            TemplatasStore(staticSizedArrayType.getName(interner), Map(), Map())
              .addEntries(
                interner,
                Vector()))
        temputs.declareKind(staticSizedArrayType)
        temputs.declareKindEnv(staticSizedArrayType, arrayEnv)

        (staticSizedArrayType)
      }
    }
  }

  def getRuntimeSizedArrayKind(globalEnv: GlobalEnvironment, temputs: Temputs, type2: CoordT, arrayMutability: MutabilityT):
  (RuntimeSizedArrayTT) = {
//    val arrayVariability =
//      arrayMutability match {
//        case ImmutableT => FinalT
//        case MutableT => VaryingT
//      }
//    val rawArrayT2 = RuntimeSizedArrayTT()

    temputs.getRuntimeSizedArray(arrayMutability, type2) match {
      case Some(staticSizedArrayT2) => (staticSizedArrayT2)
      case None => {
        val runtimeSizedArrayType = interner.intern(RuntimeSizedArrayTT(arrayMutability, type2))
        temputs.addRuntimeSizedArray(runtimeSizedArrayType)
        val runtimeSizedArrayRefType2 =
          CoordT(
            if (arrayMutability == MutableT) OwnT else ShareT,
            if (arrayMutability == MutableT) ReadwriteT else ReadonlyT,
            runtimeSizedArrayType)

        // We declare the function into the environment that we use to compile the
        // struct, so that those who use the struct can reach into its environment
        // and see the function and use it.
        // See CSFMSEO and SAFHE.
        val arrayEnv =
          CitizenEnvironment(
            globalEnv,
            PackageEnvironment(globalEnv, runtimeSizedArrayType.getName(interner), globalEnv.nameToTopLevelEnvironment.values.toVector),
            runtimeSizedArrayType.getName(interner),
            TemplatasStore(runtimeSizedArrayType.getName(interner), Map(), Map())
              .addEntries(
                interner,
                Vector()))
        temputs.declareKind(runtimeSizedArrayType)
        temputs.declareKindEnv(runtimeSizedArrayType, arrayEnv)

        (runtimeSizedArrayType)
      }
    }
  }

  private def getArrayVariability(templatas: Map[IRuneS, ITemplata], variabilityRuneA: IRuneS) = {
    val VariabilityTemplata(m) = vassertSome(templatas.get(variabilityRuneA))
    m
  }

  private def getArrayMutability(templatas: Map[IRuneS, ITemplata], mutabilityRuneA: IRuneS) = {
    val MutabilityTemplata(m) = vassertSome(templatas.get(mutabilityRuneA))
    m
  }
  private def getArraySize(templatas: Map[IRuneS, ITemplata], sizeRuneA: IRuneS): Int = {
    val IntegerTemplata(m) = vassertSome(templatas.get(sizeRuneA))
    m.toInt
  }
  private def getArrayElementType(templatas: Map[IRuneS, ITemplata], typeRuneA: IRuneS): CoordT = {
    val CoordTemplata(m) = vassertSome(templatas.get(typeRuneA))
    m
  }

  def lookupInStaticSizedArray(
      range: RangeS,
      containerExpr2: ReferenceExpressionTE,
      indexExpr2: ReferenceExpressionTE,
      at: StaticSizedArrayTT) = {
    val StaticSizedArrayTT(size, mutability, variability, memberType) = at
    val (effectiveVariability, targetPermission) =
      Templar.factorVariabilityAndPermission(
        containerExpr2.result.reference.permission,
        variability,
        memberType.permission)
    StaticSizedArrayLookupTE(range, containerExpr2, at, indexExpr2, targetPermission, effectiveVariability)
  }

  def lookupInUnknownSizedArray(
    range: RangeS,
    containerExpr2: ReferenceExpressionTE,
    indexExpr2: ReferenceExpressionTE,
    rsa: RuntimeSizedArrayTT
  ): RuntimeSizedArrayLookupTE = {
    val RuntimeSizedArrayTT(mutability, memberType) = rsa
    val (effectiveVariability, targetPermission) =
      Templar.factorVariabilityAndPermission(
        containerExpr2.result.reference.permission,
        mutability match { case ImmutableT => FinalT case MutableT => VaryingT },
        memberType.permission)
    RuntimeSizedArrayLookupTE(range, containerExpr2, rsa, indexExpr2, targetPermission, effectiveVariability)
  }

}
