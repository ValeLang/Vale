package net.verdagon.vale.templar

import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.MutableP
import net.verdagon.vale.scout.rules.IRulexSR
import net.verdagon.vale.scout.{IImpreciseNameS, IRuneS, RuneTypeSolver, SelfNameS}
import net.verdagon.vale.templar.OverloadTemplar.FindFunctionFailure
import net.verdagon.vale.templar.ast.{ConstructArrayTE, DestroyRuntimeSizedArrayTE, DestroyStaticSizedArrayIntoFunctionTE, ProgramT, PrototypeT, ReferenceExpressionTE, RuntimeSizedArrayLookupTE, StaticArrayFromCallableTE, StaticArrayFromValuesTE, StaticSizedArrayLookupTE}
import net.verdagon.vale.templar.citizen.{StructTemplar, StructTemplarCore}
import net.verdagon.vale.templar.env.{CitizenEnvironment, FunctionEnvEntry, FunctionEnvironmentBox, GlobalEnvironment, IEnvironment, IEnvironmentBox, PackageEnvironment, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.names.{FullNameT, FunctionNameT, FunctionTemplateNameT, PackageTopLevelNameT, SelfNameT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.{CodeLocationS, Err, IProfiler, Ok, RangeS, vassert, vassertOne, vassertSome, vimpl}

import scala.collection.immutable.{List, Set}

class ArrayTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    inferTemplar: InferTemplar,
    overloadTemplar: OverloadTemplar) {

  vassert(overloadTemplar != null)

  def evaluateStaticSizedArrayFromCallable(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    rulesA: Vector[IRulexSR],
    sizeRuneA: IRuneS,
    mutabilityRune: IRuneS,
    variabilityRune: IRuneS,
    callableTE: ReferenceExpressionTE):
  StaticArrayFromCallableTE = {
    val runeToType =
      RuneTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        (nameS: IImpreciseNameS) => vassertOne(fate.lookupNearestWithImpreciseName(profiler, nameS, Set(TemplataLookupContext))).tyype,
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
      inferTemplar.solveExpectComplete(fate.snapshot, temputs, rulesA, runeToType, range, Vector(), Vector())
    val IntegerTemplata(size) = vassertSome(templatas.get(sizeRuneA))
    val mutability = getArrayMutability(templatas, mutabilityRune)
    val variability = getArrayVariability(templatas, variabilityRune)
    val prototype = overloadTemplar.getArrayGeneratorPrototype(temputs, fate, range, callableTE)
    val ssaMT = getStaticSizedArrayKind(fate.snapshot.globalEnv, temputs, mutability, variability, size.toInt, prototype.returnType)
    val expr2 = StaticArrayFromCallableTE(ssaMT, callableTE, prototype)
    expr2
  }

  def evaluateRuntimeSizedArrayFromCallable(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    rulesA: Vector[IRulexSR],
    mutabilityRune: IRuneS,
    variabilityRune: IRuneS,
    sizeTE: ReferenceExpressionTE,
    callableTE: ReferenceExpressionTE):
  ConstructArrayTE = {
    val runeToType =
      RuneTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        nameS => vassertOne(fate.lookupNearestWithImpreciseName(profiler, nameS, Set(TemplataLookupContext))).tyype,
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
      inferTemplar.solveExpectComplete(fate.snapshot, temputs, rulesA, runeToType, range, Vector(), Vector())
    val mutability = getArrayMutability(templatas, mutabilityRune)
    val variability = getArrayVariability(templatas, variabilityRune)
    val prototype = overloadTemplar.getArrayGeneratorPrototype(temputs, fate, range, callableTE)
    val rsaMT = getRuntimeSizedArrayKind(fate.snapshot.globalEnv, temputs, prototype.returnType, mutability, variability)
    val expr2 = ConstructArrayTE(rsaMT, sizeTE, callableTE, prototype)
    expr2
  }

  def evaluateStaticSizedArrayFromValues(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      range: RangeS,
      rulesA: Vector[IRulexSR],
    sizeRuneA: IRuneS,
    mutabilityRuneA: IRuneS,
    variabilityRuneA: IRuneS,
      exprs2: Vector[ReferenceExpressionTE]):
   StaticArrayFromValuesTE = {
    val runeToType =
      RuneTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        nameS => vassertOne(fate.lookupNearestWithImpreciseName(profiler, nameS, Set(TemplataLookupContext))).tyype,
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
        fate.snapshot, temputs, rulesA, runeToType, range, Vector(), Vector())
    val size = getArraySize(templatas, sizeRuneA)
    val mutability = getArrayMutability(templatas, mutabilityRuneA)
    val variability = getArrayVariability(templatas, variabilityRuneA)

        if (size != exprs2.size) {
          throw CompileErrorExceptionT(InitializedWrongNumberOfElements(range, size, exprs2.size))
        }

    val staticSizedArrayType = getStaticSizedArrayKind(fate.snapshot.globalEnv, temputs, mutability, variability, exprs2.size, memberType)
    val ownership = if (staticSizedArrayType.array.mutability == MutableT) OwnT else ShareT
    val permission = if (staticSizedArrayType.array.mutability == MutableT) ReadwriteT else ReadonlyT
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
        case CoordT(_, _, s @ StaticSizedArrayTT(_, RawArrayTT(_, _, _))) => s
        case other => {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Destroying a non-array with a callable! Destroying: " + other))
        }
      }

    val prototype =
      overloadTemplar.getArrayConsumerPrototype(
        temputs, fate, range, callableTE, arrayTT.array.elementType)

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
  DestroyRuntimeSizedArrayTE = {
    val arrayTT =
      arrTE.result.reference match {
        case CoordT(_, _, s @ RuntimeSizedArrayTT(RawArrayTT(_, _, _))) => s
        case other => {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Destroying a non-array with a callable! Destroying: " + other))
        }
      }

    val prototype =
      overloadTemplar.getArrayConsumerPrototype(
        temputs, fate, range, callableTE, arrayTT.array.elementType)

    DestroyRuntimeSizedArrayTE(
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
    val rawArrayT2 = RawArrayTT(type2, mutability, variability)

    temputs.getStaticSizedArrayType(size, rawArrayT2) match {
      case Some(staticSizedArrayT2) => (staticSizedArrayT2)
      case None => {
        val staticSizedArrayType = StaticSizedArrayTT(size, rawArrayT2)
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
            PackageEnvironment(globalEnv, staticSizedArrayType.name, globalEnv.nameToTopLevelEnvironment.values.toVector),
            staticSizedArrayType.name,
            TemplatasStore(staticSizedArrayType.name, Map(), Map())
              .addEntries(
                Vector()))
        temputs.declareKind(staticSizedArrayType)
        temputs.declareKindEnv(staticSizedArrayType, arrayEnv)

        (staticSizedArrayType)
      }
    }
  }

  def getRuntimeSizedArrayKind(globalEnv: GlobalEnvironment, temputs: Temputs, type2: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT):
  (RuntimeSizedArrayTT) = {
    val rawArrayT2 = RawArrayTT(type2, arrayMutability, arrayVariability)

    temputs.getRuntimeSizedArray(rawArrayT2) match {
      case Some(staticSizedArrayT2) => (staticSizedArrayT2)
      case None => {
        val runtimeSizedArrayType = RuntimeSizedArrayTT(rawArrayT2)
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
            PackageEnvironment(globalEnv, runtimeSizedArrayType.name, globalEnv.nameToTopLevelEnvironment.values.toVector),
            runtimeSizedArrayType.name,
            TemplatasStore(runtimeSizedArrayType.name, Map(), Map())
              .addEntries(
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

  def lookupInStaticSizedArray(
      range: RangeS,
      containerExpr2: ReferenceExpressionTE,
      indexExpr2: ReferenceExpressionTE,
      at: StaticSizedArrayTT) = {
    val RawArrayTT(memberType, mutability, variability) = at.array
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
    val RawArrayTT(memberType, mutability, variability) = rsa.array
    val (effectiveVariability, targetPermission) =
      Templar.factorVariabilityAndPermission(
        containerExpr2.result.reference.permission,
        variability,
        memberType.permission)
    RuntimeSizedArrayLookupTE(range, containerExpr2, rsa, indexExpr2, targetPermission, effectiveVariability)
  }

}
