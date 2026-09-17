//
// Per typing-pass `Compiler` precedent, `StructHammer` is not a Rust struct.
// Methods become `impl Hammer { ... }` blocks colocated here.

use crate::final_ast::ast::{EdgeH, InterfaceDefinitionH, InterfaceMethodH, StructDefinitionH, StructMemberH};
use crate::final_ast::types::{CoordH, InterfaceHT, Mutability, OpaqueHT, StructHT};
use crate::instantiating::ast::ast::EdgeI;
use crate::instantiating::ast::names::IdI;
use crate::instantiating::ast::citizens::{
    AddressMemberTypeI, InterfaceDefinitionI, ReferenceMemberTypeI, StructDefinitionI, StructMemberI,
};
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::ast::types::{cI, CoordI, InterfaceIT, StructIT, VariabilityI};
use crate::simplifying::hamuts::Hamuts;
use crate::simplifying::hammer::Hammer;
use crate::final_ast::ast::PrototypeH;
use crate::final_ast::types::InterfaceHTValH;
use crate::final_ast::types::KindHT;
use crate::final_ast::types::LocationH;
use crate::final_ast::types::OwnershipH;
use crate::final_ast::types::StructHTValH;
use crate::instantiating::ast::ast::ICitizenAttributeI;
use crate::instantiating::ast::citizens::IMemberTypeI;
use crate::instantiating::ast::names::INameI;
use crate::instantiating::ast::names::IStructTemplateNameI;
use crate::instantiating::ast::names::StructNameI;
use crate::instantiating::ast::names::StructTemplateNameI;
use crate::instantiating::ast::names::add_step;
use crate::instantiating::ast::templata::CoordTemplataI;
use crate::instantiating::ast::templata::ITemplataI;
use crate::instantiating::ast::templata::RegionTemplataI;
use crate::instantiating::ast::types::InterfaceITValI;
use crate::instantiating::ast::types::MutabilityI;
use crate::simplifying::conversions::evaluate_mutability_templata;
use crate::simplifying::conversions::evaluate_variability;
use crate::simplifying::name_hammer::simplify_id;
use crate::utils::arena_index_map::ArenaIndexMap;
use std::marker::PhantomData;
use std::ptr::eq;

impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn translate_interfaces(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
    )
    {
        for interface in hinputs.interfaces.iter() {
            self.translate_interface(hinputs, hamuts, interface.instantiated_interface);
        }
    }

    pub fn translate_interface_methods(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        interface_tt: &'i InterfaceIT<'s, 'i, cI>,
    ) -> Vec<InterfaceMethodH<'s, 'h>>
    {
        let edge_blueprint = hinputs.interface_to_edge_blueprints.get(&interface_tt.id).expect("vassertSome: interface_to_edge_blueprints");
        edge_blueprint.super_family_root_headers.iter().map(|(super_family_prototype, virtual_param_index)| {
            let prototype_h = self.translate_prototype(hinputs, hamuts, super_family_prototype);
            InterfaceMethodH { prototype_h, virtual_param_index: *virtual_param_index }
        }).collect()
    }

    pub fn translate_interface(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        interface_it: &'i InterfaceIT<'s, 'i, cI>,
    ) -> &'h InterfaceHT<'s, 'h>
    {
        match hamuts.interface_t_to_interface_h.get(&interface_it).copied() {
            Some(struct_ref_h) => struct_ref_h,
            None => {
                let full_name_h = self.translate_full_name(hinputs, hamuts, &interface_it.id);
                let temporary_interface_ref_h = self.interner.intern_interface_ht(InterfaceHTValH { id: full_name_h });
                hamuts.forward_declare_interface(interface_it, temporary_interface_ref_h);
                let interface_def_i = hinputs.lookup_interface(&interface_it.id);
                let methods_h = self.translate_interface_methods(hinputs, hamuts, interface_it);
                let interface_def_h = InterfaceDefinitionH {
                    id: full_name_h,
                    weakable: interface_def_i.weakable,
                    mutability: evaluate_mutability_templata(interface_def_i.mutability),
                    super_interfaces: &[],
                    methods: self.interner.alloc_slice_from_vec(methods_h),
                };
                hamuts.add_interface(interface_it, interface_def_h);
                assert!(eq(interface_def_h.get_ref(self.interner) as *const _, temporary_interface_ref_h as *const _));
                match interface_def_i.mutability {
                    MutabilityI::Mutable => {}
                    MutabilityI::Immutable => {}
                }
                interface_def_h.get_ref(self.interner)
            }
        }
    }

    pub fn translate_structs(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
    )
    {
        for struct_def_i in hinputs.structs.iter() {
            if hinputs.kind_externs.contains_key(&struct_def_i.instantiated_citizen) {
                self.translate_opaque_i(hinputs, hamuts, struct_def_i.instantiated_citizen);
            } else {
                self.translate_struct_i(hinputs, hamuts, struct_def_i.instantiated_citizen);
            }
        }
    }

    pub fn translate_struct_i(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        struct_it: &'i StructIT<'s, 'i, cI>,
    ) -> &'h StructHT<'s, 'h>
    {
        match hamuts.struct_t_to_struct_h.get(&struct_it).copied() {
            Some(struct_ref_h) => struct_ref_h,
            None => {
                let full_name_h = self.translate_full_name(hinputs, hamuts, &struct_it.id);
                let temporary_struct_ref_h = self.interner.intern_struct_ht(StructHTValH { id: full_name_h });
                hamuts.forward_declare_struct(struct_it, temporary_struct_ref_h);
                let struct_def_i = hinputs.lookup_struct(&struct_it.id);
                let mutability_h = evaluate_mutability_templata(struct_def_i.mutability);
                let members_h = self.translate_members(hinputs, hamuts, &struct_def_i.instantiated_citizen.id, mutability_h, struct_def_i.members);
                let edges_h_vec = self.translate_edges_for_struct(hinputs, hamuts, temporary_struct_ref_h, struct_it);
                let edges_h: &'h [EdgeH<'s, 'h>] = self.interner.alloc_slice_from_vec(edges_h_vec);
                let extern_ = struct_def_i.attributes.iter().any(|a| matches!(a, ICitizenAttributeI::ExternI(_)));
                let struct_def_h = StructDefinitionH {
                    id: full_name_h,
                    weakable: struct_def_i.weakable,
                    extern_,
                    mutability: mutability_h,
                    edges: edges_h,
                    members: self.interner.alloc_slice_from_vec(members_h),
                };
                hamuts.add_struct_originating_from_typing_pass(struct_it, struct_def_h);
                assert!(eq(struct_def_h.get_ref(self.interner) as *const _, temporary_struct_ref_h as *const _));
                match struct_def_i.mutability {
                    MutabilityI::Mutable => {}
                    MutabilityI::Immutable => {}
                }
                struct_def_h.get_ref(self.interner)
            }
        }
    }

    pub fn translate_opaque_i(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        struct_it: &'i StructIT<'s, 'i, cI>,
    ) -> &'h OpaqueHT<'s, 'h>
    {
        match hamuts.struct_t_to_opaque_h.get(&struct_it).copied() {
            Some(opaque_h) => opaque_h,
            None => {
                let full_name_h = self.translate_full_name(hinputs, hamuts, &struct_it.id);
                let temporary_struct_ref_h = self.interner.intern_struct_ht(StructHTValH { id: full_name_h });
                hamuts.forward_declare_struct(struct_it, temporary_struct_ref_h);
                let struct_def_i = hinputs.lookup_struct(&struct_it.id);
                let _mutability_h = evaluate_mutability_templata(struct_def_i.mutability);
                assert!(struct_def_i.members.is_empty());

                let _edges_h = self.translate_edges_for_struct(hinputs, hamuts, temporary_struct_ref_h, struct_it);

                let opaque_h: &'h OpaqueHT<'s, 'h> = self.interner.bump().alloc(OpaqueHT {
                    package_coord: *struct_it.id.package_coord,
                    struct_id: self.translate_full_name(hinputs, hamuts, &struct_it.id),
                    simple_id: simplify_id(self.interner, self.scout_arena, &struct_it.id),
                });

                hamuts.add_opaque(struct_it, opaque_h);

                opaque_h
            }
        }
    }

    pub fn translate_members(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        struct_name: &IdI<'s, 'i, cI>,
        struct_mutability_h: Mutability,
        members: &[StructMemberI<'s, 'i, cI>],
    ) -> Vec<StructMemberH<'s, 'h>>
    {
        members.iter().map(|m| self.translate_member(hinputs, hamuts, struct_name, struct_mutability_h, m)).collect()
    }

    pub fn translate_member(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        struct_name: &IdI<'s, 'i, cI>,
        struct_mutability_h: Mutability,
        member2: &StructMemberI<'s, 'i, cI>,
    ) -> StructMemberH<'s, 'h>
    {
        let (variability, member_type) = match member2.tyype {
            IMemberTypeI::ReferenceMemberTypeI(r) => {
                (member2.variability, self.translate_coord(hinputs, hamuts, r.reference))
            }
            IMemberTypeI::AddressMemberTypeI(a) => {
                let reference_h = self.translate_coord(hinputs, hamuts, a.reference);
                let box_struct_ref_h = self.make_box(hinputs, hamuts, member2.variability, a.reference, reference_h);
                (member2.variability, CoordH {
                    ownership: OwnershipH::MutableBorrowH,
                    location: LocationH::YonderH,
                    kind: KindHT::StructHT(box_struct_ref_h),
                })
            }
        };
        let added_name = add_step(struct_name, member2.name.into());
        StructMemberH {
            name: self.translate_full_name(hinputs, hamuts, &added_name),
            variability: evaluate_variability(variability),
            tyype: member_type,
        }
    }

    pub fn make_box(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        _conceptual_variability: VariabilityI,
        type2: CoordI<'s, 'i, cI>,
        type_h: CoordH<'s, 'h>,
    ) -> &'h StructHT<'s, 'h>
    {
        let template_args = self.instantiating_interner.bump().alloc_slice_copy(&[
            ITemplataI::Coord(CoordTemplataI {
                region: RegionTemplataI { pure_height: 0, _marker: PhantomData },
                coord: type2,
            }),
        ]);
        let struct_name = StructNameI {
            template: IStructTemplateNameI::StructTemplate(self.instantiating_interner.intern_struct_template_name_ci(StructTemplateNameI { _marker: PhantomData, human_name: self.keywords.box_human_name })),
            template_args,
        };
        let box_full_name2 = IdI {
            package_coord: self.scout_arena.intern_package_coordinate(self.keywords.empty_string, &[]),
            init_steps: &[],
            local_name: INameI::StructName(self.instantiating_interner.intern_struct_name_ci(struct_name)),
        };
        let box_full_name_h = self.translate_full_name(hinputs, hamuts, &box_full_name2);
        match hamuts.struct_defs().iter().find(|s| s.id == box_full_name_h) {
            Some(struct_def_h) => struct_def_h.get_ref(self.interner),
            None => {
                let temporary_struct_ref_h = self.interner.intern_struct_ht(StructHTValH { id: box_full_name_h });
                // We don't actually care about the given variability, because even if it's final, we still need
                // the box to contain a varying reference, see VCBAAF.
                let actual_variability = VariabilityI::Varying;
                let member_h = StructMemberH {
                    name: self.add_step(hamuts, temporary_struct_ref_h.id, self.keywords.box_member_name),
                    variability: evaluate_variability(actual_variability),
                    tyype: type_h,
                };
                let members_slice: &'h [StructMemberH<'s, 'h>] = self.interner.bump().alloc_slice_copy(&[member_h]);
                let struct_def_h = StructDefinitionH {
                    id: box_full_name_h,
                    weakable: false,
                    extern_: false,
                    mutability: Mutability::Mutable,
                    edges: &[],
                    members: members_slice,
                };
                hamuts.add_struct_originating_from_hammer(struct_def_h);
                assert!(*struct_def_h.get_ref(self.interner) == *temporary_struct_ref_h);
                struct_def_h.get_ref(self.interner)
            }
        }
    }

    pub fn translate_edges_for_struct(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        struct_ref_h: &'h StructHT<'s, 'h>,
        struct_tt: &'i StructIT<'s, 'i, cI>,
    ) -> Vec<EdgeH<'s, 'h>>
    {
        let edges2: Vec<&EdgeI<'s, 'i>> = hinputs.interface_to_sub_citizen_to_edge.iter()
            .flat_map(|(_, sub_map)| sub_map.iter().map(|(_, e)| e))
            .filter(|e| e.sub_citizen.id() == struct_tt.id)
            .collect();
        self.translate_edges_for_struct_with_edges(hinputs, hamuts, struct_ref_h, &edges2)
    }

    pub fn translate_edges_for_struct_with_edges(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        struct_ref_h: &'h StructHT<'s, 'h>,
        edges2: &[&EdgeI<'s, 'i>],
    ) -> Vec<EdgeH<'s, 'h>>
    {
        edges2.iter().map(|e| self.translate_edge(hinputs, hamuts, struct_ref_h, self.instantiating_interner.intern_interface_it_ci(InterfaceITValI { id: e.super_interface }), e)).collect()
    }

    pub fn translate_edge(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        struct_ref_h: &'h StructHT<'s, 'h>,
        interface_it: &'i InterfaceIT<'s, 'i, cI>,
        edge2: &EdgeI<'s, 'i>,
    ) -> EdgeH<'s, 'h>
    {
        // Purposefully not trying to translate the entire struct here, because we might hit a circular dependency
        let interface_ref_h = self.translate_interface(hinputs, hamuts, interface_it);
        let interface_prototypes_h = self.translate_interface_methods(hinputs, hamuts, interface_it);

        let prototypes_h: Vec<&'h PrototypeH<'s, 'h>> = hinputs.interface_to_edge_blueprints.get(&interface_it.id).expect("vassertSome: interface_to_edge_blueprints")
            .super_family_root_headers.iter().map(|(super_family_prototype, _virtual_param_index)| {
                let override_prototype_i = *edge2.abstract_func_to_override_func.get(&super_family_prototype.id).expect("vassertSome: abstract_func_to_override_func");
                self.translate_prototype(hinputs, hamuts, override_prototype_i)
            }).collect();

        let struct_prototypes_by_interface_method: Vec<(InterfaceMethodH<'s, 'h>, &'h PrototypeH<'s, 'h>)> = interface_prototypes_h.iter().zip(prototypes_h.iter()).map(|(im, p)| (*im, *p)).collect();
        EdgeH {
            struct_: struct_ref_h,
            interface: interface_ref_h,
            struct_prototypes_by_interface_method: self.interner.bump().alloc(ArenaIndexMap::from_iter_in(struct_prototypes_by_interface_method.into_iter(), self.interner.bump())),
        }
    }

    pub fn lookup_struct(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        _hamuts: &Hamuts<'s, 'i, 'h>,
        struct_tt: StructIT<'s, 'i, cI>,
    ) -> &'i StructDefinitionI<'s, 'i, cI>
    {
        hinputs.lookup_struct(&struct_tt.id)
    }

    pub fn lookup_interface(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &Hamuts<'s, 'i, 'h>,
        interface_tt: &'i InterfaceIT<'s, 'i, cI>,
    ) -> &'i InterfaceDefinitionI<'s, 'i, cI>
    {
        panic!("Unimplemented: lookup_interface");
    }
}

