package net.verdagon.vale.scout

import net.verdagon.vale.parser.{BorrowP, LendBorrowP, LendWeakP, LoadAsP, MoveP, MutabilityP, OwnershipP, PermissionP, VariabilityP, WeakP}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.rules.{IRulexSR, ITypeSR}
import net.verdagon.vale.{vassert, vpass}

// patternId is a unique number, can be used to make temporary variables that wont
// collide with other things
case class LetSE(
    range: RangeS,
    rules: List[IRulexSR],
    unknowableRunes: Set[IRuneS],
    localRunes: Set[IRuneS],
    pattern: AtomSP,
    expr: IExpressionSE) extends IExpressionSE

case class IfSE(
  range: RangeS,
  condition: BlockSE,
  thenBody: BlockSE,
  elseBody: BlockSE
) extends IExpressionSE

case class WhileSE(range: RangeS, condition: BlockSE, body: BlockSE) extends IExpressionSE

case class ExprMutateSE(range: RangeS, mutatee: IExpressionSE, expr: IExpressionSE) extends IExpressionSE
case class GlobalMutateSE(range: RangeS, name: ImpreciseCodeVarNameS, expr: IExpressionSE) extends IExpressionSE
case class LocalMutateSE(range: RangeS, name: IVarNameS, expr: IExpressionSE) extends IExpressionSE

case class OwnershippedSE(range: RangeS, innerExpr1: IExpressionSE, targetOwnership: LoadAsP) extends IExpressionSE {
  targetOwnership match {
    case LendWeakP(_) =>
    case LendBorrowP(_) =>
    case MoveP =>
  }
}

case class PermissionedSE(range: RangeS, innerExpr1: IExpressionSE, targetPermission: PermissionP) extends IExpressionSE {

}


//case class CurriedFuncH(closureExpr: ExpressionH, funcName: String) extends ExpressionH

// when we make a closure, we make a struct full of pointers to all our variables
// and the first element is our parent closure
// this can live on the stack, since blocks are limited to this expression
// later we can optimize it to only have the things we use


sealed trait IVariableUseCertainty
case object Used extends IVariableUseCertainty
case object NotUsed extends IVariableUseCertainty
case object MaybeUsed extends IVariableUseCertainty

case class LocalVariable1(
    varName: IVarNameS,
    variability: VariabilityP,
    selfBorrowed: IVariableUseCertainty,
    selfMoved: IVariableUseCertainty,
    selfMutated: IVariableUseCertainty,
    childBorrowed: IVariableUseCertainty,
    childMoved: IVariableUseCertainty,
    childMutated: IVariableUseCertainty)

case class BodySE(
    range: RangeS,
    // These are all the variables we use from parent environments.
    // We have these so templar doesn't have to dive through all the functions
    // that it calls (impossible) to figure out what's needed in a closure struct.
    closuredNames: List[IVarNameS],

    block: BlockSE
)// extends IExpressionSE

case class BlockSE(
  range: RangeS,
  locals: List[LocalVariable1],

  exprs: List[IExpressionSE],
) extends IExpressionSE {
  // Every element should have at least one expression, because a block will
  // return the last expression's result as its result.
  // Even empty blocks aren't empty, they have a void() at the end.
  vassert(exprs.size >= 1)

  vassert(locals == locals.distinct)
}

case class ArgLookupSE(range: RangeS, index: Int) extends IExpressionSE

case class CheckRefCountSE(
  range: RangeS,
    refExpr: IExpressionSE,
    category: RefCountCategory,
    numExpr: IExpressionSE) extends IExpressionSE
// The type of ref count that an object might have. Used with the CheckRefCountH
// instruction for counting how many references of a certain type there are.
sealed trait RefCountCategory
// Used to count how many variables are refering to an object.
case object VariableRefCount extends RefCountCategory
// Used to count how many members are refering to an object.
case object MemberRefCount extends RefCountCategory
// Used to count how many registers are refering to an object.
case object RegisterRefCount extends RefCountCategory

 // These things will be separated by semicolons, and all be joined in a block
case class RepeaterBlockSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE

// Results in a pack, represents the differences between the expressions
case class RepeaterBlockIteratorSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE

case class ReturnSE(range: RangeS, inner: IExpressionSE) extends IExpressionSE
case class VoidSE(range: RangeS) extends IExpressionSE {}

case class SequenceESE(range: RangeS, elements: List[IExpressionSE]) extends IExpressionSE

// This thing will be repeated, separated by commas, and all be joined in a pack
case class RepeaterPackSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE

// Results in a pack, represents the differences between the elements
case class RepeaterPackIteratorSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE

case class IntLiteralSE(range: RangeS, value: Int) extends IExpressionSE

case class BoolLiteralSE(range: RangeS, value: Boolean) extends IExpressionSE

case class StrLiteralSE(range: RangeS, value: String) extends IExpressionSE

case class FloatLiteralSE(range: RangeS, value: Float) extends IExpressionSE

case class DestructSE(range: RangeS, inner: IExpressionSE) extends IExpressionSE

case class FunctionSE(function: FunctionS) extends IExpressionSE {
  override def range: RangeS = function.range
}

case class DotSE(range: RangeS, left: IExpressionSE, member: String, borrowContainer: Boolean) extends IExpressionSE

case class DotCallSE(range: RangeS, left: IExpressionSE, indexExpr: IExpressionSE) extends IExpressionSE

case class FunctionCallSE(range: RangeS, callableExpr: IExpressionSE, argsExprs1: List[IExpressionSE]) extends IExpressionSE

//case class MethodCall0(callableExpr: Expression0, objectExpr: Expression0, argsExpr: Pack0) extends Expression0

case class LocalLoadSE(range: RangeS, name: IVarNameS, targetOwnership: LoadAsP) extends IExpressionSE {
  vpass()
}
// Loads a non-local. In well formed code, this will be a function, but the user also likely
// tried to access a variable they forgot to declare.
case class OutsideLoadSE(range: RangeS, name: String, maybeTemplateArgs: Option[List[ITemplexS]], targetOwnership: LoadAsP) extends IExpressionSE
case class RuneLookupSE(range: RangeS, rune: IRuneS) extends IExpressionSE

case class UnletSE(range: RangeS, name: String) extends IExpressionSE


//case class Scramble0(elements: List[Expression0]) extends Expression0 {
//  vassert(!elements.isEmpty, "Can't have an empty scramble")
//}
//case class Scramble1(elements: List[Expression1]) extends Expression1 {
//  vassert(!elements.isEmpty, "Can't have an empty scramble")
//}
