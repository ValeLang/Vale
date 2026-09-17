use std::cell::Cell;
use std::collections::HashMap;
use std::io::Write;
use std::marker::PhantomData;
use crate::interner::StrI;
use crate::final_ast::types::{KindHT, CoordH, LocationH, OwnershipH, InterfaceHT};
use crate::final_ast::ast::{ProgramH, PrototypeH, FunctionH};
use crate::final_ast::instructions::ExpressionH;
use crate::testvm::values::{
    AllocationIdV, AllocationV, CallIdV, ExpressionIdV, IObjectReferrerV,
    KindV, PrimitiveKindV, ReferenceV, RegisterV, VariableAddressV, VariableV,
};
use crate::testvm::heap::HeapV;
use crate::final_ast::instructions::ArgumentH;
use crate::final_ast::instructions::ArrayCapacityH;
use crate::final_ast::instructions::ArrayLengthH;
use crate::final_ast::instructions::AsSubtypeH;
use crate::final_ast::instructions::BorrowToWeakH;
use crate::final_ast::instructions::CallH;
use crate::final_ast::instructions::ConsecutorH;
use crate::final_ast::instructions::ConstantBoolH;
use crate::final_ast::instructions::ConstantF64H;
use crate::final_ast::instructions::ConstantIntH;
use crate::final_ast::instructions::ConstantStrH;
use crate::final_ast::instructions::DestroyH;
use crate::final_ast::instructions::DestroyMutRuntimeSizedArrayH;
use crate::final_ast::instructions::DestroyStaticSizedArrayIntoFunctionH;
use crate::final_ast::instructions::DestroyStaticSizedArrayIntoLocalsH;
use crate::final_ast::instructions::ExternCallH;
use crate::final_ast::instructions::IfH;
use crate::final_ast::instructions::InterfaceCallH;
use crate::final_ast::instructions::IsSameInstanceH;
use crate::final_ast::instructions::LocalLoadH;
use crate::final_ast::instructions::LocalStoreH;
use crate::final_ast::instructions::LockWeakH;
use crate::final_ast::instructions::MemberLoadH;
use crate::final_ast::instructions::MemberStoreH;
use crate::final_ast::instructions::NewArrayFromValuesH;
use crate::final_ast::instructions::NewImmRuntimeSizedArrayH;
use crate::final_ast::instructions::NewMutRuntimeSizedArrayH;
use crate::final_ast::instructions::NewStructH;
use crate::final_ast::instructions::PopRuntimeSizedArrayH;
use crate::final_ast::instructions::PushRuntimeSizedArrayH;
use crate::final_ast::instructions::RestackifyH;
use crate::final_ast::instructions::RuntimeSizedArrayLoadH;
use crate::final_ast::instructions::RuntimeSizedArrayStoreH;
use crate::final_ast::instructions::StackifyH;
use crate::final_ast::instructions::StaticArrayFromCallableH;
use crate::final_ast::instructions::StaticSizedArrayLoadH;
use crate::final_ast::instructions::StructToInterfaceUpcastH;
use crate::final_ast::instructions::UnstackifyH;
use crate::final_ast::instructions::WhileH;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::testvm::function_vivem::execute_function;
use crate::testvm::function_vivem::get_extern_function;
use crate::testvm::heap::AdapterForExternsV;
use crate::testvm::heap::get_var_address;
use crate::testvm::values::BoolV;
use crate::testvm::values::ElementAddressV;
use crate::testvm::values::FloatV;
use crate::testvm::values::IntV;
use crate::testvm::values::MemberAddressV;
use crate::testvm::values::RRKindV;
use crate::testvm::values::RegisterToObjectReferrerV;
use crate::testvm::values::StrV;
use crate::testvm::vivem::VmRuntimeErrorV;
use std::mem::discriminant;

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy)]
pub enum INodeExecuteResultV<'v, 'h, 's> {
  Continue(NodeContinueV<'v, 'h, 's>),
  Return(NodeReturnV<'v, 'h, 's>),
  Break(NodeBreakV<'v, 'h, 's>),
  // (no scala counterpart — Rust adaptation: error path bubbled in-band instead of Scala's throw.)
  Error(VmRuntimeErrorV<'s>),
}

/// Temporary state
#[derive(PartialEq, Eq, Hash, Clone, Copy)]
pub struct NodeContinueV<'v, 'h, 's> {
  pub result_ref: ReferenceV<'v, 'h, 's>,
}

/// Temporary state
#[derive(PartialEq, Eq, Hash, Clone, Copy)]
pub struct NodeReturnV<'v, 'h, 's> {
  pub return_ref: ReferenceV<'v, 'h, 's>,
}

/// Temporary state
#[derive(PartialEq, Eq, Hash, Clone, Copy)]
pub struct NodeBreakV<'v, 'h, 's>
where 's: 'h, 'h: 'v,
{
  pub _phantom: PhantomData<(&'v (), &'h (), &'s ())>,
}

pub fn make_primitive<'v, 'h, 's>(heap: &mut HeapV<'v, 'h, 's>, interner: &HammerInterner<'s, 'h>, call_id: CallIdV<'v, 'h, 's>, location: LocationH, kind: KindV<'v, 'h, 's>) -> ReferenceV<'v, 'h, 's> {
    assert!(!matches!(kind, KindV::Void(_)));
    let r#ref = heap.allocate_transient(interner, OwnershipH::MutableShareH, location, kind);
    heap.increment_reference_ref_count(
        IObjectReferrerV::RegisterToObjectReferrer(
            RegisterToObjectReferrerV { call_id, ownership: OwnershipH::MutableShareH }
        ),
        r#ref,
    );
    r#ref
}

pub fn take_argument<'v, 'h, 's>(heap: &mut HeapV<'v, 'h, 's>, interner: &HammerInterner<'s, 'h>, call_id: CallIdV<'v, 'h, 's>, argument_index: i32, result_type: CoordH<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
    let r#ref = heap.take_argument(interner, call_id, argument_index, result_type);
    heap.increment_reference_ref_count(
        IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: result_type.ownership }),
        r#ref);
    r#ref
}

pub fn possess_callee_return<'v, 'h, 's>(heap: &mut HeapV<'v, 'h, 's>, call_id: CallIdV<'v, 'h, 's>, callee_call_id: CallIdV<'v, 'h, 's>, result: &NodeReturnV<'v, 'h, 's>) -> ReferenceV<'v, 'h, 's> {
    heap.decrement_reference_ref_count(
        IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id: callee_call_id, ownership: result.return_ref.ownership }),
        result.return_ref,
    );
    heap.increment_reference_ref_count(
        IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: result.return_ref.ownership }),
        result.return_ref,
    );
    result.return_ref
}

pub fn upcast<'v, 'h, 's>(source_reference: ReferenceV<'v, 'h, 's>, target_interface_ref: &'h InterfaceHT<'s, 'h>) -> ReferenceV<'v, 'h, 's> {
    ReferenceV {
        actual_kind: source_reference.actual_kind,
        seen_as_kind: RRKindV { hamut: KindHT::InterfaceHT(target_interface_ref), _phantom: PhantomData },
        ownership: source_reference.ownership,
        location: source_reference.location,
        num: source_reference.num,
    }
}

pub fn execute_node<'v, 'h, 's>(program_h: &'h ProgramH<'s, 'h>, interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, stdin: &'v dyn Fn() -> StrI<'s>, stdout: &'v dyn Fn(StrI<'s>), heap: &mut HeapV<'v, 'h, 's>, expression_id: ExpressionIdV<'v, 'h, 's>, node: &ExpressionH<'s, 'h>) -> INodeExecuteResultV<'v, 'h, 's> {
    let node_name = match node {
        ExpressionH::ConstantVoidH(_) => "ConstantVoidH",
        ExpressionH::ConstantIntH(_) => "ConstantIntH",
        ExpressionH::ConstantBoolH(_) => "ConstantBoolH",
        ExpressionH::ConstantStrH(_) => "ConstantStrH",
        ExpressionH::ConstantF64H(_) => "ConstantF64H",
        ExpressionH::ArgumentH(_) => "ArgumentH",
        ExpressionH::StackifyH(_) => "StackifyH",
        ExpressionH::RestackifyH(_) => "RestackifyH",
        ExpressionH::UnstackifyH(_) => "UnstackifyH",
        ExpressionH::DestroyH(_) => "DestroyH",
        ExpressionH::DestroyStaticSizedArrayIntoLocalsH(_) => "DestroyStaticSizedArrayIntoLocalsH",
        ExpressionH::StructToInterfaceUpcastH(_) => "StructToInterfaceUpcastH",
        ExpressionH::InterfaceToInterfaceUpcastH(_) => "InterfaceToInterfaceUpcastH",
        ExpressionH::LocalStoreH(_) => "LocalStoreH",
        ExpressionH::LocalLoadH(_) => "LocalLoadH",
        ExpressionH::MemberStoreH(_) => "MemberStoreH",
        ExpressionH::MemberLoadH(_) => "MemberLoadH",
        ExpressionH::NewArrayFromValuesH(_) => "NewArrayFromValuesH",
        ExpressionH::StaticSizedArrayStoreH(_) => "StaticSizedArrayStoreH",
        ExpressionH::RuntimeSizedArrayStoreH(_) => "RuntimeSizedArrayStoreH",
        ExpressionH::RuntimeSizedArrayLoadH(_) => "RuntimeSizedArrayLoadH",
        ExpressionH::StaticSizedArrayLoadH(_) => "StaticSizedArrayLoadH",
        ExpressionH::CallH(_) => "CallH",
        ExpressionH::ExternCallH(_) => "ExternCallH",
        ExpressionH::InterfaceCallH(_) => "InterfaceCallH",
        ExpressionH::IfH(_) => "IfH",
        ExpressionH::WhileH(_) => "WhileH",
        ExpressionH::ConsecutorH(_) => "ConsecutorH",
        ExpressionH::BlockH(_) => "BlockH",
        ExpressionH::MutabilifyH(_) => "MutabilifyH",
        ExpressionH::ImmutabilifyH(_) => "ImmutabilifyH",
        ExpressionH::ReturnH(_) => "ReturnH",
        ExpressionH::NewImmRuntimeSizedArrayH(_) => "NewImmRuntimeSizedArrayH",
        ExpressionH::NewMutRuntimeSizedArrayH(_) => "NewMutRuntimeSizedArrayH",
        ExpressionH::PushRuntimeSizedArrayH(_) => "PushRuntimeSizedArrayH",
        ExpressionH::PopRuntimeSizedArrayH(_) => "PopRuntimeSizedArrayH",
        ExpressionH::StaticArrayFromCallableH(_) => "StaticArrayFromCallableH",
        ExpressionH::DestroyStaticSizedArrayIntoFunctionH(_) => "DestroyStaticSizedArrayIntoFunctionH",
        ExpressionH::DestroyImmRuntimeSizedArrayH(_) => "DestroyImmRuntimeSizedArrayH",
        ExpressionH::DestroyMutRuntimeSizedArrayH(_) => "DestroyMutRuntimeSizedArrayH",
        ExpressionH::BreakH(_) => "BreakH",
        ExpressionH::NewStructH(_) => "NewStructH",
        ExpressionH::ArrayLengthH(_) => "ArrayLengthH",
        ExpressionH::ArrayCapacityH(_) => "ArrayCapacityH",
        ExpressionH::BorrowToWeakH(_) => "BorrowToWeakH",
        ExpressionH::IsSameInstanceH(_) => "IsSameInstanceH",
        ExpressionH::AsSubtypeH(_) => "AsSubtypeH",
        ExpressionH::LockWeakH(_) => "LockWeakH",
        ExpressionH::DiscardH(_) => "DiscardH",
        ExpressionH::PreCheckBorrowH(_) => "PreCheckBorrowH",
    };
    {
        let handle = &mut *heap.vivem_dout;
        write!(handle, "<{}> ", node_name).unwrap();
    }
    let result = execute_node_inner(program_h, interner, scout_arena, stdin, stdout, heap, expression_id, node);
    {
        let handle = &mut *heap.vivem_dout;
        writeln!(handle, "</{}>", node_name).unwrap();
    }
    result
}

pub fn execute_node_inner<'v, 'h, 's>(program_h: &'h ProgramH<'s, 'h>, interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, stdin: &'v dyn Fn() -> StrI<'s>, stdout: &'v dyn Fn(StrI<'s>), heap: &mut HeapV<'v, 'h, 's>, expression_id: ExpressionIdV<'v, 'h, 's>, node: &ExpressionH<'s, 'h>) -> INodeExecuteResultV<'v, 'h, 's> {
    let call_id = expression_id.call_id;
    match node {
        ExpressionH::NewStructH(n) => {
            let NewStructH { source_expressions: args_exprs, target_member_names: _, result_type: struct_ref_h } = **n;
            let struct_kind_h = match struct_ref_h.kind {
                KindHT::StructHT(s) => s,
                _ => panic!("NewStructH: result_type not StructHT"),
            };
            let struct_def_h = program_h.lookup_struct(interner, struct_kind_h);
            let member_references_vec: Vec<ReferenceV<'v, 'h, 's>> = args_exprs.iter().enumerate().map(|(i, arg_expr)| {
                match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, i as i32), arg_expr) {
                    INodeExecuteResultV::Return(_) => {
                        // do we have to, like, discard the previously made arguments?
                        // what happens with those?
                        panic!("NewStructH arg produced Return — vimpl; return r");
                    }
                    INodeExecuteResultV::Break(_) => panic!("NewStructH arg produced Break — vwat"),
                    INodeExecuteResultV::Continue(c) => c.result_ref,
                    INodeExecuteResultV::Error(_) => panic!("NewStructH arg produced Error — vimpl (closure can't propagate)"),
                }
            }).collect();
            for r in &member_references_vec {
                heap.decrement_reference_ref_count(
                    IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: r.ownership }),
                    *r,
                );
            }
            assert_eq!(member_references_vec.len(), struct_def_h.members.len());
            let member_references: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&member_references_vec);
            let reference = heap.new_struct(interner, *struct_def_h, struct_ref_h, member_references);
            heap.increment_reference_ref_count(
                IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: reference.ownership }),
                reference,
            );
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: reference })
        }
        ExpressionH::ConstantIntH(c) => {
            let ConstantIntH { value, bits } = **c;
            let r#ref = make_primitive(heap, interner, call_id, LocationH::InlineH, KindV::Int(IntV { value, bits, _phantom: PhantomData }));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: r#ref })
        }
        ExpressionH::ReturnH(r) => {
            let source_expr = r.source_expression;
            let source_ref = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr) {
                ret @ INodeExecuteResultV::Return(_) => return ret,
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Break(_) => panic!("execute_node_inner: ReturnH source produced Break — vwat"),
                ret @ INodeExecuteResultV::Error(_) => return ret,
            };
            INodeExecuteResultV::Return(NodeReturnV { return_ref: source_ref })
        }
        ExpressionH::UnstackifyH(u) => {
            let UnstackifyH { local } = **u;
            let var_address = get_var_address(expression_id.call_id, local);
            let reference = heap.get_reference_from_local(interner, var_address, local.type_h, local.type_h);
            heap.increment_reference_ref_count(
                IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: reference.ownership }),
                reference,
            );
            {
                let handle = &mut *heap.vivem_dout;
                write!(handle, " ^{}", var_address).unwrap();
            }
            heap.remove_local(interner, var_address, local.type_h);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: reference })
        }
        ExpressionH::StackifyH(s) => {
            let StackifyH { source_expr, local, name: _ } = **s;
            let reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr) {
                ret @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return ret,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            let var_addr = get_var_address(expression_id.call_id, local);
            heap.add_local(interner, var_addr, reference, source_expr.result_type());
            {
                let handle = &mut *heap.vivem_dout;
                write!(handle, " v{}/{}<-o{}", var_addr.call_id.call_depth, var_addr.local.id.number, reference.num).unwrap();
            }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, source_expr.result_type(), reference) { return INodeExecuteResultV::Error(e); }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: heap.void() })
        }
        ExpressionH::BlockH(b) => {
            let source_expr = b.inner;
            execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr)
        }
        ExpressionH::ConsecutorH(c) => {
            let ConsecutorH { exprs: inner_exprs } = **c;
            let mut last_inner_expr_result_ref: Option<ReferenceV<'v, 'h, 's>> = None;
            for (i, inner_expr) in inner_exprs.iter().enumerate() {
                match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, i as i32), inner_expr) {
                    ret @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return ret,
                    INodeExecuteResultV::Continue(cc) => {
                        if i == inner_exprs.len() - 1 {
                            last_inner_expr_result_ref = Some(cc.result_ref);
                        }
                    }
                }
                {
                    let handle = &mut *heap.vivem_dout;
                    writeln!(handle).unwrap();
                }
            }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: last_inner_expr_result_ref.expect("ConsecutorH: empty innerExprs") })
        }
        ExpressionH::CallH(c) => {
            let CallH { function: prototype_h, args_expressions: args_exprs } = **c;
            let mut arg_refs: Vec<ReferenceV<'v, 'h, 's>> = Vec::new();
            for (i, arg_expr) in args_exprs.iter().enumerate() {
                let r = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, i as i32), arg_expr) {
                    ret @ (INodeExecuteResultV::Break(_) | INodeExecuteResultV::Return(_) | INodeExecuteResultV::Error(_)) => return ret,
                    INodeExecuteResultV::Continue(c) => c.result_ref,
                };
                arg_refs.push(r);
            }
            let function_h = program_h.lookup_function(prototype_h);
            {
                let handle = &mut *heap.vivem_dout;
                writeln!(handle).unwrap();
                writeln!(handle, "{}Making new stack frame (call)", "  ".repeat(expression_id.call_id.call_depth as usize)).unwrap();
            }
            for r in arg_refs.iter() {
                heap.decrement_reference_ref_count(
                    IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: r.ownership }),
                    *r);
            }
            let arg_refs_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&arg_refs);
            let (callee_call_id, retuurn) = match execute_function(program_h, interner, scout_arena, stdin, stdout, heap, arg_refs_slice, function_h) {
                Ok(t) => t,
                Err(e) => return INodeExecuteResultV::Error(e),
            };
            {
                let handle = &mut *heap.vivem_dout;
                write!(handle, "{}Getting return reference", "  ".repeat(expression_id.call_id.call_depth as usize)).unwrap();
            }
            let return_ref = possess_callee_return(heap, call_id, callee_call_id, &retuurn);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: return_ref })
        }
        ExpressionH::NewStructH(n) => {
            let NewStructH { source_expressions: args_exprs, target_member_names: _, result_type: struct_ref_h } = **n;
            let struct_def_h = program_h.lookup_struct(interner, match struct_ref_h.kind { KindHT::StructHT(s) => s, _ => panic!("NewStructH: result_type.kind not StructHT") });
            let mut member_references: Vec<ReferenceV<'v, 'h, 's>> = Vec::new();
            for (i, arg_expr) in args_exprs.iter().enumerate() {
                let r = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, i as i32), arg_expr) {
                    ret @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return ret,
                    INodeExecuteResultV::Continue(c) => c.result_ref,
                };
                member_references.push(r);
            }
            for r in member_references.iter() {
                heap.decrement_reference_ref_count(
                    IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: r.ownership }),
                    *r);
            }
            assert!(member_references.len() == struct_def_h.members.len());
            let member_references_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&member_references);
            let reference = heap.new_struct(interner, *struct_def_h, struct_ref_h, member_references_slice);
            heap.increment_reference_ref_count(
                IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: reference.ownership }),
                reference);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: reference })
        }
        ExpressionH::ArgumentH(a) => {
            let ArgumentH { result_type, argument_index } = **a;
            let r#ref = take_argument(heap, interner, call_id, argument_index, result_type);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: r#ref })
        }
        ExpressionH::ConstantVoidH(_) => {
            let r#ref = heap.void();
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: r#ref })
        }
        ExpressionH::ConstantBoolH(c) => {
            let ConstantBoolH { value } = **c;
            let r#ref = make_primitive(heap, interner, call_id, LocationH::InlineH, KindV::Bool(BoolV { value, _phantom: PhantomData }));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: r#ref })
        }
        ExpressionH::ConstantF64H(c) => {
            let ConstantF64H { value } = **c;
            let r#ref = make_primitive(heap, interner, call_id, LocationH::InlineH, KindV::Float(FloatV { value, _phantom: PhantomData }));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: r#ref })
        }
        ExpressionH::ConstantStrH(c) => {
            let ConstantStrH { value} = **c;
            let interned = scout_arena.intern_str(value);
            let r#ref = make_primitive(heap, interner, call_id, LocationH::YonderH, KindV::Str(StrV { value: interned, _phantom: PhantomData }));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: r#ref })
        }
        ExpressionH::DiscardH(d) => {
            let source_expr = d.source_expression;
            match source_expr.result_type().ownership {
                OwnershipH::MutableShareH => {}
                OwnershipH::ImmutableShareH => {}
                OwnershipH::ImmutableBorrowH => {}
                OwnershipH::MutableBorrowH => {}
                OwnershipH::WeakH => {}
                OwnershipH::OwnH => panic!("MatchError: OwnH"),
            }
            let source_ref = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            // Lots of instructions do this, not just Discard, see DINSIE.
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, source_expr.result_type(), source_ref) { return INodeExecuteResultV::Error(e); }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: heap.void() })
        }
        ExpressionH::LocalLoadH(ll_ref) => {
            let ll = **ll_ref;
            let LocalLoadH { local, target_ownership, local_name: _name } = ll;
            assert!(target_ownership != OwnershipH::OwnH); // should have been Unstackified instead
            let var_address = get_var_address(expression_id.call_id, local);
            let reference = heap.get_reference_from_local(interner, var_address, local.type_h, ExpressionH::LocalLoadH(ll_ref).result_type());
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: reference.ownership }), reference);
            {
                let handle = &mut *heap.vivem_dout;
                write!(handle, " *{}", var_address).unwrap();
            }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: reference })
        }
        ExpressionH::CallH(c) => {
            let CallH { function: prototype_h, args_expressions: args_exprs } = **c;
            let arg_refs: Vec<ReferenceV<'v, 'h, 's>> =
                args_exprs.iter().enumerate().map(|(i, arg_expr)| {
                    match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, i as i32), arg_expr) {
                        INodeExecuteResultV::Break(_) | INodeExecuteResultV::Return(_) => panic!("execute_node_inner: CallH arg produced Break/Return — vwat (BRCOBS)"),
                        INodeExecuteResultV::Error(_) => panic!("execute_node_inner: CallH arg produced Error — vimpl (closure can't propagate)"),
                        INodeExecuteResultV::Continue(c) => c.result_ref,
                    }
                }).collect();

            let function_h = program_h.lookup_function(prototype_h);
            {
                let handle = &mut *heap.vivem_dout;
                writeln!(handle).unwrap();
                let prefix = "  ".repeat(expression_id.call_id.call_depth as usize);
                writeln!(handle, "{}Making new stack frame (call)", prefix).unwrap();
            }

            for r in arg_refs.iter() {
                heap.decrement_reference_ref_count(
                    IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: r.ownership }),
                    *r,
                );
            }

            let arg_refs_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&arg_refs);
            let (callee_call_id, retuurn) = match execute_function(program_h, interner, scout_arena, stdin, stdout, heap, arg_refs_slice, function_h) {
                Ok(t) => t,
                Err(e) => return INodeExecuteResultV::Error(e),
            };
            {
                let handle = &mut *heap.vivem_dout;
                let prefix = "  ".repeat(expression_id.call_id.call_depth as usize);
                write!(handle, "{}Getting return reference", prefix).unwrap();
            }
            let return_ref = possess_callee_return(heap, call_id, callee_call_id, &retuurn);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: return_ref })
        }
        ExpressionH::LocalStoreH(s) => {
            let LocalStoreH { local: local_index, source_expression: source_expr, local_name: name } = **s;
            let reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            let var_address = get_var_address(expression_id.call_id, local_index);
            {
                let handle = &mut *heap.vivem_dout;
                write!(handle, " {}(\"{}\")", var_address, name).unwrap();
                write!(handle, "<-{}", reference.num).unwrap();
            }
            let old_ref = heap.mutate_variable(interner, var_address, reference, source_expr.result_type());
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, source_expr.result_type(), reference) { return INodeExecuteResultV::Error(e); }
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: old_ref.ownership }), old_ref);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: old_ref })
        }
        ExpressionH::DestroyH(d) => {
            let DestroyH { struct_expression: struct_expr, local_types, local_indices: locals } = **d;
            let struct_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &struct_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            heap.decrement_reference_ref_count(
                IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: struct_expr.result_type().ownership }),
                struct_reference);
            // DDSOT
            if let Err(e) = heap.ensure_ref_count(interner, scout_arena, struct_reference, Some(&[OwnershipH::OwnH, OwnershipH::MutableBorrowH, OwnershipH::ImmutableBorrowH]), 0) { return INodeExecuteResultV::Error(e); }
            let old_member_references = match heap.destructure(struct_reference) {
                Ok(r) => r,
                Err(e) => return INodeExecuteResultV::Error(e),
            };
            assert!(old_member_references.len() == locals.len());
            for ((member_ref, local_type), local_index) in old_member_references.iter().zip(local_types.iter()).zip(locals.iter()) {
                let var_addr = get_var_address(expression_id.call_id, *local_index);
                heap.add_local(interner, var_addr, *member_ref, *local_type);
                {
                    let handle = &mut *heap.vivem_dout;
                    write!(handle, " v{}<-o{}", var_addr, member_ref.num).unwrap();
                }
            }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: heap.void() })
        }
        ExpressionH::ExternCallH(e) => {
            let ExternCallH { function: prototype_h, args_expressions: args_exprs } = **e;
            let extern_function = get_extern_function(program_h, prototype_h);
            let arg_refs: Vec<ReferenceV<'v, 'h, 's>> =
                args_exprs.iter().enumerate().map(|(i, arg_expr)| {
                    match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, i as i32), arg_expr) {
                        INodeExecuteResultV::Break(_) | INodeExecuteResultV::Return(_) => panic!("execute_node_inner: ExternCallH arg produced Break/Return — vwat (BRCOBS)"),
                        INodeExecuteResultV::Error(_) => panic!("execute_node_inner: ExternCallH arg produced Error — vimpl (closure can't propagate)"),
                        INodeExecuteResultV::Continue(c) => c.result_ref,
                    }
                }).collect();
            let arg_refs_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&arg_refs);
            let result_ref = {
                let mut adapter = AdapterForExternsV {
                    program_h,
                    interner,
                    scout_arena,
                    heap: &mut *heap,
                    call_id: CallIdV { call_depth: expression_id.call_id.call_depth + 1, function: prototype_h, _phantom: PhantomData },
                    stdin,
                    stdout,
                };
                match extern_function(&mut adapter, arg_refs_slice) {
                    Ok(r) => r,
                    Err(e) => return INodeExecuteResultV::Error(e),
                }
            };
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: result_ref.ownership }), result_ref);
            // Special case for externs; externs arent allowed to change ref counts at all.
            // So, we just drop these normally.
            for (r, arg_expr) in arg_refs.iter().zip(args_exprs.iter()) {
                let expected_type = arg_expr.result_type();
                if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, expected_type, *r) { return INodeExecuteResultV::Error(e); }
            }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref })
        }
        ExpressionH::IfH(i) => {
            let IfH { condition_block, then_block, else_block, common_supertype: _ } = **i;
            let condition_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &condition_block) {
                ret @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return ret,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            let condition_kind = heap.dereference(condition_reference, false);
            let condition_value = match condition_kind {
                KindV::Bool(BoolV { value, .. }) => value,
                _ => panic!("execute_node_inner: IfH condition not BoolV"),
            };
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, condition_block.result_type(), condition_reference) { return INodeExecuteResultV::Error(e); }
            let block_result = if condition_value == true {
                match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 1), &then_block) {
                    ret @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return ret,
                    INodeExecuteResultV::Continue(c) => c.result_ref,
                }
            } else {
                match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 2), &else_block) {
                    ret @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return ret,
                    INodeExecuteResultV::Continue(c) => c.result_ref,
                }
            };
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: block_result })
        }
        ExpressionH::MemberStoreH(ms) => {
            let MemberStoreH { result_type: _result_type, struct_expression: struct_expr, member_index, source_expression: source_expr, member_name } = **ms;
            let struct_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &struct_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            let source_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 1), &source_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            let address = MemberAddressV { struct_id: struct_reference.alloc_id(), field_index: member_index };
            {
                let handle = &mut *heap.vivem_dout;
                write!(handle, " {}(\"{}\")", address.to_string(), member_name).unwrap();
                write!(handle, "<-{}", source_reference.num).unwrap();
            }
            let old_member_reference = heap.mutate_struct(address, source_reference, source_expr.result_type());
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: old_member_reference.ownership }), old_member_reference);
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, struct_expr.result_type(), struct_reference) { return INodeExecuteResultV::Error(e); }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, source_expr.result_type(), source_reference) { return INodeExecuteResultV::Error(e); }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: old_member_reference })
        }
        ExpressionH::MemberLoadH(ml) => {
            let MemberLoadH { struct_expression: struct_expr, member_index, expected_member_type, result_type, member_name: _ } = **ml;
            let struct_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &struct_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            let address = MemberAddressV { struct_id: struct_reference.alloc_id(), field_index: member_index };
            {
                let handle = &mut *heap.vivem_dout;
                write!(handle, " *{}", address.to_string()).unwrap();
            }
            let member_reference = heap.get_reference_from_struct(interner, address, expected_member_type, result_type);
            assert!(result_type.ownership != OwnershipH::OwnH);
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: member_reference.ownership }), member_reference);
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, struct_expr.result_type(), struct_reference) { return INodeExecuteResultV::Error(e); }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: member_reference })
        }
        ExpressionH::NewArrayFromValuesH(n) => {
            let NewArrayFromValuesH { result_type: array_ref_type, source_expressions: element_exprs } = **n;
            let element_refs: Vec<ReferenceV<'v, 'h, 's>> =
                element_exprs.iter().enumerate().map(|(i, arg_expr)| {
                    match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, i as i32), arg_expr) {
                        INodeExecuteResultV::Return(_) => panic!("execute_node_inner: NewArrayFromValuesH element produced Return — vimpl"),
                        INodeExecuteResultV::Break(_) => panic!("execute_node_inner: NewArrayFromValuesH element produced Break — vimpl"),
                        INodeExecuteResultV::Continue(c) => c.result_ref,
                        INodeExecuteResultV::Error(_) => panic!("execute_node_inner: NewArrayFromValuesH element produced Error — vimpl (closure can't propagate)"),
                    }
                }).collect();
            for r in element_refs.iter() {
                heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: r.ownership }), *r);
            }
            let ssa_def = program_h.lookup_static_sized_array(array_ref_type.kind.expect_static_sized_array_ht());
            let element_refs_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&element_refs);
            let (array_reference, array_instance) = heap.add_array(interner, *ssa_def, array_ref_type, element_refs_slice);
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            write!(heap.vivem_dout, " o{}=", array_reference.num).unwrap();
            heap.print_kind(KindV::ArrayInstance(array_instance));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: array_reference })
        }
        ExpressionH::StaticSizedArrayLoadH(ssal) => {
            let StaticSizedArrayLoadH { array_expression: array_expr, index_expression: index_expr, target_ownership, expected_element_type, array_size: _, result_type } = **ssal;
            let array_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &array_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let index_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 1), &index_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let index = match heap.dereference(index_reference, false) {
                KindV::Int(int_v) if int_v.bits == 32 => int_v.value as i32,
                _ => panic!("execute_node_inner: StaticSizedArrayLoadH index not IntV(_, 32)"),
            };
            let address = ElementAddressV { array_id: array_reference.alloc_id(), element_index: index as i64 };
            write!(heap.vivem_dout, " **o:{}.{}", address.array_id.num, address.element_index).unwrap();
            let source = heap.get_reference_from_array(interner, address, expected_element_type, result_type);
            if target_ownership == OwnershipH::OwnH {
                panic!("impl me?");
            } else {
            }
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: source.ownership }), source);
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, index_expr.result_type(), index_reference) { return INodeExecuteResultV::Error(e); }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, array_expr.result_type(), array_reference) { return INodeExecuteResultV::Error(e); }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: source })
        }
        ExpressionH::DestroyStaticSizedArrayIntoLocalsH(d) => {
            let DestroyStaticSizedArrayIntoLocalsH { struct_expression: arr_expr, local_types, local_indices: locals } = **d;
            let arr_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &arr_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: arr_reference.ownership }), arr_reference);
            if arr_expr.result_type().ownership == OwnershipH::OwnH {
                if let Err(e) = heap.ensure_ref_count(interner, scout_arena, arr_reference, None, 0) { return INodeExecuteResultV::Error(e); }
            } else {
                // Not doing
                //   heap.ensureTotalRefCount(arrReference, 0)
                // for share because we might be taking in a shared reference and not be destroying it.
            }
            let old_member_references = heap.destructure_array(arr_reference);
            if arr_reference.ownership == OwnershipH::OwnH {
                heap.zero(arr_reference);
                if let Err(e) = heap.deallocate_if_no_weak_refs(arr_reference) {
                    return INodeExecuteResultV::Error(e);
                }
            }
            assert!(old_member_references.len() == locals.len());
            for ((member_ref, local_type), local_index) in old_member_references.iter().zip(local_types.iter()).zip(locals.iter()) {
                let var_addr = get_var_address(expression_id.call_id, *local_index);
                heap.add_local(interner, var_addr, *member_ref, *local_type);
                write!(heap.vivem_dout, " v{}<-o{}", var_addr, member_ref.num).unwrap();
            }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: heap.void() })
        }
        ExpressionH::DestroyStaticSizedArrayIntoFunctionH(d) => {
            let DestroyStaticSizedArrayIntoFunctionH { array_expression: array_expr, consumer_expression: consumer_me, consumer_method, array_element_type: _, array_size: _ } = **d;
            let array_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &array_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let consumer_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 1), &consumer_me) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            heap.check_reference(interner, consumer_me.result_type(), consumer_reference);
            heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            if let Err(e) = heap.ensure_ref_count(interner, scout_arena, array_reference, None, 0) { return INodeExecuteResultV::Error(e); }
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            let ssa_def_m = program_h.lookup_static_sized_array(array_expr.result_type().kind.expect_static_sized_array_ht());
            if let Err(e) = consume_elements(program_h, interner, scout_arena, stdin, stdout, heap, expression_id, call_id, array_reference, consumer_reference, *consumer_method, ssa_def_m.size, &mut |_, _| {}) {
                return INodeExecuteResultV::Error(e);
            }
            heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            heap.zero(array_reference);
            if let Err(e) = heap.deallocate_if_no_weak_refs(array_reference) {
                return INodeExecuteResultV::Error(e);
            }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, consumer_me.result_type(), consumer_reference) { return INodeExecuteResultV::Error(e); }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: heap.void() })
        }
        ExpressionH::BreakH(_) => {
            return INodeExecuteResultV::Break(NodeBreakV { _phantom: PhantomData });
        }
        ExpressionH::DestroyMutRuntimeSizedArrayH(d) => {
            let DestroyMutRuntimeSizedArrayH { array_expression: array_expr } = **d;
            let array_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &array_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            if let Err(e) = heap.ensure_ref_count(interner, scout_arena, array_reference, None, 0) { return INodeExecuteResultV::Error(e); }
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            let elements = match heap.dereference(array_reference, false) {
                KindV::ArrayInstance(a) => a.elements.get(),
                _ => panic!("execute_node_inner: DestroyMutRuntimeSizedArrayH array deref not ArrayInstance"),
            };
            assert!(elements.is_empty());
            heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            heap.zero(array_reference);
            if let Err(e) = heap.deallocate_if_no_weak_refs(array_reference) {
                return INodeExecuteResultV::Error(e);
            }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: heap.void() })
        }
        ExpressionH::ArrayLengthH(al) => {
            let ArrayLengthH { source_expression: arr_expr } = **al;
            let array_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &arr_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let arr = match heap.dereference(array_reference, false) {
                KindV::ArrayInstance(a) => a,
                _ => panic!("execute_node_inner: ArrayLengthH array deref not ArrayInstance"),
            };
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, arr_expr.result_type(), array_reference) { return INodeExecuteResultV::Error(e); }
            let len_ref = make_primitive(heap, interner, call_id, LocationH::InlineH, KindV::Int(IntV { value: arr.get_size(), bits: 32, _phantom: PhantomData }));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: len_ref })
        }
        ExpressionH::ArrayCapacityH(ac) => {
            let ArrayCapacityH { source_expression: arr_expr } = **ac;
            let array_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &arr_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let arr = match heap.dereference(array_reference, false) {
                KindV::ArrayInstance(a) => a,
                _ => panic!("execute_node_inner: ArrayCapacityH array deref not ArrayInstance"),
            };
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, arr_expr.result_type(), array_reference) { return INodeExecuteResultV::Error(e); }
            let len_ref = make_primitive(heap, interner, call_id, LocationH::InlineH, KindV::Int(IntV { value: arr.capacity as i64, bits: 32, _phantom: PhantomData }));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: len_ref })
        }
        ExpressionH::WhileH(w) => {
            let WhileH { body_block } = **w;
            let mut r#continue = true;
            while r#continue {
                match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &body_block) {
                    INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                    INodeExecuteResultV::Break(_) => r#continue = false,
                    INodeExecuteResultV::Continue(c) => {
                        if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, body_block.result_type(), c.result_ref) { return INodeExecuteResultV::Error(e); }
                    }
                    INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
                }
            }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: heap.void() })
        }
        ExpressionH::InterfaceCallH(ifc) => {
            let InterfaceCallH { args_expressions: args_exprs, virtual_param_index, interface_h: interface_ref_h, index_in_edge, function_type } = **ifc;
            let undeviewed_arg_references: Vec<ReferenceV<'v, 'h, 's>> = args_exprs.iter().enumerate().map(|(i, arg_expr)| {
                match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, i as i32), arg_expr) {
                    INodeExecuteResultV::Return(_) => panic!("InterfaceCallH arg produced Return — vimpl"),
                    INodeExecuteResultV::Break(_) => panic!("InterfaceCallH arg produced Break — vwat"),
                    INodeExecuteResultV::Continue(c) => c.result_ref,
                    INodeExecuteResultV::Error(_) => panic!("InterfaceCallH arg produced Error — vimpl (closure can't propagate)"),
                }
            }).collect();
            {
                let handle = &mut *heap.vivem_dout;
                writeln!(handle).unwrap();
                writeln!(handle, "{}Making new stack frame (icall)", "  ".repeat(call_id.call_depth as usize)).unwrap();
            }
            for r in &undeviewed_arg_references {
                heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: r.ownership }), *r);
            }
            let (_function_h, (callee_call_id, retuurn)) = match execute_interface_function(program_h, interner, scout_arena, stdin, stdout, heap, heap.vivem_bump.alloc_slice_copy(&undeviewed_arg_references), virtual_param_index, *interface_ref_h, index_in_edge, *function_type) {
                Ok(t) => t,
                Err(e) => return INodeExecuteResultV::Error(e),
            };
            let return_ref = match retuurn {
                INodeExecuteResultV::Return(ref r) => possess_callee_return(heap, call_id, callee_call_id, r),
                _ => panic!("InterfaceCallH: callee did not return"),
            };
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: return_ref })
        }
        ExpressionH::AsSubtypeH(a) => {
            let AsSubtypeH { source_expression: source_expr, target_type: target_kind, result_type, some_constructor: ok_constructor, none_constructor: err_constructor } = **a;
            let source_ref = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            let (constructor, deviewed_args): (&'h PrototypeH<'s, 'h>, Vec<ReferenceV<'v, 'h, 's>>) =
                if source_ref.actual_kind.hamut == target_kind {
                    let ref_aliased_as_subtype = heap.transmute(source_ref, source_expr.result_type(), ok_constructor.params[0]);
                    (ok_constructor, vec![ref_aliased_as_subtype])
                } else {
                    (err_constructor, vec![source_ref])
                };
            {
                let handle = &mut *heap.vivem_dout;
                writeln!(handle).unwrap();
                writeln!(handle, "{}Making new stack frame (lock call)", "  ".repeat(expression_id.call_id.call_depth as usize)).unwrap();
            }
            let function = program_h.lookup_function(constructor);
            heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: source_ref.ownership }), source_ref);
            let args_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&deviewed_args);
            let (callee_call_id, retuurn) = match execute_function(program_h, interner, scout_arena, stdin, stdout, heap, args_slice, function) {
                Ok(t) => t,
                Err(e) => return INodeExecuteResultV::Error(e),
            };
            {
                let handle = &mut *heap.vivem_dout;
                write!(handle, "{}Getting return reference", "  ".repeat(expression_id.call_id.call_depth as usize)).unwrap();
            }
            let return_ref = possess_callee_return(heap, call_id, callee_call_id, &retuurn);
            let target_interface_ref = match result_type.kind {
                KindHT::InterfaceHT(i) => i,
                _ => panic!("AsSubtypeH: result_type.kind not InterfaceHT"),
            };
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: upcast(return_ref, target_interface_ref) })
        }
        ExpressionH::StructToInterfaceUpcastH(siu) => {
            let StructToInterfaceUpcastH { source_expression: source_expr, target_interface: target_interface_ref } = **siu;
            let source_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            let target_reference = upcast(source_reference, target_interface_ref);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: target_reference })
        }
        ExpressionH::RuntimeSizedArrayLoadH(rsal) => {
            let RuntimeSizedArrayLoadH { array_expression: array_expr, index_expression: index_expr, target_ownership, expected_element_type, result_type } = **rsal;
            let array_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &array_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let index_int_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 1), &index_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let index = match heap.dereference(index_int_reference, false) {
                KindV::Int(int_v) if int_v.bits == 32 => int_v.value as i32,
                _ => panic!("execute_node_inner: RuntimeSizedArrayLoadH index not IntV(_, 32)"),
            };
            let address = ElementAddressV { array_id: array_reference.alloc_id(), element_index: index as i64 };
            write!(heap.vivem_dout, " **o:{}.{}", address.array_id.num, address.element_index).unwrap();
            let source = heap.get_reference_from_array(interner, address, expected_element_type, result_type);
            if target_ownership == OwnershipH::OwnH {
                panic!("impl me?");
            } else {
            }
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: source.ownership }), source);
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, index_expr.result_type(), index_int_reference) { return INodeExecuteResultV::Error(e); }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, array_expr.result_type(), array_reference) { return INodeExecuteResultV::Error(e); }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: source })
        }
        ExpressionH::PopRuntimeSizedArrayH(p) => {
            let PopRuntimeSizedArrayH { array_expression: array_he, element_type: _ } = **p;
            let array_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &array_he) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let result_reference = heap.deinitialize_array_element(array_reference);
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: result_reference.ownership }), result_reference);
            let result_value = heap.dereference(result_reference, false);
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, array_he.result_type(), array_reference) { return INodeExecuteResultV::Error(e); }
            write!(heap.vivem_dout, " o{}-=", array_reference.num).unwrap();
            heap.print_kind(result_value);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: result_reference })
        }
        ExpressionH::PushRuntimeSizedArrayH(p) => {
            let PushRuntimeSizedArrayH { array_expression: array_he, newcomer_expression: newcomer_he } = **p;
            let array_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &array_he) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let rsa_def = program_h.lookup_runtime_sized_array(array_he.result_type().kind.expect_runtime_sized_array_ht());
            assert!(rsa_def.element_type == newcomer_he.result_type());
            match heap.dereference(array_reference, false) {
                KindV::ArrayInstance(_) => {}
                _ => panic!("execute_node_inner: PushRuntimeSizedArrayH array deref not ArrayInstance"),
            };
            let newcomer_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &newcomer_he) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let newcomer_ve = heap.dereference(newcomer_reference, false);
            heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: newcomer_reference.ownership }), newcomer_reference);
            heap.initialize_array_element(array_reference, newcomer_reference);
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, array_he.result_type(), array_reference) { return INodeExecuteResultV::Error(e); }
            write!(heap.vivem_dout, " o{}+=", array_reference.num).unwrap();
            heap.print_kind(newcomer_ve);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: heap.void() })
        }
        ExpressionH::NewMutRuntimeSizedArrayH(n) => {
            let NewMutRuntimeSizedArrayH { capacity_expression: capacity_he, element_type: _, result_type: array_ref_type } = **n;
            let capacity_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &capacity_he) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let capacity_value = heap.dereference(capacity_reference, false);
            let capacity = match capacity_value {
                KindV::Int(int_v) if int_v.bits == 32 => int_v.value as i32,
                _ => panic!("execute_node_inner: NewMutRuntimeSizedArrayH capacity not IntV(_, 32)"),
            };
            let rsa_def = program_h.lookup_runtime_sized_array(array_ref_type.kind.expect_runtime_sized_array_ht());
            let (array_reference, array_instance) = heap.add_uninitialized_array(interner, *rsa_def, array_ref_type, capacity);
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, capacity_he.result_type(), capacity_reference) { return INodeExecuteResultV::Error(e); }
            write!(heap.vivem_dout, " o{}=", array_reference.num).unwrap();
            heap.print_kind(KindV::ArrayInstance(&array_instance));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: array_reference })
        }
        ExpressionH::NewImmRuntimeSizedArrayH(cac) => {
            let NewImmRuntimeSizedArrayH { size_expression: size_expr, generator_expression: generator_expr, generator_method: generator_prototype, element_type: _, result_type: array_ref_type } = **cac;
            let size_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &size_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let size_kind = heap.dereference(size_reference, false);
            let size = match size_kind {
                KindV::Int(int_v) if int_v.bits == 32 => int_v.value,
                _ => panic!("execute_node_inner: NewImmRuntimeSizedArrayH size not IntV(_, 32)"),
            };
            let rsa_def = program_h.lookup_runtime_sized_array(array_ref_type.kind.expect_runtime_sized_array_ht());
            let (array_reference, array_instance) = heap.add_uninitialized_array(interner, *rsa_def, array_ref_type, size as i32);
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            let generator_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &generator_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(_) => panic!("execute_node_inner: NewImmRuntimeSizedArrayH generator produced Break — vwat"),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            if let Err(e) = generate_elements(program_h, interner, scout_arena, stdin, stdout, heap, expression_id, call_id, generator_reference, *generator_prototype, size, &mut |_i, element_ref, heap| {
                heap.initialize_array_element(array_reference, element_ref);
            }) { return INodeExecuteResultV::Error(e); }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, generator_expr.result_type(), generator_reference) { return INodeExecuteResultV::Error(e); }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, size_expr.result_type(), size_reference) { return INodeExecuteResultV::Error(e); }
            write!(heap.vivem_dout, " o{}=", array_reference.num).unwrap();
            heap.print_kind(KindV::ArrayInstance(&array_instance));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: array_reference })
        }
        ExpressionH::RuntimeSizedArrayStoreH(rsas) => {
            let RuntimeSizedArrayStoreH { array_expression: array_expr, index_expression: index_expr, source_expression: source_expr, result_type: _ } = **rsas;
            let array_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &array_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let index_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 1), &index_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let source_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 2), &source_expr) {
                INodeExecuteResultV::Return(r) => return INodeExecuteResultV::Return(r),
                INodeExecuteResultV::Break(b) => return INodeExecuteResultV::Break(b),
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Error(e) => return INodeExecuteResultV::Error(e),
            };
            let element_index = match heap.dereference(index_reference, false) {
                KindV::Int(int_v) if int_v.bits == 32 => int_v.value as i32,
                _ => panic!("execute_node_inner: RuntimeSizedArrayStoreH index not IntV(_, 32)"),
            };
            let address = ElementAddressV { array_id: array_reference.alloc_id(), element_index: element_index as i64 };
            write!(heap.vivem_dout, " *o:{}.{}", address.array_id.num, address.element_index).unwrap();
            write!(heap.vivem_dout, "<-{}", source_reference.num).unwrap();
            let old_member_reference = heap.mutate_array(address, source_reference, source_expr.result_type());
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: old_member_reference.ownership }), old_member_reference);
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, source_expr.result_type(), source_reference) { return INodeExecuteResultV::Error(e); }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, index_expr.result_type(), index_reference) { return INodeExecuteResultV::Error(e); }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, array_expr.result_type(), array_reference) { return INodeExecuteResultV::Error(e); }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: old_member_reference })
        }
        ExpressionH::IsSameInstanceH(isi) => {
            let IsSameInstanceH { left_expression: left_expr, right_expression: right_expr } = **isi;
            let left_ref = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &left_expr) {
                ret @ INodeExecuteResultV::Return(_) => return ret,
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Break(_) => panic!("execute_node_inner: IsSameInstanceH left produced Break — vwat"),
                ret @ INodeExecuteResultV::Error(_) => return ret,
            };
            let right_ref = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 1), &right_expr) {
                ret @ INodeExecuteResultV::Return(_) => return ret,
                INodeExecuteResultV::Continue(c) => c.result_ref,
                INodeExecuteResultV::Break(_) => panic!("execute_node_inner: IsSameInstanceH right produced Break — vwat"),
                ret @ INodeExecuteResultV::Error(_) => return ret,
            };
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, left_expr.result_type(), left_ref) { return INodeExecuteResultV::Error(e); }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, right_expr.result_type(), right_ref) { return INodeExecuteResultV::Error(e); }
            let r#ref = heap.is_same_instance(interner, call_id, left_ref, right_ref);
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: r#ref })
        }
        ExpressionH::StaticArrayFromCallableH(cac) => {
            let StaticArrayFromCallableH { generator_expression: generator_expr, generator_method: generator_prototype, element_type: _, result_type: array_ref_type } = **cac;
            let ssa_def = program_h.lookup_static_sized_array(array_ref_type.kind.expect_static_sized_array_ht());
            let generator_reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &generator_expr) {
                nr @ INodeExecuteResultV::Return(_) => return nr,
                INodeExecuteResultV::Break(_) => panic!("execute_node_inner: StaticArrayFromCallableH generator produced Break — vwat"),
                INodeExecuteResultV::Continue(v) => v.result_ref,
                nr @ INodeExecuteResultV::Error(_) => return nr,
            };
            let mut element_refs: Vec<ReferenceV<'v, 'h, 's>> = Vec::new();
            if let Err(e) = generate_elements(program_h, interner, scout_arena, stdin, stdout, heap, expression_id, call_id, generator_reference, *generator_prototype, ssa_def.size, &mut |_i, element_ref, _heap| {
                element_refs.push(element_ref);
            }) { return INodeExecuteResultV::Error(e); }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, generator_expr.result_type(), generator_reference) { return INodeExecuteResultV::Error(e); }
            let element_refs_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&element_refs);
            let (array_reference, array_instance) = heap.add_array(interner, *ssa_def, array_ref_type, element_refs_slice);
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: array_reference.ownership }), array_reference);
            write!(heap.vivem_dout, " o{}=", array_reference.num).unwrap();
            heap.print_kind(KindV::ArrayInstance(array_instance));
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: array_reference })
        }
        ExpressionH::RestackifyH(s) => {
            let RestackifyH { source_expr, local, name: _ } = **s;
            let reference = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr) {
                ret @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return ret,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            let var_addr = get_var_address(expression_id.call_id, local);
            heap.add_local(interner, var_addr, reference, source_expr.result_type());
            {
                let handle = &mut *heap.vivem_dout;
                write!(handle, " v{}/{}<-o{}", var_addr.call_id.call_depth, var_addr.local.id.number, reference.num).unwrap();
            }
            if let Err(e) = discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, source_expr.result_type(), reference) { return INodeExecuteResultV::Error(e); }
            INodeExecuteResultV::Continue(NodeContinueV { result_ref: heap.void() })
        }
        ExpressionH::BorrowToWeakH(wa_h) => {
            let BorrowToWeakH { ref_expression: source_expr } = **wa_h;
            let constraint_ref = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            assert!(constraint_ref.ownership == OwnershipH::MutableBorrowH || constraint_ref.ownership == OwnershipH::ImmutableBorrowH);

            let weak_ref = heap.transmute(constraint_ref, source_expr.result_type(), ExpressionH::BorrowToWeakH(wa_h).result_type());
            heap.increment_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: weak_ref.ownership }), weak_ref);
            discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, source_expr.result_type(), constraint_ref);

            INodeExecuteResultV::Continue(NodeContinueV { result_ref: weak_ref })
        }
        ExpressionH::LockWeakH(lw) => {
            let LockWeakH { source_expression: source_expr, result_type, some_constructor, none_constructor } = **lw;
            let weak_ref = match execute_node(program_h, interner, scout_arena, stdin, stdout, heap, expression_id.add_step(heap.vivem_bump, 0), &source_expr) {
                r @ (INodeExecuteResultV::Return(_) | INodeExecuteResultV::Break(_) | INodeExecuteResultV::Error(_)) => return r,
                INodeExecuteResultV::Continue(c) => c.result_ref,
            };
            assert!(weak_ref.ownership == OwnershipH::WeakH);

            if heap.contains_live_object(weak_ref) {
                let expected_ref = CoordH { ownership: OwnershipH::MutableBorrowH, location: LocationH::YonderH, kind: source_expr.result_type().kind };
                let constraint_ref = heap.transmute(weak_ref, source_expr.result_type(), expected_ref);
                {
                    let handle = &mut *heap.vivem_dout;
                    writeln!(handle).unwrap();
                    writeln!(handle, "{}Making new stack frame (lock call)", "  ".repeat(expression_id.call_id.call_depth as usize)).unwrap();
                }
                let function = program_h.lookup_function(some_constructor);
                heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: weak_ref.ownership }), weak_ref);
                let args_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&[constraint_ref]);
                let (callee_call_id, retuurn) = match execute_function(program_h, interner, scout_arena, stdin, stdout, heap, args_slice, function) { Ok(t) => t, Err(e) => return INodeExecuteResultV::Error(e), };
                {
                    let handle = &mut *heap.vivem_dout;
                    write!(handle, "{}Getting return reference", "  ".repeat(expression_id.call_id.call_depth as usize)).unwrap();
                }
                let return_ref = possess_callee_return(heap, call_id, callee_call_id, &retuurn);
                let target_interface_ref = match result_type.kind {
                    KindHT::InterfaceHT(i) => i,
                    _ => panic!("LockWeakH: result_type.kind not InterfaceHT"),
                };
                INodeExecuteResultV::Continue(NodeContinueV { result_ref: upcast(return_ref, target_interface_ref) })
            } else {
                discard(program_h, interner, scout_arena, heap, stdout, stdin, call_id, source_expr.result_type(), weak_ref);
                {
                    let handle = &mut *heap.vivem_dout;
                    writeln!(handle).unwrap();
                    writeln!(handle, "{}Making new stack frame (lock call)", "  ".repeat(expression_id.call_id.call_depth as usize)).unwrap();
                }
                let function = program_h.lookup_function(none_constructor);
                let args_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&[]);
                let (callee_call_id, retuurn) = match execute_function(program_h, interner, scout_arena, stdin, stdout, heap, args_slice, function) { Ok(t) => t, Err(e) => return INodeExecuteResultV::Error(e), };
                {
                    let handle = &mut *heap.vivem_dout;
                    write!(handle, "{}Getting return reference", "  ".repeat(expression_id.call_id.call_depth as usize)).unwrap();
                }
                let return_ref = possess_callee_return(heap, call_id, callee_call_id, &retuurn);
                let target_interface_ref = match result_type.kind {
                    KindHT::InterfaceHT(i) => i,
                    _ => panic!("LockWeakH: result_type.kind not InterfaceHT"),
                };
                INodeExecuteResultV::Continue(NodeContinueV { result_ref: upcast(return_ref, target_interface_ref) })
            }
        }
        other => panic!("execute_node_inner: unimplemented arm {:?}", discriminant(other)),
    }
}

pub fn consume_elements<'v, 'h, 's>(program_h: &'h ProgramH<'s, 'h>, interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, stdin: &'v dyn Fn() -> StrI<'s>, stdout: &'v dyn Fn(StrI<'s>), heap: &mut HeapV<'v, 'h, 's>, _expression_id: ExpressionIdV<'v, 'h, 's>, call_id: CallIdV<'v, 'h, 's>, array_reference: ReferenceV<'v, 'h, 's>, consumer_reference: ReferenceV<'v, 'h, 's>, consumer_prototype: PrototypeH<'s, 'h>, size: i64, receiver: &mut dyn FnMut(i64, ReferenceV<'v, 'h, 's>)) -> Result<(), VmRuntimeErrorV<'s>> {
    let consumer_function = program_h.lookup_function(&consumer_prototype);
    for i in (0..size).rev() {
        writeln!(heap.vivem_dout).unwrap();
        let prefix = "  ".repeat(call_id.call_depth as usize);
        writeln!(heap.vivem_dout, "{}Making new stack frame (consumer)", prefix).unwrap();
        writeln!(heap.vivem_dout).unwrap();
        let element_addr = ElementAddressV { array_id: array_reference.alloc_id(), element_index: i };
        write!(heap.vivem_dout, " *{}", element_addr.to_string()).unwrap();
        let element_reference = heap.deinitialize_array_element(array_reference);
        writeln!(heap.vivem_dout).unwrap();
        writeln!(heap.vivem_dout, "{}Making new stack frame (icall)", prefix).unwrap();
        let args_vec: Vec<ReferenceV<'v, 'h, 's>> = vec![consumer_reference, element_reference];
        let args_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&args_vec);
        let (callee_call_id, retuurn) = execute_function(program_h, interner, scout_arena, stdin, stdout, heap, args_slice, consumer_function)?;
        write!(heap.vivem_dout, "{}Getting return reference", prefix).unwrap();
        let return_ref = possess_callee_return(heap, call_id, callee_call_id, &retuurn);
        heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: return_ref.ownership }), return_ref);
        receiver(i, return_ref);
    }
    Ok(())
}

pub fn generate_elements<'v, 'h, 's>(program_h: &'h ProgramH<'s, 'h>, interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, stdin: &'v dyn Fn() -> StrI<'s>, stdout: &'v dyn Fn(StrI<'s>), heap: &mut HeapV<'v, 'h, 's>, _expression_id: ExpressionIdV<'v, 'h, 's>, call_id: CallIdV<'v, 'h, 's>, generator_reference: ReferenceV<'v, 'h, 's>, generator_prototype: PrototypeH<'s, 'h>, size: i64, receiver: &mut dyn FnMut(i64, ReferenceV<'v, 'h, 's>, &mut HeapV<'v, 'h, 's>)) -> Result<(), VmRuntimeErrorV<'s>> {
    // Rust borrow-checker adaptation: Scala's GC lets the callback freely capture `heap`
    // while `generateElements` also holds it. Rust requires that `heap` flow through the
    // callback as a parameter on each call. Architect-approved (δ) at change_mutability.
    let generator_function = program_h.lookup_function(&generator_prototype);
    for i in 0..size {
        {
            let handle = &mut *heap.vivem_dout;
            writeln!(handle).unwrap();
            let prefix = "  ".repeat(call_id.call_depth as usize);
            writeln!(handle, "{}Making new stack frame (generator)", prefix).unwrap();
        }
        let index_reference = heap.allocate_transient(interner, OwnershipH::MutableShareH, LocationH::InlineH, KindV::Int(IntV { value: i, bits: 32, _phantom: PhantomData }));
        {
            let handle = &mut *heap.vivem_dout;
            writeln!(handle).unwrap();
            writeln!(handle).unwrap();
            let prefix = "  ".repeat(call_id.call_depth as usize);
            writeln!(handle, "{}Making new stack frame (icall)", prefix).unwrap();
        }
        let args = heap.vivem_bump.alloc_slice_copy(&[generator_reference, index_reference]);
        let (callee_call_id, retuurn) = execute_function(program_h, interner, scout_arena, stdin, stdout, heap, args, generator_function)?;
        {
            let handle = &mut *heap.vivem_dout;
            let prefix = "  ".repeat(call_id.call_depth as usize);
            write!(handle, "{}Getting return reference", prefix).unwrap();
        }
        let return_ref = possess_callee_return(heap, call_id, callee_call_id, &retuurn);
        heap.decrement_reference_ref_count(IObjectReferrerV::RegisterToObjectReferrer(RegisterToObjectReferrerV { call_id, ownership: return_ref.ownership }), return_ref);
        receiver(i, return_ref, heap);
    }
    Ok(())
}

pub fn execute_interface_function<'v, 'h, 's>(program_h: &'h ProgramH<'s, 'h>, interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, stdin: &'v dyn Fn() -> StrI<'s>, stdout: &'v dyn Fn(StrI<'s>), heap: &mut HeapV<'v, 'h, 's>, undeviewed_arg_references: &'v [ReferenceV<'v, 'h, 's>], virtual_param_index: i32, interface_ref_h: InterfaceHT<'s, 'h>, index_in_edge: i32, function_type: PrototypeH<'s, 'h>) -> Result<(FunctionH<'s, 'h>, (CallIdV<'v, 'h, 's>, INodeExecuteResultV<'v, 'h, 's>)), VmRuntimeErrorV<'s>> {
    let interface_reference = undeviewed_arg_references[virtual_param_index as usize];
    let edge = match heap.dereference(interface_reference, true) {
        KindV::StructInstance(struct_h) => struct_h.struct_h.edges.iter().find(|e| e.interface == &interface_ref_h).expect("vassertSome edge"),
        _ => panic!("execute_interface_function: not a StructInstance"),
    };
    let ReferenceV { actual_kind: actual_struct, seen_as_kind: actual_interface_kind, ownership: actual_ownership, location: actual_location, num: alloc_num } = interface_reference;
    assert!(actual_interface_kind.hamut == KindHT::InterfaceHT(&interface_ref_h));
    let struct_reference = ReferenceV {
        actual_kind: actual_struct,
        seen_as_kind: actual_struct,
        ownership: actual_ownership,
        location: actual_location,
        num: alloc_num,
    };
    let prototype_h = *edge.struct_prototypes_by_interface_method.values().nth(index_in_edge as usize).expect("vassertSome prototypeH");
    let function_h = program_h.lookup_function(prototype_h);
    let actual_prototype = function_h.prototype;
    let expected_prototype = function_type;
    for (index, arg_reference) in undeviewed_arg_references.iter().enumerate() {
        if index as i32 != virtual_param_index {
            let actual_function_param_type = actual_prototype.params[index];
            let expected_function_param_type = expected_prototype.params[index];
            heap.check_reference(interner, actual_function_param_type, *arg_reference);
            heap.check_reference(interner, expected_function_param_type, *arg_reference);
            assert!(actual_function_param_type == expected_function_param_type);
        }
    }
    let mut deviewed_arg_references: Vec<ReferenceV<'v, 'h, 's>> = undeviewed_arg_references.to_vec();
    deviewed_arg_references[virtual_param_index as usize] = struct_reference;
    let deviewed_slice: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&deviewed_arg_references);
    let (callee_call_id, retuurn) = execute_function(program_h, interner, scout_arena, stdin, stdout, heap, deviewed_slice, function_h)?;
    Ok((*function_h, (callee_call_id, INodeExecuteResultV::Return(retuurn))))
}

pub fn discard<'v, 'h, 's>(program_h: &'h ProgramH<'s, 'h>, interner: &HammerInterner<'s, 'h>, scout_arena: &ScoutArena<'s>, heap: &mut HeapV<'v, 'h, 's>, stdout: &'v dyn Fn(StrI<'s>), stdin: &'v dyn Fn() -> StrI<'s>, call_id: CallIdV<'v, 'h, 's>, expected_reference: CoordH<'s, 'h>, actual_reference: ReferenceV<'v, 'h, 's>) -> Result<(), VmRuntimeErrorV<'s>> {
    heap.decrement_reference_ref_count(
        IObjectReferrerV::RegisterToObjectReferrer(
            RegisterToObjectReferrerV { call_id, ownership: actual_reference.ownership }
        ),
        actual_reference,
    );
    cleanup(program_h, interner, heap, stdout, stdin, call_id, expected_reference, actual_reference)
}

pub fn cleanup<'v, 'h, 's>(program_h: &ProgramH<'s, 'h>, interner: &HammerInterner<'s, 'h>, heap: &mut HeapV<'v, 'h, 's>, stdout: &dyn Fn(StrI<'s>), stdin: &dyn Fn() -> StrI<'s>, call_id: CallIdV<'v, 'h, 's>, expected_reference: CoordH<'s, 'h>, actual_reference: ReferenceV<'v, 'h, 's>) -> Result<(), VmRuntimeErrorV<'s>> {
    if heap.get_total_ref_count(actual_reference) == 0 {
        match expected_reference.ownership {
            OwnershipH::OwnH => {}
            OwnershipH::WeakH => {
                heap.deallocate_if_no_weak_refs(actual_reference);
            }
            OwnershipH::MutableBorrowH | OwnershipH::ImmutableBorrowH => {}
            OwnershipH::MutableShareH | OwnershipH::ImmutableShareH => {
                match expected_reference.kind {
                    KindHT::VoidHT(_) | KindHT::IntHT(_) | KindHT::BoolHT(_) | KindHT::StrHT(_) | KindHT::FloatHT(_) | KindHT::OpaqueHT(_) => {
                        heap.zero(actual_reference);
                        heap.deallocate_if_no_weak_refs(actual_reference)?;
                    }
                    KindHT::StructHT(sr) => {
                        let struct_def = program_h.lookup_struct(interner, sr);
                        let member_expected_types: Vec<CoordH<'s, 'h>> = struct_def.members.iter().map(|m| m.tyype).collect();
                        let member_refs = heap.destructure(actual_reference)?;
                        assert_eq!(member_expected_types.len(), member_refs.len());
                        for (member_ref, member_expected_type) in member_refs.iter().zip(member_expected_types.iter()) {
                            cleanup(program_h, interner, heap, stdout, stdin, call_id, *member_expected_type, *member_ref)?;
                        }
                    }
                    KindHT::InterfaceHT(_ir) => {
                        let actual_concrete_type = match actual_reference.actual_kind.hamut {
                            KindHT::StructHT(sr) => sr,
                            _ => panic!("cleanup: InterfaceHT actual_kind not StructHT"),
                        };
                        let struct_def = program_h.lookup_struct(interner, actual_concrete_type);
                        let member_expected_types: Vec<CoordH<'s, 'h>> = struct_def.members.iter().map(|m| m.tyype).collect();
                        let member_refs = heap.destructure(actual_reference)?;
                        assert_eq!(member_expected_types.len(), member_refs.len());
                        for (member_ref, member_expected_type) in member_refs.iter().zip(member_expected_types.iter()) {
                            cleanup(program_h, interner, heap, stdout, stdin, call_id, *member_expected_type, *member_ref)?;
                        }
                    }
                    KindHT::RuntimeSizedArrayHT(rsa_ht) => {
                        let element_refs = heap.destructure_array(actual_reference);
                        let element_type = program_h.lookup_runtime_sized_array(rsa_ht).element_type;
                        for element_ref in element_refs.iter() {
                            cleanup(program_h, interner, heap, stdout, stdin, call_id, element_type, *element_ref)?;
                        }
                        heap.zero(actual_reference);
                        heap.deallocate_if_no_weak_refs(actual_reference)?;
                    }
                    KindHT::StaticSizedArrayHT(ssa_ht) => {
                        let element_refs = heap.destructure_array(actual_reference);
                        let element_type = program_h.lookup_static_sized_array(ssa_ht).element_type;
                        for element_ref in element_refs.iter() {
                            cleanup(program_h, interner, heap, stdout, stdin, call_id, element_type, *element_ref)?;
                        }
                        heap.zero(actual_reference);
                        heap.deallocate_if_no_weak_refs(actual_reference)?;
                    }
                    KindHT::NeverHT(_) => panic!("cleanup: NeverHT — pilot doesn't exercise"),
                }
            }
        }
    }
    Ok(())
}

