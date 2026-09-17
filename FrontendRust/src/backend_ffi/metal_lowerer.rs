// MetalLowerer: H-AST → MetalCache. Replaces what `Backend/src/metal/readjson.cpp`
// used to do, but in Rust against the in-process `ProgramH<'s,'h>` instead
// of parsed JSON. Each `lower_*` function mirrors a `readjson.cpp` `read*`
// function; the shape is intentionally near-1:1 so behavior parity is easy
// to verify.
//
// Coverage: complete for everything the current Rust frontend emits — proven
// end-to-end by `walks_real_*` (hammer pipeline → MetalLowerer → backend →
// link → exec) and by the full VerdagonSite rebuild. Concretely, ~50
// ExpressionH arms and 11 KindHT arms across packages, structs, interfaces,
// edges, functions, externs, static/runtime arrays, virtual dispatch, weak
// refs (alias / lock / AsSubtype), upcasts, and the full expression set
// (constants, control flow, locals, member load/store, call/extern-call,
// arithmetic helpers, array load/store/push/pop/length/capacity, destroy
// variants, etc.).
//
// MutabilifyH / ImmutabilifyH are fully wired end-to-end (Backend codegen
// already existed; only result_type and the lowering arms were missing).
//
// IsSameInstanceH and InterfaceToInterfaceUpcastH are wired through
// MetalLowerer + FFI + a Backend `class` declaration, but the LLVM-emission
// arm in `Backend/src/function/expression.cpp` is intentionally left out.
// MetalLowerer can construct the H-AST node and ship it through to Backend;
// any attempt to actually compile a program containing one will fall through
// to expression.cpp's `else { assert(false); }` at codegen time. The Rust
// frontend doesn't emit either today, so this is a tripwire, not a hazard.
//
// KindHT::OpaqueHT remains a `panic!` in `lower_kind`. It's a Kind variant
// (not an Expression), so the dispatcher-cascade trick that works for
// IsSameInstance / I2I-upcast doesn't apply: every region's translateType /
// getControlBlock / etc. does an exhaustive dynamic_cast switch over Kind
// subclasses, so adding a new Kind requires touching all of them. The current
// `kind_to_extern` walker already ignores OpaqueHT (uses info.kind instead),
// so the panic is unreachable from any code path the frontend exercises.
//
// To turn IsSameInstance / I2I-upcast on, add the corresponding
// `translateExpression` arm in `Backend/src/function/expression.cpp`.

use crate::backend_ffi::metal_cache::{
    MetalCache, Program, Package, PackageCoord, Name, Kind, Reference,
    Prototype, Function, Expression, Ownership, Location,
    VariableId, Local, StructDef, StructMember, InterfaceDef, InterfaceMethod, Edge,
    Mutability, Variability, Weakability,
};
use crate::final_ast::ast::{FunctionH, IdH, InterfaceDefinitionH, InterfaceMethodH, EdgeH, PackageH, PrototypeH, ProgramH, StructDefinitionH, StructMemberH};
use crate::final_ast::types::{StaticSizedArrayDefinitionHT, RuntimeSizedArrayDefinitionHT};
use crate::final_ast::instructions::{ExpressionH, VariableIdH, Local as LocalH};
use crate::final_ast::types::{CoordH, KindHT, LocationH, OwnershipH, Mutability as MutabilityH, Variability as VariabilityH};
use crate::utils::code_hierarchy::PackageCoordinate;

/// Walk a `ProgramH` and populate the given `MetalCache`, returning a fully
/// constructed `Program` ready to hand to `backend_compile_program`.
pub fn populate_metal_cache<'cache, 's, 'h>(
    cache: &'cache MetalCache,
    hamuts: &ProgramH<'s, 'h>,
) -> Program<'cache>
where
    's: 'h,
{
    let pb = cache.new_program_builder();
    for (coord, pkg_h) in hamuts.packages.package_coord_to_contents.iter() {
        let coord_handle = lower_package_coord(cache, coord);
        let pkg_handle = lower_package(cache, coord_handle, pkg_h);
        pb.add_package(coord_handle, pkg_handle);
    }
    pb.finish()
}

fn lower_package_coord<'cache, 's>(
    cache: &'cache MetalCache,
    coord: &PackageCoordinate<'s>,
) -> PackageCoord<'cache> {
    // Mirrors name_hammer::translate_package_coordinate: the empty-module
    // builtin coord becomes "__vale" before crossing the FFI boundary.
    // Backend's userFuncName computation prepends `<project>_` so this
    // turns `streq` into `__vale_streq`, matching Backend/builtins/strings.c.
    let project = if coord.module.0.is_empty() { "__vale" } else { coord.module.0 };
    let steps: Vec<&str> = coord.packages.iter().map(|s| s.0).collect();
    cache.get_package_coordinate(project, &steps)
}

fn lower_package<'cache, 's, 'h>(
    cache: &'cache MetalCache,
    coord: PackageCoord<'cache>,
    pkg: &PackageH<'s, 'h>,
) -> Package<'cache>
where
    's: 'h,
{
    let pb = cache.new_package_builder(coord);

    // Backend's compileValeCode asserts that each Package map's key equals
    // the value's Name->name. Since lower_id_to_name uses shortened_name
    // (matching readjson.cpp), the map keys must too.
    for idef in pkg.interfaces.iter() {
        let id_h = lower_interface_def(cache, idef);
        pb.add_interface(idef.id.shortened_name.0, id_h);
    }
    for sdef in pkg.structs.iter() {
        let sd = lower_struct_def(cache, sdef);
        pb.add_struct(sdef.id.shortened_name.0, sd);
    }
    for ssa in pkg.static_sized_arrays.iter() {
        let name = lower_id_to_name(cache, ssa.name);
        let kind = cache.get_static_sized_array(name);
        let region_id = match ssa.mutability {
            MutabilityH::Immutable => cache.rcimm_region_id(),
            MutabilityH::Mutable => cache.mut_region_id(),
        };
        let elem_ty = lower_coord_to_reference(cache, &ssa.element_type);
        let def = cache.new_static_sized_array_def(
            name, kind, ssa.size as i32, region_id,
            lower_mutability(ssa.mutability), lower_variability(ssa.variability), elem_ty,
        );
        pb.add_static_sized_array(ssa.name.shortened_name.0, def);
    }
    for rsa in pkg.runtime_sized_arrays.iter() {
        let name = lower_id_to_name(cache, rsa.name);
        let kind = cache.get_runtime_sized_array(name);
        let region_id = match rsa.mutability {
            MutabilityH::Immutable => cache.rcimm_region_id(),
            MutabilityH::Mutable => cache.mut_region_id(),
        };
        let elem_ty = lower_coord_to_reference(cache, &rsa.element_type);
        let def = cache.new_runtime_sized_array_def(
            name, kind, region_id, lower_mutability(rsa.mutability), elem_ty,
        );
        pb.add_runtime_sized_array(rsa.name.shortened_name.0, def);
    }

    for func in pkg.functions.iter() {
        let proto = lower_prototype(cache, func.prototype);
        let body = lower_expression(cache, &func.body);
        let f = cache.new_function(proto, Some(body));
        pb.add_function(func.prototype.id.shortened_name.0, f);
    }

    for (export_name, proto_ref) in pkg.export_name_to_function.iter() {
        let proto = lower_prototype(cache, *proto_ref);
        pb.add_export_function(export_name.0, proto);
    }
    for (export_name, kind_ref) in pkg.export_name_to_kind.iter() {
        let k = lower_kind(cache, kind_ref);
        pb.add_export_kind(export_name.0, k);
    }
    for (proto_ref, info) in pkg.prototype_to_extern.iter() {
        let proto = lower_prototype(cache, *proto_ref);
        pb.add_extern_function(info.maybe_extern_name.0, proto);
    }
    for (opaque_ht, info) in pkg.kind_to_extern.iter() {
        // kind_to_extern keys on OpaqueHT, which isn't a walkable Kind variant
        // — extern kinds materialize as the underlying KindHT in info.kind.
        let _ = opaque_ht;
        let k = lower_kind(cache, &info.kind);
        pb.add_extern_kind(info.maybe_extern_name.0, k);
    }

    pb.finish()
}

fn lower_mutability(m: MutabilityH) -> Mutability {
    match m { MutabilityH::Immutable => Mutability::Immutable, MutabilityH::Mutable => Mutability::Mutable }
}

fn lower_variability(v: VariabilityH) -> Variability {
    match v { VariabilityH::Final => Variability::Final, VariabilityH::Varying => Variability::Varying }
}

fn lower_struct_member<'cache, 's, 'h>(cache: &'cache MetalCache, m: &StructMemberH<'s, 'h>) -> StructMember<'cache>
where 's: 'h,
{
    let ty = lower_coord_to_reference(cache, &m.tyype);
    cache.new_struct_member(m.name.shortened_name.0, m.name.local_name.0, lower_variability(m.variability), ty)
}

fn lower_interface_method<'cache, 's, 'h>(cache: &'cache MetalCache, im: &InterfaceMethodH<'s, 'h>) -> InterfaceMethod<'cache>
where 's: 'h,
{
    let proto = lower_prototype(cache, im.prototype_h);
    cache.get_interface_method(proto, im.virtual_param_index)
}

fn lower_interface_def<'cache, 's, 'h>(cache: &'cache MetalCache, i: &InterfaceDefinitionH<'s, 'h>) -> InterfaceDef<'cache>
where 's: 'h,
{
    let name = lower_id_to_name(cache, i.id);
    let kind = cache.get_interface_kind(name);
    let region_id = match i.mutability {
        MutabilityH::Immutable => cache.rcimm_region_id(),
        MutabilityH::Mutable => cache.mut_region_id(),
    };
    let super_names: Vec<Name<'cache>> = i.super_interfaces.iter().map(|iht| lower_id_to_name(cache, iht.id)).collect();
    let methods: Vec<InterfaceMethod<'cache>> = i.methods.iter().map(|m| lower_interface_method(cache, m)).collect();
    cache.new_interface_def(
        name, kind, region_id, lower_mutability(i.mutability),
        &super_names, &methods,
        if i.weakable { Weakability::Weakable } else { Weakability::NonWeakable },
    )
}

fn lower_edge<'cache, 's, 'h>(cache: &'cache MetalCache, e: &EdgeH<'s, 'h>) -> Edge<'cache>
where 's: 'h,
{
    let struct_kind = cache.get_struct_kind(lower_id_to_name(cache, e.struct_.id));
    let interface_kind = cache.get_interface_kind(lower_id_to_name(cache, e.interface.id));
    let pairs: Vec<(InterfaceMethod<'cache>, Prototype<'cache>)> =
        e.struct_prototypes_by_interface_method.iter().map(|(im, proto)| {
            (lower_interface_method(cache, im), lower_prototype(cache, *proto))
        }).collect();
    cache.new_edge(struct_kind, interface_kind, &pairs)
}

fn lower_struct_def<'cache, 's, 'h>(cache: &'cache MetalCache, s: &StructDefinitionH<'s, 'h>) -> StructDef<'cache>
where 's: 'h,
{
    let name = lower_id_to_name(cache, s.id);
    let kind = cache.get_struct_kind(name);
    // Mirrors readjson.cpp:654 — rcimm for immutable, mut for mutable.
    let region_id = match s.mutability {
        MutabilityH::Immutable => cache.rcimm_region_id(),
        MutabilityH::Mutable => cache.mut_region_id(),
    };
    let members: Vec<StructMember<'cache>> = s.members.iter().map(|m| lower_struct_member(cache, m)).collect();
    let edges: Vec<Edge<'cache>> = s.edges.iter().map(|e| lower_edge(cache, e)).collect();
    cache.new_struct_def(
        name, kind, region_id, lower_mutability(s.mutability),
        &edges, &members,
        if s.weakable { Weakability::Weakable } else { Weakability::NonWeakable },
    )
}

fn lower_id_to_name<'cache, 's>(cache: &'cache MetalCache, id: &IdH<'s>) -> Name<'cache> {
    // Matches readjson.cpp's readName: uses shortenedName, NOT
    // fully_qualified_name. (Backend's `__vbi_` extern-skip check
    // inspects this name; using the fully-qualified form bypasses it
    // and produces broken ABI shims for builtin externs.)
    let coord = lower_package_coord(cache, &id.package_coordinate);
    cache.get_name(coord, id.shortened_name.0)
}

fn lower_prototype<'cache, 's, 'h>(
    cache: &'cache MetalCache,
    proto: &PrototypeH<'s, 'h>,
) -> Prototype<'cache>
where
    's: 'h,
{
    let name = lower_id_to_name(cache, proto.id);
    let return_ref = lower_coord_to_reference(cache, &proto.return_type);
    let param_refs: Vec<Reference<'cache>> = proto
        .params
        .iter()
        .map(|p| lower_coord_to_reference(cache, p))
        .collect();
    cache.get_prototype(name, return_ref, &param_refs)
}

fn lower_kind<'cache, 's, 'h>(cache: &'cache MetalCache, kind: &KindHT<'s, 'h>) -> Kind<'cache>
where
    's: 'h,
{
    match kind {
        KindHT::IntHT(i) => cache.get_int(cache.rcimm_region_id(), i.bits),
        KindHT::BoolHT(_) => cache.bool_kind(),
        KindHT::VoidHT(_) => cache.void_kind(),
        KindHT::NeverHT(_) => cache.never_kind(),
        KindHT::StrHT(_) => cache.str_kind(),
        KindHT::FloatHT(_) => cache.get_float(cache.rcimm_region_id()),
        KindHT::StructHT(s) => cache.get_struct_kind(lower_id_to_name(cache, s.id)),
        KindHT::InterfaceHT(i) => cache.get_interface_kind(lower_id_to_name(cache, i.id)),
        KindHT::StaticSizedArrayHT(a) => cache.get_static_sized_array(lower_id_to_name(cache, a.id)),
        KindHT::RuntimeSizedArrayHT(a) => cache.get_runtime_sized_array(lower_id_to_name(cache, a.name)),
        KindHT::OpaqueHT(_) => panic!("MetalLowerer: KindHT::OpaqueHT not yet implemented"),
    }
}

fn lower_ownership(o: OwnershipH) -> Ownership {
    match o {
        OwnershipH::OwnH => Ownership::Own,
        OwnershipH::MutableBorrowH => Ownership::MutableBorrow,
        OwnershipH::ImmutableBorrowH => Ownership::ImmutableBorrow,
        OwnershipH::MutableShareH => Ownership::MutableShare,
        OwnershipH::ImmutableShareH => Ownership::ImmutableShare,
        OwnershipH::WeakH => Ownership::Weak,
    }
}

fn lower_location(l: LocationH) -> Location {
    match l {
        LocationH::InlineH => Location::Inline,
        LocationH::YonderH => Location::Yonder,
    }
}

fn lower_coord_to_reference<'cache, 's, 'h>(
    cache: &'cache MetalCache,
    coord: &CoordH<'s, 'h>,
) -> Reference<'cache>
where
    's: 'h,
{
    let kind = lower_kind(cache, &coord.kind);
    cache.get_reference(
        lower_ownership(coord.ownership),
        lower_location(coord.location),
        kind,
    )
}

fn lower_variable_id<'cache, 's, 'h>(cache: &'cache MetalCache, v: &VariableIdH<'s, 'h>) -> VariableId<'cache>
where 's: 'h,
{
    let name = v.name.map(|id| id.shortened_name.0);
    cache.get_variable_id(v.number, v.height, name)
}

fn lower_local<'cache, 's, 'h>(cache: &'cache MetalCache, l: &LocalH<'s, 'h>) -> Local<'cache>
where 's: 'h,
{
    let vid = lower_variable_id(cache, &l.id);
    let r = lower_coord_to_reference(cache, &l.type_h);
    cache.get_local(vid, r)
}

fn lower_expression<'cache, 's, 'h>(
    cache: &'cache MetalCache,
    expr: &ExpressionH<'s, 'h>,
) -> Expression<'cache>
where
    's: 'h,
{
    match expr {
        ExpressionH::ConstantIntH(c) => cache.expr_constant_int(c.value, c.bits),
        ExpressionH::BlockH(b) => {
            let inner = lower_expression(cache, &b.inner);
            let inner_ty = lower_coord_to_reference(cache, &b.inner.result_type());
            cache.expr_block(inner, inner_ty)
        }
        ExpressionH::ReturnH(r) => {
            let source = lower_expression(cache, &r.source_expression);
            let source_ty = lower_coord_to_reference(cache, &r.source_expression.result_type());
            cache.expr_return(source, source_ty)
        }
        ExpressionH::ConsecutorH(c) => {
            let parts: Vec<Expression<'cache>> = c.exprs.iter().map(|e| lower_expression(cache, e)).collect();
            cache.expr_consecutor(&parts)
        }
        ExpressionH::ConstantVoidH(_) => cache.expr_constant_void(),
        ExpressionH::ConstantBoolH(b) => cache.expr_constant_bool(b.value),
        ExpressionH::ConstantF64H(f) => cache.expr_constant_f64(f.value),
        ExpressionH::BreakH(_) => cache.expr_break(),
        ExpressionH::StackifyH(s) => {
            let src = lower_expression(cache, &s.source_expr);
            let local = lower_local(cache, &s.local);
            let name = s.name.map(|id| id.shortened_name.0);
            cache.expr_stackify(src, local, false, name)
        }
        ExpressionH::UnstackifyH(u) => {
            let local = lower_local(cache, &u.local);
            cache.expr_unstackify(local)
        }
        ExpressionH::LocalLoadH(l) => {
            let local = lower_local(cache, &l.local);
            cache.expr_local_load(local, lower_ownership(l.target_ownership), l.local_name.local_name.0)
        }
        ExpressionH::LocalStoreH(s) => {
            let local = lower_local(cache, &s.local);
            let src = lower_expression(cache, &s.source_expression);
            cache.expr_local_store(local, src, s.local_name.local_name.0, false)
        }
        ExpressionH::DiscardH(d) => {
            let src = lower_expression(cache, &d.source_expression);
            let src_ty = lower_coord_to_reference(cache, &d.source_expression.result_type());
            cache.expr_discard(src, src_ty)
        }
        ExpressionH::CallH(c) => {
            let proto = lower_prototype(cache, c.function);
            let args: Vec<Expression<'cache>> = c.args_expressions.iter()
                .map(|e| lower_expression(cache, e)).collect();
            cache.expr_call(proto, &args)
        }
        ExpressionH::ExternCallH(c) => {
            let proto = lower_prototype(cache, c.function);
            let args: Vec<Expression<'cache>> = c.args_expressions.iter()
                .map(|e| lower_expression(cache, e)).collect();
            let arg_tys: Vec<Reference<'cache>> = c.args_expressions.iter()
                .map(|e| lower_coord_to_reference(cache, &e.result_type())).collect();
            cache.expr_extern_call(proto, &args, &arg_tys)
        }
        ExpressionH::IfH(i) => {
            let cond = lower_expression(cache, &i.condition_block);
            let then_e = lower_expression(cache, &i.then_block);
            let then_ty = lower_coord_to_reference(cache, &i.then_block.result_type());
            let else_e = lower_expression(cache, &i.else_block);
            let else_ty = lower_coord_to_reference(cache, &i.else_block.result_type());
            let common_ty = lower_coord_to_reference(cache, &i.common_supertype);
            cache.expr_if(cond, then_e, then_ty, else_e, else_ty, common_ty)
        }
        ExpressionH::WhileH(w) => {
            let body = lower_expression(cache, &w.body_block);
            cache.expr_while(body)
        }
        ExpressionH::ArgumentH(a) => {
            let ty = lower_coord_to_reference(cache, &a.result_type);
            cache.expr_argument(ty, a.argument_index)
        }
        ExpressionH::MemberLoadH(m) => {
            let struct_e = lower_expression(cache, &m.struct_expression);
            let struct_ty_coord = m.struct_expression.result_type();
            let struct_id = lower_kind(cache, &struct_ty_coord.kind);
            let struct_ty = lower_coord_to_reference(cache, &struct_ty_coord);
            let expected_member = lower_coord_to_reference(cache, &m.expected_member_type);
            let expected_result = lower_coord_to_reference(cache, &m.result_type);
            cache.expr_member_load(
                struct_e, struct_id, struct_ty, false,
                m.member_index, lower_ownership(m.result_type.ownership),
                expected_member, expected_result,
                m.member_name.shortened_name.0,
            )
        }
        ExpressionH::NewStructH(n) => {
            let exprs: Vec<Expression<'cache>> = n.source_expressions.iter()
                .map(|e| lower_expression(cache, e)).collect();
            let ty = lower_coord_to_reference(cache, &n.result_type);
            cache.expr_new_struct(&exprs, ty)
        }
        ExpressionH::ConstantStrH(c) => cache.expr_constant_str(c.value),
        ExpressionH::DestroyH(d) => {
            let s_expr = lower_expression(cache, &d.struct_expression);
            let s_ty = lower_coord_to_reference(cache, &d.struct_expression.result_type());
            let tys: Vec<Reference<'cache>> = d.local_types.iter().map(|t| lower_coord_to_reference(cache, t)).collect();
            let locs: Vec<Local<'cache>> = d.local_indices.iter().map(|l| lower_local(cache, l)).collect();
            let lives: Vec<bool> = d.local_indices.iter().map(|_| false).collect();
            cache.expr_destroy(s_expr, s_ty, &tys, &locs, &lives)
        }
        ExpressionH::MemberStoreH(m) => {
            let s_expr = lower_expression(cache, &m.struct_expression);
            let s_ty = lower_coord_to_reference(cache, &m.struct_expression.result_type());
            let src = lower_expression(cache, &m.source_expression);
            let result_ty = lower_coord_to_reference(cache, &m.result_type);
            cache.expr_member_store(s_expr, s_ty, false, m.member_index, src, result_ty, m.member_name.shortened_name.0)
        }
        ExpressionH::ArrayLengthH(a) => {
            let s_expr = lower_expression(cache, &a.source_expression);
            let s_ty = lower_coord_to_reference(cache, &a.source_expression.result_type());
            cache.expr_array_length(s_expr, s_ty)
        }
        ExpressionH::ArrayCapacityH(a) => {
            let s_expr = lower_expression(cache, &a.source_expression);
            let s_ty = lower_coord_to_reference(cache, &a.source_expression.result_type());
            cache.expr_array_capacity(s_expr, s_ty)
        }
        ExpressionH::PreCheckBorrowH(p) => {
            let s_expr = lower_expression(cache, &p.inner_expression);
            let s_ty = lower_coord_to_reference(cache, &p.inner_expression.result_type());
            cache.expr_pre_check_borrow(s_expr, s_ty)
        }
        ExpressionH::RestackifyH(r) => {
            let src = lower_expression(cache, &r.source_expr);
            let local = lower_local(cache, &r.local);
            let name = r.name.map(|id| id.shortened_name.0);
            cache.expr_restackify(src, local, false, name)
        }
        // ---- Variants the Rust frontend doesn't yet emit ----
        //
        // Each panic names the variant explicitly so it's clear what's
        // missing when the frontend gains that feature. The C ABI shims and
        // safe wrappers needed to implement these are not yet written —
        // adding them is mechanical (mirror the existing patterns), but
        // dead code without an emitting frontend.
        ExpressionH::MutabilifyH(m) => {
            let src = lower_expression(cache, &m.inner);
            let src_ty = lower_coord_to_reference(cache, &m.inner.result_type());
            let res_ty = lower_coord_to_reference(cache, &expr.result_type());
            cache.expr_mutabilify(src, src_ty, res_ty)
        }
        ExpressionH::ImmutabilifyH(im) => {
            let src = lower_expression(cache, &im.inner);
            let src_ty = lower_coord_to_reference(cache, &im.inner.result_type());
            let res_ty = lower_coord_to_reference(cache, &expr.result_type());
            cache.expr_immutabilify(src, src_ty, res_ty)
        }
        ExpressionH::StructToInterfaceUpcastH(u) => {
            let src = lower_expression(cache, &u.source_expression);
            let src_coord = u.source_expression.result_type();
            let src_ty = lower_coord_to_reference(cache, &src_coord);
            let src_kind = lower_kind(cache, &src_coord.kind);
            let target_kind = cache.get_interface_kind(lower_id_to_name(cache, u.target_interface.id));
            let target_coord = CoordH {
                ownership: src_coord.ownership,
                location: src_coord.location,
                kind: KindHT::InterfaceHT(u.target_interface),
            };
            let target_ty = lower_coord_to_reference(cache, &target_coord);
            cache.expr_struct_to_interface_upcast(src, src_ty, src_kind, target_ty, target_kind)
        }
        ExpressionH::InterfaceToInterfaceUpcastH(u) => {
            let src = lower_expression(cache, &u.source_expression);
            let src_coord = u.source_expression.result_type();
            let src_ty = lower_coord_to_reference(cache, &src_coord);
            let src_kind = lower_kind(cache, &src_coord.kind);
            let target_kind = cache.get_interface_kind(lower_id_to_name(cache, u.target_interface.id));
            let target_coord = CoordH {
                ownership: src_coord.ownership,
                location: src_coord.location,
                kind: KindHT::InterfaceHT(u.target_interface),
            };
            let target_ty = lower_coord_to_reference(cache, &target_coord);
            cache.expr_interface_to_interface_upcast(src, src_ty, src_kind, target_ty, target_kind)
        }
        ExpressionH::InterfaceCallH(c) => {
            let args: Vec<Expression<'cache>> = c.args_expressions.iter()
                .map(|e| lower_expression(cache, e)).collect();
            let interface_kind = cache.get_interface_kind(lower_id_to_name(cache, c.interface_h.id));
            let proto = lower_prototype(cache, c.function_type);
            cache.expr_interface_call(&args, c.virtual_param_index, interface_kind, c.index_in_edge, proto)
        }
        ExpressionH::NewArrayFromValuesH(n) => {
            let exprs: Vec<Expression<'cache>> = n.source_expressions.iter()
                .map(|e| lower_expression(cache, e)).collect();
            let arr_ref = lower_coord_to_reference(cache, &n.result_type);
            let arr_kind = lower_kind(cache, &n.result_type.kind);
            cache.expr_new_array_from_values(&exprs, arr_ref, arr_kind)
        }
        ExpressionH::StaticSizedArrayStoreH(s) => {
            let arr = lower_expression(cache, &s.array_expression);
            let idx = lower_expression(cache, &s.index_expression);
            let src = lower_expression(cache, &s.source_expression);
            cache.expr_static_sized_array_store(arr, idx, src)
        }
        ExpressionH::StaticSizedArrayLoadH(l) => {
            let arr = lower_expression(cache, &l.array_expression);
            let arr_coord = l.array_expression.result_type();
            let arr_ty = lower_coord_to_reference(cache, &arr_coord);
            let arr_kind = lower_kind(cache, &arr_coord.kind);
            let idx = lower_expression(cache, &l.index_expression);
            let result_ty = lower_coord_to_reference(cache, &l.result_type);
            let elem_ty = lower_coord_to_reference(cache, &l.expected_element_type);
            cache.expr_static_sized_array_load(
                arr, arr_ty, arr_kind, idx, result_ty,
                lower_ownership(l.target_ownership), elem_ty, l.array_size as i32,
            )
        }
        ExpressionH::RuntimeSizedArrayStoreH(s) => {
            let arr = lower_expression(cache, &s.array_expression);
            let arr_coord = s.array_expression.result_type();
            let arr_ty = lower_coord_to_reference(cache, &arr_coord);
            let arr_kind = lower_kind(cache, &arr_coord.kind);
            let idx = lower_expression(cache, &s.index_expression);
            let idx_coord = s.index_expression.result_type();
            let idx_ty = lower_coord_to_reference(cache, &idx_coord);
            let idx_kind = lower_kind(cache, &idx_coord.kind);
            let src = lower_expression(cache, &s.source_expression);
            let src_coord = s.source_expression.result_type();
            let src_ty = lower_coord_to_reference(cache, &src_coord);
            let src_kind = lower_kind(cache, &src_coord.kind);
            cache.expr_runtime_sized_array_store(arr, arr_ty, arr_kind, idx, idx_ty, idx_kind, src, src_ty, src_kind)
        }
        ExpressionH::RuntimeSizedArrayLoadH(l) => {
            let arr = lower_expression(cache, &l.array_expression);
            let arr_coord = l.array_expression.result_type();
            let arr_ty = lower_coord_to_reference(cache, &arr_coord);
            let arr_kind = lower_kind(cache, &arr_coord.kind);
            let idx = lower_expression(cache, &l.index_expression);
            let idx_coord = l.index_expression.result_type();
            let idx_ty = lower_coord_to_reference(cache, &idx_coord);
            let idx_kind = lower_kind(cache, &idx_coord.kind);
            let result_ty = lower_coord_to_reference(cache, &l.result_type);
            let elem_ty = lower_coord_to_reference(cache, &l.expected_element_type);
            cache.expr_runtime_sized_array_load(arr, arr_ty, arr_kind, idx, idx_ty, idx_kind, result_ty, lower_ownership(l.target_ownership), elem_ty)
        }
        ExpressionH::NewImmRuntimeSizedArrayH(n) => {
            let size = lower_expression(cache, &n.size_expression);
            let size_coord = n.size_expression.result_type();
            let size_ty = lower_coord_to_reference(cache, &size_coord);
            let size_kind = lower_kind(cache, &size_coord.kind);
            let gen = lower_expression(cache, &n.generator_expression);
            let gen_coord = n.generator_expression.result_type();
            let gen_ty = lower_coord_to_reference(cache, &gen_coord);
            let gen_kind = lower_kind(cache, &gen_coord.kind);
            let gen_method = lower_prototype(cache, n.generator_method);
            let arr_ref = lower_coord_to_reference(cache, &n.result_type);
            let elem_ty = lower_coord_to_reference(cache, &n.element_type);
            cache.expr_new_imm_runtime_sized_array(size, size_ty, size_kind, gen, gen_ty, gen_kind, gen_method, arr_ref, elem_ty)
        }
        ExpressionH::NewMutRuntimeSizedArrayH(n) => {
            let size = lower_expression(cache, &n.capacity_expression);
            let size_coord = n.capacity_expression.result_type();
            let size_ty = lower_coord_to_reference(cache, &size_coord);
            let size_kind = lower_kind(cache, &size_coord.kind);
            let arr_ref = lower_coord_to_reference(cache, &n.result_type);
            let elem_ty = lower_coord_to_reference(cache, &n.element_type);
            cache.expr_new_mut_runtime_sized_array(size, size_ty, size_kind, arr_ref, elem_ty)
        }
        ExpressionH::StaticArrayFromCallableH(n) => {
            let gen = lower_expression(cache, &n.generator_expression);
            let gen_coord = n.generator_expression.result_type();
            let gen_ty = lower_coord_to_reference(cache, &gen_coord);
            let gen_kind = lower_kind(cache, &gen_coord.kind);
            let gen_method = lower_prototype(cache, n.generator_method);
            let arr_ref = lower_coord_to_reference(cache, &n.result_type);
            let elem_ty = lower_coord_to_reference(cache, &n.element_type);
            cache.expr_static_array_from_callable(gen, gen_ty, gen_kind, gen_method, arr_ref, elem_ty)
        }
        ExpressionH::PushRuntimeSizedArrayH(p) => {
            let arr = lower_expression(cache, &p.array_expression);
            let arr_ty = lower_coord_to_reference(cache, &p.array_expression.result_type());
            let new = lower_expression(cache, &p.newcomer_expression);
            let new_ty = lower_coord_to_reference(cache, &p.newcomer_expression.result_type());
            cache.expr_push_runtime_sized_array(arr, arr_ty, new, new_ty)
        }
        ExpressionH::PopRuntimeSizedArrayH(p) => {
            let arr = lower_expression(cache, &p.array_expression);
            let arr_ty = lower_coord_to_reference(cache, &p.array_expression.result_type());
            cache.expr_pop_runtime_sized_array(arr, arr_ty)
        }
        ExpressionH::DestroyStaticSizedArrayIntoLocalsH(d) => {
            let arr = lower_expression(cache, &d.struct_expression);
            let arr_ty = lower_coord_to_reference(cache, &d.struct_expression.result_type());
            let tys: Vec<Reference<'cache>> = d.local_types.iter().map(|t| lower_coord_to_reference(cache, t)).collect();
            let locs: Vec<Local<'cache>> = d.local_indices.iter().map(|l| lower_local(cache, l)).collect();
            cache.expr_destroy_static_sized_array_into_locals(arr, arr_ty, &tys, &locs)
        }
        ExpressionH::DestroyStaticSizedArrayIntoFunctionH(d) => {
            let arr = lower_expression(cache, &d.array_expression);
            let arr_coord = d.array_expression.result_type();
            let arr_ty = lower_coord_to_reference(cache, &arr_coord);
            let arr_kind = lower_kind(cache, &arr_coord.kind);
            let cons = lower_expression(cache, &d.consumer_expression);
            let cons_ty = lower_coord_to_reference(cache, &d.consumer_expression.result_type());
            let cons_method = lower_prototype(cache, d.consumer_method);
            let elem_ty = lower_coord_to_reference(cache, &d.array_element_type);
            cache.expr_destroy_static_sized_array_into_function(arr, arr_ty, arr_kind, cons, cons_ty, cons_method, elem_ty, d.array_size as i32)
        }
        ExpressionH::DestroyImmRuntimeSizedArrayH(d) => {
            let arr = lower_expression(cache, &d.array_expression);
            let arr_coord = d.array_expression.result_type();
            let arr_ty = lower_coord_to_reference(cache, &arr_coord);
            let arr_kind = lower_kind(cache, &arr_coord.kind);
            let cons = lower_expression(cache, &d.consumer_expression);
            let cons_coord = d.consumer_expression.result_type();
            let cons_ty = lower_coord_to_reference(cache, &cons_coord);
            let cons_kind = lower_kind(cache, &cons_coord.kind);
            let cons_method = lower_prototype(cache, d.consumer_method);
            cache.expr_destroy_imm_runtime_sized_array(arr, arr_ty, arr_kind, cons, cons_ty, cons_kind, cons_method)
        }
        ExpressionH::DestroyMutRuntimeSizedArrayH(d) => {
            let arr = lower_expression(cache, &d.array_expression);
            let arr_coord = d.array_expression.result_type();
            let arr_ty = lower_coord_to_reference(cache, &arr_coord);
            let arr_kind = lower_kind(cache, &arr_coord.kind);
            cache.expr_destroy_mut_runtime_sized_array(arr, arr_ty, arr_kind)
        }
        ExpressionH::BorrowToWeakH(wa) => {
            let src = lower_expression(cache, &wa.ref_expression);
            let src_coord = wa.ref_expression.result_type();
            let src_ty = lower_coord_to_reference(cache, &src_coord);
            let src_kind = lower_kind(cache, &src_coord.kind);
            let result_coord = ExpressionH::BorrowToWeakH(wa).result_type();
            let result_ty = lower_coord_to_reference(cache, &result_coord);
            cache.expr_weak_alias(src, src_ty, src_kind, result_ty)
        }
        ExpressionH::LockWeakH(l) => {
            let src = lower_expression(cache, &l.source_expression);
            let src_ty = lower_coord_to_reference(cache, &l.source_expression.result_type());
            let some_ctor = lower_prototype(cache, l.some_constructor);
            let some_ty = lower_coord_to_reference(cache, &l.some_constructor.return_type);
            let some_kind = lower_kind(cache, &l.some_constructor.return_type.kind);
            let none_ctor = lower_prototype(cache, l.none_constructor);
            let none_ty = lower_coord_to_reference(cache, &l.none_constructor.return_type);
            let none_kind = lower_kind(cache, &l.none_constructor.return_type.kind);
            let result_ty = lower_coord_to_reference(cache, &l.result_type);
            let result_kind = lower_kind(cache, &l.result_type.kind);
            cache.expr_lock_weak(src, src_ty, some_ctor, some_ty, some_kind, none_ctor, none_ty, none_kind, result_ty, result_kind)
        }
        ExpressionH::AsSubtypeH(a) => {
            let src = lower_expression(cache, &a.source_expression);
            let src_ty = lower_coord_to_reference(cache, &a.source_expression.result_type());
            let target_kind = lower_kind(cache, &a.target_type);
            let ok_ctor = lower_prototype(cache, a.some_constructor);
            let ok_ty = lower_coord_to_reference(cache, &a.some_constructor.return_type);
            let ok_kind = lower_kind(cache, &a.some_constructor.return_type.kind);
            let err_ctor = lower_prototype(cache, a.none_constructor);
            let err_ty = lower_coord_to_reference(cache, &a.none_constructor.return_type);
            let err_kind = lower_kind(cache, &a.none_constructor.return_type.kind);
            let result_ty = lower_coord_to_reference(cache, &a.result_type);
            let result_kind = lower_kind(cache, &a.result_type.kind);
            cache.expr_as_subtype(src, src_ty, target_kind, ok_ctor, ok_ty, ok_kind, err_ctor, err_ty, err_kind, result_ty, result_kind)
        }
        ExpressionH::IsSameInstanceH(s) => {
            let left = lower_expression(cache, &s.left_expression);
            let left_ty = lower_coord_to_reference(cache, &s.left_expression.result_type());
            let right = lower_expression(cache, &s.right_expression);
            let right_ty = lower_coord_to_reference(cache, &s.right_expression.result_type());
            cache.expr_is_same_instance(left, left_ty, right, right_ty)
        }
    }
}
