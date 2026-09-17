use super::ast::NameP;
use super::templex::ITemplexPT;
use crate::lexing::RangeL;

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct AbstractP {
  pub range: RangeL,
}

#[derive(Debug, PartialEq)]
pub struct ParameterP<'p> {
  pub range: RangeL,
  pub virtuality: Option<AbstractP>,
  pub maybe_pre_checked: Option<RangeL>,
  pub self_borrow: Option<RangeL>,
  pub pattern: Option<PatternPP<'p>>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct DestinationLocalP<'p> {
  pub decl: INameDeclarationP<'p>,
  pub mutate: Option<RangeL>,
}

#[derive(Debug, PartialEq)]
pub struct PatternPP<'p> {
  pub range: RangeL,
  pub destination: Option<DestinationLocalP<'p>>,
  pub templex: Option<ITemplexPT<'p>>,
  pub destructure: Option<DestructureP<'p>>,
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub struct DestructureP<'p> {
  pub range: RangeL,
  pub patterns: &'p [PatternPP<'p>],
}

#[derive(Copy, Clone, Debug, PartialEq)]
pub enum INameDeclarationP<'p> {
  LocalNameDeclaration(NameP<'p>),
  IgnoredLocalNameDeclaration(RangeL),
  IterableNameDeclaration(RangeL),
  IteratorNameDeclaration(RangeL),
  IterationOptionNameDeclaration(RangeL),
  ConstructingMemberNameDeclaration(NameP<'p>),
}
impl INameDeclarationP<'_> {
  pub fn range(&self) -> RangeL {
    match self {
      INameDeclarationP::LocalNameDeclaration(n) => n.range(),
      INameDeclarationP::IgnoredLocalNameDeclaration(r) => *r,
      INameDeclarationP::IterableNameDeclaration(r) => *r,
      INameDeclarationP::IteratorNameDeclaration(r) => *r,
      INameDeclarationP::IterationOptionNameDeclaration(r) => *r,
      INameDeclarationP::ConstructingMemberNameDeclaration(n) => n.range(),
    }
  }
}

