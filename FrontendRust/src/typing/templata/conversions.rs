use crate::parsing::ast::ast::*;
use crate::typing::types::types::*;

pub fn evaluate_mutability(mutability: MutabilityP) -> MutabilityT {
  match mutability {
    MutabilityP::Mutable => MutabilityT::Mutable,
    MutabilityP::Immutable => MutabilityT::Immutable,
  }
}

pub fn evaluate_location(location: LocationP) -> LocationT {
  panic!("Unimplemented: evaluate_location");
}

pub fn evaluate_variability(variability: VariabilityP) -> VariabilityT {
  match variability {
    VariabilityP::Final => VariabilityT::Final,
    VariabilityP::Varying => VariabilityT::Varying,
  }
}

pub fn evaluate_ownership(ownership: OwnershipP) -> OwnershipT {
  match ownership {
    OwnershipP::Own => OwnershipT::Own,
    OwnershipP::Borrow => OwnershipT::Borrow,
    OwnershipP::Weak => OwnershipT::Weak,
    OwnershipP::Share => OwnershipT::Share,
    OwnershipP::Live => { panic!("implement: evaluate_ownership Live"); }
  }
}

pub fn evaluate_maybe_ownership(maybe_ownership: Option<OwnershipP>) -> Option<OwnershipT> {
  panic!("Unimplemented: evaluate_maybe_ownership");
}

pub fn unevaluate_ownership(ownership: OwnershipT) -> OwnershipP {
  panic!("Unimplemented: unevaluate_ownership");
}

pub fn unevaluate_mutability(mutability: MutabilityT) -> MutabilityP {
  panic!("Unimplemented: unevaluate_mutability");
}

