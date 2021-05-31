package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, IRulexAR, IRuneA, ITemplataType}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.MutableP
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.citizen.{StructTemplar, StructTemplarCore}
import net.verdagon.vale.templar.env.{FunctionEnvironmentBox, IEnvironment, IEnvironmentBox}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.{vassert, vassertSome}

import scala.collection.immutable.{List, Set}

trait IArrayTemplarDelegate {
  def getArrayDestructor(
    env: IEnvironment,
    temputs: Temputs,
    type2: Coord):
  (Prototype2)
}

class ArrayTemplar(
    opts: TemplarOptions,
    delegate: IArrayTemplarDelegate,
    inferTemplar: InferTemplar,
    overloadTemplar: OverloadTemplar) {

  vassert(overloadTemplar != null)

  def evaluateStaticSizedArrayFromCallable(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    sizeRuneA: IRuneA,
    maybeMutabilityRune: Option[IRuneA],
    maybeVariabilityRune: Option[IRuneA],
    callableTE: ReferenceExpression2):
  StaticArrayFromCallable2 = {
    val templatas =
      inferTemplar.inferOrdinaryRules(fate.snapshot, temputs, rules, typeByRune, Set(sizeRuneA) ++ maybeMutabilityRune ++ maybeVariabilityRune)
    val IntegerTemplata(size) = vassertSome(templatas.get(NameTranslator.translateRune(sizeRuneA)))
    val mutability = maybeMutabilityRune.map(getArrayMutability(templatas, _)).getOrElse(Mutable)
    val variability = maybeVariabilityRune.map(getArrayVariability(templatas, _)).getOrElse(Final)
    val prototype = overloadTemplar.getArrayGeneratorPrototype(temputs, fate, range, callableTE)
    val ssaMT = getStaticSizedArrayKind(fate.snapshot, temputs, mutability, variability, size, prototype.returnType)
    val expr2 = StaticArrayFromCallable2(ssaMT, callableTE, prototype)
    expr2
  }

  def evaluateRuntimeSizedArrayFromCallable(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    maybeMutabilityRune: Option[IRuneA],
    maybeVariabilityRune: Option[IRuneA],
    sizeTE: ReferenceExpression2,
    callableTE: ReferenceExpression2):
  ConstructArray2 = {
    val templatas =
      inferTemplar.inferOrdinaryRules(fate.snapshot, temputs, rules, typeByRune, Set() ++ maybeMutabilityRune ++ maybeVariabilityRune)
    val mutability = maybeMutabilityRune.map(getArrayMutability(templatas, _)).getOrElse(Mutable)
    val variability = maybeVariabilityRune.map(getArrayVariability(templatas, _)).getOrElse(Final)
    val prototype = overloadTemplar.getArrayGeneratorPrototype(temputs, fate, range, callableTE)
    val rsaMT = getRuntimeSizedArrayKind(fate.snapshot, temputs, prototype.returnType, mutability, variability)
    val expr2 = ConstructArray2(rsaMT, sizeTE, callableTE, prototype)
    expr2
  }

  def evaluateStaticSizedArrayFromValues(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      range: RangeS,
      rules: List[IRulexAR],
      typeByRune: Map[IRuneA, ITemplataType],
      maybeSizeRuneA: Option[IRuneA],
      maybeMutabilityRuneA: Option[IRuneA],
      maybeVariabilityRuneA: Option[IRuneA],
      exprs2: List[ReferenceExpression2]):
   StaticArrayFromValues2 = {
    val memberTypes = exprs2.map(_.resultRegister.reference).toSet
    if (memberTypes.size > 1) {
      throw CompileErrorExceptionT(ArrayElementsHaveDifferentTypes(range, memberTypes))
    }
    val memberType = memberTypes.head

    val templatas =
      inferTemplar.inferOrdinaryRules(
        fate.snapshot, temputs, rules, typeByRune, Set() ++ maybeSizeRuneA ++ maybeMutabilityRuneA ++ maybeVariabilityRuneA)
    val maybeSize = maybeSizeRuneA.map(getArraySize(templatas, _))
    val mutability = maybeMutabilityRuneA.map(getArrayMutability(templatas, _)).getOrElse(Mutable)
    val variability = maybeVariabilityRuneA.map(getArrayVariability(templatas, _)).getOrElse(Final)

    maybeSize match {
      case None =>
      case Some(size) => {
        if (size != exprs2.size) {
          throw CompileErrorExceptionT(InitializedWrongNumberOfElements(range, size, exprs2.size))
        }
      }
    }

    val staticSizedArrayType = getStaticSizedArrayKind(fate.snapshot, temputs, mutability, variability, exprs2.size, memberType)
    val ownership = if (staticSizedArrayType.array.mutability == Mutable) Own else Share
    val permission = if (staticSizedArrayType.array.mutability == Mutable) Readwrite else Readonly
    val finalExpr = StaticArrayFromValues2(exprs2, Coord(ownership, permission, staticSizedArrayType), staticSizedArrayType)
    (finalExpr)
  }

  def getStaticSizedArrayKind(
    env: IEnvironment,
    temputs: Temputs,
    mutability: Mutability,
    variability: Variability,
    size: Int,
    type2: Coord):
  (StaticSizedArrayT2) = {
//    val tupleMutability =
//      StructTemplarCore.getCompoundTypeMutability(temputs, List(type2))
//    val tupleMutability = Templar.getMutability(temputs, type2.referend)
    val rawArrayT2 = RawArrayT2(type2, mutability, variability)

    temputs.getStaticSizedArrayType(size, rawArrayT2) match {
      case Some(staticSizedArrayT2) => (staticSizedArrayT2)
      case None => {
        val staticSizedArrayType = StaticSizedArrayT2(size, rawArrayT2)
        temputs.addStaticSizedArray(staticSizedArrayType)
        val staticSizedArrayOwnership = if (mutability == Mutable) Own else Share
        val staticSizedArrayPermission = if (mutability == Mutable) Readwrite else Readonly
        val staticSizedArrayRefType2 = Coord(staticSizedArrayOwnership, staticSizedArrayPermission, staticSizedArrayType)
        val prototype = delegate.getArrayDestructor(env, temputs, staticSizedArrayRefType2)
        temputs.addDestructor(staticSizedArrayType, prototype)
        (staticSizedArrayType)
      }
    }
  }

  def getRuntimeSizedArrayKind(env: IEnvironment, temputs: Temputs, type2: Coord, arrayMutability: Mutability, arrayVariability: Variability):
  (RuntimeSizedArrayT2) = {
    val rawArrayT2 = RawArrayT2(type2, arrayMutability, arrayVariability)

    temputs.getRuntimeSizedArray(rawArrayT2) match {
      case Some(staticSizedArrayT2) => (staticSizedArrayT2)
      case None => {
        val runtimeSizedArrayType = RuntimeSizedArrayT2(rawArrayT2)
        temputs.addRuntimeSizedArray(runtimeSizedArrayType)
        val runtimeSizedArrayRefType2 =
          Coord(
            if (arrayMutability == Mutable) Own else Share,
            if (arrayMutability == Mutable) Readwrite else Readonly,
            runtimeSizedArrayType)
        val prototype =
          delegate.getArrayDestructor(
            env, temputs, runtimeSizedArrayRefType2)
        temputs.addDestructor(runtimeSizedArrayType, prototype)
        (runtimeSizedArrayType)
      }
    }
  }

  private def getArrayVariability(templatas: Map[IRune2, ITemplata], variabilityRuneA: IRuneA) = {
    val VariabilityTemplata(m) = vassertSome(templatas.get(NameTranslator.translateRune(variabilityRuneA)))
    m
  }

  private def getArrayMutability(templatas: Map[IRune2, ITemplata], mutabilityRuneA: IRuneA) = {
    val MutabilityTemplata(m) = vassertSome(templatas.get(NameTranslator.translateRune(mutabilityRuneA)))
    m
  }
  private def getArraySize(templatas: Map[IRune2, ITemplata], sizeRuneA: IRuneA): Int = {
    val IntegerTemplata(m) = vassertSome(templatas.get(NameTranslator.translateRune(sizeRuneA)))
    m
  }

  def lookupInStaticSizedArray(range: RangeS, containerExpr2: ReferenceExpression2, indexExpr2: ReferenceExpression2, at: StaticSizedArrayT2) = {
    val RawArrayT2(memberType, mutability, variability) = at.array
    val (effectiveVariability, targetPermission) =
      Templar.factorVariabilityAndPermission(
        containerExpr2.resultRegister.reference.permission,
        variability,
        memberType.permission)
    StaticSizedArrayLookup2(range, containerExpr2, at, indexExpr2, targetPermission, effectiveVariability)
  }

  def lookupInUnknownSizedArray(
    range: RangeS,
    containerExpr2: ReferenceExpression2,
    indexExpr2: ReferenceExpression2,
    rsa: RuntimeSizedArrayT2
  ): RuntimeSizedArrayLookup2 = {
    val RawArrayT2(memberType, mutability, variability) = rsa.array
    val (effectiveVariability, targetPermission) =
      Templar.factorVariabilityAndPermission(
        containerExpr2.resultRegister.reference.permission,
        variability,
        memberType.permission)
    RuntimeSizedArrayLookup2(range, containerExpr2, rsa, indexExpr2, targetPermission, effectiveVariability)
  }

}
