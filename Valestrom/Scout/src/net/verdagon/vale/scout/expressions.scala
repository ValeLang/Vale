package net.verdagon.vale.scout

import net.verdagon.vale.parser.ast.{LoadAsBorrowOrIfContainerIsPointerThenPointerP, LoadAsBorrowP, LoadAsP, LoadAsPointerP, LoadAsWeakP, MoveP, PermissionP}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.rules.{ILiteralSL, IRulexSR, RuneUsage}
import net.verdagon.vale.{RangeS, vassert, vcurious, vimpl, vpass, vwat}

// patternId is a unique number, can be used to make temporary variables that wont
// collide with other things
case class LetSE(
    range: RangeS,
    rules: Array[IRulexSR],
    pattern: AtomSP,
    expr: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class IfSE(
  range: RangeS,
  condition: IExpressionSE,
  thenBody: BlockSE,
  elseBody: BlockSE
) extends IExpressionSE {
  override def hashCode(): Int = vcurious()

  vcurious(!condition.isInstanceOf[BlockSE])
}

case class LoopSE(range: RangeS, body: BlockSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
  vpass()
}

case class BreakSE(range: RangeS) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class WhileSE(range: RangeS, body: BlockSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
  vpass()
}

case class ExprMutateSE(range: RangeS, mutatee: IExpressionSE, expr: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}
case class GlobalMutateSE(range: RangeS, name: CodeNameS, expr: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}
case class LocalMutateSE(range: RangeS, name: IVarNameS, expr: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class OwnershippedSE(range: RangeS, innerExpr1: IExpressionSE, targetOwnership: LoadAsP) extends IExpressionSE {
  override def hashCode(): Int = vcurious()

  targetOwnership match {
    case LoadAsWeakP(_) =>
    case LoadAsPointerP(_) =>
    case LoadAsBorrowP(_) =>
    case LoadAsBorrowOrIfContainerIsPointerThenPointerP(_) =>
    case MoveP =>
  }
}

case class PermissionedSE(range: RangeS, innerExpr1: IExpressionSE, targetPermission: PermissionP) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}


// when we make a closure, we make a struct full of pointers to all our variables
// and the first element is our parent closure
// this can live on the stack, since blocks are limited to this expression
// later we can optimize it to only have the things we use


sealed trait IVariableUseCertainty
case object Used extends IVariableUseCertainty
case object NotUsed extends IVariableUseCertainty

case class LocalS(
    varName: IVarNameS,
    selfBorrowed: IVariableUseCertainty,
    selfMoved: IVariableUseCertainty,
    selfMutated: IVariableUseCertainty,
    childBorrowed: IVariableUseCertainty,
    childMoved: IVariableUseCertainty,
    childMutated: IVariableUseCertainty) {
  override def hashCode(): Int = vcurious()
}

case class BodySE(
    range: RangeS,
    // These are all the variables we use from parent environments.
    // We have these so templar doesn't have to dive through all the functions
    // that it calls (impossible) to figure out what's needed in a closure struct.
    closuredNames: Vector[IVarNameS],

    block: BlockSE
) {
  override def hashCode(): Int = vcurious()
  vpass()
}

case class BlockSE(
  range: RangeS,
  locals: Vector[LocalS],
  expr: IExpressionSE,
) extends IExpressionSE {
  override def hashCode(): Int = vcurious()

  vassert(locals.map(_.varName) == locals.map(_.varName).distinct)
//  expr match {
//    case BlockSE(range, locals, expr) => vcurious()
//    case _ =>
//  }
}

case class ConsecutorSE(
  exprs: Vector[IExpressionSE],
) extends IExpressionSE {
  override def hashCode(): Int = vcurious()

  override def range: RangeS = RangeS(exprs.head.range.begin, exprs.last.range.end)

  // Should have at least one expression, because we'll
  // return the last expression's result as its result.
  vassert(exprs.size > 1)
  vassert(exprs.collect({ case ConsecutorSE(_) => }).isEmpty)


//  if (exprs.size >= 2) {
//    exprs.last match {
//      case VoidSE(_) => {
//        exprs.init.last match {
//          case ReturnSE(_, _) => vcurious()
//          case VoidSE(_) => vcurious()
//          case _ =>
//        }
//      }
//      case _ =>
//    }
//  }
}

case class ArgLookupSE(range: RangeS, index: Int) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

 // These things will be separated by semicolons, and all be joined in a block
case class RepeaterBlockSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
 }

// Results in a pack, represents the differences between the expressions
case class RepeaterBlockIteratorSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class ReturnSE(range: RangeS, inner: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
  inner match {
    case ReturnSE(_, _) => vwat()
    case _ =>
  }
}
case class VoidSE(range: RangeS) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class TupleSE(range: RangeS, elements: Vector[IExpressionSE]) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}
case class StaticArrayFromValuesSE(
  range: RangeS,
  rules: Array[IRulexSR],
  maybeElementTypeST: Option[RuneUsage],
  mutabilityST: RuneUsage,
  variabilityST: RuneUsage,
  sizeST: RuneUsage,
  elements: Vector[IExpressionSE]
) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}
case class StaticArrayFromCallableSE(
  range: RangeS,
  rules: Array[IRulexSR],
  maybeElementTypeST: Option[RuneUsage],
  mutabilityST: RuneUsage,
  variabilityST: RuneUsage,
  sizeST: RuneUsage,
  callable: IExpressionSE
) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}
case class RuntimeArrayFromCallableSE(
  range: RangeS,
  rules: Array[IRulexSR],
  maybeElementTypeST: Option[RuneUsage],
  mutabilityST: RuneUsage,
  sizeSE: IExpressionSE,
  callable: IExpressionSE
) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

// This thing will be repeated, separated by commas, and all be joined in a pack
case class RepeaterPackSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

// Results in a pack, represents the differences between the elements
case class RepeaterPackIteratorSE(range: RangeS, expression: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class ConstantIntSE(range: RangeS, value: Long, bits: Int) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class ConstantBoolSE(range: RangeS, value: Boolean) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class ConstantStrSE(range: RangeS, value: String) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class ConstantFloatSE(range: RangeS, value: Double) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class DestructSE(range: RangeS, inner: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class FunctionSE(function: FunctionS) extends IExpressionSE {
  override def range: RangeS = function.range
}

case class DotSE(range: RangeS, left: IExpressionSE, member: String, borrowContainer: Boolean) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class IndexSE(range: RangeS, left: IExpressionSE, indexExpr: IExpressionSE) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}

case class FunctionCallSE(range: RangeS, callableExpr: IExpressionSE, argsExprs1: Vector[IExpressionSE]) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}


case class LocalLoadSE(range: RangeS, name: IVarNameS, targetOwnership: LoadAsP) extends IExpressionSE {
  vpass()
}
// Loads a non-local. In well formed code, this will be a function, but the user also likely
// tried to access a variable they forgot to declare.
case class OutsideLoadSE(
  range: RangeS,
  rules: Array[IRulexSR],
  name: IImpreciseNameS,
  maybeTemplateArgs: Option[Array[RuneUsage]],
  targetOwnership: LoadAsP
) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
  vpass()
}
case class RuneLookupSE(range: RangeS, rune: IRuneS) extends IExpressionSE {
  override def hashCode(): Int = vcurious()
}
