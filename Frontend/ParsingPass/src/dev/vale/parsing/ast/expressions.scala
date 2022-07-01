package dev.vale.parsing.ast

import dev.vale.lexing.RangeL
import dev.vale.{vassert, vcurious, vpass}
import dev.vale.vpass

trait IExpressionPE {
  def range: RangeL
  def needsSemicolonBeforeNextStatement: Boolean
  def producesResult(): Boolean
}

case class VoidPE(range: RangeL) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  override def producesResult(): Boolean = false
  vpass()
}

// We have this because it sometimes even a single-member pack can change the semantics.
// (moo).someMethod() will move moo, and moo.someMethod() will point moo.
// There's probably a better way to distinguish this...
case class PackPE(range: RangeL, inners: Vector[IExpressionPE]) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

// Parens that we use for precedence
case class SubExpressionPE(range: RangeL, inner: IExpressionPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class AndPE(range: RangeL, left: IExpressionPE, right: BlockPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class OrPE(range: RangeL, left: IExpressionPE, right: BlockPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class IfPE(range: RangeL, condition: IExpressionPE, thenBody: BlockPE, elseBody: BlockPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  vcurious(!condition.isInstanceOf[BlockPE])

  // assert(thenBody.producesResult() == elseBody.producesResult())
  // We dont do the above assert because we might have cases like this:
  //   if blah {
  //     return 3;
  //   } else {
  //     6
  //   }

  override def producesResult(): Boolean = {
    thenBody.producesResult()
  }
}
// condition and body are both blocks because otherwise, if we declare a variable inside them, then
// we could be declaring a variable twice. a block ensures that its scope is cleaned up, which helps
// know we can run it again.
case class WhilePE(range: RangeL, condition: IExpressionPE, body: BlockPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  override def producesResult(): Boolean = false
}
case class EachPE(range: RangeL, entryPattern: PatternPP, inKeywordRange: RangeL, iterableExpr: IExpressionPE, body: BlockPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  override def producesResult(): Boolean = body.producesResult()
}
case class RangePE(range: RangeL, fromExpr: IExpressionPE, toExpr: IExpressionPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class DestructPE(range: RangeL, inner: IExpressionPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = false
}
case class UnletPE(range: RangeL, name: IImpreciseNameP) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = false
}
//case class MatchPE(range: RangeP, condition: IExpressionPE, lambdas: Vector[LambdaPE]) extends IExpressionPE {
//  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
//  override def needsSemicolonAtEndOfStatement: Boolean = false
//}
case class MutatePE(range: RangeL, mutatee: IExpressionPE, source: IExpressionPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class ReturnPE(range: RangeL, expr: IExpressionPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = false
}
case class BreakPE(range: RangeL) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = false
}

case class LetPE(
  range: RangeL,
//  templateRules: Option[TemplateRulesP],
  pattern: PatternPP,
  source: IExpressionPE
) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = false
}

case class TuplePE(range: RangeL, elements: Vector[IExpressionPE]) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

sealed trait IArraySizeP
case object RuntimeSizedP extends IArraySizeP
case class StaticSizedP(sizePT: Option[ITemplexPT]) extends IArraySizeP { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

case class ConstructArrayPE(
  range: RangeL,
  typePT: Option[ITemplexPT],
  mutabilityPT: Option[ITemplexPT],
  variabilityPT: Option[ITemplexPT],
  size: IArraySizeP,
  // True if theyre making it like [3][10, 20, 30]
  // False if theyre making it like [3]({ _ * 10 })
  initializingIndividualElements: Boolean,
  args: Vector[IExpressionPE]
) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class ConstantIntPE(range: RangeL, value: Long, bits: Option[Long]) extends IExpressionPE {
  vpass()
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class ConstantBoolPE(range: RangeL, value: Boolean) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class ConstantStrPE(range: RangeL, value: String) extends IExpressionPE {
  vpass()
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class ConstantFloatPE(range: RangeL, value: Double) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class StrInterpolatePE(range: RangeL, parts: Vector[IExpressionPE]) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class DotPE(
  range: RangeL,
  left: IExpressionPE,
  operatorRange: RangeL,
  member: NameP) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class IndexPE(range: RangeL, left: IExpressionPE, args: Vector[IExpressionPE]) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class FunctionCallPE(
  range: RangeL,
  operatorRange: RangeL,
  callableExpr: IExpressionPE,
  argExprs: Vector[IExpressionPE]
) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class BraceCallPE(
  range: RangeL,
  operatorRange: RangeL,
  subjectExpr: IExpressionPE,
  argExprs: Vector[IExpressionPE],
  callableReadwrite: Boolean
) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class NotPE(range: RangeL, inner: IExpressionPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
  vpass()
}

case class AugmentPE(
  range: RangeL,
  targetOwnership: OwnershipP,
  inner: IExpressionPE
) extends IExpressionPE {

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
  vpass()
}

case class BinaryCallPE(
  range: RangeL,
  functionName: NameP,
  leftExpr: IExpressionPE,
  rightExpr: IExpressionPE
) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class MethodCallPE(
  range: RangeL,
  subjectExpr: IExpressionPE,
  operatorRange: RangeL,
  methodLookup: LookupPE,
  argExprs: Vector[IExpressionPE]
) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
  vpass()
}

sealed trait IImpreciseNameP {
  def range: RangeL
}
case class LookupNameP(name: NameP) extends IImpreciseNameP { override def range: RangeL = name.range }
case class IterableNameP(range: RangeL) extends IImpreciseNameP
case class IteratorNameP(range: RangeL) extends IImpreciseNameP
case class IterationOptionNameP(range: RangeL) extends IImpreciseNameP

case class LookupPE(
  name: IImpreciseNameP,
  templateArgs: Option[TemplateArgsP]
) extends IExpressionPE {
  vpass()
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def range: RangeL = name.range
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class TemplateArgsP(range: RangeL, args: Vector[ITemplexPT]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class MagicParamLookupPE(range: RangeL) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class LambdaPE(
  // Just here for syntax highlighting so far
  captures: Option[UnitP],
  function: FunctionP
) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def range: RangeL = function.range
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class BlockPE(range: RangeL, inner: IExpressionPE) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  override def producesResult(): Boolean = inner.producesResult()
}

case class ConsecutorPE(inners: Vector[IExpressionPE]) extends IExpressionPE {
  // Should have at least one expression, because a block will
  // return the last expression's result as its result.
  // Even empty blocks aren't empty, they have a void() at the end.
  vassert(inners.size >= 1)

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();

  override def range: RangeL = RangeL(inners.head.range.begin, inners.last.range.end)

  override def needsSemicolonBeforeNextStatement: Boolean = inners.last.needsSemicolonBeforeNextStatement
  override def producesResult(): Boolean = inners.last.producesResult()
}

case class ShortcallPE(range: RangeL, argExprs: Vector[IExpressionPE]) extends IExpressionPE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true

  vpass()
}
