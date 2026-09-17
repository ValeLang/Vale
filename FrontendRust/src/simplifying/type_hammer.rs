//
// Per typing-pass `Compiler` precedent, `TypeHammer` is not a Rust struct.
// Methods become `impl Hammer { ... }` blocks colocated here.

use crate::final_ast::ast::{PrototypeH, PrototypeHValH, RegionH};
use crate::final_ast::types::{CoordH, KindHT, LocationH, RuntimeSizedArrayHT, StaticSizedArrayHT};
use crate::instantiating::ast::types::OwnershipI;
use crate::simplifying::conversions::evaluate_ownership;
use crate::instantiating::ast::ast::PrototypeI;
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::ast::templata::RegionTemplataI;
use crate::instantiating::ast::types::{cI, CoordI, IntIT, KindIT, NeverIT, RuntimeSizedArrayIT, StaticSizedArrayIT};
use crate::simplifying::hamuts::Hamuts;
use crate::simplifying::hammer::Hammer;
use crate::final_ast::types::BoolHT;
use crate::final_ast::types::FloatHT;
use crate::final_ast::types::IntHT;
use crate::final_ast::types::NeverHT;
use crate::final_ast::types::RuntimeSizedArrayDefinitionHT;
use crate::final_ast::types::RuntimeSizedArrayHTValH;
use crate::final_ast::types::StaticSizedArrayDefinitionHT;
use crate::final_ast::types::StaticSizedArrayHTValH;
use crate::final_ast::types::StrHT;
use crate::final_ast::types::VoidHT;
use crate::instantiating::ast::names::INameI;
use crate::instantiating::ast::names::RawArrayNameI;
use crate::instantiating::ast::names::RuntimeSizedArrayNameI;
use crate::instantiating::ast::names::StaticSizedArrayNameI;
use crate::instantiating::ast::types::StaticSizedArrayITValI;
use crate::simplifying::conversions::evaluate_mutability;
use crate::simplifying::conversions::evaluate_mutability_templata;
use crate::simplifying::conversions::evaluate_variability_templata;
use std::ptr::eq;

impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn translate_kind(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        tyype: KindIT<'s, 'i, cI>,
    ) -> KindHT<'s, 'h>
    {
        match tyype {
            KindIT::NeverIT(NeverIT { from_break, .. }) => KindHT::NeverHT(NeverHT { from_break }),
            KindIT::IntIT(IntIT { bits, .. }) => KindHT::IntHT(IntHT { bits }),
            KindIT::BoolIT(_) => KindHT::BoolHT(BoolHT),
            KindIT::FloatIT(_) => KindHT::FloatHT(FloatHT),
            KindIT::StrIT(_) => KindHT::StrHT(StrHT),
            KindIT::VoidIT(_) => KindHT::VoidHT(VoidHT),
            KindIT::StructIT(s) => {
                if hinputs.kind_externs.contains_key(&s) {
                    KindHT::OpaqueHT(self.translate_opaque_i(hinputs, hamuts, s))
                } else {
                    KindHT::StructHT(self.translate_struct_i(hinputs, hamuts, s))
                }
            }
            KindIT::InterfaceIT(i) => KindHT::InterfaceHT(self.translate_interface(hinputs, hamuts, i)),
            KindIT::StaticSizedArrayIT(a) => KindHT::StaticSizedArrayHT(self.translate_static_sized_array(hinputs, hamuts, *a)),
            KindIT::RuntimeSizedArrayIT(a) => KindHT::RuntimeSizedArrayHT(self.translate_runtime_sized_array(hinputs, hamuts, a)),
        }
    }

    pub fn translate_region(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        region: &RegionTemplataI<cI>,
    ) -> RegionH
    {
        panic!("Unimplemented: translate_region");
    }

    pub fn translate_coord(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        coord: CoordI<'s, 'i, cI>,
    ) -> CoordH<'s, 'h>
    {
        let CoordI { ownership, kind: inner_type } = coord;
        let location = match (ownership, inner_type) {
            (OwnershipI::Own, _) => LocationH::YonderH,
            (OwnershipI::ImmutableBorrow | OwnershipI::MutableBorrow, _) => LocationH::YonderH,
            (OwnershipI::Weak, _) => LocationH::YonderH,
            (_, KindIT::StructIT(s)) if hinputs.kind_externs.contains_key(&s) => LocationH::InlineH,
            (OwnershipI::ImmutableShare | OwnershipI::MutableShare, KindIT::VoidIT(_) | KindIT::IntIT(_) | KindIT::BoolIT(_) | KindIT::FloatIT(_) | KindIT::NeverIT(_)) => LocationH::InlineH,
            (OwnershipI::ImmutableShare | OwnershipI::MutableShare, KindIT::StrIT(_)) => LocationH::YonderH,
            (OwnershipI::ImmutableShare | OwnershipI::MutableShare, _) => LocationH::YonderH,
        };
        let inner_h = self.translate_kind(hinputs, hamuts, inner_type);
        CoordH { ownership: evaluate_ownership(ownership), location, kind: inner_h }
    }

    pub fn translate_coords(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        references2: &[CoordI<'s, 'i, cI>],
    ) -> Vec<CoordH<'s, 'h>>
    {
        references2.iter().map(|c| self.translate_coord(hinputs, hamuts, *c)).collect()
    }

    pub fn check_conversion(
        &self,
        expected: CoordH<'s, 'h>,
        actual: CoordH<'s, 'h>,
    ) {
        panic!("Unimplemented: check_conversion");
    }

    pub fn translate_static_sized_array(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        ssa_it: StaticSizedArrayIT<'s, 'i, cI>,
    ) -> &'h StaticSizedArrayHT<'s, 'h>
    {
        let ssa_it = self.instantiating_interner.intern_static_sized_array_it_ci(StaticSizedArrayITValI { name: ssa_it.name });
        match hamuts.static_sized_arrays().get(&ssa_it).copied() {
            Some(x) => self.interner.intern_static_sized_array_ht(StaticSizedArrayHTValH { id: x.name }),
            None => {
                let name = self.translate_full_name(hinputs, hamuts, &ssa_it.name);
                let (mutability_i, variability_i, member_type, _arr_region, size) = match ssa_it.name.local_name {
                    INameI::StaticSizedArray(n) => {
                        let StaticSizedArrayNameI { template: _, size, variability, arr } = *n;
                        let RawArrayNameI { mutability, element_type, self_region } = arr;
                        (mutability, variability, element_type, self_region, size)
                    }
                    _ => panic!("translate_static_sized_array: local_name not StaticSizedArrayNameI"),
                };
                let member_reference_h = self.translate_coord(hinputs, hamuts, member_type.coord);
                let mutability = evaluate_mutability_templata(mutability_i);
                let variability = evaluate_variability_templata(variability_i);
                let definition = StaticSizedArrayDefinitionHT { name, size, mutability, variability, element_type: member_reference_h };
                let result = self.interner.intern_static_sized_array_ht(StaticSizedArrayHTValH { id: name });
                match hamuts.static_sized_arrays().iter().find(|(_, def)| eq(self.interner.intern_static_sized_array_ht(StaticSizedArrayHTValH { id: def.name }) as *const _, result as *const _)) {
                    Some(x) => panic!("vwat: {:?}", x),
                    None => hamuts.add_static_sized_array(ssa_it, definition),
                }
                result
            }
        }
    }

    pub fn translate_runtime_sized_array(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        rsa_it: &'i RuntimeSizedArrayIT<'s, 'i, cI>,
    ) -> &'h RuntimeSizedArrayHT<'s, 'h>
    {
        match hamuts.runtime_sized_arrays().get(&rsa_it).copied() {
            Some(x) => self.interner.intern_runtime_sized_array_ht(RuntimeSizedArrayHTValH { name: x.name }),
            None => {
                let name_h = self.translate_full_name(hinputs, hamuts, &rsa_it.name);
                let (mutability_i, member_type, _arr_region) = match rsa_it.name.local_name {
                    INameI::RuntimeSizedArray(n) => {
                        let RuntimeSizedArrayNameI { template: _, arr } = *n;
                        let RawArrayNameI { mutability, element_type, self_region } = arr;
                        (mutability, element_type, self_region)
                    }
                    _ => panic!("translate_runtime_sized_array: local_name not RuntimeSizedArrayNameI"),
                };
                let member_reference_h = self.translate_coord(hinputs, hamuts, member_type.coord);
                let mutability = evaluate_mutability(mutability_i);
                let definition = RuntimeSizedArrayDefinitionHT { name: name_h, mutability, element_type: member_reference_h };
                let result = self.interner.intern_runtime_sized_array_ht(RuntimeSizedArrayHTValH { name: name_h });
                match hamuts.runtime_sized_arrays().iter().find(|(_, def)| eq(self.interner.intern_runtime_sized_array_ht(RuntimeSizedArrayHTValH { name: def.name }) as *const _, result as *const _)) {
                    Some(x) => panic!("vwat: {:?}", x),
                    None => hamuts.add_runtime_sized_array(rsa_it, definition),
                }
                result
            }
        }
    }

    pub fn translate_prototype(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        prototype2: &'i PrototypeI<'s, 'i, cI>,
    ) -> &'h PrototypeH<'s, 'h>
    {
        let PrototypeI { id: full_name2, return_type: return_type2, _must_intern: _ } = prototype2;
        let params_types_h = self.translate_coords(hinputs, hamuts, &prototype2.param_types());
        let return_type_h = self.translate_coord(hinputs, hamuts, *return_type2);
        let full_name_h = self.translate_full_name(hinputs, hamuts, full_name2);
        let prototype_h = self.interner.intern_prototype(PrototypeHValH {
            id: full_name_h,
            params: self.interner.alloc_slice_from_vec(params_types_h),
            return_type: return_type_h,
        });
        prototype_h
    }
}

