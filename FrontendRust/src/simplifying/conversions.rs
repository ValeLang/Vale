#[allow(unused_imports)]
use crate::utils::range::CodeLocationS;
#[allow(unused_imports)]
use crate::instantiating::ast::types::{MutabilityI, VariabilityI, OwnershipI, LocationI};
#[allow(unused_imports)]
use crate::final_ast::types::{CodeLocation, Mutability, Variability, OwnershipH, LocationH};
#[allow(unused_imports)]
use crate::postparsing::itemplatatype::ITemplataType;

pub fn evaluate_code_location(loc: CodeLocationS) -> CodeLocation {
    panic!("Unimplemented: evaluate_code_location");
}

pub fn evaluate_mutability(mutability: MutabilityI) -> Mutability {
    match mutability {
        MutabilityI::Mutable => Mutability::Mutable,
        MutabilityI::Immutable => Mutability::Immutable,
    }
}

pub fn evaluate_mutability_templata(mutability: MutabilityI) -> Mutability {
    match mutability {
        MutabilityI::Mutable => Mutability::Mutable,
        MutabilityI::Immutable => Mutability::Immutable,
    }
}

pub fn evaluate_variability_templata(mutability: VariabilityI) -> Variability {
    match mutability {
        VariabilityI::Varying => Variability::Varying,
        VariabilityI::Final => Variability::Final,
    }
}

pub fn evaluate_location(location: LocationI) -> LocationH {
    panic!("Unimplemented: evaluate_location");
}

pub fn evaluate_variability(variability: VariabilityI) -> Variability {
    match variability {
        VariabilityI::Final => Variability::Final,
        VariabilityI::Varying => Variability::Varying,
    }
}

pub fn evaluate_ownership(ownership: OwnershipI) -> OwnershipH {
    match ownership {
        OwnershipI::Own => OwnershipH::OwnH,
        OwnershipI::ImmutableBorrow => OwnershipH::ImmutableBorrowH,
        OwnershipI::MutableBorrow => OwnershipH::MutableBorrowH,
        OwnershipI::ImmutableShare => OwnershipH::ImmutableShareH,
        OwnershipI::MutableShare => OwnershipH::MutableShareH,
        OwnershipI::Weak => OwnershipH::WeakH,
    }
}

pub fn unevaluate_templata_type(tyype: ITemplataType) -> ITemplataType {
    panic!("Unimplemented: unevaluate_templata_type");
}

