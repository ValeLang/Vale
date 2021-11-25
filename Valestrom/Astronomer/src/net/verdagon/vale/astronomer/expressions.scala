//package net.verdagon.vale.astronomer
//
//import net.verdagon.vale.parser.{ConstraintP, LendConstraintP, LendWeakP, LoadAsP, MoveP, MutabilityP, OwnershipP, VariabilityP, WeakP}
//import net.verdagon.vale.scout.{CodeLocationS, IRuneS, IVariableUseCertainty, LocalS, RangeS}
//import net.verdagon.vale.scout.patterns.AtomSP
//import net.verdagon.vale.scout.rules.IRulexSR
//import net.verdagon.vale.{vassert, vcurious, vimpl, vpass, vwat}
//
//// patternId is a unique number, can be used to make temporary variables that wont
//// collide with other things
//case class LetSE(
//    range: RangeS,
//    rules: Array[IRulexSR],
//    pattern: AtomSP,
//    expr: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class DestructSE(range: RangeS, inner: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class IfSE(range: RangeS, condition: IExpressionSE, thenBody: BlockSE, elseBody: BlockSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class WhileSE(range: RangeS, condition: IExpressionSE, body: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class ExprMutateSE(range: RangeS, mutatee: IExpressionSE, expr: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//case class GlobalMutateSE(range: RangeS, name: INameS, expr: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//case class LocalMutateSE(range: RangeS, name: IVarNameA, expr: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class LendSE(range: RangeS, innerExpr1: IExpressionSE, targetOwnership: LoadAsP) extends IExpressionSE {
//  targetOwnership match {
//    case LendWeakP(_) =>
//    case LendConstraintP(_) =>
//    case MoveP =>
//  }
//}
//case class LockWeakSE(range: RangeS, innerExpr1: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class ReturnSE(range: RangeS, innerExpr1: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//
////case class CurriedFuncH(closureExpr: ExpressionH, funcName: String) extends ExpressionH { override def hashCode(): Int = vcurious() }
//
//// when we make a closure, we make a struct full of pointers to all our variables
//// and the first element is our parent closure
//// this can live on the stack, since blocks are limited to this expression
//// later we can optimize it to only have the things we use
//
////
////sealed trait IVariableUseCertainty
////case object Used extends IVariableUseCertainty
////case object NotUsed extends IVariableUseCertainty
////case object MaybeUsed extends IVariableUseCertainty
//
//case class BodySE(
//  range: RangeS,
//  // These are all the variables we use from parent environments.
//  // We have these so templar doesn't have to dive through all the functions
//  // that it calls (impossible) to figure out what's needed in a closure struct.
//  closuredNames: Vector[IVarNameA],
//
//  block: BlockSE
//) extends IExpressionSE {
//  override def hashCode(): Int = vcurious()
//}
//
//case class BlockSE(
//  range: RangeS,
//
//  exprs: Vector[IExpressionSE],
//) extends IExpressionSE {
//  override def hashCode(): Int = vcurious()
//  // Every element should have at least one expression, because a block will
//  // return the last expression's result as its result.
//  // Even empty blocks aren't empty, they have a void() at the end.
//  vassert(exprs.nonEmpty)
//}
//
////case class StaticSizedArrayFromCallableAE(
////    range: RangeS,
////    typeTemplex: IRulexSR,
////    mutabilityTemplex: IRulexSR,
////    variabilityTemplex: IRulexSR,
////    generatorPrototypeTemplex: IRulexSR,
////    sizeExpr: IExpressionAE,
////    generatorExpr: IExpressionAE) extends IExpressionAE { override def hashCode(): Int = vcurious() }
//
//case class ArgLookupSE(range: RangeS, index: Int) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
// // These things will be separated by semicolons, and all be joined in a block
//case class RepeaterBlockSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//// Results in a pack, represents the differences between the expressions
//case class RepeaterBlockIteratorSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class VoidSE(range: RangeS) extends IExpressionSE {}
//
//case class TupleSE(range: RangeS, elements: Vector[IExpressionSE]) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//case class StaticArrayFromValuesSE(
//  range: RangeS,
//  rules: Array[IRulexSR],
//  runeToType: Map[IRuneS, ITemplataType],
//  maybeSizeRune: Option[IRuneS],
//  maybeMutabilityRune: Option[IRuneS],
//  maybeVariabilityRune: Option[IRuneS],
//  elements: Vector[IExpressionSE]
//) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//case class StaticArrayFromCallableSE(
//  range: RangeS,
//  rules: Array[IRulexSR],
//  runeToType: Map[IRuneS, ITemplataType],
//  sizeRune: IRuneS,
//  maybeMutabilityRune: Option[IRuneS],
//  maybeVariabilityRune: Option[IRuneS],
//  callable: IExpressionSE
//) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class RuntimeArrayFromCallableSE(
//  range: RangeS,
//  rules: Array[IRulexSR],
//  runeToType: Map[IRuneS, ITemplataType],
//  maybeMutabilityRune: Option[IRuneS],
//  maybeVariabilityRune: Option[IRuneS],
//  size: IExpressionSE,
//  callable: IExpressionSE
//) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//// This thing will be repeated, separated by commas, and all be joined in a pack
//case class RepeaterPackSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//// Results in a pack, represents the differences between the elements
//case class RepeaterPackIteratorSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class ConstantIntSE(range: RangeS, value: Long, bits: Int) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class ConstantBoolSE(range: RangeS, value: Boolean) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class ConstantStrSE(range: RangeS, value: String) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class ConstantFloatSE(range: RangeS, value: Double) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class FunctionSE(name: LambdaNameS, function: FunctionA) extends IExpressionSE {
//  override def hashCode(): Int = vcurious()
//  override def range: RangeS = function.range
//}
//
//case class DotSE(range: RangeS, left: IExpressionSE, member: String, borrowContainer: Boolean) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class IndexSE(range: RangeS, left: IExpressionSE, indexExpr: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class FunctionCallSE(range: RangeS, callableExpr: IExpressionSE, argsExprs1: Vector[IExpressionSE]) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
////case class MethodCall0(callableExpr: Expression0, objectExpr: Expression0, argsExpr: Pack0) extends Expression0 { override def hashCode(): Int = vcurious() }
//
//case class TemplateSpecifiedLookupSE(
//  range: RangeS,
//  name: String,
//  rules: Array[IRulexSR],
//  templateArgRunes: Array[IRuneS],
//  targetOwnership: LoadAsP) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//case class RuneLookupSE(range: RangeS, rune: IRuneS, tyype: ITemplataType) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class LocalLoadSE(range: RangeS, name: IVarNameA, targetOwnership: LoadAsP) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//case class OutsideLoadSE(range: RangeS, name: String, targetOwnership: LoadAsP) extends IExpressionSE {
//  override def hashCode(): Int = vcurious()
//}
//
//case class UnletSE(range: RangeS, name: String) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//case class ArrayLengthSE(range: RangeS, arrayExpr: IExpressionSE) extends IExpressionSE { override def hashCode(): Int = vcurious() }
//
//
////case class LocalA(
////    varName: IVarNameA,
////    selfBorrowed: IVariableUseCertainty, // unused
////    selfMoved: IVariableUseCertainty, // used to know whether to box, if we have branching.
////    selfMutated: IVariableUseCertainty, // unused
////    childBorrowed: IVariableUseCertainty, // unused
////    childMoved: IVariableUseCertainty, // used to know whether to box
////    childMutated: IVariableUseCertainty) { // used to know whether to box
////  override def hashCode(): Int = vcurious()
////}