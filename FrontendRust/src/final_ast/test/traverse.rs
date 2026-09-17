
// Test-only traversal helper for the final_ast (H-side) pass. Mirrors
// `src/typing/test/traverse.rs` and `src/postparsing/test/traverse.rs`.
//
// Scala uses `Collector.only` (in `Frontend/Utils/.../Collector.scala`) which walks
// arbitrary case-class trees via `Product.productIterator` runtime reflection. Rust
// can't replicate that ergonomically, so this file enumerates the final_ast with a
// `NodeRefH` enum, hand-written `visit_*` walkers, and `collect_only_*` /
// `collect_where_*` macros that compile a pattern down to predicate-based collection.
//
// No Scala counterpart — pure scaffolding (same as the typing/postparsing precedents).

use crate::final_ast::ast::{
    EdgeH, FunctionH, IdH, InterfaceDefinitionH, InterfaceMethodH, PackageH, ProgramH, PrototypeH,
    StructDefinitionH, StructMemberH,
};
use crate::final_ast::instructions::{
    ArgumentH, ArrayCapacityH, ArrayLengthH, AsSubtypeH, BlockH, BorrowToWeakH, BreakH, CallH,
    ConsecutorH, ConstantBoolH, ConstantF64H, ConstantIntH, ConstantStrH, ConstantVoidH, DestroyH,
    DestroyImmRuntimeSizedArrayH, DestroyMutRuntimeSizedArrayH, DestroyStaticSizedArrayIntoFunctionH,
    DestroyStaticSizedArrayIntoLocalsH, DiscardH, ExpressionH, ExternCallH, IExpressionH, IfH,
    ImmutabilifyH, InterfaceCallH, InterfaceToInterfaceUpcastH, IsSameInstanceH, Local, LocalLoadH,
    LocalStoreH, LockWeakH, MemberLoadH, MemberStoreH, MutabilifyH, NewArrayFromValuesH,
    NewImmRuntimeSizedArrayH, NewMutRuntimeSizedArrayH, NewStructH, PopRuntimeSizedArrayH,
    PreCheckBorrowH, PushRuntimeSizedArrayH, ReferenceExpressionH, AddressExpressionH, RestackifyH,
    ReturnH, RuntimeSizedArrayLoadH, RuntimeSizedArrayStoreH, StackifyH, StaticArrayFromCallableH,
    StaticSizedArrayLoadH, StaticSizedArrayStoreH, StructToInterfaceUpcastH, UnstackifyH,
    VariableIdH, WhileH,
};
use crate::final_ast::types::{
    CoordH, HamutsFunctionExtern, HamutsKindExtern, InterfaceHT, KindHT, OpaqueHT, RuntimeSizedArrayHT,
    RuntimeSizedArrayDefinitionHT, SimpleId, SimpleIdStep, StaticSizedArrayHT,
    StaticSizedArrayDefinitionHT, StructHT,
};

pub enum NodeRefH<'s, 'h>
where
    's: 'h,
{
    // ---- Top-level / definition nodes ----
    Program(&'h ProgramH<'s, 'h>),
    Package(&'h PackageH<'s, 'h>),
    Function(&'h FunctionH<'s, 'h>),
    Prototype(&'h PrototypeH<'s, 'h>),
    StructDefinition(&'h StructDefinitionH<'s, 'h>),
    StructMember(&'h StructMemberH<'s, 'h>),
    InterfaceDefinition(&'h InterfaceDefinitionH<'s, 'h>),
    InterfaceMethod(&'h InterfaceMethodH<'s, 'h>),
    Edge(&'h EdgeH<'s, 'h>),
    HamutsFunctionExtern(&'h HamutsFunctionExtern<'s, 'h>),
    HamutsKindExtern(&'h HamutsKindExtern<'s, 'h>),

    // ---- Expression hierarchy ----
    Expression(ExpressionH<'s, 'h>),
    IExpression(IExpressionH<'s, 'h>),
    ReferenceExpression(&'h ReferenceExpressionH<'s, 'h>),
    AddressExpression(&'h AddressExpressionH<'s, 'h>),

    // 50 expression variants
    ConstantVoidH(&'h ConstantVoidH),
    ConstantIntH(&'h ConstantIntH),
    ConstantBoolH(&'h ConstantBoolH),
    ConstantStrH(&'h ConstantStrH<'h>),
    ConstantF64H(&'h ConstantF64H),
    ArgumentH(&'h ArgumentH<'s, 'h>),
    StackifyH(&'h StackifyH<'s, 'h>),
    RestackifyH(&'h RestackifyH<'s, 'h>),
    UnstackifyH(&'h UnstackifyH<'s, 'h>),
    DestroyH(&'h DestroyH<'s, 'h>),
    DestroyStaticSizedArrayIntoLocalsH(&'h DestroyStaticSizedArrayIntoLocalsH<'s, 'h>),
    StructToInterfaceUpcastH(&'h StructToInterfaceUpcastH<'s, 'h>),
    InterfaceToInterfaceUpcastH(&'h InterfaceToInterfaceUpcastH<'s, 'h>),
    LocalStoreH(&'h LocalStoreH<'s, 'h>),
    LocalLoadH(&'h LocalLoadH<'s, 'h>),
    MemberStoreH(&'h MemberStoreH<'s, 'h>),
    MemberLoadH(&'h MemberLoadH<'s, 'h>),
    NewArrayFromValuesH(&'h NewArrayFromValuesH<'s, 'h>),
    StaticSizedArrayStoreH(&'h StaticSizedArrayStoreH<'s, 'h>),
    RuntimeSizedArrayStoreH(&'h RuntimeSizedArrayStoreH<'s, 'h>),
    RuntimeSizedArrayLoadH(&'h RuntimeSizedArrayLoadH<'s, 'h>),
    StaticSizedArrayLoadH(&'h StaticSizedArrayLoadH<'s, 'h>),
    CallH(&'h CallH<'s, 'h>),
    ExternCallH(&'h ExternCallH<'s, 'h>),
    InterfaceCallH(&'h InterfaceCallH<'s, 'h>),
    IfH(&'h IfH<'s, 'h>),
    WhileH(&'h WhileH<'s, 'h>),
    ConsecutorH(&'h ConsecutorH<'s, 'h>),
    BlockH(&'h BlockH<'s, 'h>),
    MutabilifyH(&'h MutabilifyH<'s, 'h>),
    ImmutabilifyH(&'h ImmutabilifyH<'s, 'h>),
    ReturnH(&'h ReturnH<'s, 'h>),
    NewImmRuntimeSizedArrayH(&'h NewImmRuntimeSizedArrayH<'s, 'h>),
    NewMutRuntimeSizedArrayH(&'h NewMutRuntimeSizedArrayH<'s, 'h>),
    PushRuntimeSizedArrayH(&'h PushRuntimeSizedArrayH<'s, 'h>),
    PopRuntimeSizedArrayH(&'h PopRuntimeSizedArrayH<'s, 'h>),
    StaticArrayFromCallableH(&'h StaticArrayFromCallableH<'s, 'h>),
    DestroyStaticSizedArrayIntoFunctionH(&'h DestroyStaticSizedArrayIntoFunctionH<'s, 'h>),
    DestroyImmRuntimeSizedArrayH(&'h DestroyImmRuntimeSizedArrayH<'s, 'h>),
    DestroyMutRuntimeSizedArrayH(&'h DestroyMutRuntimeSizedArrayH<'s, 'h>),
    BreakH(&'h BreakH),
    NewStructH(&'h NewStructH<'s, 'h>),
    ArrayLengthH(&'h ArrayLengthH<'s, 'h>),
    ArrayCapacityH(&'h ArrayCapacityH<'s, 'h>),
    BorrowToWeakH(&'h BorrowToWeakH<'s, 'h>),
    IsSameInstanceH(&'h IsSameInstanceH<'s, 'h>),
    AsSubtypeH(&'h AsSubtypeH<'s, 'h>),
    LockWeakH(&'h LockWeakH<'s, 'h>),
    DiscardH(&'h DiscardH<'s, 'h>),
    PreCheckBorrowH(&'h PreCheckBorrowH<'s, 'h>),

    // ---- Locals ----
    Local(&'h Local<'s, 'h>),
    VariableId(&'h VariableIdH<'s, 'h>),

    // ---- Types ----
    Coord(&'h CoordH<'s, 'h>),
    Kind(&'h KindHT<'s, 'h>),
    Opaque(&'h OpaqueHT<'s, 'h>),
    StructHT(&'h StructHT<'s, 'h>),
    InterfaceHT(&'h InterfaceHT<'s, 'h>),
    StaticSizedArrayHT(&'h StaticSizedArrayHT<'s, 'h>),
    RuntimeSizedArrayHT(&'h RuntimeSizedArrayHT<'s, 'h>),
    StaticSizedArrayDefinitionHT(&'h StaticSizedArrayDefinitionHT<'s, 'h>),
    RuntimeSizedArrayDefinitionHT(&'h RuntimeSizedArrayDefinitionHT<'s, 'h>),
    SimpleId(&'h SimpleId<'s, 'h>),
    SimpleIdStep(&'h SimpleIdStep<'s, 'h>),

    // ---- Names ----
    Id(&'h IdH<'s>),
}

fn collect_if<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, node: NodeRefH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    if let Some(v) = pred(node) {
        out.push(v);
    }
}

// ============================================================================
// Public entry points
// ============================================================================

pub fn collect_in_program<'s, 'h, T, F>(p: &'h ProgramH<'s, 'h>, predicate: &F) -> Vec<T>
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    let mut out = Vec::new();
    visit_program(predicate, &mut out, p);
    out
}

pub fn collect_in_package<'s, 'h, T, F>(p: &'h PackageH<'s, 'h>, predicate: &F) -> Vec<T>
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    let mut out = Vec::new();
    visit_package(predicate, &mut out, p);
    out
}

pub fn collect_in_function<'s, 'h, T, F>(f: &'h FunctionH<'s, 'h>, predicate: &F) -> Vec<T>
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    let mut out = Vec::new();
    visit_function(predicate, &mut out, f);
    out
}

pub fn collect_in_struct<'s, 'h, T, F>(s: &'h StructDefinitionH<'s, 'h>, predicate: &F) -> Vec<T>
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    let mut out = Vec::new();
    visit_struct_definition(predicate, &mut out, s);
    out
}

pub fn collect_in_interface<'s, 'h, T, F>(
    i: &'h InterfaceDefinitionH<'s, 'h>,
    predicate: &F,
) -> Vec<T>
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    let mut out = Vec::new();
    visit_interface_definition(predicate, &mut out, i);
    out
}

pub fn collect_in_expression<'s, 'h, T, F>(e: ExpressionH<'s, 'h>, predicate: &F) -> Vec<T>
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    let mut out = Vec::new();
    visit_expression(predicate, &mut out, e);
    out
}

pub fn collect_in_coord<'s, 'h, T, F>(c: &'h CoordH<'s, 'h>, predicate: &F) -> Vec<T>
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    let mut out = Vec::new();
    visit_coord(predicate, &mut out, c);
    out
}

pub fn collect_in_kind<'s, 'h, T, F>(k: &'h KindHT<'s, 'h>, predicate: &F) -> Vec<T>
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    let mut out = Vec::new();
    visit_kind(predicate, &mut out, k);
    out
}

// ============================================================================
// Top-level / definition visitors
// ============================================================================

fn visit_program<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, p: &'h ProgramH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Program(p));
    for pkg in p.packages.package_coord_to_contents.values() {
        visit_package(pred, out, pkg);
    }
}

fn visit_package<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, p: &'h PackageH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Package(p));
    for i in p.interfaces {
        visit_interface_definition(pred, out, i);
    }
    for s in p.structs {
        visit_struct_definition(pred, out, s);
    }
    for f in p.functions {
        visit_function(pred, out, f);
    }
    for ssa in p.static_sized_arrays {
        visit_static_sized_array_definition(pred, out, ssa);
    }
    for rsa in p.runtime_sized_arrays {
        visit_runtime_sized_array_definition(pred, out, rsa);
    }
    for (_name, proto) in p.export_name_to_function {
        visit_prototype(pred, out, proto);
    }
    for (_name, kind) in p.export_name_to_kind {
        visit_kind(pred, out, kind);
    }
    for (_proto, extern_) in p.prototype_to_extern {
        visit_hamuts_function_extern(pred, out, extern_);
    }
    for (_opaque, extern_) in p.kind_to_extern {
        visit_hamuts_kind_extern(pred, out, extern_);
    }
}

fn visit_function<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, f: &'h FunctionH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Function(f));
    visit_prototype(pred, out, f.prototype);
    visit_expression(pred, out, f.body);
}

fn visit_prototype<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, p: &'h PrototypeH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Prototype(p));
    visit_id(pred, out, p.id);
    for c in p.params {
        visit_coord(pred, out, c);
    }
    visit_coord(pred, out, &p.return_type);
}

fn visit_struct_definition<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, s: &'h StructDefinitionH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StructDefinition(s));
    visit_id(pred, out, s.id);
    for e in s.edges {
        visit_edge(pred, out, e);
    }
    for m in s.members {
        visit_struct_member(pred, out, m);
    }
}

fn visit_struct_member<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, m: &'h StructMemberH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StructMember(m));
    visit_id(pred, out, m.name);
    visit_coord(pred, out, &m.tyype);
}

fn visit_interface_definition<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    i: &'h InterfaceDefinitionH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::InterfaceDefinition(i));
    visit_id(pred, out, i.id);
    for si in i.super_interfaces {
        visit_interface_ht(pred, out, si);
    }
    for method in i.methods {
        visit_interface_method(pred, out, method);
    }
}

fn visit_interface_method<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, m: &'h InterfaceMethodH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::InterfaceMethod(m));
    visit_prototype(pred, out, m.prototype_h);
}

fn visit_edge<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, e: &'h EdgeH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Edge(e));
    visit_struct_ht(pred, out, e.struct_);
    visit_interface_ht(pred, out, e.interface);
    for (method, proto) in e.struct_prototypes_by_interface_method {
        visit_interface_method(pred, out, method);
        visit_prototype(pred, out, proto);
    }
}

fn visit_hamuts_function_extern<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    e: &'h HamutsFunctionExtern<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::HamutsFunctionExtern(e));
    visit_prototype(pred, out, e.prototype);
    visit_simple_id(pred, out, &e.simple_id);
}

fn visit_hamuts_kind_extern<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    e: &'h HamutsKindExtern<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::HamutsKindExtern(e));
    visit_kind(pred, out, &e.kind);
    visit_simple_id(pred, out, &e.simple_id);
}

// ============================================================================
// Expression hierarchy visitors
// ============================================================================

fn visit_expression<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, e: ExpressionH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Expression(e));
    match e {
        ExpressionH::ConstantVoidH(x) => visit_constant_void(pred, out, x),
        ExpressionH::ConstantIntH(x) => visit_constant_int(pred, out, x),
        ExpressionH::ConstantBoolH(x) => visit_constant_bool(pred, out, x),
        ExpressionH::ConstantStrH(x) => visit_constant_str(pred, out, x),
        ExpressionH::ConstantF64H(x) => visit_constant_f64(pred, out, x),
        ExpressionH::ArgumentH(x) => visit_argument(pred, out, x),
        ExpressionH::StackifyH(x) => visit_stackify(pred, out, x),
        ExpressionH::RestackifyH(x) => visit_restackify(pred, out, x),
        ExpressionH::UnstackifyH(x) => visit_unstackify(pred, out, x),
        ExpressionH::DestroyH(x) => visit_destroy(pred, out, x),
        ExpressionH::DestroyStaticSizedArrayIntoLocalsH(x) => {
            visit_destroy_static_sized_array_into_locals(pred, out, x)
        }
        ExpressionH::StructToInterfaceUpcastH(x) => {
            visit_struct_to_interface_upcast(pred, out, x)
        }
        ExpressionH::InterfaceToInterfaceUpcastH(x) => {
            visit_interface_to_interface_upcast(pred, out, x)
        }
        ExpressionH::LocalStoreH(x) => visit_local_store(pred, out, x),
        ExpressionH::LocalLoadH(x) => visit_local_load(pred, out, x),
        ExpressionH::MemberStoreH(x) => visit_member_store(pred, out, x),
        ExpressionH::MemberLoadH(x) => visit_member_load(pred, out, x),
        ExpressionH::NewArrayFromValuesH(x) => visit_new_array_from_values(pred, out, x),
        ExpressionH::StaticSizedArrayStoreH(x) => {
            visit_static_sized_array_store(pred, out, x)
        }
        ExpressionH::RuntimeSizedArrayStoreH(x) => {
            visit_runtime_sized_array_store(pred, out, x)
        }
        ExpressionH::RuntimeSizedArrayLoadH(x) => {
            visit_runtime_sized_array_load(pred, out, x)
        }
        ExpressionH::StaticSizedArrayLoadH(x) => visit_static_sized_array_load(pred, out, x),
        ExpressionH::CallH(x) => visit_call(pred, out, x),
        ExpressionH::ExternCallH(x) => visit_extern_call(pred, out, x),
        ExpressionH::InterfaceCallH(x) => visit_interface_call(pred, out, x),
        ExpressionH::IfH(x) => visit_if(pred, out, x),
        ExpressionH::WhileH(x) => visit_while(pred, out, x),
        ExpressionH::ConsecutorH(x) => visit_consecutor(pred, out, x),
        ExpressionH::BlockH(x) => visit_block(pred, out, x),
        ExpressionH::MutabilifyH(x) => visit_mutabilify(pred, out, x),
        ExpressionH::ImmutabilifyH(x) => visit_immutabilify(pred, out, x),
        ExpressionH::ReturnH(x) => visit_return(pred, out, x),
        ExpressionH::NewImmRuntimeSizedArrayH(x) => {
            visit_new_imm_runtime_sized_array(pred, out, x)
        }
        ExpressionH::NewMutRuntimeSizedArrayH(x) => {
            visit_new_mut_runtime_sized_array(pred, out, x)
        }
        ExpressionH::PushRuntimeSizedArrayH(x) => visit_push_runtime_sized_array(pred, out, x),
        ExpressionH::PopRuntimeSizedArrayH(x) => visit_pop_runtime_sized_array(pred, out, x),
        ExpressionH::StaticArrayFromCallableH(x) => {
            visit_static_array_from_callable(pred, out, x)
        }
        ExpressionH::DestroyStaticSizedArrayIntoFunctionH(x) => {
            visit_destroy_static_sized_array_into_function(pred, out, x)
        }
        ExpressionH::DestroyImmRuntimeSizedArrayH(x) => {
            visit_destroy_imm_runtime_sized_array(pred, out, x)
        }
        ExpressionH::DestroyMutRuntimeSizedArrayH(x) => {
            visit_destroy_mut_runtime_sized_array(pred, out, x)
        }
        ExpressionH::BreakH(x) => visit_break(pred, out, x),
        ExpressionH::NewStructH(x) => visit_new_struct(pred, out, x),
        ExpressionH::ArrayLengthH(x) => visit_array_length(pred, out, x),
        ExpressionH::ArrayCapacityH(x) => visit_array_capacity(pred, out, x),
        ExpressionH::BorrowToWeakH(x) => visit_borrow_to_weak(pred, out, x),
        ExpressionH::IsSameInstanceH(x) => visit_is_same_instance(pred, out, x),
        ExpressionH::AsSubtypeH(x) => visit_as_subtype(pred, out, x),
        ExpressionH::LockWeakH(x) => visit_lock_weak(pred, out, x),
        ExpressionH::DiscardH(x) => visit_discard(pred, out, x),
        ExpressionH::PreCheckBorrowH(x) => visit_pre_check_borrow(pred, out, x),
    }
}

fn visit_constant_void<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ConstantVoidH)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ConstantVoidH(x));
}

fn visit_constant_int<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ConstantIntH)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ConstantIntH(x));
}

fn visit_constant_bool<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ConstantBoolH)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ConstantBoolH(x));
}

fn visit_constant_str<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ConstantStrH<'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ConstantStrH(x));
}

fn visit_constant_f64<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ConstantF64H)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ConstantF64H(x));
}

fn visit_argument<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ArgumentH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ArgumentH(x));
    visit_coord(pred, out, &x.result_type);
}

fn visit_stackify<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h StackifyH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StackifyH(x));
    visit_expression(pred, out, x.source_expr);
    visit_local(pred, out, &x.local);
    if let Some(name) = x.name {
        visit_id(pred, out, name);
    }
}

fn visit_restackify<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h RestackifyH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::RestackifyH(x));
    visit_expression(pred, out, x.source_expr);
    visit_local(pred, out, &x.local);
    if let Some(name) = x.name {
        visit_id(pred, out, name);
    }
}

fn visit_unstackify<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h UnstackifyH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::UnstackifyH(x));
    visit_local(pred, out, &x.local);
}

fn visit_destroy<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h DestroyH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::DestroyH(x));
    visit_expression(pred, out, x.struct_expression);
    for c in x.local_types {
        visit_coord(pred, out, c);
    }
    for l in x.local_indices {
        visit_local(pred, out, l);
    }
}

fn visit_destroy_static_sized_array_into_locals<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h DestroyStaticSizedArrayIntoLocalsH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::DestroyStaticSizedArrayIntoLocalsH(x));
    visit_expression(pred, out, x.struct_expression);
    for c in x.local_types {
        visit_coord(pred, out, c);
    }
    for l in x.local_indices {
        visit_local(pred, out, l);
    }
}

fn visit_struct_to_interface_upcast<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h StructToInterfaceUpcastH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StructToInterfaceUpcastH(x));
    visit_expression(pred, out, x.source_expression);
    visit_interface_ht(pred, out, x.target_interface);
}

fn visit_interface_to_interface_upcast<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h InterfaceToInterfaceUpcastH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::InterfaceToInterfaceUpcastH(x));
    visit_expression(pred, out, x.source_expression);
    visit_interface_ht(pred, out, x.target_interface);
}

fn visit_local_store<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h LocalStoreH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::LocalStoreH(x));
    visit_local(pred, out, &x.local);
    visit_expression(pred, out, x.source_expression);
    visit_id(pred, out, x.local_name);
}

fn visit_local_load<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h LocalLoadH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::LocalLoadH(x));
    visit_local(pred, out, &x.local);
    visit_id(pred, out, x.local_name);
}

fn visit_member_store<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h MemberStoreH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::MemberStoreH(x));
    visit_coord(pred, out, &x.result_type);
    visit_expression(pred, out, x.struct_expression);
    visit_expression(pred, out, x.source_expression);
    visit_id(pred, out, x.member_name);
}

fn visit_member_load<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h MemberLoadH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::MemberLoadH(x));
    visit_expression(pred, out, x.struct_expression);
    visit_coord(pred, out, &x.expected_member_type);
    visit_coord(pred, out, &x.result_type);
    visit_id(pred, out, x.member_name);
}

fn visit_new_array_from_values<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h NewArrayFromValuesH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::NewArrayFromValuesH(x));
    visit_coord(pred, out, &x.result_type);
    for e in x.source_expressions {
        visit_expression(pred, out, *e);
    }
}

fn visit_static_sized_array_store<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h StaticSizedArrayStoreH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StaticSizedArrayStoreH(x));
    visit_expression(pred, out, x.array_expression);
    visit_expression(pred, out, x.index_expression);
    visit_expression(pred, out, x.source_expression);
    visit_coord(pred, out, &x.result_type);
}

fn visit_runtime_sized_array_store<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h RuntimeSizedArrayStoreH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::RuntimeSizedArrayStoreH(x));
    visit_expression(pred, out, x.array_expression);
    visit_expression(pred, out, x.index_expression);
    visit_expression(pred, out, x.source_expression);
    visit_coord(pred, out, &x.result_type);
}

fn visit_runtime_sized_array_load<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h RuntimeSizedArrayLoadH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::RuntimeSizedArrayLoadH(x));
    visit_expression(pred, out, x.array_expression);
    visit_expression(pred, out, x.index_expression);
    visit_coord(pred, out, &x.expected_element_type);
    visit_coord(pred, out, &x.result_type);
}

fn visit_static_sized_array_load<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h StaticSizedArrayLoadH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StaticSizedArrayLoadH(x));
    visit_expression(pred, out, x.array_expression);
    visit_expression(pred, out, x.index_expression);
    visit_coord(pred, out, &x.expected_element_type);
    visit_coord(pred, out, &x.result_type);
}

fn visit_call<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h CallH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::CallH(x));
    visit_prototype(pred, out, x.function);
    for e in x.args_expressions {
        visit_expression(pred, out, *e);
    }
}

fn visit_extern_call<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ExternCallH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ExternCallH(x));
    visit_prototype(pred, out, x.function);
    for e in x.args_expressions {
        visit_expression(pred, out, *e);
    }
}

fn visit_interface_call<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h InterfaceCallH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::InterfaceCallH(x));
    for e in x.args_expressions {
        visit_expression(pred, out, *e);
    }
    visit_interface_ht(pred, out, x.interface_h);
    visit_prototype(pred, out, x.function_type);
}

fn visit_if<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h IfH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::IfH(x));
    visit_expression(pred, out, x.condition_block);
    visit_expression(pred, out, x.then_block);
    visit_expression(pred, out, x.else_block);
    visit_coord(pred, out, &x.common_supertype);
}

fn visit_while<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h WhileH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::WhileH(x));
    visit_expression(pred, out, x.body_block);
}

fn visit_consecutor<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ConsecutorH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ConsecutorH(x));
    for e in x.exprs {
        visit_expression(pred, out, *e);
    }
}

fn visit_block<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h BlockH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::BlockH(x));
    visit_expression(pred, out, x.inner);
}

fn visit_mutabilify<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h MutabilifyH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::MutabilifyH(x));
    visit_expression(pred, out, x.inner);
}

fn visit_immutabilify<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ImmutabilifyH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ImmutabilifyH(x));
    visit_expression(pred, out, x.inner);
}

fn visit_return<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ReturnH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ReturnH(x));
    visit_expression(pred, out, x.source_expression);
}

fn visit_new_imm_runtime_sized_array<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h NewImmRuntimeSizedArrayH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::NewImmRuntimeSizedArrayH(x));
    visit_expression(pred, out, x.size_expression);
    visit_expression(pred, out, x.generator_expression);
    visit_prototype(pred, out, x.generator_method);
    visit_coord(pred, out, &x.element_type);
    visit_coord(pred, out, &x.result_type);
}

fn visit_new_mut_runtime_sized_array<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h NewMutRuntimeSizedArrayH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::NewMutRuntimeSizedArrayH(x));
    visit_expression(pred, out, x.capacity_expression);
    visit_coord(pred, out, &x.element_type);
    visit_coord(pred, out, &x.result_type);
}

fn visit_push_runtime_sized_array<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h PushRuntimeSizedArrayH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::PushRuntimeSizedArrayH(x));
    visit_expression(pred, out, x.array_expression);
    visit_expression(pred, out, x.newcomer_expression);
}

fn visit_pop_runtime_sized_array<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h PopRuntimeSizedArrayH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::PopRuntimeSizedArrayH(x));
    visit_expression(pred, out, x.array_expression);
    visit_coord(pred, out, &x.element_type);
}

fn visit_static_array_from_callable<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h StaticArrayFromCallableH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StaticArrayFromCallableH(x));
    visit_expression(pred, out, x.generator_expression);
    visit_prototype(pred, out, x.generator_method);
    visit_coord(pred, out, &x.element_type);
    visit_coord(pred, out, &x.result_type);
}

fn visit_destroy_static_sized_array_into_function<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h DestroyStaticSizedArrayIntoFunctionH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::DestroyStaticSizedArrayIntoFunctionH(x));
    visit_expression(pred, out, x.array_expression);
    visit_expression(pred, out, x.consumer_expression);
    visit_prototype(pred, out, x.consumer_method);
    visit_coord(pred, out, &x.array_element_type);
}

fn visit_destroy_imm_runtime_sized_array<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h DestroyImmRuntimeSizedArrayH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::DestroyImmRuntimeSizedArrayH(x));
    visit_expression(pred, out, x.array_expression);
    visit_expression(pred, out, x.consumer_expression);
    visit_prototype(pred, out, x.consumer_method);
    visit_coord(pred, out, &x.array_element_type);
}

fn visit_destroy_mut_runtime_sized_array<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    x: &'h DestroyMutRuntimeSizedArrayH<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::DestroyMutRuntimeSizedArrayH(x));
    visit_expression(pred, out, x.array_expression);
}

fn visit_break<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h BreakH)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::BreakH(x));
}

fn visit_new_struct<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h NewStructH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::NewStructH(x));
    for e in x.source_expressions {
        visit_expression(pred, out, *e);
    }
    for name in x.target_member_names {
        visit_id(pred, out, name);
    }
    visit_coord(pred, out, &x.result_type);
}

fn visit_array_length<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ArrayLengthH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ArrayLengthH(x));
    visit_expression(pred, out, x.source_expression);
}

fn visit_array_capacity<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h ArrayCapacityH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::ArrayCapacityH(x));
    visit_expression(pred, out, x.source_expression);
}

fn visit_borrow_to_weak<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h BorrowToWeakH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::BorrowToWeakH(x));
    visit_expression(pred, out, x.ref_expression);
}

fn visit_is_same_instance<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h IsSameInstanceH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::IsSameInstanceH(x));
    visit_expression(pred, out, x.left_expression);
    visit_expression(pred, out, x.right_expression);
}

fn visit_as_subtype<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h AsSubtypeH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::AsSubtypeH(x));
    visit_expression(pred, out, x.source_expression);
    visit_kind(pred, out, &x.target_type);
    visit_coord(pred, out, &x.result_type);
    visit_prototype(pred, out, x.some_constructor);
    visit_prototype(pred, out, x.none_constructor);
}

fn visit_lock_weak<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h LockWeakH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::LockWeakH(x));
    visit_expression(pred, out, x.source_expression);
    visit_coord(pred, out, &x.result_type);
    visit_prototype(pred, out, x.some_constructor);
    visit_prototype(pred, out, x.none_constructor);
}

fn visit_discard<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h DiscardH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::DiscardH(x));
    visit_expression(pred, out, x.source_expression);
}

fn visit_pre_check_borrow<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, x: &'h PreCheckBorrowH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::PreCheckBorrowH(x));
    visit_expression(pred, out, x.inner_expression);
}

// ============================================================================
// Locals
// ============================================================================

fn visit_local<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, l: &'h Local<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Local(l));
    visit_variable_id(pred, out, &l.id);
    visit_coord(pred, out, &l.type_h);
}

fn visit_variable_id<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, v: &'h VariableIdH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::VariableId(v));
    if let Some(name) = v.name {
        visit_id(pred, out, name);
    }
}

// ============================================================================
// Type visitors
// ============================================================================

fn visit_coord<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, c: &'h CoordH<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Coord(c));
    visit_kind(pred, out, &c.kind);
}

fn visit_kind<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, k: &'h KindHT<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Kind(k));
    match k {
        KindHT::IntHT(_) => {}
        KindHT::VoidHT(_) => {}
        KindHT::OpaqueHT(o) => visit_opaque(pred, out, o),
        KindHT::BoolHT(_) => {}
        KindHT::StrHT(_) => {}
        KindHT::FloatHT(_) => {}
        KindHT::NeverHT(_) => {}
        KindHT::InterfaceHT(i) => visit_interface_ht(pred, out, i),
        KindHT::StructHT(s) => visit_struct_ht(pred, out, s),
        KindHT::StaticSizedArrayHT(ssa) => visit_static_sized_array_ht(pred, out, ssa),
        KindHT::RuntimeSizedArrayHT(rsa) => visit_runtime_sized_array_ht(pred, out, rsa),
    }
}

fn visit_opaque<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, o: &'h OpaqueHT<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Opaque(o));
    visit_id(pred, out, o.struct_id);
    visit_simple_id(pred, out, &o.simple_id);
}

fn visit_struct_ht<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, s: &'h StructHT<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StructHT(s));
    visit_id(pred, out, s.id);
}

fn visit_interface_ht<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, i: &'h InterfaceHT<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::InterfaceHT(i));
    visit_id(pred, out, i.id);
}

fn visit_static_sized_array_ht<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    s: &'h StaticSizedArrayHT<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StaticSizedArrayHT(s));
    visit_id(pred, out, s.id);
}

fn visit_runtime_sized_array_ht<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    r: &'h RuntimeSizedArrayHT<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::RuntimeSizedArrayHT(r));
    visit_id(pred, out, r.name);
}

fn visit_static_sized_array_definition<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    s: &'h StaticSizedArrayDefinitionHT<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::StaticSizedArrayDefinitionHT(s));
    visit_id(pred, out, s.name);
    visit_coord(pred, out, &s.element_type);
}

fn visit_runtime_sized_array_definition<'s, 'h, T, F>(
    pred: &F,
    out: &mut Vec<T>,
    r: &'h RuntimeSizedArrayDefinitionHT<'s, 'h>,
) where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::RuntimeSizedArrayDefinitionHT(r));
    visit_id(pred, out, r.name);
    visit_coord(pred, out, &r.element_type);
}

fn visit_simple_id<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, s: &'h SimpleId<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::SimpleId(s));
    for step in s.steps {
        visit_simple_id_step(pred, out, step);
    }
}

fn visit_simple_id_step<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, st: &'h SimpleIdStep<'s, 'h>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::SimpleIdStep(st));
    for ta in st.template_args {
        visit_simple_id(pred, out, ta);
    }
}

// ============================================================================
// Names
// ============================================================================

fn visit_id<'s, 'h, T, F>(pred: &F, out: &mut Vec<T>, id: &'h IdH<'s>)
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    collect_if(pred, out, NodeRefH::Id(id));
}

// ============================================================================
// Dispatcher
// ============================================================================

pub fn collect_in_hnode<'s, 'h, T, F>(node: &NodeRefH<'s, 'h>, predicate: &F) -> Vec<T>
where
    F: Fn(NodeRefH<'s, 'h>) -> Option<T>,
    's: 'h,
{
    let mut out = Vec::new();
    match node {
        NodeRefH::Program(p) => visit_program(predicate, &mut out, p),
        NodeRefH::Package(p) => visit_package(predicate, &mut out, p),
        NodeRefH::Function(f) => visit_function(predicate, &mut out, f),
        NodeRefH::StructDefinition(s) => visit_struct_definition(predicate, &mut out, s),
        NodeRefH::InterfaceDefinition(i) => visit_interface_definition(predicate, &mut out, i),
        NodeRefH::Expression(e) => visit_expression(predicate, &mut out, *e),
        NodeRefH::Coord(c) => visit_coord(predicate, &mut out, c),
        NodeRefH::Kind(k) => visit_kind(predicate, &mut out, k),
        _ => panic!("FINALAST_TEST_COLLECT_IN_HNODE_NODE_KIND_NOT_YET_IMPLEMENTED"),
    }
    out
}

// ============================================================================
// Macros (verbatim-ported from typing/test/traverse.rs)
// ============================================================================

#[macro_export]
macro_rules! collect_in_hnodes {
  ($expr:expr, $pattern:pat => $body:expr) => {{
    let mut out = Vec::new();
    for node in $expr {
      out.extend($crate::final_ast::test::traverse::collect_in_hnode(
        node,
        &|node| match node {
          $pattern => $body,
          _ => None,
        },
      ));
    }
    out
  }};
  ($expr:expr, $pattern:pat if $guard:expr => $body:expr) => {{
    let mut out = Vec::new();
    for node in $expr {
      out.extend($crate::final_ast::test::traverse::collect_in_hnode(
        node,
        &|node| match node {
          $pattern if $guard => $body,
          _ => None,
        },
      ));
    }
    out
  }};
}

#[macro_export]
macro_rules! collect_where_hnodes {
  ($expr:expr, $pattern:pat => $body:expr) => {{
    $crate::collect_in_hnodes!($expr, $pattern => $body)
  }};
  ($expr:expr, $pattern:pat if $guard:expr => $body:expr) => {{
    $crate::collect_in_hnodes!($expr, $pattern if $guard => $body)
  }};
}

#[macro_export]
macro_rules! collect_only_hnodes {
  ($expr:expr, $pattern:pat => $body:expr) => {{
    let mut matches = $crate::collect_where_hnodes!($expr, $pattern => $body);
    assert_eq!(1, matches.len());
    matches.remove(0)
  }};
  ($expr:expr, $pattern:pat if $guard:expr => $body:expr) => {{
    let mut matches = $crate::collect_where_hnodes!($expr, $pattern if $guard => $body);
    assert_eq!(1, matches.len());
    matches.remove(0)
  }};
}

#[macro_export]
macro_rules! collect_where_hnode {
  ($expr:expr, $pattern:pat => $body:expr) => {{
    $crate::collect_where_hnodes!(&[$expr], $pattern => $body)
  }};
  ($expr:expr, $pattern:pat if $guard:expr => $body:expr) => {{
    $crate::collect_where_hnodes!(&[$expr], $pattern if $guard => $body)
  }};
}

#[macro_export]
macro_rules! collect_only_hnode {
  ($expr:expr, $pattern:pat => $body:expr) => {{
    $crate::collect_only_hnodes!(&[$expr], $pattern => $body)
  }};
  ($expr:expr, $pattern:pat if $guard:expr => $body:expr) => {{
    $crate::collect_only_hnodes!(&[$expr], $pattern if $guard => $body)
  }};
}
