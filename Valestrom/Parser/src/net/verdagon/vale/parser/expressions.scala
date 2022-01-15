package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vcurious, vimpl, vpass}

trait IExpressionPE {
  def range: Range
}

case class VoidPE(range: Range) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

// We have this because it sometimes even a single-member pack can change the semantics.
// (moo).someMethod() will move moo, and moo.someMethod() will point moo.
// There's probably a better way to distinguish this...
case class PackPE(range: Range, inners: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class LoadPE(
    range: Range,
    expr: IExpressionPE,
    targetOwnership: LoadAsP) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  targetOwnership match {
    case LoadAsWeakP(_) =>
    case LoadAsBorrowP(_) =>
    case LoadAsPointerP(_) =>
  }
}

case class AndPE(range: Range, left: BlockPE, right: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class OrPE(range: Range, left: BlockPE, right: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class IfPE(range: Range, condition: BlockPE, thenBody: BlockPE, elseBody: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
// condition and body are both blocks because otherwise, if we declare a variable inside them, then
// we could be declaring a variable twice. a block ensures that its scope is cleaned up, which helps
// know we can run it again.
case class WhilePE(range: Range, condition: BlockPE, body: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class ResultPE(range: Range, sourceExpr: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class EachPE(range: Range, entryPattern: PatternPP, iterableExpr: IExpressionPE, body: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class RangePE(range: Range, fromExpr: BlockPE, toExpr: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class DestructPE(range: Range, inner: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class MatchPE(range: Range, condition: IExpressionPE, lambdas: Vector[LambdaPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class MutatePE(range: Range, mutatee: IExpressionPE, expr: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class ReturnPE(range: Range, expr: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class LetPE(
  range: Range,
  templateRules: Option[TemplateRulesP],
  pattern: PatternPP,
  source: IExpressionPE
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class BadLetPE(
  range: Range
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class TuplePE(range: Range, elements: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

sealed trait IArraySizeP
case object RuntimeSizedP extends IArraySizeP
case class StaticSizedP(sizePT: Option[ITemplexPT]) extends IArraySizeP { override def hashCode(): Int = vcurious() }

case class ConstructArrayPE(
  range: Range,
  mutabilityPT: Option[ITemplexPT],
  variabilityPT: Option[ITemplexPT],
  size: IArraySizeP,
  // True if theyre making it like [3][10, 20, 30]
  // False if theyre making it like [3]({ _ * 10 })
  initializingIndividualElements: Boolean,
  args: Vector[IExpressionPE]
) extends IExpressionPE {
  override def hashCode(): Int = vcurious()
}

case class ConstantIntPE(range: Range, value: Long, bits: Int) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class ConstantBoolPE(range: Range, value: Boolean) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class ConstantStrPE(range: Range, value: String) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}
case class ConstantFloatPE(range: Range, value: Double) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class StrInterpolatePE(range: Range, parts: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class DotPE(
  range: Range,
  left: IExpressionPE,
  operatorRange: Range,
  isMapAccess: Boolean,
  member: NameP) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class IndexPE(range: Range, left: IExpressionPE, args: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class FunctionCallPE(
  range: Range,
  inline: Option[UnitP],
  operatorRange: Range,
  isMapCall: Boolean,
  callableExpr: IExpressionPE,
  argExprs: Vector[IExpressionPE],
  // If we're calling a lambda or some other callable struct,
  // the 'this' ptr parameter might want a certain ownership,
  // so the user might specify that.
  targetOwnershipForCallable: LoadAsP
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class MethodCallPE(
  range: Range,
  inline: Option[UnitP],
  subjectExpr: IExpressionPE,
  operatorRange: Range,
  subjectTargetOwnership: LoadAsP,
  isMapCall: Boolean,
  methodLookup: LookupPE,
  argExprs: Vector[IExpressionPE]
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  vpass()
}

case class LookupPE(
  name: NameP,
  templateArgs: Option[TemplateArgsP]
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def range: Range = name.range
}
case class TemplateArgsP(range: Range, args: Vector[ITemplexPT]) { override def hashCode(): Int = vcurious() }

case class MagicParamLookupPE(range: Range) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
}

case class LambdaPE(
  // Just here for syntax highlighting so far
  captures: Option[UnitP],
  function: FunctionP
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def range: Range = function.range
}

case class BlockPE(range: Range, elements: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  // Every element should have at least one expression, because a block will
  // return the last expression's result as its result.
  // Even empty blocks aren't empty, they have a void() at the end.
  vassert(elements.size >= 1)
}

case class ShortcallPE(range: Range, argExprs: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious()

  vpass()
}
