//
// Per typing-pass `Compiler` precedent, `LetHammer` is not a Rust struct.
// Methods become `impl Hammer { ... }` blocks colocated here.
// `LetHammer.BOX_MEMBER_INDEX` (Scala `object LetHammer`) becomes a module
// constant.

use crate::final_ast::instructions::{ExpressionH, RestackifyH, StackifyH};
use crate::final_ast::types::CoordH;
use crate::instantiating::ast::ast::FunctionHeaderI;
use crate::instantiating::ast::expressions::{
    DestroyIE, DestroyStaticSizedArrayIntoLocalsIE, LetAndLendIE, LetNormalIE, ReferenceExpressionIE,
    RestackifyIE, UnletIE,
};
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::ast::names::IVarNameI;
use crate::instantiating::ast::types::{cI, CoordI, VariabilityI};
use crate::simplifying::hamuts::Hamuts;
use crate::simplifying::hammer::{Hammer, Locals};
use crate::final_ast::ast::IdH;
use crate::final_ast::instructions::ConsecutorH;
use crate::final_ast::instructions::DestroyH;
use crate::final_ast::instructions::DestroyStaticSizedArrayIntoLocalsH;
use crate::final_ast::instructions::DiscardH;
use crate::final_ast::instructions::Local;
use crate::final_ast::instructions::NewStructH;
use crate::final_ast::instructions::UnstackifyH;
use crate::final_ast::types::KindHT;
use crate::final_ast::types::LocationH;
use crate::final_ast::types::OwnershipH;
use crate::final_ast::types::Variability;
use crate::instantiating::ast::ast::ILocalVariableI;
use crate::instantiating::ast::ast::ReferenceLocalVariableI;
use crate::instantiating::ast::citizens::IMemberTypeI;
use crate::instantiating::ast::expressions::ExpressionIE;
use crate::instantiating::ast::names::INameI;
use crate::instantiating::ast::names::add_step;
use crate::simplifying::conversions::evaluate_variability;
use std::ptr::eq;

pub const BOX_MEMBER_INDEX: i32 = 0;

impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn translate_let(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        let2: &'i LetNormalIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let local_variable = let2.variable;
        let source_expr2 = let2.expr;
        let (source_expr_he, deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(source_expr2));
        let source_result_pointer_type_h = self.translate_coord(hinputs, hamuts, source_expr2.result());
        match source_expr_he.result_type().kind {
            KindHT::NeverHT(_) => return source_expr_he,
            _ => {}
        }
        let stackify_node = match local_variable {
            ILocalVariableI::ReferenceLocalVariableI(rlv) => {
                ExpressionH::StackifyH(self.translate_mundane_let(hinputs, hamuts, current_function_header, locals, source_expr_he, source_result_pointer_type_h, &rlv.name, rlv.variability))
            }
            ILocalVariableI::AddressibleLocalVariableI(alv) => {
                self.translate_addressible_let(hinputs, hamuts, current_function_header, locals, source_expr_he, source_result_pointer_type_h, &alv.name, alv.variability, alv.collapsed_coord)
            }
        };
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, stackify_node, deferreds)
    }

    pub fn translate_restackify(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        let2: &'i RestackifyIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let local_variable = let2.variable;
        let source_expr2 = let2.expr;
        let (source_expr_he, deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(source_expr2));
        let source_result_pointer_type_h = self.translate_coord(hinputs, hamuts, source_expr2.result());
        match source_expr_he.result_type().kind {
            KindHT::NeverHT(_) => return source_expr_he,
            _ => {}
        }
        let stackify_node = match local_variable {
            ILocalVariableI::ReferenceLocalVariableI(rlv) => {
                ExpressionH::RestackifyH(self.translate_mundane_restackify(hinputs, hamuts, current_function_header, locals, source_expr_he, &rlv.name))
            }
            ILocalVariableI::AddressibleLocalVariableI(alv) => {
                self.translate_addressible_restackify(hinputs, hamuts, current_function_header, locals, source_expr_he, source_result_pointer_type_h, &alv.name, alv.variability, alv.collapsed_coord)
            }
        };
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, stackify_node, deferreds)
    }

    pub fn translate_let_and_point(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        let_ie: &LetAndLendIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let LetAndLendIE { variable: local_variable, expr: source_expr2, target_ownership: _target_ownership, result: _ } = *let_ie;
        let (source_expr_he, deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(source_expr2));
        let source_result_pointer_type_h = self.translate_coord(hinputs, hamuts, source_expr2.result());
        let borrow_access = match local_variable {
            ILocalVariableI::ReferenceLocalVariableI(r) => {
                self.translate_mundane_let_and_point(hinputs, hamuts, current_function_header, locals, source_expr2, source_expr_he, source_result_pointer_type_h, let_ie, &r.name, r.variability)
            }
            ILocalVariableI::AddressibleLocalVariableI(alv) => {
                self.translate_addressible_let_and_point(hinputs, hamuts, current_function_header, locals, source_expr2, source_expr_he, source_result_pointer_type_h, let_ie, &alv.name, alv.variability, alv.collapsed_coord)
            }
        };
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, borrow_access, deferreds)
    }

    pub(crate) fn translate_addressible_let(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_he: ExpressionH<'s, 'h>,
        source_result_pointer_type_h: CoordH<'s, 'h>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        variability: VariabilityI,
        reference: CoordI<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let box_struct_ref_h = self.make_box(hinputs, hamuts, variability, reference, source_result_pointer_type_h);
        let expected_local_box_type = CoordH {
            ownership: OwnershipH::OwnH,
            location: LocationH::YonderH,
            kind: KindHT::StructHT(box_struct_ref_h),
        };
        let var_id_full = add_step(&current_function_header.id, INameI::from(*var_id));
        let var_id_name_h = self.translate_full_name(hinputs, hamuts, &var_id_full);
        let local = locals.add_typing_pass_local(var_id, var_id_name_h, evaluate_variability(variability), expected_local_box_type);
        let member_names: Vec<&'h IdH<'s>> = hamuts.struct_defs().iter().find(|s| eq(s.get_ref(self.interner), box_struct_ref_h)).unwrap().members.iter().map(|m| m.name).collect();
        let source_expressions = self.interner.bump().alloc_slice_copy(&[source_expr_he]);
        let target_member_names = self.interner.bump().alloc_slice_copy(&member_names);
        let new_struct_node = ExpressionH::NewStructH(self.interner.alloc(NewStructH {
            source_expressions,
            target_member_names,
            result_type: expected_local_box_type,
        }));
        ExpressionH::StackifyH(self.interner.alloc(StackifyH {
            source_expr: new_struct_node,
            local,
            name: Some(self.translate_full_name(hinputs, hamuts, &var_id_full)),
        }))
    }

    pub(crate) fn translate_addressible_restackify(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_he: ExpressionH<'s, 'h>,
        source_result_pointer_type_h: CoordH<'s, 'h>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        variability: VariabilityI,
        reference: CoordI<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        panic!("Unimplemented: translate_addressible_restackify");
    }

    pub(crate) fn translate_addressible_let_and_point(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        source_expr_he: ExpressionH<'s, 'h>,
        source_result_pointer_type_h: CoordH<'s, 'h>,
        let_ie: &LetAndLendIE<'s, 'i, cI>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        variability: VariabilityI,
        reference: CoordI<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        panic!("Unimplemented: translate_addressible_let_and_point");
    }

    pub(crate) fn translate_mundane_let(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_he: ExpressionH<'s, 'h>,
        source_result_pointer_type_h: CoordH<'s, 'h>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        variability: VariabilityI,
    ) -> &'h StackifyH<'s, 'h>
    {
        match source_expr_he.result_type().kind {
            KindHT::NeverHT(_) => panic!("translate_mundane_let: source NeverHT (vwat)"),
            _ => {}
        }
        let var_id_full = add_step(&current_function_header.id, INameI::from(*var_id));
        let var_id_name_h = self.translate_full_name(hinputs, hamuts, &var_id_full);
        let local_index = locals.add_typing_pass_local(var_id, var_id_name_h, evaluate_variability(variability), source_result_pointer_type_h);
        let stack_node = self.interner.alloc(StackifyH {
            source_expr: source_expr_he,
            local: local_index,
            name: Some(self.translate_full_name(hinputs, hamuts, &var_id_full)),
        });
        stack_node
    }

    pub(crate) fn translate_mundane_restackify(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr_he: ExpressionH<'s, 'h>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
    ) -> &'h RestackifyH<'s, 'h>
    {
        locals.mark_restackified_by_var_name(var_id);
        match source_expr_he.result_type().kind {
            KindHT::NeverHT(_) => panic!("translate_mundane_restackify: source NeverHT (vwat)"),
            _ => {}
        }
        let local = locals.get_by_var_name(var_id).expect("locals.get_by_var_name");
        let var_id_full = add_step(&current_function_header.id, INameI::from(*var_id));
        let stack_node = self.interner.alloc(RestackifyH {
            source_expr: source_expr_he,
            local,
            name: Some(self.translate_full_name(hinputs, hamuts, &var_id_full)),
        });
        stack_node
    }

    pub(crate) fn translate_mundane_let_and_point(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        source_expr2: ReferenceExpressionIE<'s, 'i, cI>,
        source_expr_he: ExpressionH<'s, 'h>,
        source_result_pointer_type_h: CoordH<'s, 'h>,
        let_ie: &LetAndLendIE<'s, 'i, cI>,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        variability: VariabilityI,
    ) -> ExpressionH<'s, 'h>
    {
        let stackify_h = self.translate_mundane_let(hinputs, hamuts, current_function_header, locals, source_expr_he, source_result_pointer_type_h, var_id, variability);
        let (borrow_access, borrow_deferreds) =
            self.translate_mundane_local_load(hinputs, hamuts, current_function_header, locals, var_id, source_expr2.result(), let_ie.result.ownership);
        assert!(borrow_deferreds.is_empty());
        let _ = borrow_deferreds;
        ExpressionH::ConsecutorH(self.interner.alloc(ConsecutorH {
            exprs: self.interner.bump().alloc_slice_copy(&[ExpressionH::StackifyH(stackify_h), borrow_access]),
        }))
    }

    pub fn translate_unlet(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        unlet2: &UnletIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let var_name = unlet2.variable.name();
        let local = match locals.get_by_var_name(&var_name) {
            None => panic!("Unletting an unknown variable: {:?}", var_name),
            Some(local) => local,
        };
        match unlet2.variable {
            ILocalVariableI::ReferenceLocalVariableI(rlv) => {
                let local_type2 = rlv.collapsed_coord;
                let _local_type_h = self.translate_coord(hinputs, hamuts, local_type2);
                let unstackify_node = ExpressionH::UnstackifyH(self.interner.alloc(UnstackifyH { local }));
                locals.mark_unstackified_by_var_name(&rlv.name);
                unstackify_node
            }
            ILocalVariableI::AddressibleLocalVariableI(alv) => {
                let inner_type2 = alv.collapsed_coord;
                let inner_type_h = self.translate_coord(hinputs, hamuts, inner_type2);
                let _struct_ref_h = self.make_box(hinputs, hamuts, alv.variability, inner_type2, inner_type_h);
                let unstackify_box_node = ExpressionH::UnstackifyH(self.interner.alloc(UnstackifyH { local }));
                locals.mark_unstackified_by_var_name(&alv.name);
                let inner_local = locals.add_hammer_local(inner_type_h, evaluate_variability(alv.variability));
                let des_h = ExpressionH::DestroyH(self.interner.alloc(DestroyH {
                    struct_expression: unstackify_box_node.expect_struct_access(),
                    local_types: self.interner.bump().alloc_slice_copy(&[inner_type_h]),
                    local_indices: self.interner.bump().alloc_slice_copy(&[inner_local]),
                }));
                locals.mark_unstackified(inner_local.id);
                let unstackify_contents_node = ExpressionH::UnstackifyH(self.interner.alloc(UnstackifyH { local: inner_local }));
                let exprs = self.interner.bump().alloc_slice_copy(&[des_h, unstackify_contents_node]);
                ExpressionH::ConsecutorH(self.interner.alloc(ConsecutorH { exprs }))
            }
        }
    }

    pub fn translate_destructure_static_sized_array(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        des2: &'i DestroyStaticSizedArrayIntoLocalsIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let DestroyStaticSizedArrayIntoLocalsIE { expr: source_expr2, static_sized_array: arr_seq_i, destination_reference_variables: destination_reference_local_variables } = *des2;
        let (source_expr_he, source_expr_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(source_expr2));
        assert!(destination_reference_local_variables.len() as i64 == arr_seq_i.size());
        let (local_types, local_indices): (Vec<CoordH<'s, 'h>>, Vec<Local<'s, 'h>>) = destination_reference_local_variables.iter().map(|destination_reference_local_variable| {
            let member_ref_type_h = self.translate_coord(hinputs, hamuts, arr_seq_i.element_type().coord);
            let var_id_full = add_step(&current_function_header.id, INameI::from(destination_reference_local_variable.name));
            let var_id_name_h = self.translate_full_name(hinputs, hamuts, &var_id_full);
            let local_index = locals.add_typing_pass_local(
                &destination_reference_local_variable.name,
                var_id_name_h,
                evaluate_variability(destination_reference_local_variable.variability),
                member_ref_type_h);
            (member_ref_type_h, local_index)
        }).unzip();
        let stack_node = ExpressionH::DestroyStaticSizedArrayIntoLocalsH(self.interner.alloc(DestroyStaticSizedArrayIntoLocalsH {
            struct_expression: source_expr_he.expect_static_sized_array_access(),
            local_types: self.interner.alloc_slice_from_vec(local_types),
            local_indices: self.interner.alloc_slice_from_vec(local_indices),
        }));
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, stack_node, source_expr_deferreds)
    }

    pub fn translate_destroy(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        des2: &DestroyIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let DestroyIE { expr: source_expr2, struct_tt: struct_ti, destination_reference_variables: destination_reference_local_variables } = *des2;
        let (source_expr_he, source_expr_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(source_expr2));
        let struct_def_t = self.lookup_struct(hinputs, hamuts, struct_ti);
        // We put Vector.empty here to make sure that we've consumed all the destination
        // reference local variables.
        let mut remaining_destination_reference_local_variables: Vec<&ReferenceLocalVariableI<'s, 'i>> = destination_reference_local_variables.iter().collect();
        let mut local_types: Vec<CoordH<'s, 'h>> = Vec::new();
        let mut local_indices: Vec<Local<'s, 'h>> = Vec::new();
        for member2 in struct_def_t.members.iter() {
            match member2.tyype {
                IMemberTypeI::ReferenceMemberTypeI(member_ref_type2) => {
                    let destination_reference_local_variable = remaining_destination_reference_local_variables.remove(0);
                    let member_ref_type_h = self.translate_coord(hinputs, hamuts, member_ref_type2.reference);
                    let var_id_full = add_step(&current_function_header.id, INameI::from(destination_reference_local_variable.name));
                    let var_id_name_h = self.translate_full_name(hinputs, hamuts, &var_id_full);
                    let local_index = locals.add_typing_pass_local(
                        &destination_reference_local_variable.name,
                        var_id_name_h,
                        evaluate_variability(destination_reference_local_variable.variability),
                        member_ref_type_h);
                    local_types.push(member_ref_type_h);
                    local_indices.push(local_index);
                }
                IMemberTypeI::AddressMemberTypeI(member_ref_type2_addr) => {
                    let member_ref_type_h = self.translate_coord(hinputs, hamuts, member_ref_type2_addr.reference);
                    let box_struct_ref_h = self.make_box(hinputs, hamuts, member2.variability, member_ref_type2_addr.reference, member_ref_type_h);
                    let local_box_type = CoordH {
                        ownership: OwnershipH::MutableBorrowH,
                        location: LocationH::YonderH,
                        kind: KindHT::StructHT(box_struct_ref_h),
                    };
                    let local_index = locals.add_hammer_local(local_box_type, Variability::Final);
                    local_types.push(local_box_type);
                    local_indices.push(local_index);
                }
            }
        }
        assert!(remaining_destination_reference_local_variables.is_empty());
        let _ = &mut remaining_destination_reference_local_variables;
        let destructure_h =
            ExpressionH::DestroyH(self.interner.alloc(DestroyH {
                struct_expression: source_expr_he.expect_struct_access(),
                local_types: self.interner.bump().alloc_slice_copy(&local_types),
                local_indices: self.interner.bump().alloc_slice_copy(&local_indices),
            }));
        let unboxings_h: Vec<ExpressionH<'s, 'h>> =
            struct_def_t.members.iter().zip(local_types.iter().zip(local_indices.iter())).flat_map(|(member, (_local_type, local))| {
                match member.tyype {
                    IMemberTypeI::ReferenceMemberTypeI(_) => Vec::<ExpressionH<'s, 'h>>::new(),
                    IMemberTypeI::AddressMemberTypeI(_) => {
                        let unstackify_node = ExpressionH::UnstackifyH(self.interner.alloc(UnstackifyH { local: *local }));
                        locals.mark_unstackified(local.id);
                        let discard_node = ExpressionH::DiscardH(self.interner.alloc(DiscardH { source_expression: unstackify_node }));
                        vec![discard_node]
                    }
                }
            }).collect();
        let mut destructure_and_unboxings: Vec<ExpressionH<'s, 'h>> = vec![destructure_h];
        destructure_and_unboxings.extend(unboxings_h);
        let destructure_and_unboxings_h = ExpressionH::ConsecutorH(self.interner.alloc(ConsecutorH {
            exprs: self.interner.bump().alloc_slice_copy(&destructure_and_unboxings),
        }));
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, destructure_and_unboxings_h, source_expr_deferreds)
    }
}

