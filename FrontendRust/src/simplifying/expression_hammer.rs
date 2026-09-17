//
// Per typing-pass `Compiler` precedent, `ExpressionHammer` is not a Rust struct.
// Methods become `impl Hammer { ... }` blocks colocated here.

use crate::final_ast::instructions::{ExpressionH, WhileH};
use crate::instantiating::ast::ast::{FunctionHeaderI, PrototypeI};
use crate::instantiating::ast::expressions::{
    DestroyImmRuntimeSizedArrayIE, DestroyStaticSizedArrayIntoFunctionIE, ExpressionIE, IfIE,
    NewImmRuntimeSizedArrayIE, NewMutRuntimeSizedArrayIE, ReferenceExpressionIE,
    StaticArrayFromCallableIE, WhileIE,
};
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::ast::types::{cI, CoordI};
use crate::simplifying::hamuts::Hamuts;
use crate::simplifying::hammer::{Hammer, Locals};
use crate::final_ast::ast::IdH;
use crate::final_ast::instructions::ArgumentH;
use crate::final_ast::instructions::ArrayCapacityH;
use crate::final_ast::instructions::ArrayLengthH;
use crate::final_ast::instructions::AsSubtypeH;
use crate::final_ast::instructions::BorrowToWeakH;
use crate::final_ast::instructions::BreakH;
use crate::final_ast::instructions::CallH;
use crate::final_ast::instructions::ConsecutorH;
use crate::final_ast::instructions::ConstantBoolH;
use crate::final_ast::instructions::ConstantF64H;
use crate::final_ast::instructions::ConstantIntH;
use crate::final_ast::instructions::ConstantStrH;
use crate::final_ast::instructions::ConstantVoidH;
use crate::final_ast::instructions::DestroyMutRuntimeSizedArrayH;
use crate::final_ast::instructions::DestroyStaticSizedArrayIntoFunctionH;
use crate::final_ast::instructions::DiscardH;
use crate::final_ast::instructions::ExternCallH;
use crate::final_ast::instructions::IfH;
use crate::final_ast::instructions::InterfaceCallH;
use crate::final_ast::instructions::IsSameInstanceH;
use crate::final_ast::instructions::LockWeakH;
use crate::final_ast::instructions::NewArrayFromValuesH;
use crate::final_ast::instructions::NewImmRuntimeSizedArrayH;
use crate::final_ast::instructions::NewMutRuntimeSizedArrayH;
use crate::final_ast::instructions::NewStructH;
use crate::final_ast::instructions::PopRuntimeSizedArrayH;
use crate::final_ast::instructions::PushRuntimeSizedArrayH;
use crate::final_ast::instructions::ReturnH;
use crate::final_ast::instructions::StackifyH;
use crate::final_ast::instructions::StaticArrayFromCallableH;
use crate::final_ast::instructions::StructToInterfaceUpcastH;
use crate::final_ast::instructions::UnstackifyH;
use crate::final_ast::instructions::VariableIdH;
use crate::final_ast::types::BoolHT;
use crate::final_ast::types::CoordH;
use crate::final_ast::types::KindHT;
use crate::final_ast::types::LocationH;
use crate::final_ast::types::NeverHT;
use crate::final_ast::types::OwnershipH;
use crate::final_ast::types::Variability;
use crate::instantiating::ast::ast::ILocalVariableI;
use crate::instantiating::ast::expressions::AddressExpressionIE;
use crate::instantiating::ast::expressions::ArgLookupIE;
use crate::instantiating::ast::expressions::AsSubtypeIE;
use crate::instantiating::ast::expressions::BorrowToWeakIE;
use crate::instantiating::ast::expressions::DeferIE;
use crate::instantiating::ast::expressions::InterfaceFunctionCallIE;
use crate::instantiating::ast::expressions::IsSameInstanceIE;
use crate::instantiating::ast::expressions::LockWeakIE;
use crate::instantiating::ast::expressions::StaticArrayFromValuesIE;
use crate::instantiating::ast::expressions::TupleIE;
use crate::instantiating::ast::types::KindIT;
use crate::instantiating::ast::types::NeverIT;
use crate::instantiating::ast::types::RuntimeSizedArrayITValI;
use crate::simplifying::hammer::consecrash;
use crate::simplifying::hammer::consecutive;
use std::collections::HashSet;
use std::marker::PhantomData;

// from `Hammer.translate` per overload-suffix pattern, since both methods now
// live on the same `impl Hammer` per typing-pass collapse.)
impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn translate_expression(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        expr2: ExpressionIE<'s, 'i, cI>,
    ) -> (ExpressionH<'s, 'h>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        use crate::instantiating::ast::expressions::ReferenceExpressionIE as RE;
        match expr2 {
            ExpressionIE::Reference(r) => match r {
                RE::ConstantInt(c) => {
                    (ExpressionH::ConstantIntH(self.interner.alloc(ConstantIntH { value: c.value, bits: c.bits })), Vec::new())
                }
                RE::VoidLiteral(_) => {
                    let construct_h = ExpressionH::ConstantVoidH(self.interner.alloc(ConstantVoidH));
                    (construct_h, Vec::new())
                }
                RE::ConstantStr(c) => (ExpressionH::ConstantStrH(self.interner.alloc(ConstantStrH { value: self.interner.bump().alloc_str(c.value)})), Vec::new()),
                RE::ConstantFloat(c) => {
                    (ExpressionH::ConstantF64H(self.interner.alloc(ConstantF64H { value: c.value })), Vec::new())
                }
                RE::ConstantBool(c) => {
                    (ExpressionH::ConstantBoolH(self.interner.alloc(ConstantBoolH { value: c.value })), Vec::new())
                }
                RE::LetNormal(let2) => {
                    let let_h = self.translate_let(hinputs, hamuts, current_function_header, locals, let2);
                    (let_h, Vec::new())
                }
                RE::Restackify(let2) => {
                    let let_h = self.translate_restackify(hinputs, hamuts, current_function_header, locals, let2);
                    (let_h, Vec::new())
                }
                RE::LetAndLend(let2) => {
                    let borrow_access = self.translate_let_and_point(hinputs, hamuts, current_function_header, locals, let2);
                    (borrow_access, Vec::new())
                }
                RE::Destroy(des2) => {
                    let destroy_h = self.translate_destroy(hinputs, hamuts, current_function_header, locals, des2);
                    // Compiler destructures put things in local variables (even though hammer itself
                    // uses registers internally to make that happen).
                    // Since all the members landed in locals, we still need something to ret, so we
                    // return a void.
                    (destroy_h, Vec::new())
                }
                RE::DestroyStaticSizedArrayIntoLocals(des2) => {
                    let destructure_h = self.translate_destructure_static_sized_array(hinputs, hamuts, current_function_header, locals, des2);
                    (destructure_h, Vec::new())
                }
                RE::Unlet(unlet2) => {
                    let value_access = self.translate_unlet(hinputs, hamuts, current_function_header, locals, unlet2);
                    (value_access, Vec::new())
                }
                RE::Mutate(mutate2) => {
                    let access = self.translate_mutate(hinputs, hamuts, current_function_header, locals, mutate2);
                    (access, Vec::new())
                }
                RE::Mutabilify(b) => panic!("translate_expression: Mutabilify branch"),
                RE::Immutabilify(b) => panic!("translate_expression: Immutabilify branch"),
                RE::Block(b) => {
                    let block_h = self.translate_block(hinputs, hamuts, current_function_header, locals, b);
                    (ExpressionH::BlockH(block_h), Vec::new())
                }
                RE::FunctionCall(call2) => {
                    let args_ie: Vec<ExpressionIE<'s, 'i, cI>> = call2.args.iter().map(|a| ExpressionIE::Reference(*a)).collect();
                    let access = self.translate_function_pointer_call(hinputs, hamuts, current_function_header, locals, &call2.callable, &args_ie, call2.result);
                    (access, Vec::new())
                }
                RE::PreCheckBorrow(p) => panic!("translate_expression: PreCheckBorrow branch"),
                RE::InterfaceFunctionCall(ic) => {
                    let InterfaceFunctionCallIE { super_function_prototype, virtual_param_index, args: args_exprs2, result: result_type2 } = *ic;
                    let args_exprs2_ie: Vec<ExpressionIE<'s, 'i, cI>> = args_exprs2.iter().map(|e| ExpressionIE::Reference(*e)).collect();
                    let access = self.translate_interface_function_call(hinputs, hamuts, current_function_header, locals, self.interner.alloc(super_function_prototype), virtual_param_index, result_type2, &args_exprs2_ie);
                    (access, Vec::new())
                }
                RE::Consecutor(c) => {
                    let exprs_ie = c.exprs;
                    let mut exprs_he: Vec<ExpressionH<'s, 'h>> = Vec::new();
                    for next_ie in exprs_ie.iter() {
                        let last_is_never = match exprs_he.last().map(|e| e.result_type().kind) {
                            Some(KindHT::NeverHT(_)) => true,
                            _ => false,
                        };
                        if last_is_never {
                            continue;
                        }
                        let (next_he, next_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(*next_ie));
                        let next_expr_with_deferreds_he = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, next_he, next_deferreds);
                        exprs_he.push(next_expr_with_deferreds_he);
                    }
                    let last_is_never = match exprs_he.last().map(|e| e.result_type().kind) {
                        Some(KindHT::NeverHT(_)) => {
                            return (consecrash(self.interner, locals, &exprs_he), Vec::new());
                        }
                        _ => {}
                    };
                    let _ = last_is_never;
                    (consecutive(self.interner, &exprs_he), Vec::new())
                }
                RE::ArrayLength(a) => {
                    let (result_he, deferreds) =
                        self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(a.array_expr));
                    let length_result_node = ExpressionH::ArrayLengthH(self.interner.alloc(ArrayLengthH { source_expression: result_he }));
                    let array_length_and_deferreds_expr_h =
                        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, length_result_node, deferreds);
                    (array_length_and_deferreds_expr_h, Vec::new())
                }
                RE::RuntimeSizedArrayCapacity(a) => {
                    let (result_he, deferreds) =
                        self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(a.array_expr));
                    let length_result_node = ExpressionH::ArrayCapacityH(self.interner.alloc(ArrayCapacityH { source_expression: result_he }));
                    let array_length_and_deferreds_expr_h =
                        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, length_result_node, deferreds);
                    (array_length_and_deferreds_expr_h, Vec::new())
                }
                RE::ArraySize(a) => panic!("translate_expression: ArraySize branch"),
                RE::LockWeak(lw) => {
                    let LockWeakIE { inner_expr: inner_expr_2, result_opt_borrow_type: result_opt_borrow_type_2, .. } = *lw;
                    let (result_he, deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(inner_expr_2));
                    let result_opt_borrow_type_h = self.translate_coord(hinputs, hamuts, result_opt_borrow_type_2).expect_interface_coord();
                    let some_constructor_h = self.translate_function_ref(hinputs, hamuts, current_function_header, &lw.some_constructor);
                    let none_constructor_h = self.translate_function_ref(hinputs, hamuts, current_function_header, &lw.none_constructor);
                    let result_node = ExpressionH::LockWeakH(self.interner.alloc(LockWeakH {
                        source_expression: result_he,
                        result_type: result_opt_borrow_type_h,
                        some_constructor: some_constructor_h.prototype,
                        none_constructor: none_constructor_h.prototype,
                    }));
                    (result_node, deferreds)
                }
                RE::BorrowToWeak(b) => {
                    let BorrowToWeakIE { inner_expr, result: _ } = *b;
                    let (inner_expr_he, inner_deferreds) =
                        self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(inner_expr));
                    (ExpressionH::BorrowToWeakH(self.interner.alloc(BorrowToWeakH { ref_expression: inner_expr_he })), inner_deferreds)
                }
                RE::Defer(d2) => {
                    let DeferIE { inner_expr, deferred_expr, result: _ } = *d2;
                    let (inner_expr_he, inner_deferreds) =
                        self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(inner_expr));
                    let mut new_deferreds: Vec<ExpressionIE<'s, 'i, cI>> = vec![ExpressionIE::Reference(deferred_expr)];
                    new_deferreds.extend(inner_deferreds);
                    (inner_expr_he, new_deferreds)
                }
                RE::If(if2) => {
                    let maybe_access = self.translate_if(hinputs, hamuts, current_function_header, locals, if2);
                    (maybe_access, Vec::new())
                }
                RE::While(while2) => {
                    let while_h = self.translate_while(hinputs, hamuts, current_function_header, locals, while2);
                    (ExpressionH::WhileH(while_h), Vec::new())
                }
                RE::Return(return_ie) => {
                    let inner_expr = return_ie.source_expr;
                    let inner_result = ExpressionIE::Reference(inner_expr).result();
                    assert!(matches!(inner_result.kind, KindIT::NeverIT(NeverIT { from_break: false, .. })) || inner_result == current_function_header.return_type);
                    let (inner_expr_he, inner_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(inner_expr));
                    let inner_with_deferreds = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, inner_expr_he, inner_deferreds);
                    match inner_with_deferreds.result_type().kind {
                        KindHT::NeverHT(_) => {
                            return (inner_with_deferreds, Vec::new());
                        }
                        _ => {}
                    }
                    assert!(ExpressionIE::Reference(inner_expr).result() == current_function_header.return_type);
                    (ExpressionH::ReturnH(self.interner.alloc(ReturnH { source_expression: inner_with_deferreds })), Vec::new())
                }
                RE::Break(_) => (ExpressionH::BreakH(self.interner.alloc(BreakH)), Vec::new()),
                RE::Discard(discard_ie) => {
                    let inner_expr = discard_ie.expr;
                    let (undiscarded_inner_expr_h, inner_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(inner_expr));
                    assert!(inner_deferreds.is_empty());
                    let inner_expr_h = ExpressionH::DiscardH(self.interner.alloc(DiscardH { source_expression: undiscarded_inner_expr_h }));
                    let inner_with_deferreds_expr_h = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, inner_expr_h, inner_deferreds);
                    (inner_with_deferreds_expr_h, Vec::new())
                }
                RE::Tuple(t) => {
                    let TupleIE { elements: exprs, result: result_type } = *t;
                    let exprs_ie: Vec<ExpressionIE<'s, 'i, cI>> = exprs.iter().map(|e| ExpressionIE::Reference(*e)).collect();
                    let (results_he, deferreds) = self.translate_expressions_until_never(hinputs, hamuts, current_function_header, locals, &exprs_ie);
                    match results_he.last().map(|e| e.result_type().kind) {
                        Some(KindHT::NeverHT(_)) => {
                            return (consecrash(self.interner, locals, &results_he), Vec::new());
                        }
                        _ => {}
                    }
                    let result_struct_i = match result_type.kind {
                        KindIT::StructIT(s) => s,
                        _ => panic!("Tuple: result_type.kind not StructIT"),
                    };
                    let underlying_struct_ref_h = self.translate_struct_i(hinputs, hamuts, result_struct_i);
                    let result_reference = self.translate_coord(hinputs, hamuts, result_type);
                    assert!(result_reference.kind == KindHT::StructHT(underlying_struct_ref_h));
                    let struct_def_h = *hamuts.struct_t_to_struct_def_h().get(result_struct_i).expect("structDefH not in map");
                    assert!(results_he.len() == struct_def_h.members.len());
                    let target_member_names: Vec<&'h IdH<'s>> = struct_def_h.members.iter().map(|m| m.name).collect();
                    let new_struct_node = ExpressionH::NewStructH(self.interner.alloc(NewStructH {
                        source_expressions: self.interner.bump().alloc_slice_copy(&results_he),
                        target_member_names: self.interner.bump().alloc_slice_copy(&target_member_names),
                        result_type: result_reference.expect_struct_coord(),
                    }));
                    let new_struct_and_deferreds_expr_h = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, new_struct_node, deferreds);
                    (new_struct_and_deferreds_expr_h, Vec::new())
                }
                RE::StaticArrayFromValues(a) => {
                    let StaticArrayFromValuesIE { elements: exprs, result_reference: array_reference_2, array_type: array_type_2 } = *a;
                    let exprs_ie: Vec<ExpressionIE<'s, 'i, cI>> = exprs.iter().map(|e| ExpressionIE::Reference(*e)).collect();
                    let (results_he, deferreds) = self.translate_expressions_until_never(hinputs, hamuts, current_function_header, locals, &exprs_ie);
                    match results_he.last().map(|e| e.result_type().kind) {
                        Some(KindHT::NeverHT(_)) => {
                            panic!("Unimplemented: StaticArrayFromValues Never branch (Hammer.consecrash)");
                        }
                        _ => {}
                    }
                    let underlying_array_h = self.translate_static_sized_array(hinputs, hamuts, array_type_2);
                    let array_reference_h = self.translate_coord(hinputs, hamuts, array_reference_2);
                    assert!(array_reference_h.kind == KindHT::StaticSizedArrayHT(underlying_array_h));
                    let new_struct_node = ExpressionH::NewArrayFromValuesH(self.interner.alloc(NewArrayFromValuesH {
                        result_type: array_reference_h.expect_static_sized_array_coord(),
                        source_expressions: self.interner.alloc_slice_from_vec(results_he),
                    }));
                    let new_struct_and_deferreds_expr_h = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, new_struct_node, deferreds);
                    (new_struct_and_deferreds_expr_h, Vec::new())
                }
                RE::IsSameInstance(a) => {
                    let IsSameInstanceIE { left: left_expr_i, right: right_expr_i } = *a;
                    let (left_expr_he, left_deferreds) =
                        self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(left_expr_i));
                    let (right_expr_he, right_deferreds) =
                        self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(right_expr_i));
                    let result_he = ExpressionH::IsSameInstanceH(self.interner.alloc(IsSameInstanceH {
                        left_expression: left_expr_he,
                        right_expression: right_expr_he,
                    }));
                    let all_deferreds: Vec<_> = left_deferreds.into_iter().chain(right_deferreds.into_iter()).collect();
                    let expr = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, result_he, all_deferreds);
                    (expr, Vec::new())
                }
                RE::AsSubtype(a) => {
                    let AsSubtypeIE { source_expr: left_expr_i, target_type: target_subtype, result_result_type: result_opt_type, ok_constructor: some_constructor, err_constructor: none_constructor, .. } = *a;
                    let (result_he, deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(left_expr_i));
                    let target_subtype_h = self.translate_coord(hinputs, hamuts, target_subtype).kind;
                    let result_opt_type_h = self.translate_coord(hinputs, hamuts, result_opt_type).expect_interface_coord();
                    let some_constructor_h = self.translate_function_ref(hinputs, hamuts, current_function_header, self.interner.alloc(some_constructor));
                    let none_constructor_h = self.translate_function_ref(hinputs, hamuts, current_function_header, self.interner.alloc(none_constructor));
                    let result_node = ExpressionH::AsSubtypeH(self.interner.alloc(AsSubtypeH {
                        source_expression: result_he,
                        target_type: target_subtype_h,
                        result_type: result_opt_type_h,
                        some_constructor: some_constructor_h.prototype,
                        none_constructor: none_constructor_h.prototype,
                    }));
                    (result_node, deferreds)
                }
                RE::ArgLookup(a) => {
                    let ArgLookupIE { param_index, coord: type2 } = *a;
                    let type_h = self.translate_coord(hinputs, hamuts, type2);
                    assert!(current_function_header.param_types()[param_index as usize] == type2);
                    assert!(self.translate_coord(hinputs, hamuts, current_function_header.param_types()[param_index as usize]) == type_h);
                    let arg_node = ExpressionH::ArgumentH(self.interner.alloc(ArgumentH { result_type: type_h, argument_index: param_index }));
                    (arg_node, Vec::new())
                }
                RE::ExternFunctionCall(efc) => {
                    let access = self.translate_extern_function_call(hinputs, hamuts, current_function_header, locals, &efc.prototype2, efc.args);
                    (access, Vec::new())
                }
                RE::Reinterpret(a) => panic!("translate_expression: Reinterpret branch"),
                RE::Construct(a) => {
                    let (members_he, deferreds) = self.translate_expressions_until_never(hinputs, hamuts, current_function_header, locals, a.args);
                    // Don't evaluate anything that can't ever be run, see BRCOBS
                    match members_he.last().map(|e| e.result_type().kind) {
                        Some(KindHT::NeverHT(_)) => {
                            panic!("Unimplemented: Construct Never branch (Hammer.consecrash)");
                        }
                        _ => {}
                    }
                    let result_type_h = self.translate_coord(hinputs, hamuts, a.result);
                    let struct_def_h = *hamuts.struct_t_to_struct_def_h().get(&a.struct_tt).unwrap();
                    assert_eq!(members_he.len(), struct_def_h.members.len());
                    for (member_he, member_h) in members_he.iter().zip(struct_def_h.members.iter()) {
                        assert_eq!(member_he.result_type(), member_h.tyype);
                    }
                    let member_names: Vec<&'h IdH<'s>> = struct_def_h.members.iter().map(|m| m.name).collect();
                    let new_struct_node = ExpressionH::NewStructH(self.interner.alloc(NewStructH {
                        source_expressions: self.interner.bump().alloc_slice_fill_iter(members_he.into_iter()),
                        target_member_names: self.interner.bump().alloc_slice_fill_iter(member_names.into_iter()),
                        result_type: { assert!(matches!(result_type_h.kind, KindHT::StructHT(_))); result_type_h },
                    }));
                    let new_struct_and_deferreds_expr_h = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, new_struct_node, deferreds);
                    (new_struct_and_deferreds_expr_h, Vec::new())
                }
                RE::NewMutRuntimeSizedArray(nmrsa_ie) => {
                    let access = self.translate_new_mut_runtime_sized_array(hinputs, hamuts, current_function_header, locals, nmrsa_ie);
                    (access, Vec::new())
                }
                RE::StaticArrayFromCallable(a) => {
                    let access = self.translate_static_array_from_callable(hinputs, hamuts, current_function_header, locals, a);
                    (access, Vec::new())
                }
                RE::DestroyStaticSizedArrayIntoFunction(das2) => {
                    let das_h = self.translate_destroy_static_sized_array(hinputs, hamuts, current_function_header, locals, das2);
                    (das_h, Vec::new())
                }
                RE::DestroyMutRuntimeSizedArray(dmrsa) => {
                    let (rsa_he, rsa_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(dmrsa.array_expr));
                    let destroy_he = ExpressionH::DestroyMutRuntimeSizedArrayH(self.interner.alloc(DestroyMutRuntimeSizedArrayH {
                        array_expression: rsa_he.expect_runtime_sized_array_access(),
                    }));
                    let expr = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, destroy_he, rsa_deferreds);
                    (expr, Vec::new())
                }
                RE::PushRuntimeSizedArray(prsa_ie) => {
                    let (array_he, array_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(prsa_ie.array_expr));
                    let rsa_he = array_he.expect_runtime_sized_array_access();
                    let rsa_def_h = hamuts.get_runtime_sized_array(rsa_he.result_type().kind.expect_runtime_sized_array_ht());
                    let (newcomer_he, newcomer_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(prsa_ie.new_element_expr));
                    assert!(newcomer_he.result_type() == rsa_def_h.element_type);
                    let construct_array_call_node = ExpressionH::PushRuntimeSizedArrayH(self.interner.alloc(PushRuntimeSizedArrayH {
                        array_expression: rsa_he,
                        newcomer_expression: newcomer_he,
                    }));
                    let mut deferreds = array_deferreds;
                    deferreds.extend(newcomer_deferreds);
                    let access = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, construct_array_call_node, deferreds);
                    (access, Vec::new())
                }
                RE::PopRuntimeSizedArray(prsa_ie) => {
                    let (array_he, array_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(prsa_ie.array_expr));
                    let rsa_he = array_he.expect_runtime_sized_array_access();
                    let rsa_def_h = hamuts.get_runtime_sized_array(rsa_he.result_type().kind.expect_runtime_sized_array_ht());
                    let construct_array_call_node = ExpressionH::PopRuntimeSizedArrayH(self.interner.alloc(PopRuntimeSizedArrayH {
                        array_expression: rsa_he,
                        element_type: rsa_def_h.element_type,
                    }));
                    let access = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, construct_array_call_node, array_deferreds);
                    (access, Vec::new())
                }
                RE::InterfaceToInterfaceUpcast(a) => panic!("translate_expression: InterfaceToInterfaceUpcast branch"),
                RE::Upcast(up) => {
                    let target_pointer_type2 = up.result;
                    let source_pointer_type2 = ExpressionIE::Reference(up.inner_expr).result();
                    let source_pointer_type_h = self.translate_coord(hinputs, hamuts, source_pointer_type2);
                    let target_pointer_type_h = self.translate_coord(hinputs, hamuts, target_pointer_type2);
                    let _source_struct_ref_h = match source_pointer_type_h.kind {
                        KindHT::StructHT(s) => s,
                        _ => panic!("Upcast: source not struct"),
                    };
                    let target_interface_ref_h = match target_pointer_type_h.kind {
                        KindHT::InterfaceHT(i) => i,
                        _ => panic!("Upcast: target not interface"),
                    };
                    let (inner_expr_he, inner_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(up.inner_expr));
                    let upcast_node = ExpressionH::StructToInterfaceUpcastH(self.interner.alloc(StructToInterfaceUpcastH {
                        source_expression: inner_expr_he.expect_struct_access(),
                        target_interface: target_interface_ref_h,
                    }));
                    (upcast_node, inner_deferreds)
                }
                RE::SoftLoad(load2) => {
                    let (loaded_access_h, deferreds) = self.translate_load(hinputs, hamuts, current_function_header, locals, load2);
                    (loaded_access_h, deferreds)
                }
                RE::DestroyImmRuntimeSizedArray(a) => panic!("translate_expression: DestroyImmRuntimeSizedArray branch"),
                RE::NewImmRuntimeSizedArray(nirsa_ie) => {
                    let access = self.translate_new_imm_runtime_sized_array(hinputs, hamuts, current_function_header, locals, nirsa_ie);
                    (access, Vec::new())
                }
            },
            ExpressionIE::Address(a) => match a {
                AddressExpressionIE::LocalLookup(lookup2) => {
                    match lookup2.local_variable {
                        ILocalVariableI::AddressibleLocalVariableI(_) => {
                            let load_box_access = self.translate_local_address(hinputs, hamuts, current_function_header, locals, lookup2);
                            (load_box_access, Vec::new())
                        }
                        _ => panic!("translate_expression: LocalLookup non-addressible"),
                    }
                }
                AddressExpressionIE::AddressMemberLookup(lookup2) => {
                    let (load_box_access, deferreds) = self.translate_member_address(hinputs, hamuts, current_function_header, locals, lookup2);
                    (load_box_access, deferreds)
                }
                _ => panic!("translate_expression: Address other branch"),
            },
        }
    }

    pub fn translate_deferreds(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        original_expr: ExpressionH<'s, 'h>,
        deferreds: Vec<ExpressionIE<'s, 'i, cI>>,
    ) -> ExpressionH<'s, 'h>
    {
        if deferreds.is_empty() {
            return original_expr;
        }
        let (deferred_exprs, deferred_deferreds) =
            self.translate_expressions_until_never(hinputs, hamuts, current_function_header, locals, &deferreds);
        match deferred_exprs.last().map(|e| e.result_type().kind) {
            Some(KindHT::NeverHT(_)) => {
                return consecrash(self.interner, locals, &deferred_exprs);
            }
            _ => {}
        }
        if deferred_exprs.iter().map(|e| e.result_type().kind).any(|k| !matches!(k, KindHT::VoidHT(_))) {
            panic!("translate_deferreds: vcurious — deferred had non-void result");
        }
        if locals.locals.len() != locals.locals.len() {
            // There shouldnt have been any locals introduced
            panic!("wat");
        }
        assert!(deferred_deferreds.is_empty());
        assert!(!deferred_exprs.is_empty());
        let new_exprs: Vec<ExpressionH<'s, 'h>> =
            if matches!(original_expr.result_type().kind, KindHT::VoidHT(_)) {
                let void = ExpressionH::ConstantVoidH(self.interner.alloc(ConstantVoidH));
                let mut v = vec![original_expr];
                v.extend(deferred_exprs.iter().copied());
                v.push(void);
                v
            } else {
                let temporary_result_local = locals.add_hammer_local(original_expr.result_type(), Variability::Final);
                let stackify = ExpressionH::StackifyH(self.interner.alloc(StackifyH { source_expr: original_expr, local: temporary_result_local, name: None }));
                let unstackify = ExpressionH::UnstackifyH(self.interner.alloc(UnstackifyH { local: temporary_result_local }));
                locals.mark_unstackified(temporary_result_local.id);
                let mut v = vec![stackify];
                v.extend(deferred_exprs.iter().copied());
                v.push(unstackify);
                v
            };
        let result = ExpressionH::ConsecutorH(self.interner.alloc(ConsecutorH {
            exprs: self.interner.bump().alloc_slice_copy(&new_exprs),
        }));
        assert!(original_expr.result_type() == result.result_type());
        result
    }

    pub fn translate_expressions_until_never(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        exprs_ie: &[ExpressionIE<'s, 'i, cI>],
    ) -> (Vec<ExpressionH<'s, 'h>>, Vec<ExpressionIE<'s, 'i, cI>>)
    {
        let (exprs_he, deferreds) = exprs_ie.iter().fold((Vec::<ExpressionH<'s, 'h>>::new(), Vec::<ExpressionIE<'s, 'i, cI>>::new()), |(prev_exprs_he, prev_deferreds), next_ie| {
            let prev_is_never = match prev_exprs_he.last().map(|e| e.result_type().kind) {
                Some(KindHT::NeverHT(_)) => true,
                _ => false,
            };
            if prev_is_never {
                (prev_exprs_he, Vec::new())
            } else {
                let (next_he, next_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, *next_ie);
                let mut new_exprs = prev_exprs_he;
                new_exprs.push(next_he);
                let mut new_deferreds = prev_deferreds;
                new_deferreds.extend(next_deferreds);
                (new_exprs, new_deferreds)
            }
        });
        // We'll never get to the deferreds, so forget them.
        match exprs_he.last().map(|e| e.result_type().kind) {
            Some(KindHT::NeverHT(_)) => (exprs_he, Vec::new()),
            _ => (exprs_he, deferreds),
        }
    }

    pub fn translate_expressions_and_deferreds(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        exprs2: &[ExpressionIE<'s, 'i, cI>],
    ) -> ExpressionH<'s, 'h>
    {
        let exprs: Vec<ExpressionH<'s, 'h>> = exprs2.iter().map(|expr2| {
            let (first_he, first_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, *expr2);
            self.translate_deferreds(hinputs, hamuts, current_function_header, locals, first_he, first_deferreds)
        }).collect();
        consecutive(self.interner, &exprs)
    }

    pub fn translate_extern_function_call(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        prototype2: &'i PrototypeI<'s, 'i, cI>,
        args_exprs2: &[ReferenceExpressionIE<'s, 'i, cI>],
    ) -> ExpressionH<'s, 'h>
    {
        let args_ie: Vec<ExpressionIE<'s, 'i, cI>> = args_exprs2.iter().map(|a| ExpressionIE::Reference(*a)).collect();
        let (args_he, args_deferreds) = self.translate_expressions_until_never(hinputs, hamuts, current_function_header, locals, &args_ie);
        // Don't evaluate anything that can't ever be run, see BRCOBS
        if !args_he.is_empty() && matches!(args_he.last().unwrap().result_type().kind, KindHT::NeverHT(NeverHT { from_break: true })) {
            return consecrash(self.interner, locals, &args_he);
        }
        let param_types = self.translate_coords(hinputs, hamuts, &prototype2.param_types());
        assert!(args_he.iter().map(|e| e.result_type()).collect::<Vec<_>>() == param_types);
        let function_ref_h = self.translate_function_ref(hinputs, hamuts, current_function_header, prototype2);
        let call_result_node = ExpressionH::ExternCallH(self.interner.alloc(ExternCallH { function: function_ref_h.prototype, args_expressions: self.interner.bump().alloc_slice_fill_iter(args_he.into_iter()) }));
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, call_result_node, args_deferreds)
    }

    pub fn translate_function_pointer_call(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        function: &'i PrototypeI<'s, 'i, cI>,
        args: &[ExpressionIE<'s, 'i, cI>],
        result_type2: CoordI<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let return_type2 = function.return_type;
        let param_types = function.param_types();
        let (args_he, args_deferreds) = self.translate_expressions_until_never(hinputs, hamuts, current_function_header, locals, args);
        // Don't evaluate anything that can't ever be run, see BRCOBS
        match args_he.last().map(|e| e.result_type().kind) {
            Some(KindHT::NeverHT(_)) => {
                return consecrash(self.interner, locals, &args_he);
            }
            _ => {}
        }
        let prototype_h = self.translate_prototype(hinputs, hamuts, function);
        let param_types_h = self.translate_coords(hinputs, hamuts, &param_types);
        assert!(args_he.iter().map(|e| e.result_type()).collect::<Vec<_>>() == param_types_h);
        let return_type_h = self.translate_coord(hinputs, hamuts, return_type2);
        let result_type_h = self.translate_coord(hinputs, hamuts, result_type2);
        assert!(return_type_h == result_type_h);
        let call_result_node = ExpressionH::CallH(self.interner.alloc(CallH { function: prototype_h, args_expressions: self.interner.bump().alloc_slice_fill_iter(args_he.into_iter()) }));
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, call_result_node, args_deferreds)
    }

    pub fn translate_new_mut_runtime_sized_array(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        construct_array2: &NewMutRuntimeSizedArrayIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let array_type2 = construct_array2.array_type;
        let capacity_expr2 = construct_array2.capacity_expr;
        let (capacity_register_id, capacity_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(capacity_expr2));
        let array_ref_type_h = self.translate_coord(hinputs, hamuts, construct_array2.result);
        let array_type_h = self.translate_runtime_sized_array(hinputs, hamuts, self.instantiating_interner.intern_runtime_sized_array_it_ci(RuntimeSizedArrayITValI { name: array_type2.name }));
        assert!(array_ref_type_h.expect_runtime_sized_array_coord().kind == KindHT::RuntimeSizedArrayHT(array_type_h));
        let element_type = hamuts.get_runtime_sized_array(array_type_h).element_type;
        let construct_array_call_node = ExpressionH::NewMutRuntimeSizedArrayH(self.interner.alloc(NewMutRuntimeSizedArrayH {
            capacity_expression: capacity_register_id.expect_int_access(),
            element_type,
            result_type: array_ref_type_h.expect_runtime_sized_array_coord(),
        }));
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, construct_array_call_node, capacity_deferreds)
    }

    pub fn translate_new_imm_runtime_sized_array(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        construct_array2: &'i NewImmRuntimeSizedArrayIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let NewImmRuntimeSizedArrayIE { array_type: array_type2, size_expr: size_expr2, generator: generator_expr2, generator_method, result: _ } = construct_array2;
        let (size_register_id, size_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(*size_expr2));
        let (generator_register_id, generator_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(*generator_expr2));
        let array_ref_type_h =
            self.translate_coord(hinputs, hamuts, construct_array2.result);
        let array_type_h =
            self.translate_runtime_sized_array(hinputs, hamuts, array_type2);
        assert!(array_ref_type_h.expect_runtime_sized_array_coord().kind == KindHT::RuntimeSizedArrayHT(array_type_h));
        let element_type = hamuts.get_runtime_sized_array(array_type_h).element_type;
        let generator_method_h =
            self.translate_prototype(hinputs, hamuts, generator_method);
        let construct_array_call_node = ExpressionH::NewImmRuntimeSizedArrayH(self.interner.alloc(NewImmRuntimeSizedArrayH {
            size_expression: size_register_id.expect_int_access(),
            generator_expression: generator_register_id,
            generator_method: generator_method_h,
            element_type,
            result_type: array_ref_type_h.expect_runtime_sized_array_coord(),
        }));
        let mut deferreds: Vec<ExpressionIE<'s, 'i, cI>> = generator_deferreds;
        deferreds.extend(size_deferreds);
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, construct_array_call_node, deferreds)
    }

    pub fn translate_static_array_from_callable(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        expr_ie: &'i StaticArrayFromCallableIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let StaticArrayFromCallableIE { array_type: array_type_2, generator: generator_expr_2, generator_method, result: _ } = *expr_ie;
        let (generator_register_id, generator_deferreds) =
            self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(generator_expr_2));
        let array_ref_type_h = self.translate_coord(hinputs, hamuts, expr_ie.result);
        let array_type_h = self.translate_static_sized_array(hinputs, hamuts, array_type_2);
        assert!(array_ref_type_h.expect_static_sized_array_coord().kind == KindHT::StaticSizedArrayHT(array_type_h));
        let element_type = hamuts.get_static_sized_array(array_type_h).element_type;
        let generator_method_h = self.translate_prototype(hinputs, hamuts, &expr_ie.generator_method);
        let construct_array_call_node = ExpressionH::StaticArrayFromCallableH(self.interner.alloc(StaticArrayFromCallableH {
            generator_expression: generator_register_id,
            generator_method: generator_method_h,
            element_type,
            result_type: array_ref_type_h.expect_static_sized_array_coord(),
        }));
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, construct_array_call_node, generator_deferreds)
    }

    pub fn translate_destroy_static_sized_array(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        das2: &'i DestroyStaticSizedArrayIntoFunctionIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let DestroyStaticSizedArrayIntoFunctionIE { array_expr: array_expr_2, array_type: static_sized_array_type, consumer: consumer_expr_2, consumer_method: _consumer_method_2 } = *das2;
        let array_type_h = self.translate_static_sized_array(hinputs, hamuts, static_sized_array_type);
        let array_ref_type_h = self.translate_coord(hinputs, hamuts, ExpressionIE::Reference(array_expr_2).result());
        assert!(array_ref_type_h.expect_static_sized_array_coord().kind == KindHT::StaticSizedArrayHT(array_type_h));
        let (array_expr_result_he, array_expr_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(array_expr_2));
        let (consumer_callable_result_he, consumer_callable_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(consumer_expr_2));
        let static_sized_array_def = hamuts.get_static_sized_array(array_type_h);
        let consumer_method = self.translate_prototype(hinputs, hamuts, &das2.consumer_method);
        let destroy_static_sized_array_call_node = ExpressionH::DestroyStaticSizedArrayIntoFunctionH(self.interner.alloc(DestroyStaticSizedArrayIntoFunctionH {
            array_expression: array_expr_result_he.expect_static_sized_array_access(),
            consumer_expression: consumer_callable_result_he,
            consumer_method,
            array_element_type: static_sized_array_def.element_type,
            array_size: static_sized_array_def.size,
        }));
        let mut combined_deferreds = consumer_callable_deferreds;
        combined_deferreds.extend(array_expr_deferreds);
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, destroy_static_sized_array_call_node, combined_deferreds)
    }

    pub fn translate_destroy_imm_runtime_sized_array(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        das2: &DestroyImmRuntimeSizedArrayIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        panic!("Unimplemented: translate_destroy_imm_runtime_sized_array");
    }

    pub fn translate_if(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        parent_locals: &mut Locals<'s, 'i, 'h>,
        if2: &IfIE<'s, 'i, cI>,
    ) -> ExpressionH<'s, 'h>
    {
        let condition2 = if2.condition;
        let then_block2 = if2.then_call;
        let else_block2 = if2.else_call;
        let (condition_block_h, cond_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, parent_locals, ExpressionIE::Reference(condition2));
        assert!(cond_deferreds.is_empty());
        assert_eq!(condition_block_h.result_type(), CoordH { ownership: OwnershipH::MutableShareH, location: LocationH::InlineH, kind: KindHT::BoolHT(BoolHT) });
        let mut then_locals = parent_locals.snapshot();
        let (then_block_h, then_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, &mut then_locals, ExpressionIE::Reference(then_block2));
        assert!(then_deferreds.is_empty());
        let then_result_coord = then_block_h.result_type();
        parent_locals.set_next_local_id_number(then_locals.next_local_id_number);
        let mut else_locals = parent_locals.snapshot();
        let (else_block_h, else_deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, &mut else_locals, ExpressionIE::Reference(else_block2));
        assert!(else_deferreds.is_empty());
        let else_result_coord = else_block_h.result_type();
        parent_locals.set_next_local_id_number(else_locals.next_local_id_number);
        let common_supertype_h = self.translate_coord(hinputs, hamuts, if2.result);
        let if_call_node = ExpressionH::IfH(self.interner.alloc(IfH {
            condition_block: condition_block_h.expect_bool_access(),
            then_block: then_block_h,
            else_block: else_block_h,
            common_supertype: common_supertype_h,
        }));
        let then_continues = match then_result_coord.kind { KindHT::NeverHT(_) => false, _ => true };
        let else_continues = match else_result_coord.kind { KindHT::NeverHT(_) => false, _ => true };
        let unstackifies_of_parent_locals: HashSet<VariableIdH<'s, 'h>> =
            if then_continues && else_continues {
                let parent_locals_after_then: HashSet<_> = then_locals.locals.keys().copied().filter(|k| !then_locals.unstackified_vars.contains(k)).collect();
                let parent_locals_after_else: HashSet<_> = else_locals.locals.keys().copied().filter(|k| !else_locals.unstackified_vars.contains(k)).collect();
                if parent_locals_after_then != parent_locals_after_else {
                    panic!("Internal error: Mismatch in if branches' parent-unstackifies");
                }
                then_locals.unstackified_vars.iter().copied().collect()
            } else if then_continues {
                then_locals.unstackified_vars.iter().copied().collect()
            } else if else_continues {
                else_locals.unstackified_vars.iter().copied().collect()
            } else {
                HashSet::new()
            };
        let parent_locals_to_unstackify: Vec<_> = parent_locals.locals.keys().copied()
            .filter(|k| !parent_locals.unstackified_vars.contains(k))
            .filter(|k| unstackifies_of_parent_locals.contains(k))
            .collect();
        for var in parent_locals_to_unstackify {
            parent_locals.mark_unstackified(var);
        }
        if_call_node
    }

    pub fn translate_while(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        while2: &'i WhileIE<'s, 'i, cI>,
    ) -> &'h WhileH<'s, 'h>
    {
        let body_expr2 = &while2.block;
        let (expr_without_deferreds, deferreds) = self.translate_expression(hinputs, hamuts, current_function_header, locals, ExpressionIE::Reference(ReferenceExpressionIE::Block(body_expr2)));
        let expr = self.translate_deferreds(hinputs, hamuts, current_function_header, locals, expr_without_deferreds, deferreds);
        let while_call_node = self.interner.alloc(WhileH { body_block: expr });
        while_call_node
    }

    pub fn translate_interface_function_call(
        &self,
        hinputs: &HinputsI<'s, 'i>,
        hamuts: &mut Hamuts<'s, 'i, 'h>,
        current_function_header: &FunctionHeaderI<'s, 'i>,
        locals: &mut Locals<'s, 'i, 'h>,
        super_function_prototype: &'i PrototypeI<'s, 'i, cI>,
        virtual_param_index: i32,
        result_type2: CoordI<'s, 'i, cI>,
        args_exprs2: &[ExpressionIE<'s, 'i, cI>],
    ) -> ExpressionH<'s, 'h>
    {
        let (args_he, args_deferreds) = self.translate_expressions_until_never(hinputs, hamuts, current_function_header, locals, args_exprs2);
        if !args_he.is_empty() && matches!(args_he.last().unwrap().result_type().kind, KindHT::NeverHT(NeverHT { from_break: false, .. })) {
            return consecrash(self.interner, locals, &args_he);
        }
        let interface_it = match super_function_prototype.param_types()[virtual_param_index as usize].kind {
            KindIT::InterfaceIT(ii) => ii,
            _ => panic!("translate_interface_function_call: param.kind not InterfaceIT"),
        };
        let interface_ref_h = self.translate_interface(hinputs, hamuts, interface_it);
        let edge = hinputs.interface_to_edge_blueprints.get(&interface_it.id).expect("vassertSome interface_to_edge_blueprints");
        assert!(edge.interface == interface_it.id);
        let index_in_edge = edge.super_family_root_headers.iter().position(|(p, _)| p.to_signature() == super_function_prototype.to_signature()).expect("indexInEdge >= 0") as i32;
        let prototype_h = self.translate_prototype(hinputs, hamuts, super_function_prototype);
        let call_node = ExpressionH::InterfaceCallH(self.interner.alloc(InterfaceCallH {
            args_expressions: self.interner.bump().alloc_slice_copy(&args_he),
            virtual_param_index,
            interface_h: interface_ref_h,
            index_in_edge,
            function_type: prototype_h,
        }));
        self.translate_deferreds(hinputs, hamuts, current_function_header, locals, call_node, args_deferreds)
    }
}

