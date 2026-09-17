//
// Per typing-pass `Compiler` precedent, `MutateHammer` is not a Rust struct.
// Methods become `impl Hammer { ... }` blocks colocated here.

use crate::final_ast::instructions::ExpressionH;
use crate::instantiating::ast::ast::FunctionHeaderI;
use crate::instantiating::ast::expressions::{ExpressionIE, MutateIE, ReferenceExpressionIE};
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::ast::names::IVarNameI;
use crate::instantiating::ast::types::{cI, CoordI, VariabilityI};
use crate::simplifying::hamuts::Hamuts;
use crate::simplifying::hammer::{Hammer, Locals};
use crate::final_ast::instructions::LocalStoreH;
use crate::final_ast::instructions::MemberLoadH;
use crate::final_ast::instructions::MemberStoreH;
use crate::final_ast::instructions::RuntimeSizedArrayStoreH;
use crate::final_ast::types::CoordH;
use crate::final_ast::types::KindHT;
use crate::final_ast::types::LocationH;
use crate::final_ast::types::OwnershipH;
use crate::instantiating::ast::ast::AddressibleLocalVariableI;
use crate::instantiating::ast::ast::ILocalVariableI;
use crate::instantiating::ast::ast::ReferenceLocalVariableI;
use crate::instantiating::ast::expressions::AddressExpressionIE;
use crate::instantiating::ast::expressions::AddressMemberLookupIE;
use crate::instantiating::ast::expressions::LocalLookupIE;
use crate::instantiating::ast::expressions::ReferenceMemberLookupIE;
use crate::instantiating::ast::expressions::RuntimeSizedArrayLookupIE;
use crate::instantiating::ast::expressions::StaticSizedArrayLookupIE;
use crate::instantiating::ast::names::INameI;
use crate::instantiating::ast::names::add_step;
use crate::instantiating::ast::types::KindIT;
use crate::simplifying::let_hammer::BOX_MEMBER_INDEX;

impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn translate_mutate(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        mutate2: &MutateIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let MutateIE { destination_expr: destination_expr2, source_expr: source_expr2, result: _ } = *mutate2;
        let (source_expr_result_line, source_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(source_expr2));
        let _source_result_pointer_type_h =
            self.translate_coord(hinputs, hamuts, source_expr2.result());
        let (old_value_access, destination_deferreds) = match destination_expr2 {
            AddressExpressionIE::LocalLookup(LocalLookupIE { local_variable: ILocalVariableI::ReferenceLocalVariableI(ReferenceLocalVariableI { name: var_id, variability: _, collapsed_coord: _ }), .. }) => {
                self.translate_mundane_local_mutate(hinputs, hamuts, current_function_header, locals, source_expr_result_line, var_id)
            }
            AddressExpressionIE::LocalLookup(LocalLookupIE { local_variable: ILocalVariableI::AddressibleLocalVariableI(AddressibleLocalVariableI { name: var_id, variability, collapsed_coord: reference }), .. }) => {
                self.translate_addressible_local_mutate(hinputs, hamuts, current_function_header, locals, source_expr_result_line, _source_result_pointer_type_h, var_id, *variability, *reference)
            }
            AddressExpressionIE::ReferenceMemberLookup(ReferenceMemberLookupIE { struct_expr: struct_expr2, member_name, .. }) => {
                self.translate_mundane_member_mutate(hinputs, hamuts, current_function_header, locals, source_expr_result_line, *struct_expr2, member_name)
            }
            AddressExpressionIE::AddressMemberLookup(AddressMemberLookupIE { struct_expr: struct_expr2, member_name, .. }) => {
                self.translate_addressible_member_mutate(hinputs, hamuts, current_function_header, locals, source_expr_result_line, *struct_expr2, member_name)
            }
            AddressExpressionIE::StaticSizedArrayLookup(StaticSizedArrayLookupIE { array_expr: array_expr2, index_expr: index_expr2, .. }) => {
                self.translate_mundane_static_sized_array_mutate(hinputs, hamuts, current_function_header, locals, source_expr_result_line, *array_expr2, *index_expr2)
            }
            AddressExpressionIE::RuntimeSizedArrayLookup(RuntimeSizedArrayLookupIE { array_expr: array_expr2, index_expr: index_expr2, .. }) => {
                self.translate_mundane_runtime_sized_array_mutate(hinputs, hamuts, current_function_header, locals, source_expr_result_line, *array_expr2, *index_expr2)
            }
        };
        let combined_deferreds: Vec<_> = source_deferreds.into_iter().chain(destination_deferreds.into_iter()).collect();
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, old_value_access, combined_deferreds)
    }

    pub fn translate_mundane_runtime_sized_array_mutate(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_result_line: ExpressionH<'s, 'h>,
        array_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        index_expr2: ReferenceExpressionIE<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let (destination_result_line, destination_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(array_expr2));
        let (index_expr_result_line, index_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(index_expr2));
        let result_type =
            hamuts.get_runtime_sized_array(
                destination_result_line.expect_runtime_sized_array_access().result_type().kind.expect_runtime_sized_array_ht())
                .element_type;
        // We're storing into a regular reference element of an array.
        let store_node = ExpressionH::RuntimeSizedArrayStoreH(self.interner.alloc(RuntimeSizedArrayStoreH {
            array_expression: destination_result_line.expect_runtime_sized_array_access(),
            index_expression: index_expr_result_line.expect_int_access(),
            source_expression: source_expr_result_line,
            result_type,
        }));
        let mut deferreds: Vec<ExpressionIE<'s, 'i, cI>> = destination_deferreds;
        deferreds.extend(index_deferreds);
        (store_node, deferreds)
    }

    pub fn translate_mundane_static_sized_array_mutate(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_result_line: ExpressionH<'s, 'h>,
        array_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        index_expr2: ReferenceExpressionIE<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        panic!("Unimplemented: translate_mundane_static_sized_array_mutate");
    }

    pub fn translate_addressible_member_mutate(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_result_line: ExpressionH<'s, 'h>,
        struct_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        member_name: &'i IVarNameI<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let (destination_result_line, destination_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(struct_expr2));
        let struct_it = match struct_expr2.result().kind {
            KindIT::StructIT(sr) => sr,
            _ => panic!("translate_addressible_member_mutate: non-struct kind"),
        };
        let struct_def_i = hinputs.lookup_struct(&struct_it.id);
        let member_index = struct_def_i.members.iter().position(|m| m.name == *member_name).expect("member not found") as i32;
        assert!(member_index >= 0);
        let member2 = &struct_def_i.members[member_index as usize];
        let variability = member2.variability;
        let boxed_type2 = member2.tyype.expect_address_member().reference;
        let boxed_type_h = self.translate_coord(hinputs, hamuts, boxed_type2);
        let box_struct_ref_h = self.make_box(hinputs, hamuts, variability, boxed_type2, boxed_type_h);
        let expected_struct_box_member_type = CoordH {
            ownership: OwnershipH::MutableBorrowH,
            location: LocationH::YonderH,
            kind: KindHT::StructHT(box_struct_ref_h),
        };
        let name_h = self.translate_full_name(hinputs, hamuts, &add_step(&current_function_header.id, INameI::from(*member_name)));
        let load_result_type = CoordH {
            ownership: OwnershipH::MutableBorrowH,
            location: LocationH::YonderH,
            kind: KindHT::StructHT(box_struct_ref_h),
        };
        let load_box_node = ExpressionH::MemberLoadH(self.interner.alloc(MemberLoadH {
            struct_expression: destination_result_line.expect_struct_access(),
            member_index,
            expected_member_type: expected_struct_box_member_type,
            result_type: load_result_type,
            member_name: name_h,
        }));
        let store_node = ExpressionH::MemberStoreH(self.interner.alloc(MemberStoreH {
            result_type: boxed_type_h,
            struct_expression: load_box_node.expect_struct_access(),
            member_index: BOX_MEMBER_INDEX,
            source_expression: source_expr_result_line,
            member_name: self.add_step(hamuts, box_struct_ref_h.id, self.keywords.box_member_name),
        }));
        (store_node, destination_deferreds)
    }

    pub fn translate_mundane_member_mutate(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_result_line: ExpressionH<'s, 'h>,
        struct_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        member_name: &'i IVarNameI<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let (destination_result_line, destination_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(struct_expr2));
        let struct_it = match struct_expr2.result().kind {
            KindIT::StructIT(sr) => sr,
            _ => panic!("translate_mundane_member_mutate: struct_expr2.result.kind not StructIT"),
        };
        let struct_def_i = hinputs.lookup_struct(&struct_it.id);
        let member_index = struct_def_i.members.iter().position(|m| m.name == *member_name).expect("memberIndex >= 0") as i32;
        let struct_def_h = *hamuts.struct_t_to_struct_def_h().get(struct_it).expect("structDefH not in map");
        let store_node = ExpressionH::MemberStoreH(self.interner.alloc(MemberStoreH {
            result_type: struct_def_h.members[member_index as usize].tyype,
            struct_expression: destination_result_line.expect_struct_access(),
            member_index,
            source_expression: source_expr_result_line,
            member_name: self.translate_full_name(hinputs, hamuts, &add_step(&current_function_header.id, (*member_name).into())),
        }));
        (store_node, destination_deferreds)
    }

    pub fn translate_addressible_local_mutate(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_result_line: ExpressionH<'s, 'h>,
        source_result_pointer_type_h: CoordH<'s, 'h>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        variability: VariabilityI,
        reference: CoordI<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        panic!("Unimplemented: translate_addressible_local_mutate");
    }

    pub fn translate_mundane_local_mutate(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_result_line: ExpressionH<'s, 'h>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let local = locals.get_by_var_name(var_id).expect("local not found");
        assert!(!locals.unstackified_vars.contains(&local.id));
        let new_local_name = add_step(&current_function_header.id, INameI::from(*var_id));
        let new_store_node =
            ExpressionH::LocalStoreH(self.interner.alloc(LocalStoreH {
                local,
                source_expression: source_expr_result_line,
                local_name: self.translate_full_name(hinputs, hamuts, &new_local_name),
            }));
        (new_store_node, Vec::new())
    }
}

