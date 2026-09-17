//
// Per typing-pass `Compiler` precedent, `LoadHammer` is not a Rust struct.
// Methods become `impl Hammer { ... }` blocks colocated here.

use crate::final_ast::instructions::ExpressionH;
use crate::final_ast::types::CoordH;
use crate::instantiating::ast::ast::FunctionHeaderI;
use crate::instantiating::ast::expressions::{
    AddressMemberLookupIE, ExpressionIE, LocalLookupIE, ReferenceExpressionIE, SoftLoadIE,
};
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::ast::names::IVarNameI;
use crate::instantiating::ast::types::{cI, CoordI, OwnershipI, VariabilityI};
use crate::simplifying::hamuts::Hamuts;
use crate::simplifying::hammer::{Hammer, Locals};
use crate::final_ast::instructions::LocalLoadH;
use crate::final_ast::instructions::MemberLoadH;
use crate::final_ast::instructions::RuntimeSizedArrayLoadH;
use crate::final_ast::instructions::StaticSizedArrayLoadH;
use crate::final_ast::types::KindHT;
use crate::final_ast::types::LocationH;
use crate::final_ast::types::OwnershipH;
use crate::instantiating::ast::names::INameI;
use crate::instantiating::ast::names::add_step;
use crate::instantiating::ast::types::KindIT;
use crate::simplifying::conversions::evaluate_ownership;
use crate::simplifying::let_hammer::BOX_MEMBER_INDEX;
use crate::instantiating::ast::ast::ILocalVariableI;
use crate::instantiating::ast::expressions::AddressExpressionIE;

impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn translate_load(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        load2: &SoftLoadIE<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let SoftLoadIE { expr: source_expr2, target_ownership, .. } = *load2;
        let (loaded_access_h, source_deferreds) = match source_expr2 {
            AddressExpressionIE::LocalLookup(LocalLookupIE { local_variable: ILocalVariableI::ReferenceLocalVariableI(r), .. }) => {
                let combined_target_ownership = target_ownership;
                self.translate_mundane_local_load(hinputs, hamuts, current_function_header, locals, &r.name, r.collapsed_coord, combined_target_ownership)
            }
            AddressExpressionIE::LocalLookup(LocalLookupIE { local_variable: ILocalVariableI::AddressibleLocalVariableI(a), .. }) => {
                let combined_target_ownership = target_ownership;
                self.translate_addressible_local_load(hinputs, hamuts, current_function_header, locals, &a.name, a.variability, a.collapsed_coord, combined_target_ownership)
            }
            AddressExpressionIE::ReferenceMemberLookup(rml) => {
                self.translate_mundane_member_load(hinputs, hamuts, current_function_header, locals, rml.struct_expr, &rml.member_name, target_ownership, rml.member_reference)
            }
            AddressExpressionIE::AddressMemberLookup(aml) => {
                self.translate_addressible_member_load(hinputs, hamuts, current_function_header, locals, aml.struct_expr, &aml.member_name, target_ownership, aml.member_reference)
            }
            AddressExpressionIE::RuntimeSizedArrayLookup(rsl) => {
                let combined_target_ownership = target_ownership;
                self.translate_mundane_runtime_sized_array_load(hinputs, hamuts, current_function_header, locals, rsl.array_expr, rsl.index_expr, combined_target_ownership, rsl.element_type)
            }
            AddressExpressionIE::StaticSizedArrayLookup(s) => {
                self.translate_mundane_static_sized_array_load(hinputs, hamuts, current_function_header, locals, s.array_expr, s.index_expr, target_ownership)
            }
        };
        (loaded_access_h, source_deferreds)
    }

    pub fn translate_mundane_runtime_sized_array_load(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        array_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        index_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        target_ownership_i: OwnershipI,
        result_type2: CoordI<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let _ = result_type2;
        let target_ownership = evaluate_ownership(target_ownership_i);
        let (array_result_line, array_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(array_expr2));
        let array_access = array_result_line.expect_runtime_sized_array_access();
        let (index_expr_result_line, index_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(index_expr2));
        let index_access = index_expr_result_line.expect_int_access();
        assert!(target_ownership == OwnershipH::MutableBorrowH || target_ownership == OwnershipH::ImmutableBorrowH || target_ownership == OwnershipH::ImmutableShareH || target_ownership == OwnershipH::MutableShareH);
        let rsa = hamuts.get_runtime_sized_array(array_access.result_type().kind.expect_runtime_sized_array_ht());
        let expected_element_type = rsa.element_type;
        let location = match (target_ownership, expected_element_type.location) {
            (OwnershipH::ImmutableBorrowH, _) => LocationH::YonderH,
            (OwnershipH::MutableBorrowH, _) => LocationH::YonderH,
            (OwnershipH::OwnH, location) => location,
            (OwnershipH::MutableShareH, location) => location,
            (OwnershipH::ImmutableShareH, location) => location,
            _ => panic!("translate_mundane_runtime_sized_array_load: unexpected ownership"),
        };
        let result_type = CoordH { ownership: target_ownership, location, kind: expected_element_type.kind };
        let loaded_node_h = ExpressionH::RuntimeSizedArrayLoadH(self.interner.alloc(RuntimeSizedArrayLoadH {
            array_expression: array_access,
            index_expression: index_access,
            target_ownership,
            expected_element_type,
            result_type,
        }));
        let mut deferreds: Vec<ExpressionIE<'s, 'i, cI>> = array_deferreds;
        deferreds.extend(index_deferreds);
        (loaded_node_h, deferreds)
    }

    pub fn translate_mundane_static_sized_array_load(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        array_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        index_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        target_ownership_i: OwnershipI,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let target_ownership = evaluate_ownership(target_ownership_i);
        let (array_result_line, array_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(array_expr2));
        let array_access = array_result_line.expect_static_sized_array_access();
        let (index_expr_result_line, index_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(index_expr2));
        let index_access = index_expr_result_line.expect_int_access();
        assert!(target_ownership == OwnershipH::MutableBorrowH || target_ownership == OwnershipH::ImmutableBorrowH || target_ownership == OwnershipH::MutableShareH || target_ownership == OwnershipH::ImmutableShareH);
        let ssa = hamuts.get_static_sized_array(array_access.result_type().kind.expect_static_sized_array_ht());
        let expected_element_type = ssa.element_type;
        let location = match (target_ownership, expected_element_type.location) {
            (OwnershipH::MutableBorrowH, _) | (OwnershipH::ImmutableBorrowH, _) => LocationH::YonderH,
            (OwnershipH::OwnH, location) => location,
            (OwnershipH::ImmutableShareH, location) | (OwnershipH::MutableShareH, location) => location,
            _ => panic!("translate_mundane_static_sized_array_load: unexpected ownership"),
        };
        let result_type = CoordH { ownership: target_ownership, location, kind: expected_element_type.kind };
        let loaded_node_h = ExpressionH::StaticSizedArrayLoadH(self.interner.alloc(StaticSizedArrayLoadH {
            array_expression: array_access,
            index_expression: index_access,
            target_ownership,
            expected_element_type,
            array_size: ssa.size,
            result_type,
        }));
        let mut combined_deferreds = array_deferreds;
        combined_deferreds.extend(index_deferreds);
        (loaded_node_h, combined_deferreds)
    }

    pub fn translate_addressible_member_load(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        struct_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        member_name: &'i IVarNameI<'s, 'i, cI>,
        target_ownership_i: OwnershipI,
        expected_member_coord: CoordI<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let (struct_result_line, struct_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(struct_expr2));
        let struct_it = match struct_expr2.result().kind {
            KindIT::StructIT(sr) => sr,
            _ => panic!("translate_addressible_member_load: non-struct kind"),
        };
        let struct_def_i = self.lookup_struct(hinputs, hamuts, *struct_it);
        let member_index = struct_def_i.members.iter().position(|m| m.name == *member_name).expect("member not found") as i32;
        assert!(member_index >= 0);
        let member2 = &struct_def_i.members[member_index as usize];
        let variability = member2.variability;
        let boxed_type2 = member2.tyype.expect_address_member().reference;
        let boxed_type_h = self.translate_coord(hinputs, hamuts, boxed_type2);
        let box_struct_ref_h = self.make_box(hinputs, hamuts, variability, boxed_type2, boxed_type_h);
        let box_in_struct_coord = CoordH {
            ownership: OwnershipH::MutableBorrowH,
            location: LocationH::YonderH,
            kind: KindHT::StructHT(box_struct_ref_h),
        };
        let var_full_name_h = self.translate_full_name(hinputs, hamuts, &add_step(&current_function_header.id, INameI::from(*member_name)));
        let load_box_node = ExpressionH::MemberLoadH(self.interner.alloc(MemberLoadH {
            struct_expression: struct_result_line.expect_struct_access(),
            member_index,
            expected_member_type: box_in_struct_coord,
            result_type: box_in_struct_coord,
            member_name: var_full_name_h,
        }));
        let target_ownership = evaluate_ownership(target_ownership_i);
        let load_result_type = CoordH {
            ownership: target_ownership,
            location: boxed_type_h.location,
            kind: boxed_type_h.kind,
        };
        let loaded_node_h = ExpressionH::MemberLoadH(self.interner.alloc(MemberLoadH {
            struct_expression: load_box_node.expect_struct_access(),
            member_index: BOX_MEMBER_INDEX,
            expected_member_type: boxed_type_h,
            result_type: load_result_type,
            member_name: self.add_step(hamuts, box_struct_ref_h.id, self.keywords.box_member_name),
        }));
        (loaded_node_h, struct_deferreds)
    }

    pub fn translate_mundane_member_load(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        struct_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        member_name: &'i IVarNameI<'s, 'i, cI>,
        target_ownership_i: OwnershipI,
        expected_member_coord: CoordI<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let (struct_result_line, struct_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(struct_expr2));
        let expected_member_type_h = self.translate_coord(hinputs, hamuts, expected_member_coord);
        let struct_it = match struct_expr2.result().kind {
            KindIT::StructIT(sr) => sr,
            _ => panic!("translate_mundane_member_load: struct_expr2.result.kind not StructIT"),
        };
        let struct_def_i = self.lookup_struct(hinputs, hamuts, *struct_it);
        let member_index = struct_def_i.members.iter().position(|m| m.name == *member_name).expect("memberIndex >= 0") as i32;
        let target_ownership = evaluate_ownership(target_ownership_i);
        let load_result_type = CoordH { ownership: target_ownership, location: expected_member_type_h.location, kind: expected_member_type_h.kind };
        let loaded_node = ExpressionH::MemberLoadH(self.interner.alloc(MemberLoadH {
            struct_expression: struct_result_line.expect_struct_access(),
            member_index,
            expected_member_type: expected_member_type_h,
            result_type: load_result_type,
            member_name: self.translate_full_name(hinputs, hamuts, &add_step(&current_function_header.id, (*member_name).into())),
        }));
        (loaded_node, struct_deferreds)
    }

    pub fn translate_addressible_local_load(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        variability: VariabilityI,
        local_reference2: CoordI<'s, 'i, cI>,
        target_ownership_i: OwnershipI,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let local = locals.get_by_var_name(var_id).unwrap();
        assert!(!locals.unstackified_vars.contains(&local.id));
        let local_type_h = self.translate_coord(hinputs, hamuts, local_reference2);
        let box_struct_ref_h = self.make_box(hinputs, hamuts, variability, local_reference2, local_type_h);
        assert!(local.type_h.kind == KindHT::StructHT(box_struct_ref_h));
        let var_name_h = self.translate_full_name(hinputs, hamuts, &add_step(&current_function_header.id, INameI::from(*var_id)));
        let load_box_node = ExpressionH::LocalLoadH(self.interner.alloc(LocalLoadH {
            local,
            target_ownership: OwnershipH::MutableBorrowH,
            local_name: var_name_h,
        }));
        let target_ownership = evaluate_ownership(target_ownership_i);
        let load_result_type = CoordH {
            ownership: target_ownership,
            location: local_type_h.location,
            kind: local_type_h.kind,
        };
        let loaded_node = ExpressionH::MemberLoadH(self.interner.alloc(MemberLoadH {
            struct_expression: load_box_node.expect_struct_access(),
            member_index: BOX_MEMBER_INDEX,
            expected_member_type: local_type_h,
            result_type: load_result_type,
            member_name: self.add_step(hamuts, box_struct_ref_h.id, self.keywords.box_member_name),
        }));
        (loaded_node, Vec::new())
    }

    pub fn translate_mundane_local_load(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        local_type: CoordI<'s, 'i, cI>,
        target_ownership_i: OwnershipI,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let target_ownership = evaluate_ownership(target_ownership_i);
        let local = locals.get_by_var_name(var_id).expect("wot");
        assert!(!locals.unstackified_vars.contains(&local.id));
        let loaded_node = ExpressionH::LocalLoadH(self.interner.alloc(LocalLoadH {
            local,
            target_ownership,
            local_name: self.translate_full_name(hinputs, hamuts, &add_step(&current_function_header.id, (*var_id).into())),
        }));
        (loaded_node, Vec::new())
    }

    pub fn translate_local_address(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        lookup2: &LocalLookupIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let local_var = lookup2.local_variable;
        let local = locals.get_by_var_name(&local_var.name()).unwrap();
        assert!(!locals.unstackified_vars.contains(&local.id));
        let _box_struct_ref_h = self.make_box(hinputs, hamuts, local_var.variability(), local_var.collapsed_coord(), local.type_h);
        let var_id_full = add_step(&current_function_header.id, INameI::from(local_var.name()));
        ExpressionH::LocalLoadH(self.interner.alloc(LocalLoadH {
            local,
            target_ownership: OwnershipH::MutableBorrowH,
            local_name: self.translate_full_name(hinputs, hamuts, &var_id_full),
        }))
    }

    pub fn translate_member_address(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        lookup2: &AddressMemberLookupIE<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let struct_expr2 = lookup2.struct_expr;
        let member_name = lookup2.member_name;
        let (struct_result_line, struct_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(struct_expr2));
        let struct_it = match struct_expr2.result().kind {
            KindIT::StructIT(sr) => sr,
            _ => panic!("translate_member_address: non-struct kind"),
        };
        let struct_def_i = self.lookup_struct(hinputs, hamuts, *struct_it);
        let member_index = struct_def_i.members.iter().position(|m| m.name == member_name).expect("member not found") as i32;
        assert!(member_index >= 0);
        let member2 = &struct_def_i.members[member_index as usize];
        let variability = member2.variability;
        assert!(variability == VariabilityI::Varying, "Expected varying for member {:?}", member_name);
        let boxed_type2 = member2.tyype.expect_address_member().reference;
        let boxed_type_h = self.translate_coord(hinputs, hamuts, boxed_type2);
        let box_struct_ref_h = self.make_box(hinputs, hamuts, variability, boxed_type2, boxed_type_h);
        let expected_struct_box_member_type = CoordH {
            ownership: OwnershipH::MutableBorrowH,
            location: LocationH::YonderH,
            kind: KindHT::StructHT(box_struct_ref_h),
        };
        let load_result_type = CoordH {
            ownership: OwnershipH::MutableBorrowH,
            location: LocationH::YonderH,
            kind: KindHT::StructHT(box_struct_ref_h),
        };
        let load_box_node = ExpressionH::MemberLoadH(self.interner.alloc(MemberLoadH {
            struct_expression: struct_result_line.expect_struct_access(),
            member_index,
            expected_member_type: expected_struct_box_member_type,
            result_type: load_result_type,
            member_name: self.translate_full_name(hinputs, hamuts, &add_step(&current_function_header.id, INameI::from(member_name))),
        }));
        (load_box_node, struct_deferreds)
    }
}

pub fn get_borrowed_location<'s, 'h>(member_type: CoordH<'s, 'h>) -> LocationH
where 's: 'h,
{
    panic!("Unimplemented: get_borrowed_location");
}

