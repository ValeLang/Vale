// Re-interning bridge: translates typing-pass output (`&'t IdT<'s, 't>` and
// friends) into instantiating-pass input (`&'i IdI<'s, 'i, sI>`) at the
// T → I boundary.
//
// Per Slab 16h: SIGNATURES ONLY land here. All bodies panic. Identity-preserving
// memoization and pointer-equality assertions are body-migration concerns.
//
// Region mode at the T → I boundary is always `sI` — by definition, instantiation
// starts with subjective region mode and only collapses to `nI` / `cI` later
// (see RegionCollapserConsistent / RegionCollapserIndividual).
//
// Architectural choice: free functions on `Reintern`, not methods on the interner
// (which stays pure-`'i`-side) or on the instantiator (which is stateful).

use crate::instantiating::ast::names::IdI;
use crate::instantiating::ast::names::INameI;
use crate::instantiating::ast::types::{
    CoordI, IRegionsModeIT, KindIT, sI, StaticSizedArrayIT, RuntimeSizedArrayIT, StructIT, InterfaceIT,
};
use crate::instantiating::instantiating_interner::InstantiatingInterner;
use crate::typing::ast::ast::{PrototypeT, SignatureT};
use crate::typing::names::names::INameT;
use crate::typing::names::names::IdT;
use crate::typing::types::types::{CoordT, KindT};
use crate::typing::templata::templata::ITemplataT;
use crate::typing::types::types::InterfaceTT;
use crate::typing::types::types::RuntimeSizedArrayTT;
use crate::typing::types::types::StaticSizedArrayTT;
use crate::typing::types::types::StructTT;

/// Re-intern a typing-pass name into the instantiating arena. Region mode = `sI`.
pub fn name<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _n: INameT<'s, 't>,
) -> INameI<'s, 'i, sI>
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::name")
}

/// Re-intern a typing-pass Id into the instantiating arena. Region mode = `sI`.
pub fn id<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _x: &'t IdT<'s, 't>,
) -> &'i IdI<'s, 'i, sI>
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::id")
}

/// Re-intern a typing-pass Prototype.
pub fn prototype<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _p: &'t PrototypeT<'s, 't>,
) -> &'i () // &'i PrototypeI<'s, 'i, sI> when ast.rs is migrated
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::prototype")
}

/// Re-intern a typing-pass Signature.
pub fn signature<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _s: &'t SignatureT<'s, 't>,
) -> &'i () // &'i SignatureI<'s, 'i, sI> when ast.rs is migrated
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::signature")
}

/// Re-intern a typing-pass Coord.
pub fn coord<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _c: CoordT<'s, 't>,
) -> CoordI<'s, 'i, sI>
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::coord")
}

/// Re-intern a typing-pass Kind.
pub fn kind<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _k: KindT<'s, 't>,
) -> KindIT<'s, 'i, sI>
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::kind")
}

/// Re-intern a typing-pass StructTT payload as a StructIT.
pub fn struct_payload<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _s: &'t StructTT<'s, 't>,
) -> &'i StructIT<'s, 'i, sI>
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::struct_payload")
}

/// Re-intern a typing-pass InterfaceTT payload as an InterfaceIT.
pub fn interface_payload<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _x: &'t InterfaceTT<'s, 't>,
) -> &'i InterfaceIT<'s, 'i, sI>
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::interface_payload")
}

/// Re-intern a typing-pass StaticSizedArrayTT payload.
pub fn static_sized_array_payload<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _x: &'t StaticSizedArrayTT<'s, 't>,
) -> &'i StaticSizedArrayIT<'s, 'i, sI>
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::static_sized_array_payload")
}

/// Re-intern a typing-pass RuntimeSizedArrayTT payload.
pub fn runtime_sized_array_payload<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _x: &'t RuntimeSizedArrayTT<'s, 't>,
) -> &'i RuntimeSizedArrayIT<'s, 'i, sI>
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::runtime_sized_array_payload")
}

/// Re-intern a typing-pass Templata.
pub fn templata<'s, 't, 'i>(
    _intr: &InstantiatingInterner<'s, 'i>,
    _t: ITemplataT<'s, 't>,
) -> () // ITemplataI<'s, 'i, sI> when templata.rs is migrated
where 's: 't, 's: 'i {
    panic!("Unimplemented: reintern::templata")
}
