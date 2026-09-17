use super::ast::{FunctionP, NameP, OwnershipP, UnitP};
use crate::interner::StrI;
use super::pattern::PatternPP;
use super::templex::{ITemplexPT, RegionRunePT};
use crate::lexing::RangeL;

/// Expression enum - idiomatic Rust replacement for Scala's trait hierarchy
#[derive(Debug, PartialEq)]
pub enum IExpressionPE<'p> {
  Void(VoidPE),
  Pack(PackPE<'p>),
  SubExpression(SubExpressionPE<'p>),
  And(AndPE<'p>),
  Or(OrPE<'p>),
  If(IfPE<'p>),
  While(WhilePE<'p>),
  Each(EachPE<'p>),
  Range(RangePE<'p>),
  Destruct(DestructPE<'p>),
  Unlet(UnletPE<'p>),
  Mutate(MutatePE<'p>),
  Return(ReturnPE<'p>),
  Break(BreakPE),
  Let(LetPE<'p>),
  Tuple(TuplePE<'p>),
  ConstructArray(ConstructArrayPE<'p>),
  ConstantInt(ConstantIntPE),
  ConstantBool(ConstantBoolPE),
  ConstantStr(ConstantStrPE<'p>),
  ConstantFloat(ConstantFloatPE),
  StrInterpolate(StrInterpolatePE<'p>),
  Dot(DotPE<'p>),
  Index(IndexPE<'p>),
  FunctionCall(FunctionCallPE<'p>),
  BraceCall(BraceCallPE<'p>),
  Not(NotPE<'p>),
  Augment(AugmentPE<'p>),
  Transmigrate(TransmigratePE<'p>),
  BinaryCall(BinaryCallPE<'p>),
  MethodCall(MethodCallPE<'p>),
  Lookup(&'p LookupPE<'p>),
  MagicParamLookup(MagicParamLookupPE),
  Lambda(LambdaPE<'p>),
  Block(BlockPE<'p>),
  Consecutor(ConsecutorPE<'p>),
  Shortcall(ShortcallPE<'p>),
}
impl IExpressionPE<'_> {
  pub fn range(&self) -> RangeL {
    match self {
      IExpressionPE::Void(x) => x.range,
      IExpressionPE::Pack(x) => x.range,
      IExpressionPE::SubExpression(x) => x.range,
      IExpressionPE::And(x) => x.range,
      IExpressionPE::Or(x) => x.range,
      IExpressionPE::If(x) => x.range,
      IExpressionPE::While(x) => x.range,
      IExpressionPE::Each(x) => x.range,
      IExpressionPE::Range(x) => x.range,
      IExpressionPE::Destruct(x) => x.range,
      IExpressionPE::Unlet(x) => x.range,
      IExpressionPE::Mutate(x) => x.range,
      IExpressionPE::Return(x) => x.range,
      IExpressionPE::Break(x) => x.range,
      IExpressionPE::Let(x) => x.range,
      IExpressionPE::Tuple(x) => x.range,
      IExpressionPE::ConstructArray(x) => x.range,
      IExpressionPE::ConstantInt(x) => x.range,
      IExpressionPE::ConstantBool(x) => x.range,
      IExpressionPE::ConstantStr(x) => x.range,
      IExpressionPE::ConstantFloat(x) => x.range,
      IExpressionPE::StrInterpolate(x) => x.range,
      IExpressionPE::Dot(x) => x.range,
      IExpressionPE::Index(x) => x.range,
      IExpressionPE::FunctionCall(x) => x.range,
      IExpressionPE::BraceCall(x) => x.range,
      IExpressionPE::Not(x) => x.range,
      IExpressionPE::Augment(x) => x.range,
      IExpressionPE::Transmigrate(x) => x.range,
      IExpressionPE::BinaryCall(x) => x.range,
      IExpressionPE::MethodCall(x) => x.range,
      IExpressionPE::Lookup(x) => x.name.range(),
      IExpressionPE::MagicParamLookup(x) => x.range,
      IExpressionPE::Lambda(x) => x.function.range,
      IExpressionPE::Block(x) => x.range,
      IExpressionPE::Consecutor(x) => {
        assert!(!x.inners.is_empty());
        let begin = x.inners.first().unwrap().range().begin();
        let end = x.inners.last().unwrap().range().end();
        RangeL(begin, end)
      }
      IExpressionPE::Shortcall(x) => x.range,
    }
  }

  pub fn needs_semicolon_before_next_statement(&self) -> bool {
    match self {
      IExpressionPE::Void(_) => false,
      IExpressionPE::Pack(_) => true,
      IExpressionPE::SubExpression(_) => true,
      IExpressionPE::And(_) => true,
      IExpressionPE::Or(_) => true,
      IExpressionPE::If(_) => false,
      IExpressionPE::While(_) => false,
      IExpressionPE::Each(_) => false,
      IExpressionPE::Range(_) => true,
      IExpressionPE::Destruct(_) => true,
      IExpressionPE::Unlet(_) => true,
      IExpressionPE::Mutate(_) => true,
      IExpressionPE::Return(_) => true,
      IExpressionPE::Break(_) => true,
      IExpressionPE::Let(_) => true,
      IExpressionPE::Tuple(_) => true,
      IExpressionPE::ConstructArray(_) => true,
      IExpressionPE::ConstantInt(_) => true,
      IExpressionPE::ConstantBool(_) => true,
      IExpressionPE::ConstantStr(_) => true,
      IExpressionPE::ConstantFloat(_) => true,
      IExpressionPE::StrInterpolate(_) => true,
      IExpressionPE::Dot(_) => true,
      IExpressionPE::Index(_) => true,
      IExpressionPE::FunctionCall(_) => true,
      IExpressionPE::BraceCall(_) => true,
      IExpressionPE::Not(_) => true,
      IExpressionPE::Augment(_) => true,
      IExpressionPE::Transmigrate(_) => true,
      IExpressionPE::BinaryCall(_) => true,
      IExpressionPE::MethodCall(_) => true,
      IExpressionPE::Lookup(_) => true,
      IExpressionPE::MagicParamLookup(_) => true,
      IExpressionPE::Lambda(_) => true,
      IExpressionPE::Block(_) => false,
      IExpressionPE::Consecutor(x) => x
        .inners
        .last()
        .unwrap()
        .needs_semicolon_before_next_statement(),
      IExpressionPE::Shortcall(_) => true,
    }
  }

  pub fn produces_result(&self) -> bool {
    match self {
      IExpressionPE::Void(_) => false,
      IExpressionPE::Pack(_) => true,
      IExpressionPE::SubExpression(_) => true,
      IExpressionPE::And(_) => true,
      IExpressionPE::Or(_) => true,
      IExpressionPE::If(x) => x.then_body.inner.produces_result(),
      IExpressionPE::While(_) => false,
      IExpressionPE::Each(x) => x.body.inner.produces_result(),
      IExpressionPE::Range(_) => true,
      IExpressionPE::Destruct(_) => false,
      IExpressionPE::Unlet(_) => false,
      IExpressionPE::Mutate(_) => true,
      IExpressionPE::Return(_) => false,
      IExpressionPE::Break(_) => false,
      IExpressionPE::Let(_) => false,
      IExpressionPE::Tuple(_) => true,
      IExpressionPE::ConstructArray(_) => true,
      IExpressionPE::ConstantInt(_) => true,
      IExpressionPE::ConstantBool(_) => true,
      IExpressionPE::ConstantStr(_) => true,
      IExpressionPE::ConstantFloat(_) => true,
      IExpressionPE::StrInterpolate(_) => true,
      IExpressionPE::Dot(_) => true,
      IExpressionPE::Index(_) => true,
      IExpressionPE::FunctionCall(_) => true,
      IExpressionPE::BraceCall(_) => true,
      IExpressionPE::Not(_) => true,
      IExpressionPE::Augment(_) => true,
      IExpressionPE::Transmigrate(_) => true,
      IExpressionPE::BinaryCall(_) => true,
      IExpressionPE::MethodCall(_) => true,
      IExpressionPE::Lookup(_) => true,
      IExpressionPE::MagicParamLookup(_) => true,
      IExpressionPE::Lambda(_) => true,
      IExpressionPE::Block(x) => x.inner.produces_result(),
      IExpressionPE::Consecutor(x) => x.inners.last().unwrap().produces_result(),
      IExpressionPE::Shortcall(_) => true,
    }
  }
}

#[derive(Debug, PartialEq)]
pub struct VoidPE {
  pub range: RangeL,
}

#[derive(Debug, PartialEq)]
pub struct PackPE<'p> {
  pub range: RangeL,
  pub inners: &'p [&'p IExpressionPE<'p>],
}

// Parens that we use for precedence
#[derive(Debug, PartialEq)]
pub struct SubExpressionPE<'p> {
  pub range: RangeL,
  pub inner: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct AndPE<'p> {
  pub range: RangeL,
  pub left: &'p IExpressionPE<'p>,
  pub right: &'p BlockPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct OrPE<'p> {
  pub range: RangeL,
  pub left: &'p IExpressionPE<'p>,
  pub right: &'p BlockPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct IfPE<'p> {
  pub range: RangeL,
  pub condition: &'p IExpressionPE<'p>,
  pub then_body: &'p BlockPE<'p>,
  pub else_body: &'p BlockPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct WhilePE<'p> {
  pub range: RangeL,
  pub condition: &'p IExpressionPE<'p>,
  pub body: &'p BlockPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct EachPE<'p> {
  pub range: RangeL,
  pub maybe_pure: Option<RangeL>,
  pub entry_pattern: PatternPP<'p>,
  pub in_keyword_range: RangeL,
  pub iterable_expr: &'p IExpressionPE<'p>,
  pub body: &'p BlockPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct RangePE<'p> {
  pub range: RangeL,
  pub from_expr: &'p IExpressionPE<'p>,
  pub to_expr: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct DestructPE<'p> {
  pub range: RangeL,
  pub inner: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct UnletPE<'p> {
  pub range: RangeL,
  pub name: IImpreciseNameP<'p>,
}

#[derive(Debug, PartialEq)]
pub struct MutatePE<'p> {
  pub range: RangeL,
  pub mutatee: &'p IExpressionPE<'p>,
  pub source: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct ReturnPE<'p> {
  pub range: RangeL,
  pub expr: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct BreakPE {
  pub range: RangeL,
}

#[derive(Debug, PartialEq)]
pub struct LetPE<'p> {
  pub range: RangeL,
  pub pattern: &'p PatternPP<'p>,
  pub source: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct TuplePE<'p> {
  pub range: RangeL,
  pub elements: &'p [&'p IExpressionPE<'p>],
}

#[derive(Debug, PartialEq)]
pub struct StaticSizedArraySizeP<'p> {
  pub size_pt: Option<ITemplexPT<'p>>,
}

#[derive(Debug, PartialEq)]
pub enum IArraySizeP<'p> {
  RuntimeSized,
  StaticSized(StaticSizedArraySizeP<'p>),
}

#[derive(Debug, PartialEq)]
pub struct ConstructArrayPE<'p> {
  pub range: RangeL,
  pub type_pt: Option<ITemplexPT<'p>>,
  pub mutability_pt: Option<ITemplexPT<'p>>,
  pub variability_pt: Option<ITemplexPT<'p>>,
  pub size: IArraySizeP<'p>,
  pub initializing_individual_elements: bool,
  pub args: &'p [&'p IExpressionPE<'p>],
}

#[derive(Debug, PartialEq)]
pub struct ConstantIntPE {
  pub range: RangeL,
  pub value: i64,
  pub bits: Option<i64>,
}

#[derive(Debug, PartialEq)]
pub struct ConstantBoolPE {
  pub range: RangeL,
  pub value: bool,
}

#[derive(Debug, PartialEq)]
pub struct ConstantStrPE<'p> {
  pub range: RangeL,
  pub value: StrI<'p>,
}

#[derive(Debug, PartialEq)]
pub struct ConstantFloatPE {
  pub range: RangeL,
  pub value: f64,
}

#[derive(Debug, PartialEq)]
pub struct StrInterpolatePE<'p> {
  pub range: RangeL,
  pub parts: &'p [&'p IExpressionPE<'p>],
}

#[derive(Debug, PartialEq)]
pub struct DotPE<'p> {
  pub range: RangeL,
  pub left: &'p IExpressionPE<'p>,
  pub operator_range: RangeL,
  pub member: NameP<'p>,
}

#[derive(Debug, PartialEq)]
pub struct IndexPE<'p> {
  pub range: RangeL,
  pub left: &'p IExpressionPE<'p>,
  pub args: &'p [&'p IExpressionPE<'p>],
}

#[derive(Debug, PartialEq)]
pub struct FunctionCallPE<'p> {
  pub range: RangeL,
  pub operator_range: RangeL,
  pub callable_expr: &'p IExpressionPE<'p>,
  pub arg_exprs: &'p [&'p IExpressionPE<'p>],
}

#[derive(Debug, PartialEq)]
pub struct BraceCallPE<'p> {
  pub range: RangeL,
  pub operator_range: RangeL,
  pub subject_expr: &'p IExpressionPE<'p>,
  pub arg_exprs: &'p [&'p IExpressionPE<'p>],
  pub callable_readwrite: bool,
}

#[derive(Debug, PartialEq)]
pub struct NotPE<'p> {
  pub range: RangeL,
  pub inner: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct AugmentPE<'p> {
  pub range: RangeL,
  pub target_ownership: OwnershipP,
  pub inner: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct TransmigratePE<'p> {
  pub range: RangeL,
  pub target_region: NameP<'p>,
  pub inner: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct BinaryCallPE<'p> {
  pub range: RangeL,
  pub function_name: NameP<'p>,
  pub left_expr: &'p IExpressionPE<'p>,
  pub right_expr: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct MethodCallPE<'p> {
  pub range: RangeL,
  pub subject_expr: &'p IExpressionPE<'p>,
  pub operator_range: RangeL,
  pub method_lookup: &'p LookupPE<'p>,
  pub arg_exprs: &'p [&'p IExpressionPE<'p>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub enum IImpreciseNameP<'p> {
  LookupName(NameP<'p>),
  IterableName(RangeL),
  IteratorName(RangeL),
  IterationOptionName(RangeL),
}
impl IImpreciseNameP<'_> {
  pub fn range(&self) -> RangeL {
    match self {
      IImpreciseNameP::LookupName(n) => n.range(),
      IImpreciseNameP::IterableName(r) => *r,
      IImpreciseNameP::IteratorName(r) => *r,
      IImpreciseNameP::IterationOptionName(r) => *r,
    }
  }
}

#[derive(Debug, PartialEq)]
pub struct LookupPE<'p> {
  pub name: IImpreciseNameP<'p>,
  pub template_args: Option<TemplateArgsP<'p>>,
}

#[derive(Debug, PartialEq)]
pub struct TemplateArgsP<'p> {
  pub range: RangeL,
  pub args: &'p [&'p ITemplexPT<'p>],
}

#[derive(Debug, PartialEq)]
pub struct MagicParamLookupPE {
  pub range: RangeL,
}

#[derive(Debug, PartialEq)]
pub struct LambdaPE<'p> {
  pub captures: Option<UnitP>,
  pub function: FunctionP<'p>,
}

#[derive(Debug, PartialEq)]
pub struct BlockPE<'p> {
  pub range: RangeL,
  pub maybe_pure: Option<RangeL>,
  pub maybe_default_region: Option<RegionRunePT<'p>>,
  pub inner: &'p IExpressionPE<'p>,
}

#[derive(Debug, PartialEq)]
pub struct ConsecutorPE<'p> {
  pub inners: &'p [&'p IExpressionPE<'p>],
}

#[derive(Debug, PartialEq)]
pub struct ShortcallPE<'p> {
  pub range: RangeL,
  pub arg_exprs: &'p [&'p IExpressionPE<'p>],
}

