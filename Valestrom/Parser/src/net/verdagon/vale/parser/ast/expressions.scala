package net.verdagon.vale.parser.ast

import net.verdagon.vale.{vassert, vcurious, vpass}

trait IExpressionPE {
  def range: RangeP
  def needsSemicolonBeforeNextStatement: Boolean
  def producesResult(): Boolean
}

case class VoidPE(range: RangeP) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  override def producesResult(): Boolean = false
  vpass()
}

// We have this because it sometimes even a single-member pack can change the semantics.
// (moo).someMethod() will move moo, and moo.someMethod() will point moo.
// There's probably a better way to distinguish this...
case class PackPE(range: RangeP, inners: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

// Parens that we use for precedence
case class SubExpressionPE(range: RangeP, inner: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class AndPE(range: RangeP, left: IExpressionPE, right: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class OrPE(range: RangeP, left: IExpressionPE, right: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class IfPE(range: RangeP, condition: IExpressionPE, thenBody: BlockPE, elseBody: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  vcurious(!condition.isInstanceOf[BlockPE])

  // assert(thenBody.producesResult() == elseBody.producesResult())
  // We dont do the above assert because we might have cases like this:
  //   if blah {
  //     ret 3;
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
case class WhilePE(range: RangeP, condition: IExpressionPE, body: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  override def producesResult(): Boolean = false
}
case class EachPE(range: RangeP, entryPattern: PatternPP, inKeywordRange: RangeP, iterableExpr: IExpressionPE, body: BlockPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  override def producesResult(): Boolean = body.producesResult()
}
case class RangePE(range: RangeP, fromExpr: IExpressionPE, toExpr: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class DestructPE(range: RangeP, inner: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = false
}
//case class MatchPE(range: RangeP, condition: IExpressionPE, lambdas: Vector[LambdaPE]) extends IExpressionPE {
//  override def hashCode(): Int = vcurious();
//  override def needsSemicolonAtEndOfStatement: Boolean = false
//}
case class MutatePE(range: RangeP, mutatee: IExpressionPE, source: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class ReturnPE(range: RangeP, expr: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = false
}
case class BreakPE(range: RangeP) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = false
}

case class LetPE(
  range: RangeP,
//  templateRules: Option[TemplateRulesP],
  pattern: PatternPP,
  source: IExpressionPE
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = false
}

case class TuplePE(range: RangeP, elements: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

sealed trait IArraySizeP
case object RuntimeSizedP extends IArraySizeP
case class StaticSizedP(sizePT: Option[ITemplexPT]) extends IArraySizeP { override def hashCode(): Int = vcurious() }

case class ConstructArrayPE(
  range: RangeP,
  typePT: Option[ITemplexPT],
  mutabilityPT: Option[ITemplexPT],
  variabilityPT: Option[ITemplexPT],
  size: IArraySizeP,
  // True if theyre making it like [3][10, 20, 30]
  // False if theyre making it like [3]({ _ * 10 })
  initializingIndividualElements: Boolean,
  args: Vector[IExpressionPE]
) extends IExpressionPE {
  override def hashCode(): Int = vcurious()
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class ConstantIntPE(range: RangeP, value: Long, bits: Int) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class ConstantBoolPE(range: RangeP, value: Boolean) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class ConstantStrPE(range: RangeP, value: String) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class ConstantFloatPE(range: RangeP, value: Double) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class StrInterpolatePE(range: RangeP, parts: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class DotPE(
  range: RangeP,
  left: IExpressionPE,
  operatorRange: RangeP,
  member: NameP) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class IndexPE(range: RangeP, left: IExpressionPE, args: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class FunctionCallPE(
  range: RangeP,
  operatorRange: RangeP,
  callableExpr: IExpressionPE,
  argExprs: Vector[IExpressionPE],
  callableReadwrite: Boolean
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class BraceCallPE(
  range: RangeP,
  operatorRange: RangeP,
  subjectExpr: IExpressionPE,
  argExprs: Vector[IExpressionPE],
  callableReadwrite: Boolean
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class NotPE(range: RangeP, inner: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
  vpass()
}

case class AugmentPE(
  range: RangeP,
  targetOwnership: OwnershipP,
  targetPermission: Option[PermissionP],
  inner: IExpressionPE
) extends IExpressionPE {

  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
  vpass()
}

case class BinaryCallPE(
  range: RangeP,
  functionName: NameP,
  leftExpr: IExpressionPE,
  rightExpr: IExpressionPE
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class MethodCallPE(
  range: RangeP,
  subjectExpr: IExpressionPE,
  operatorRange: RangeP,
  subjectReadwrite: Boolean,
  methodLookup: LookupPE,
  argExprs: Vector[IExpressionPE]
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
  vpass()
}

sealed trait IImpreciseNameP {
  def range: RangeP
}
case class LookupNameP(name: NameP) extends IImpreciseNameP { override def range: RangeP = name.range }
case class IterableNameP(range: RangeP) extends IImpreciseNameP
case class IteratorNameP(range: RangeP) extends IImpreciseNameP
case class IterationOptionNameP(range: RangeP) extends IImpreciseNameP

case class LookupPE(
  name: IImpreciseNameP,
  templateArgs: Option[TemplateArgsP]
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def range: RangeP = name.range
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}
case class TemplateArgsP(range: RangeP, args: Vector[ITemplexPT]) {
  override def hashCode(): Int = vcurious()
}

case class MagicParamLookupPE(range: RangeP) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class LambdaPE(
  // Just here for syntax highlighting so far
  captures: Option[UnitP],
  function: FunctionP
) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def range: RangeP = function.range
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true
}

case class BlockPE(range: RangeP, inner: IExpressionPE) extends IExpressionPE {
  override def hashCode(): Int = vcurious();
  override def needsSemicolonBeforeNextStatement: Boolean = false
  override def producesResult(): Boolean = inner.producesResult()
}

case class ConsecutorPE(inners: Vector[IExpressionPE]) extends IExpressionPE {
  // Should have at least one expression, because a block will
  // return the last expression's result as its result.
  // Even empty blocks aren't empty, they have a void() at the end.
  vassert(inners.size >= 1)

  override def hashCode(): Int = vcurious();

  override def range: RangeP = RangeP(inners.head.range.begin, inners.last.range.end)

  override def needsSemicolonBeforeNextStatement: Boolean = inners.last.needsSemicolonBeforeNextStatement
  override def producesResult(): Boolean = inners.last.producesResult()
}

case class ShortcallPE(range: RangeP, argExprs: Vector[IExpressionPE]) extends IExpressionPE {
  override def hashCode(): Int = vcurious()
  override def needsSemicolonBeforeNextStatement: Boolean = true
  override def producesResult(): Boolean = true

  vpass()
}
