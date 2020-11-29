package net.verdagon.vale.parser

import net.verdagon.vale.vassert

trait IExpressionPE {
  def range: Range
}

case class VoidPE(range: Range) extends IExpressionPE {}

case class OwnershippedPE(range: Range, expr: IExpressionPE, targetOwnership: OwnershipP) extends IExpressionPE {
  targetOwnership match {
    case WeakP =>
    case BorrowP =>
  }
}

case class MovePE(range: Range, expr: IExpressionPE) extends IExpressionPE

case class AndPE(range: Range, left: BlockPE, right: BlockPE) extends IExpressionPE

case class OrPE(range: Range, left: BlockPE, right: BlockPE) extends IExpressionPE

case class IfPE(range: Range, condition: BlockPE, thenBody: BlockPE, elseBody: BlockPE) extends IExpressionPE
// condition and body are both blocks because otherwise, if we declare a variable inside them, then
// we could be declaring a variable twice. a block ensures that its scope is cleaned up, which helps
// know we can run it again.
case class WhilePE(range: Range, condition: BlockPE, body: BlockPE) extends IExpressionPE
case class DestructPE(range: Range, inner: IExpressionPE) extends IExpressionPE
case class MatchPE(range: Range, condition: IExpressionPE, lambdas: List[LambdaPE]) extends IExpressionPE
case class MutatePE(range: Range, mutatee: IExpressionPE, expr: IExpressionPE) extends IExpressionPE
case class ReturnPE(range: Range, expr: IExpressionPE) extends IExpressionPE
//case class SwapPE(exprA: IExpressionPE, exprB: IExpressionPE) extends IExpressionPE

case class LetPE(
  range: Range,
  templateRules: Option[TemplateRulesP],
  pattern: PatternPP,
  expr: IExpressionPE
) extends IExpressionPE

case class SequencePE(range: Range, elements: List[IExpressionPE]) extends IExpressionPE

case class IntLiteralPE(range: Range, value: Int) extends IExpressionPE
case class BoolLiteralPE(range: Range, value: Boolean) extends IExpressionPE
case class StrLiteralPE(range: Range, value: String) extends IExpressionPE
case class FloatLiteralPE(range: Range, value: Float) extends IExpressionPE

case class StrInterpolatePE(range: Range, parts: List[IExpressionPE]) extends IExpressionPE

case class DotPE(
  range: Range,
  left: IExpressionPE,
  operatorRange: Range,
  isMapAccess: Boolean,
  targetOwnership: OwnershipP,
  member: StringP) extends IExpressionPE

case class IndexPE(range: Range, left: IExpressionPE, args: List[IExpressionPE]) extends IExpressionPE

case class FunctionCallPE(
  range: Range,
  inline: Option[UnitP],
  operatorRange: Range,
  isMapCall: Boolean,
  callableExpr: IExpressionPE,
  argExprs: List[IExpressionPE]
) extends IExpressionPE

case class MethodCallPE(
  range: Range,
  callableExpr: IExpressionPE,
  operatorRange: Range,
  isMapCall: Boolean,
  methodLookup: LookupPE,
  argExprs: List[IExpressionPE]
) extends IExpressionPE

case class TemplateArgsP(range: Range, args: List[ITemplexPT])
// Function lookups and local lookups are combined into the same thing because
// we can't tell whether something's a function or a local until we get to the scout.
case class LookupPE(
  name: StringP,
  templateArgs: Option[TemplateArgsP],
  targetOwnership: OwnershipP,
) extends IExpressionPE {
  override def range: Range = name.range

  if (templateArgs.nonEmpty) {
    vassert(targetOwnership == BorrowP)
  }
}
case class MagicParamLookupPE(
  range: Range,
  targetOwnership: OwnershipP
) extends IExpressionPE

case class LambdaPE(
  // Just here for syntax highlighting so far
  captures: Option[UnitP],
  function: FunctionP
) extends IExpressionPE {
  override def range: Range = function.range
}

case class BlockPE(range: Range, elements: List[IExpressionPE]) extends IExpressionPE {
  // Every element should have at least one expression, because a block will
  // return the last expression's result as its result.
  // Even empty blocks aren't empty, they have a void() at the end.
  vassert(elements.size >= 1)
}
