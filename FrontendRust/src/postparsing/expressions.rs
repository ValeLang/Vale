use crate::interner::StrI;
use crate::postparsing::ast::{FunctionS, IExpressionSE as IExpressionSETrait, LocationInDenizen};
use crate::postparsing::names::{CodeNameS, IImpreciseNameS, IRuneS, IVarNameS};
use crate::postparsing::patterns::AtomSP;
use crate::postparsing::rules::{IRulexSR, RuneUsage};
use crate::parsing::ast::LoadAsP;
use crate::utils::range::RangeS;

#[derive(Debug, PartialEq)]
pub struct LetSE<'s> {
  pub range: RangeS<'s>,
  pub rules: &'s [IRulexSR<'s>],
  pub pattern: AtomSP<'s>,
  pub expr: &'s IExpressionSE<'s>,
}
#[derive(Debug, PartialEq)]
pub struct IfSE<'s> {
  pub range: RangeS<'s>,
  pub condition: &'s IExpressionSE<'s>,
  pub then_body: &'s BlockSE<'s>,
  pub else_body: &'s BlockSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct BreakSE<'s> {
  pub range: RangeS<'s>,
}

#[derive(Debug, PartialEq)]
pub struct WhileSE<'s> {
  pub range: RangeS<'s>,
  pub body: &'s BlockSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct MapSE<'s> {
  pub range: RangeS<'s>,
  pub body: &'s BlockSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct ExprMutateSE<'s> {
  pub range: RangeS<'s>,
  pub mutatee: &'s IExpressionSE<'s>,
  pub expr: &'s IExpressionSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct LocalMutateSE<'s> {
  pub range: RangeS<'s>,
  pub name: IVarNameS<'s>,
  pub expr: &'s IExpressionSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct OwnershippedSE<'s> {
  pub range: RangeS<'s>,
  pub inner_expr: &'s IExpressionSE<'s>,
  pub target_ownership: LoadAsP,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum IVariableUseCertainty {
  Used,
  NotUsed,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub struct LocalS<'s> {
  pub var_name: IVarNameS<'s>,
  pub self_borrowed: IVariableUseCertainty,
  pub self_moved: IVariableUseCertainty,
  pub self_mutated: IVariableUseCertainty,
  pub child_borrowed: IVariableUseCertainty,
  pub child_moved: IVariableUseCertainty,
  pub child_mutated: IVariableUseCertainty,
}

#[derive(Debug, PartialEq)]
pub struct BodySE<'s> {
  pub range: RangeS<'s>,
  pub closured_names: &'s [IVarNameS<'s>],
  pub block: &'s BlockSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct PureSE<'s> {
  pub range: RangeS<'s>,
  pub location: LocationInDenizen<'s>,
  pub inner: &'s IExpressionSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct BlockSE<'s> {
  pub range: RangeS<'s>,
  pub locals: &'s [LocalS<'s>],
  pub expr: &'s IExpressionSE<'s>,
}

#[derive(Debug, PartialEq)]
pub enum IExpressionSE<'s> {
  Let(LetSE<'s>),
  If(IfSE<'s>),
  Break(BreakSE<'s>),
  While(WhileSE<'s>),
  Map(MapSE<'s>),
  ExprMutate(ExprMutateSE<'s>),
  LocalMutate(LocalMutateSE<'s>),
  Consecutor(ConsecutorSE<'s>),
  Void(VoidSE<'s>),
  Tuple(TupleSE<'s>),
  StaticArrayFromValues(StaticArrayFromValuesSE<'s>),
  StaticArrayFromCallable(StaticArrayFromCallableSE<'s>),
  NewRuntimeSizedArray(NewRuntimeSizedArraySE<'s>),
  Block(&'s BlockSE<'s>),
  Pure(PureSE<'s>),
  Return(ReturnSE<'s>),
  ConstantInt(ConstantIntSE<'s>),
  ConstantBool(ConstantBoolSE<'s>),
  ConstantStr(ConstantStrSE<'s>),
  ConstantFloat(ConstantFloatSE<'s>),
  Destruct(DestructSE<'s>),
  Unlet(UnletSE<'s>),
  Function(FunctionSE<'s>),
  Dot(DotSE<'s>),
  Index(IndexSE<'s>),
  FunctionCall(FunctionCallSE<'s>),
  LocalLoad(LocalLoadSE<'s>),
  OverloadSet(OverloadSetSE<'s>),
  RuneLookup(RuneLookupSE<'s>),
  Ownershipped(OwnershippedSE<'s>),
}

impl<'s> IExpressionSETrait<'s> for IExpressionSE<'s> {
  fn range(&self) -> RangeS<'s> {
    match self {
      IExpressionSE::Let(x) => x.range.clone(),
      IExpressionSE::If(x) => x.range.clone(),
      IExpressionSE::Break(x) => x.range.clone(),
      IExpressionSE::While(x) => x.range.clone(),
      IExpressionSE::Map(x) => x.range.clone(),
      IExpressionSE::ExprMutate(x) => x.range.clone(),
      IExpressionSE::LocalMutate(x) => x.range.clone(),
      IExpressionSE::Consecutor(x) => x.range(),
      IExpressionSE::Void(x) => x.range.clone(),
      IExpressionSE::Tuple(x) => x.range.clone(),
      IExpressionSE::StaticArrayFromValues(x) => x.range.clone(),
      IExpressionSE::StaticArrayFromCallable(x) => x.range.clone(),
      IExpressionSE::NewRuntimeSizedArray(x) => x.range.clone(),
      IExpressionSE::Block(x) => x.range.clone(),
      IExpressionSE::Pure(x) => x.range.clone(),
      IExpressionSE::Return(x) => x.range.clone(),
      IExpressionSE::ConstantInt(x) => x.range.clone(),
      IExpressionSE::ConstantBool(x) => x.range.clone(),
      IExpressionSE::ConstantStr(x) => x.range.clone(),
      IExpressionSE::ConstantFloat(x) => x.range.clone(),
      IExpressionSE::Destruct(x) => x.range.clone(),
      IExpressionSE::Unlet(x) => x.range.clone(),
      IExpressionSE::Function(x) => x.function.range.clone(),
      IExpressionSE::Dot(x) => x.range.clone(),
      IExpressionSE::Index(x) => x.range.clone(),
      IExpressionSE::FunctionCall(x) => x.range.clone(),
      IExpressionSE::LocalLoad(x) => x.range.clone(),
      IExpressionSE::OverloadSet(x) => x.lookup.range.clone(),
      IExpressionSE::RuneLookup(x) => x.range.clone(),
      IExpressionSE::Ownershipped(x) => x.range.clone(),
    }
  }
  
}

#[derive(Debug, PartialEq)]
pub struct ConsecutorSE<'s> {
  pub exprs: &'s [&'s IExpressionSE<'s>],
}

impl<'s> ConsecutorSE<'s> {
  pub fn range(&self) -> RangeS<'s> {
    assert!(!self.exprs.is_empty());
    RangeS::new(
      self.exprs.first().unwrap().range().begin,
      self.exprs.last().unwrap().range().end,
    )
  }

}

#[derive(Debug, PartialEq)]
pub struct ReturnSE<'s> {
  pub range: RangeS<'s>,
  pub inner: &'s IExpressionSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct VoidSE<'s> {
  pub range: RangeS<'s>,
}

#[derive(Debug, PartialEq)]
pub struct TupleSE<'s> {
  pub range: RangeS<'s>,
  pub elements: &'s [&'s IExpressionSE<'s>],
}

#[derive(Debug, PartialEq)]
pub struct StaticArrayFromValuesSE<'s> {
  pub range: RangeS<'s>,
  pub rules: &'s [IRulexSR<'s>],
  pub maybe_element_type_st: Option<RuneUsage<'s>>,
  pub mutability_st: RuneUsage<'s>,
  pub variability_st: RuneUsage<'s>,
  pub size_st: RuneUsage<'s>,
  pub elements: &'s [&'s IExpressionSE<'s>],
}

#[derive(Debug, PartialEq)]
pub struct StaticArrayFromCallableSE<'s> {
  pub range: RangeS<'s>,
  pub rules: &'s [IRulexSR<'s>],
  pub maybe_element_type_st: Option<RuneUsage<'s>>,
  pub mutability_st: RuneUsage<'s>,
  pub variability_st: RuneUsage<'s>,
  pub size_st: RuneUsage<'s>,
  pub callable: &'s IExpressionSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct NewRuntimeSizedArraySE<'s> {
  pub range: RangeS<'s>,
  pub rules: &'s [IRulexSR<'s>],
  pub maybe_element_type_st: Option<RuneUsage<'s>>,
  pub mutability_st: RuneUsage<'s>,
  pub size: &'s IExpressionSE<'s>,
  pub callable: Option<&'s IExpressionSE<'s>>,
}

#[derive(Debug, PartialEq)]
pub struct ConstantIntSE<'s> {
  pub range: RangeS<'s>,
  pub value: i64,
  pub bits: i32,
}
#[derive(Debug, PartialEq)]
pub struct ConstantBoolSE<'s> {
  pub range: RangeS<'s>,
  pub value: bool,
}

#[derive(Debug, PartialEq)]
pub struct ConstantStrSE<'s> {
  pub range: RangeS<'s>,
  pub value: StrI<'s>,
}

#[derive(Debug, PartialEq)]
pub struct ConstantFloatSE<'s> {
  pub range: RangeS<'s>,
  pub value: f64,
}

#[derive(Debug, PartialEq)]
pub struct DestructSE<'s> {
  pub range: RangeS<'s>,
  pub inner: &'s IExpressionSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct UnletSE<'s> {
  pub range: RangeS<'s>,
  pub name: IVarNameS<'s>,
}

#[derive(Debug, PartialEq)]
pub struct FunctionSE<'s> {
  pub function: &'s FunctionS<'s>,
}

#[derive(Debug, PartialEq)]
pub struct DotSE<'s> {
  pub range: RangeS<'s>,
  pub left: &'s IExpressionSE<'s>,
  pub member: StrI<'s>,
  pub borrow_container: bool,
}

#[derive(Debug, PartialEq)]
pub struct IndexSE<'s> {
  pub range: RangeS<'s>,
  pub left: &'s IExpressionSE<'s>,
  pub index_expr: &'s IExpressionSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct FunctionCallSE<'s> {
  pub range: RangeS<'s>,
  pub location: LocationInDenizen<'s>,
  pub callable_expr: &'s IExpressionSE<'s>,
  pub arg_exprs: &'s [&'s IExpressionSE<'s>],
}

#[derive(Debug, PartialEq)]
pub struct LocalLoadSE<'s> {
  pub range: RangeS<'s>,
  pub name: IVarNameS<'s>,
  pub target_ownership: LoadAsP,
}
// One step in a OutsideLoadSE. See OutsideLoadSE comments.
#[derive(Debug, PartialEq)]
pub struct LoadPartSE<'s> {
  pub name: IImpreciseNameS<'s>,
  pub explicit_template_args: &'s [RuneUsage<'s>],
}

// A load from something that lives outside the current definition.
// For example:
//     v = Vec<int>.with_capacity(42)
// would have a OutsideLoadSE for the `Vec<int>.with_capacity` part.
// It would look like this:
// - parts: [LoadPartSE("Vec", [$0]), LoadPartSE("with_capacity", [])]
// - rules: [$0 = LookupSR("int")]
// Per @PRIIROZ, we add containers' generic params *after* the function's generic params.
// Example:
//     number_to_corresponding_string = HashMap<int, str>.create_and_fill(64, 42, i => to_string(i))
// Would look like this:
// - parts: [LoadPartSE("HashMap", [$0, $1]), LoadPartSE("create_and_fill", [$2])]
// - rules: [$0 = "int", $1 = "str", $2 = main:lambda:1]
//
// This is only used by OverloadSetSE so far, but someday it could be used for looking up associated aliases on structs
// or something.
#[derive(Debug, PartialEq)]
pub struct OutsideLoadSE<'s> {
  pub range: RangeS<'s>,
  pub rules: &'s [IRulexSR<'s>],
  // parts' explicitArgs are runes that refer to the above rules.
  pub parts: &'s [&'s LoadPartSE<'s>],
}

#[derive(Debug, PartialEq)]
pub struct OverloadSetSE<'s> {
  pub lookup: OutsideLoadSE<'s>,
}

#[derive(Debug, PartialEq)]
pub struct RuneLookupSE<'s> {
  pub range: RangeS<'s>,
  pub rune: IRuneS<'s>,
}
