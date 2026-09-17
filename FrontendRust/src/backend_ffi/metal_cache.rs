// Safe-ish Rust bindings for Backend/src/metal/metal_cache_ffi.h.
//
// Handles are exposed as `*mut Opaque` newtypes (`Copy` so a borrow can be
// passed to several FFI calls without lifetime gymnastics). Lifetime safety
// is enforced via `&MetalCache` references on the safe wrappers — handles
// returned by `get_*` borrow from the cache for as long as it lives.

use std::ffi::c_void;
use std::marker::PhantomData;
use std::os::raw::{c_char, c_int};
use std::ptr::NonNull;

#[repr(C)]
pub struct MetalCacheHandleRaw {
    _opaque: [u8; 0],
}

#[repr(transparent)]
#[derive(Copy, Clone, Debug)]
pub struct PackageCoord<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct RegionId<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct Name<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct Kind<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct Reference<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct Prototype<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct InterfaceMethod<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct StructMember<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct Edge<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct StructDef<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct InterfaceDef<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct Function<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
/// Opaque handle to a populated `Expression*` tree. The constructors that
/// produce these will land alongside the per-`ExpressionH`-variant FFI shims;
/// `None` is permitted for stubs (e.g. function bodies in tests that never
/// reach codegen).
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct Expression<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct VariableId<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[repr(transparent)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct Local<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);

/// Mirror of metal/types.h `enum class Mutability`.
#[repr(u32)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum Mutability { Immutable = 0, Mutable = 1 }

/// Mirror of metal/types.h `enum class Variability`.
#[repr(u32)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum Variability { Final = 0, Varying = 1 }

/// Mirror of metal/types.h `enum class Weakability`.
#[repr(u32)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum Weakability { Weakable = 0, NonWeakable = 1 }

/// Mirror of metal/types.h `enum class Ownership` numeric encoding.
#[repr(u32)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum Ownership {
    Own = 0,
    MutableBorrow = 1,
    ImmutableBorrow = 2,
    Weak = 3,
    MutableShare = 4,
    ImmutableShare = 5,
}

/// Mirror of metal/types.h `enum class Location`.
#[repr(u32)]
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum Location {
    Inline = 0,
    Yonder = 1,
}

extern "C" {
    fn metal_cache_new() -> *mut MetalCacheHandleRaw;
    fn metal_cache_free(_: *mut MetalCacheHandleRaw);

    fn metal_cache_builtin_package_coord(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_rcimm_region_id(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_linear_region_id(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_mut_region_id(_: *mut MetalCacheHandleRaw) -> *mut c_void;

    fn metal_cache_i32(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_i32_ref(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_i64(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_i64_ref(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_bool(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_bool_ref(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_float(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_float_ref(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_str(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_mut_str_ref(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_imm_str_ref(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_never(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_never_ref(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_void(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_cache_void_ref(_: *mut MetalCacheHandleRaw) -> *mut c_void;

    fn metal_cache_get_package_coordinate(
        _: *mut MetalCacheHandleRaw,
        project_name_ptr: *const c_char, project_name_len: usize,
        steps_ptrs: *const *const c_char, steps_lens: *const usize, steps_count: usize,
    ) -> *mut c_void;

    fn metal_cache_get_region_id(
        _: *mut MetalCacheHandleRaw, package_coord: *mut c_void,
        id_ptr: *const c_char, id_len: usize,
    ) -> *mut c_void;

    fn metal_cache_get_name(
        _: *mut MetalCacheHandleRaw, package_coord: *mut c_void,
        name_ptr: *const c_char, name_len: usize,
    ) -> *mut c_void;

    fn metal_cache_get_int(_: *mut MetalCacheHandleRaw, region: *mut c_void, bits: i32) -> *mut c_void;
    fn metal_cache_get_bool(_: *mut MetalCacheHandleRaw, region: *mut c_void) -> *mut c_void;
    fn metal_cache_get_str(_: *mut MetalCacheHandleRaw, region: *mut c_void) -> *mut c_void;
    fn metal_cache_get_float(_: *mut MetalCacheHandleRaw, region: *mut c_void) -> *mut c_void;
    fn metal_cache_get_void(_: *mut MetalCacheHandleRaw, region: *mut c_void) -> *mut c_void;
    fn metal_cache_get_never(_: *mut MetalCacheHandleRaw, region: *mut c_void) -> *mut c_void;

    fn metal_cache_get_struct_kind(_: *mut MetalCacheHandleRaw, name: *mut c_void) -> *mut c_void;
    fn metal_cache_get_interface_kind(_: *mut MetalCacheHandleRaw, name: *mut c_void) -> *mut c_void;
    fn metal_cache_get_static_sized_array(_: *mut MetalCacheHandleRaw, name: *mut c_void) -> *mut c_void;
    fn metal_cache_get_runtime_sized_array(_: *mut MetalCacheHandleRaw, name: *mut c_void) -> *mut c_void;

    fn metal_cache_get_reference(
        _: *mut MetalCacheHandleRaw, ownership: u32, location: u32, kind: *mut c_void,
    ) -> *mut c_void;

    fn metal_cache_get_prototype(
        _: *mut MetalCacheHandleRaw, name: *mut c_void, return_type: *mut c_void,
        param_types: *const *mut c_void, param_count: usize,
    ) -> *mut c_void;

    fn metal_cache_get_interface_method(
        _: *mut MetalCacheHandleRaw, prototype: *mut c_void, virtual_param_index: i32,
    ) -> *mut c_void;

    fn metal_struct_member_new(
        full_name_ptr: *const c_char, full_name_len: usize,
        name_ptr: *const c_char, name_len: usize,
        variability: u32, ty: *mut c_void,
    ) -> *mut c_void;

    fn metal_edge_new(
        struct_kind: *mut c_void, interface_kind: *mut c_void,
        interface_methods: *const *mut c_void,
        struct_prototypes: *const *mut c_void,
        pair_count: usize,
    ) -> *mut c_void;

    fn metal_struct_def_new(
        name: *mut c_void, struct_kind: *mut c_void, region_id: *mut c_void,
        mutability: u32,
        edges: *const *mut c_void, edge_count: usize,
        members: *const *mut c_void, member_count: usize,
        weakability: u32,
    ) -> *mut c_void;

    fn metal_interface_def_new(
        name: *mut c_void, interface_kind: *mut c_void, region_id: *mut c_void,
        mutability: u32,
        super_interfaces: *const *mut c_void, super_count: usize,
        methods: *const *mut c_void, method_count: usize,
        weakability: u32,
    ) -> *mut c_void;

    fn metal_function_new(prototype: *mut c_void, body: *mut c_void) -> *mut c_void;

    fn metal_expr_constant_void() -> *mut c_void;
    fn metal_expr_constant_int(value: i64, bits: i32) -> *mut c_void;
    fn metal_expr_constant_bool(value: i32) -> *mut c_void;
    fn metal_expr_constant_f64(value: f64) -> *mut c_void;
    fn metal_expr_constant_str(value_ptr: *const c_char, value_len: usize) -> *mut c_void;
    fn metal_expr_break() -> *mut c_void;
    fn metal_expr_return(source_expr: *mut c_void, source_type: *mut c_void) -> *mut c_void;
    fn metal_expr_block(inner: *mut c_void, inner_type: *mut c_void) -> *mut c_void;
    fn metal_expr_consecutor(exprs: *const *mut c_void, expr_count: usize) -> *mut c_void;

    fn metal_cache_get_variable_id(
        _: *mut MetalCacheHandleRaw, number: i32, height: i32,
        maybe_name_ptr: *const c_char, maybe_name_len: usize,
    ) -> *mut c_void;
    fn metal_cache_get_local(
        _: *mut MetalCacheHandleRaw, var_id: *mut c_void, ref_type: *mut c_void,
    ) -> *mut c_void;

    fn metal_expr_stackify(
        source_expr: *mut c_void, local: *mut c_void,
        known_live: i32, maybe_name_ptr: *const c_char, maybe_name_len: usize,
    ) -> *mut c_void;
    fn metal_expr_unstackify(local: *mut c_void) -> *mut c_void;
    fn metal_expr_local_load(
        local: *mut c_void, target_ownership: u32,
        local_name_ptr: *const c_char, local_name_len: usize,
    ) -> *mut c_void;
    fn metal_expr_local_store(
        local: *mut c_void, source_expr: *mut c_void,
        local_name_ptr: *const c_char, local_name_len: usize, known_live: i32,
    ) -> *mut c_void;
    fn metal_expr_discard(source_expr: *mut c_void, source_type: *mut c_void) -> *mut c_void;
    fn metal_expr_call(
        function: *mut c_void,
        arg_exprs: *const *mut c_void, arg_count: usize,
    ) -> *mut c_void;
    fn metal_expr_extern_call(
        function: *mut c_void,
        arg_exprs: *const *mut c_void, arg_count: usize,
        arg_types: *const *mut c_void, arg_type_count: usize,
    ) -> *mut c_void;
    fn metal_expr_if(
        condition: *mut c_void,
        then_expr: *mut c_void, then_type: *mut c_void,
        else_expr: *mut c_void, else_type: *mut c_void,
        common_supertype: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_while(body: *mut c_void) -> *mut c_void;
    fn metal_expr_argument(result_type: *mut c_void, argument_index: i32) -> *mut c_void;
    fn metal_expr_member_load(
        struct_expr: *mut c_void, struct_id: *mut c_void,
        struct_type: *mut c_void, struct_known_live: i32,
        member_index: i32, target_ownership: u32,
        expected_member_type: *mut c_void, expected_result_type: *mut c_void,
        member_name_ptr: *const c_char, member_name_len: usize,
    ) -> *mut c_void;
    fn metal_expr_new_struct(
        source_exprs: *const *mut c_void, source_count: usize,
        result_type: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_struct_to_interface_upcast(
        source_expr: *mut c_void,
        source_struct_type: *mut c_void, source_struct_kind: *mut c_void,
        target_interface_type: *mut c_void, target_interface_kind: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_interface_to_interface_upcast(
        source_expr: *mut c_void,
        source_interface_type: *mut c_void, source_interface_kind: *mut c_void,
        target_interface_type: *mut c_void, target_interface_kind: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_interface_call(
        arg_exprs: *const *mut c_void, arg_count: usize,
        virtual_param_index: i32,
        interface_kind: *mut c_void,
        index_in_edge: i32,
        function_type: *mut c_void,
    ) -> *mut c_void;

    fn metal_expr_static_sized_array_load(
        array_expr: *mut c_void, array_type: *mut c_void,
        array_kind: *mut c_void, array_known_live: i32,
        index_expr: *mut c_void, result_type: *mut c_void,
        target_ownership: u32, array_element_type: *mut c_void,
        array_size: i32,
    ) -> *mut c_void;
    fn metal_expr_static_sized_array_store(
        array_expr: *mut c_void, index_expr: *mut c_void,
        source_expr: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_runtime_sized_array_load(
        array_expr: *mut c_void, array_type: *mut c_void,
        array_kind: *mut c_void, array_known_live: i32,
        index_expr: *mut c_void, index_type: *mut c_void,
        index_kind: *mut c_void, result_type: *mut c_void,
        target_ownership: u32, array_element_type: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_runtime_sized_array_store(
        array_expr: *mut c_void, array_type: *mut c_void,
        array_kind: *mut c_void, array_known_live: i32,
        index_expr: *mut c_void, index_type: *mut c_void,
        index_kind: *mut c_void, source_expr: *mut c_void,
        source_type: *mut c_void, source_kind: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_new_array_from_values(
        source_exprs: *const *mut c_void, source_count: usize,
        array_ref_type: *mut c_void, array_kind: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_new_mut_runtime_sized_array(
        size_expr: *mut c_void, size_type: *mut c_void, size_kind: *mut c_void,
        array_ref_type: *mut c_void, element_type: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_new_imm_runtime_sized_array(
        size_expr: *mut c_void, size_type: *mut c_void, size_kind: *mut c_void,
        generator_expr: *mut c_void, generator_type: *mut c_void, generator_kind: *mut c_void,
        generator_method: *mut c_void, generator_known_live: i32,
        array_ref_type: *mut c_void, element_type: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_static_array_from_callable(
        generator_expr: *mut c_void, generator_type: *mut c_void, generator_kind: *mut c_void,
        generator_method: *mut c_void, generator_known_live: i32,
        array_ref_type: *mut c_void, element_type: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_push_runtime_sized_array(
        array_expr: *mut c_void, array_type: *mut c_void, array_known_live: i32,
        newcomer_expr: *mut c_void, newcomer_type: *mut c_void, newcomer_known_live: i32,
    ) -> *mut c_void;
    fn metal_expr_pop_runtime_sized_array(
        array_expr: *mut c_void, array_type: *mut c_void, array_known_live: i32,
    ) -> *mut c_void;
    fn metal_expr_destroy_static_sized_array_into_locals(
        array_expr: *mut c_void, array_type: *mut c_void,
        local_types: *const *mut c_void, local_type_count: usize,
        local_indices: *const *mut c_void, local_count: usize,
    ) -> *mut c_void;
    fn metal_expr_destroy_static_sized_array_into_function(
        array_expr: *mut c_void, array_type: *mut c_void, array_kind: *mut c_void,
        consumer_expr: *mut c_void, consumer_type: *mut c_void,
        consumer_method: *mut c_void, consumer_known_live: i32,
        array_element_type: *mut c_void, array_size: i32,
    ) -> *mut c_void;
    fn metal_expr_destroy_imm_runtime_sized_array(
        array_expr: *mut c_void, array_type: *mut c_void, array_kind: *mut c_void,
        consumer_expr: *mut c_void, consumer_type: *mut c_void, consumer_kind: *mut c_void,
        consumer_method: *mut c_void, consumer_known_live: i32,
    ) -> *mut c_void;
    fn metal_expr_destroy_mut_runtime_sized_array(
        array_expr: *mut c_void, array_type: *mut c_void, array_kind: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_weak_alias(
        source_expr: *mut c_void, source_type: *mut c_void,
        source_kind: *mut c_void, result_type: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_lock_weak(
        source_expr: *mut c_void, source_type: *mut c_void, source_known_live: i32,
        some_constructor: *mut c_void, some_type: *mut c_void, some_kind: *mut c_void,
        none_constructor: *mut c_void, none_type: *mut c_void, none_kind: *mut c_void,
        result_opt_type: *mut c_void, result_opt_kind: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_as_subtype(
        source_expr: *mut c_void, source_type: *mut c_void, source_known_live: i32,
        target_kind: *mut c_void,
        ok_constructor: *mut c_void, ok_type: *mut c_void, ok_kind: *mut c_void,
        err_constructor: *mut c_void, err_type: *mut c_void, err_kind: *mut c_void,
        result_result_type: *mut c_void, result_result_kind: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_destroy(
        struct_expr: *mut c_void, struct_type: *mut c_void,
        local_types: *const *mut c_void, local_type_count: usize,
        local_indices: *const *mut c_void, local_count: usize,
        locals_known_lives: *const i32, known_lives_count: usize,
    ) -> *mut c_void;
    fn metal_expr_member_store(
        struct_expr: *mut c_void, struct_type: *mut c_void,
        struct_known_live: i32, member_index: i32,
        source_expr: *mut c_void, result_type: *mut c_void,
        member_name_ptr: *const c_char, member_name_len: usize,
    ) -> *mut c_void;
    fn metal_expr_array_length(source_expr: *mut c_void, source_type: *mut c_void, source_known_live: i32) -> *mut c_void;
    fn metal_expr_array_capacity(source_expr: *mut c_void, source_type: *mut c_void, source_known_live: i32) -> *mut c_void;
    fn metal_expr_pre_check_borrow(source_expr: *mut c_void, source_result_type: *mut c_void) -> *mut c_void;
    fn metal_expr_mutabilify(source_expr: *mut c_void, source_type: *mut c_void, result_type: *mut c_void) -> *mut c_void;
    fn metal_expr_immutabilify(source_expr: *mut c_void, source_type: *mut c_void, result_type: *mut c_void) -> *mut c_void;
    fn metal_expr_is_same_instance(
        left_expr: *mut c_void, left_type: *mut c_void,
        right_expr: *mut c_void, right_type: *mut c_void,
    ) -> *mut c_void;
    fn metal_expr_borrow_to_pointer(source_expr: *mut c_void, result_type: *mut c_void) -> *mut c_void;
    fn metal_expr_pointer_to_borrow(source_expr: *mut c_void, result_type: *mut c_void) -> *mut c_void;
    fn metal_expr_narrow_permission(source_expr: *mut c_void) -> *mut c_void;
    fn metal_expr_restackify(
        source_expr: *mut c_void, local: *mut c_void,
        known_live: i32, maybe_name_ptr: *const c_char, maybe_name_len: usize,
    ) -> *mut c_void;

    fn metal_package_builder_new(_: *mut MetalCacheHandleRaw, package_coord: *mut c_void) -> *mut c_void;
    fn metal_package_builder_add_interface(_: *mut c_void, name_ptr: *const c_char, name_len: usize, v: *mut c_void);
    fn metal_package_builder_add_struct(_: *mut c_void, name_ptr: *const c_char, name_len: usize, v: *mut c_void);
    fn metal_package_builder_add_function(_: *mut c_void, name_ptr: *const c_char, name_len: usize, v: *mut c_void);
    fn metal_package_builder_add_static_sized_array(_: *mut c_void, name_ptr: *const c_char, name_len: usize, v: *mut c_void);
    fn metal_package_builder_add_runtime_sized_array(_: *mut c_void, name_ptr: *const c_char, name_len: usize, v: *mut c_void);
    fn metal_static_sized_array_def_new(
        name: *mut c_void, array_kind: *mut c_void, size: i32,
        region_id: *mut c_void, mutability: u32, variability: u32,
        element_type: *mut c_void,
    ) -> *mut c_void;
    fn metal_runtime_sized_array_def_new(
        name: *mut c_void, array_kind: *mut c_void,
        region_id: *mut c_void, mutability: u32,
        element_type: *mut c_void,
    ) -> *mut c_void;
    fn metal_package_builder_add_export_function(_: *mut c_void, name_ptr: *const c_char, name_len: usize, v: *mut c_void);
    fn metal_package_builder_add_export_kind(_: *mut c_void, name_ptr: *const c_char, name_len: usize, v: *mut c_void);
    fn metal_package_builder_add_extern_function(_: *mut c_void, name_ptr: *const c_char, name_len: usize, v: *mut c_void);
    fn metal_package_builder_add_extern_kind(_: *mut c_void, name_ptr: *const c_char, name_len: usize, v: *mut c_void);
    fn metal_package_builder_finish(_: *mut c_void) -> *mut c_void;

    fn metal_program_builder_new(_: *mut MetalCacheHandleRaw) -> *mut c_void;
    fn metal_program_builder_add_package(_: *mut c_void, coord: *mut c_void, package: *mut c_void);
    fn metal_program_builder_finish(_: *mut c_void) -> *mut c_void;
    fn metal_program_free(program: *mut c_void);
}

/// Owning wrapper around a MetalCache. Frees the underlying cache on drop.
pub struct MetalCache {
    raw: *mut MetalCacheHandleRaw,
}

impl MetalCache {
    pub fn new() -> Self {
        let raw = unsafe { metal_cache_new() };
        assert!(!raw.is_null(), "metal_cache_new returned null");
        MetalCache { raw }
    }

    pub fn raw(&self) -> *mut MetalCacheHandleRaw { self.raw }

    pub fn builtin_package_coord(&self) -> PackageCoord<'_> {
        unsafe { PackageCoord(NonNull::new(metal_cache_builtin_package_coord(self.raw)).unwrap(), PhantomData) }
    }
    pub fn rcimm_region_id(&self) -> RegionId<'_> {
        unsafe { RegionId(NonNull::new(metal_cache_rcimm_region_id(self.raw)).unwrap(), PhantomData) }
    }
    pub fn linear_region_id(&self) -> RegionId<'_> {
        unsafe { RegionId(NonNull::new(metal_cache_linear_region_id(self.raw)).unwrap(), PhantomData) }
    }
    pub fn mut_region_id(&self) -> RegionId<'_> {
        unsafe { RegionId(NonNull::new(metal_cache_mut_region_id(self.raw)).unwrap(), PhantomData) }
    }
    pub fn i32(&self) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_i32(self.raw)).unwrap(), PhantomData) }
    }
    pub fn i32_ref(&self) -> Reference<'_> {
        unsafe { Reference(NonNull::new(metal_cache_i32_ref(self.raw)).unwrap(), PhantomData) }
    }
    pub fn bool_kind(&self) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_bool(self.raw)).unwrap(), PhantomData) }
    }
    pub fn void_kind(&self) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_void(self.raw)).unwrap(), PhantomData) }
    }
    pub fn never_kind(&self) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_never(self.raw)).unwrap(), PhantomData) }
    }
    pub fn str_kind(&self) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_str(self.raw)).unwrap(), PhantomData) }
    }
    pub fn imm_str_ref(&self) -> Reference<'_> {
        unsafe { Reference(NonNull::new(metal_cache_imm_str_ref(self.raw)).unwrap(), PhantomData) }
    }

    pub fn get_package_coordinate(&self, project_name: &str, steps: &[&str]) -> PackageCoord<'_> {
        let step_ptrs: Vec<*const c_char> = steps.iter().map(|s| s.as_ptr() as *const c_char).collect();
        let step_lens: Vec<usize> = steps.iter().map(|s| s.len()).collect();
        unsafe {
            PackageCoord(
                NonNull::new(metal_cache_get_package_coordinate(
                    self.raw,
                    project_name.as_ptr() as *const c_char, project_name.len(),
                    step_ptrs.as_ptr(), step_lens.as_ptr(), step_ptrs.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn get_region_id(&self, pkg: PackageCoord<'_>, id: &str) -> RegionId<'_> {
        unsafe {
            RegionId(
                NonNull::new(metal_cache_get_region_id(
                    self.raw, pkg.0.as_ptr(),
                    id.as_ptr() as *const c_char, id.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn get_name(&self, pkg: PackageCoord<'_>, name: &str) -> Name<'_> {
        unsafe {
            Name(
                NonNull::new(metal_cache_get_name(
                    self.raw, pkg.0.as_ptr(),
                    name.as_ptr() as *const c_char, name.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn get_int(&self, region: RegionId<'_>, bits: i32) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_get_int(self.raw, region.0.as_ptr(), bits)).unwrap(), PhantomData) }
    }

    pub fn get_struct_kind(&self, name: Name<'_>) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_get_struct_kind(self.raw, name.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn get_interface_kind(&self, name: Name<'_>) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_get_interface_kind(self.raw, name.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn get_static_sized_array(&self, name: Name<'_>) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_get_static_sized_array(self.raw, name.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn get_runtime_sized_array(&self, name: Name<'_>) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_get_runtime_sized_array(self.raw, name.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn get_float(&self, region: RegionId<'_>) -> Kind<'_> {
        unsafe { Kind(NonNull::new(metal_cache_get_float(self.raw, region.0.as_ptr())).unwrap(), PhantomData) }
    }

    pub fn get_reference(&self, ownership: Ownership, location: Location, kind: Kind<'_>) -> Reference<'_> {
        unsafe {
            Reference(
                NonNull::new(metal_cache_get_reference(
                    self.raw, ownership as u32, location as u32, kind.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn get_prototype(&self, name: Name<'_>, return_type: Reference<'_>, param_types: &[Reference<'_>]) -> Prototype<'_> {
        let param_ptrs: Vec<*mut c_void> = param_types.iter().map(|r| r.0.as_ptr()).collect();
        unsafe {
            Prototype(
                NonNull::new(metal_cache_get_prototype(
                    self.raw, name.0.as_ptr(), return_type.0.as_ptr(),
                    param_ptrs.as_ptr(), param_ptrs.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn get_interface_method(&self, prototype: Prototype<'_>, virtual_param_index: i32) -> InterfaceMethod<'_> {
        unsafe {
            InterfaceMethod(
                NonNull::new(metal_cache_get_interface_method(
                    self.raw, prototype.0.as_ptr(), virtual_param_index,
                )).unwrap(),
                PhantomData,
            )
        }
    }

    // --- Non-interned constructors (each call allocates a fresh object) ---

    pub fn new_struct_member(
        &self, full_name: &str, name: &str, variability: Variability, ty: Reference<'_>,
    ) -> StructMember<'_> {
        unsafe {
            StructMember(
                NonNull::new(metal_struct_member_new(
                    full_name.as_ptr() as *const c_char, full_name.len(),
                    name.as_ptr() as *const c_char, name.len(),
                    variability as u32, ty.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn new_edge<'c>(
        &'c self, struct_kind: Kind<'c>, interface_kind: Kind<'c>,
        pairs: &[(InterfaceMethod<'c>, Prototype<'c>)],
    ) -> Edge<'c> {
        let methods: Vec<*mut c_void> = pairs.iter().map(|(im, _)| im.0.as_ptr()).collect();
        let protos: Vec<*mut c_void> = pairs.iter().map(|(_, p)| p.0.as_ptr()).collect();
        unsafe {
            Edge(
                NonNull::new(metal_edge_new(
                    struct_kind.0.as_ptr(), interface_kind.0.as_ptr(),
                    methods.as_ptr(), protos.as_ptr(), pairs.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn new_struct_def<'c>(
        &'c self, name: Name<'c>, struct_kind: Kind<'c>, region_id: RegionId<'c>,
        mutability: Mutability, edges: &[Edge<'c>], members: &[StructMember<'c>],
        weakability: Weakability,
    ) -> StructDef<'c> {
        let edge_ptrs: Vec<*mut c_void> = edges.iter().map(|e| e.0.as_ptr()).collect();
        let member_ptrs: Vec<*mut c_void> = members.iter().map(|m| m.0.as_ptr()).collect();
        unsafe {
            StructDef(
                NonNull::new(metal_struct_def_new(
                    name.0.as_ptr(), struct_kind.0.as_ptr(), region_id.0.as_ptr(),
                    mutability as u32,
                    edge_ptrs.as_ptr(), edge_ptrs.len(),
                    member_ptrs.as_ptr(), member_ptrs.len(),
                    weakability as u32,
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn new_interface_def<'c>(
        &'c self, name: Name<'c>, interface_kind: Kind<'c>, region_id: RegionId<'c>,
        mutability: Mutability, super_interfaces: &[Name<'c>], methods: &[InterfaceMethod<'c>],
        weakability: Weakability,
    ) -> InterfaceDef<'c> {
        let super_ptrs: Vec<*mut c_void> = super_interfaces.iter().map(|n| n.0.as_ptr()).collect();
        let method_ptrs: Vec<*mut c_void> = methods.iter().map(|m| m.0.as_ptr()).collect();
        unsafe {
            InterfaceDef(
                NonNull::new(metal_interface_def_new(
                    name.0.as_ptr(), interface_kind.0.as_ptr(), region_id.0.as_ptr(),
                    mutability as u32,
                    super_ptrs.as_ptr(), super_ptrs.len(),
                    method_ptrs.as_ptr(), method_ptrs.len(),
                    weakability as u32,
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn new_function<'c>(&'c self, prototype: Prototype<'c>, body: Option<Expression<'c>>) -> Function<'c> {
        let body_ptr = body.map(|e| e.0.as_ptr()).unwrap_or(std::ptr::null_mut());
        unsafe {
            Function(
                NonNull::new(metal_function_new(prototype.0.as_ptr(), body_ptr)).unwrap(),
                PhantomData,
            )
        }
    }

    // --- Expression constructors ---

    pub fn expr_constant_void(&self) -> Expression<'_> {
        unsafe { Expression(NonNull::new(metal_expr_constant_void()).unwrap(), PhantomData) }
    }
    pub fn expr_constant_int(&self, value: i64, bits: i32) -> Expression<'_> {
        unsafe { Expression(NonNull::new(metal_expr_constant_int(value, bits)).unwrap(), PhantomData) }
    }
    pub fn expr_constant_bool(&self, value: bool) -> Expression<'_> {
        unsafe { Expression(NonNull::new(metal_expr_constant_bool(value as i32)).unwrap(), PhantomData) }
    }
    pub fn expr_constant_f64(&self, value: f64) -> Expression<'_> {
        unsafe { Expression(NonNull::new(metal_expr_constant_f64(value)).unwrap(), PhantomData) }
    }
    pub fn expr_constant_str(&self, value: &str) -> Expression<'_> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_constant_str(value.as_ptr() as *const c_char, value.len())).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_break(&self) -> Expression<'_> {
        unsafe { Expression(NonNull::new(metal_expr_break()).unwrap(), PhantomData) }
    }
    pub fn expr_return<'c>(&'c self, source_expr: Expression<'c>, source_type: Reference<'c>) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_return(source_expr.0.as_ptr(), source_type.0.as_ptr())).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_block<'c>(&'c self, inner: Expression<'c>, inner_type: Reference<'c>) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_block(inner.0.as_ptr(), inner_type.0.as_ptr())).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_consecutor<'c>(&'c self, exprs: &[Expression<'c>]) -> Expression<'c> {
        let ptrs: Vec<*mut c_void> = exprs.iter().map(|e| e.0.as_ptr()).collect();
        unsafe {
            Expression(
                NonNull::new(metal_expr_consecutor(ptrs.as_ptr(), ptrs.len())).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn get_variable_id(&self, number: i32, height: i32, maybe_name: Option<&str>) -> VariableId<'_> {
        let (ptr, len) = match maybe_name {
            Some(n) => (n.as_ptr() as *const c_char, n.len()),
            None => (std::ptr::null(), 0),
        };
        unsafe {
            VariableId(
                NonNull::new(metal_cache_get_variable_id(self.raw, number, height, ptr, len)).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn get_local<'c>(&'c self, var_id: VariableId<'c>, ref_type: Reference<'c>) -> Local<'c> {
        unsafe {
            Local(
                NonNull::new(metal_cache_get_local(self.raw, var_id.0.as_ptr(), ref_type.0.as_ptr())).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_stackify<'c>(
        &'c self, source_expr: Expression<'c>, local: Local<'c>,
        known_live: bool, maybe_name: Option<&str>,
    ) -> Expression<'c> {
        let (ptr, len) = match maybe_name {
            Some(n) => (n.as_ptr() as *const c_char, n.len()),
            None => (std::ptr::null(), 0),
        };
        unsafe {
            Expression(
                NonNull::new(metal_expr_stackify(
                    source_expr.0.as_ptr(), local.0.as_ptr(),
                    known_live as i32, ptr, len,
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_unstackify<'c>(&'c self, local: Local<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_unstackify(local.0.as_ptr())).unwrap(), PhantomData) }
    }

    pub fn expr_local_load<'c>(
        &'c self, local: Local<'c>, target_ownership: Ownership, local_name: &str,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_local_load(
                    local.0.as_ptr(), target_ownership as u32,
                    local_name.as_ptr() as *const c_char, local_name.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_local_store<'c>(
        &'c self, local: Local<'c>, source_expr: Expression<'c>,
        local_name: &str, known_live: bool,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_local_store(
                    local.0.as_ptr(), source_expr.0.as_ptr(),
                    local_name.as_ptr() as *const c_char, local_name.len(),
                    known_live as i32,
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_discard<'c>(&'c self, source_expr: Expression<'c>, source_type: Reference<'c>) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_discard(source_expr.0.as_ptr(), source_type.0.as_ptr())).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_call<'c>(&'c self, function: Prototype<'c>, args: &[Expression<'c>]) -> Expression<'c> {
        let ptrs: Vec<*mut c_void> = args.iter().map(|e| e.0.as_ptr()).collect();
        unsafe {
            Expression(
                NonNull::new(metal_expr_call(function.0.as_ptr(), ptrs.as_ptr(), ptrs.len())).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_extern_call<'c>(
        &'c self, function: Prototype<'c>,
        args: &[Expression<'c>], arg_types: &[Reference<'c>],
    ) -> Expression<'c> {
        let arg_ptrs: Vec<*mut c_void> = args.iter().map(|e| e.0.as_ptr()).collect();
        let ty_ptrs: Vec<*mut c_void> = arg_types.iter().map(|r| r.0.as_ptr()).collect();
        unsafe {
            Expression(
                NonNull::new(metal_expr_extern_call(
                    function.0.as_ptr(),
                    arg_ptrs.as_ptr(), arg_ptrs.len(),
                    ty_ptrs.as_ptr(), ty_ptrs.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_if<'c>(
        &'c self, condition: Expression<'c>,
        then_expr: Expression<'c>, then_type: Reference<'c>,
        else_expr: Expression<'c>, else_type: Reference<'c>,
        common_supertype: Reference<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_if(
                    condition.0.as_ptr(),
                    then_expr.0.as_ptr(), then_type.0.as_ptr(),
                    else_expr.0.as_ptr(), else_type.0.as_ptr(),
                    common_supertype.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_while<'c>(&'c self, body: Expression<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_while(body.0.as_ptr())).unwrap(), PhantomData) }
    }

    pub fn expr_argument<'c>(&'c self, result_type: Reference<'c>, argument_index: i32) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_argument(result_type.0.as_ptr(), argument_index)).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_member_load<'c>(
        &'c self, struct_expr: Expression<'c>, struct_id: Kind<'c>,
        struct_type: Reference<'c>, struct_known_live: bool,
        member_index: i32, target_ownership: Ownership,
        expected_member_type: Reference<'c>, expected_result_type: Reference<'c>,
        member_name: &str,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_member_load(
                    struct_expr.0.as_ptr(), struct_id.0.as_ptr(),
                    struct_type.0.as_ptr(), struct_known_live as i32,
                    member_index, target_ownership as u32,
                    expected_member_type.0.as_ptr(), expected_result_type.0.as_ptr(),
                    member_name.as_ptr() as *const c_char, member_name.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_new_struct<'c>(
        &'c self, source_exprs: &[Expression<'c>], result_type: Reference<'c>,
    ) -> Expression<'c> {
        let ptrs: Vec<*mut c_void> = source_exprs.iter().map(|e| e.0.as_ptr()).collect();
        unsafe {
            Expression(
                NonNull::new(metal_expr_new_struct(
                    ptrs.as_ptr(), ptrs.len(), result_type.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_struct_to_interface_upcast<'c>(
        &'c self, source_expr: Expression<'c>,
        source_struct_type: Reference<'c>, source_struct_kind: Kind<'c>,
        target_interface_type: Reference<'c>, target_interface_kind: Kind<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_struct_to_interface_upcast(
                    source_expr.0.as_ptr(),
                    source_struct_type.0.as_ptr(), source_struct_kind.0.as_ptr(),
                    target_interface_type.0.as_ptr(), target_interface_kind.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_interface_to_interface_upcast<'c>(
        &'c self, source_expr: Expression<'c>,
        source_interface_type: Reference<'c>, source_interface_kind: Kind<'c>,
        target_interface_type: Reference<'c>, target_interface_kind: Kind<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_interface_to_interface_upcast(
                    source_expr.0.as_ptr(),
                    source_interface_type.0.as_ptr(), source_interface_kind.0.as_ptr(),
                    target_interface_type.0.as_ptr(), target_interface_kind.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_interface_call<'c>(
        &'c self, args: &[Expression<'c>], virtual_param_index: i32,
        interface_kind: Kind<'c>, index_in_edge: i32, function_type: Prototype<'c>,
    ) -> Expression<'c> {
        let ptrs: Vec<*mut c_void> = args.iter().map(|e| e.0.as_ptr()).collect();
        unsafe {
            Expression(
                NonNull::new(metal_expr_interface_call(
                    ptrs.as_ptr(), ptrs.len(),
                    virtual_param_index,
                    interface_kind.0.as_ptr(),
                    index_in_edge,
                    function_type.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    // --- Array ops ---

    pub fn expr_static_sized_array_load<'c>(
        &'c self, array_expr: Expression<'c>, array_type: Reference<'c>,
        array_kind: Kind<'c>, index_expr: Expression<'c>, result_type: Reference<'c>,
        target_ownership: Ownership, array_element_type: Reference<'c>, array_size: i32,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_static_sized_array_load(
                    array_expr.0.as_ptr(), array_type.0.as_ptr(),
                    array_kind.0.as_ptr(), 0,
                    index_expr.0.as_ptr(), result_type.0.as_ptr(),
                    target_ownership as u32, array_element_type.0.as_ptr(),
                    array_size,
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_static_sized_array_store<'c>(
        &'c self, array_expr: Expression<'c>, index_expr: Expression<'c>, source_expr: Expression<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_static_sized_array_store(
                    array_expr.0.as_ptr(), index_expr.0.as_ptr(), source_expr.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_runtime_sized_array_load<'c>(
        &'c self, array_expr: Expression<'c>, array_type: Reference<'c>,
        array_kind: Kind<'c>, index_expr: Expression<'c>, index_type: Reference<'c>,
        index_kind: Kind<'c>, result_type: Reference<'c>,
        target_ownership: Ownership, array_element_type: Reference<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_runtime_sized_array_load(
                    array_expr.0.as_ptr(), array_type.0.as_ptr(),
                    array_kind.0.as_ptr(), 0,
                    index_expr.0.as_ptr(), index_type.0.as_ptr(),
                    index_kind.0.as_ptr(), result_type.0.as_ptr(),
                    target_ownership as u32, array_element_type.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_runtime_sized_array_store<'c>(
        &'c self, array_expr: Expression<'c>, array_type: Reference<'c>,
        array_kind: Kind<'c>, index_expr: Expression<'c>, index_type: Reference<'c>,
        index_kind: Kind<'c>, source_expr: Expression<'c>, source_type: Reference<'c>,
        source_kind: Kind<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_runtime_sized_array_store(
                    array_expr.0.as_ptr(), array_type.0.as_ptr(),
                    array_kind.0.as_ptr(), 0,
                    index_expr.0.as_ptr(), index_type.0.as_ptr(),
                    index_kind.0.as_ptr(), source_expr.0.as_ptr(),
                    source_type.0.as_ptr(), source_kind.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_new_array_from_values<'c>(
        &'c self, source_exprs: &[Expression<'c>], array_ref_type: Reference<'c>, array_kind: Kind<'c>,
    ) -> Expression<'c> {
        let ptrs: Vec<*mut c_void> = source_exprs.iter().map(|e| e.0.as_ptr()).collect();
        unsafe {
            Expression(
                NonNull::new(metal_expr_new_array_from_values(
                    ptrs.as_ptr(), ptrs.len(), array_ref_type.0.as_ptr(), array_kind.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_new_mut_runtime_sized_array<'c>(
        &'c self, size_expr: Expression<'c>, size_type: Reference<'c>, size_kind: Kind<'c>,
        array_ref_type: Reference<'c>, element_type: Reference<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_new_mut_runtime_sized_array(
                    size_expr.0.as_ptr(), size_type.0.as_ptr(), size_kind.0.as_ptr(),
                    array_ref_type.0.as_ptr(), element_type.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_new_imm_runtime_sized_array<'c>(
        &'c self, size_expr: Expression<'c>, size_type: Reference<'c>, size_kind: Kind<'c>,
        generator_expr: Expression<'c>, generator_type: Reference<'c>, generator_kind: Kind<'c>,
        generator_method: Prototype<'c>, array_ref_type: Reference<'c>, element_type: Reference<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_new_imm_runtime_sized_array(
                    size_expr.0.as_ptr(), size_type.0.as_ptr(), size_kind.0.as_ptr(),
                    generator_expr.0.as_ptr(), generator_type.0.as_ptr(), generator_kind.0.as_ptr(),
                    generator_method.0.as_ptr(), 0,
                    array_ref_type.0.as_ptr(), element_type.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_static_array_from_callable<'c>(
        &'c self, generator_expr: Expression<'c>, generator_type: Reference<'c>, generator_kind: Kind<'c>,
        generator_method: Prototype<'c>, array_ref_type: Reference<'c>, element_type: Reference<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_static_array_from_callable(
                    generator_expr.0.as_ptr(), generator_type.0.as_ptr(), generator_kind.0.as_ptr(),
                    generator_method.0.as_ptr(), 0,
                    array_ref_type.0.as_ptr(), element_type.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_push_runtime_sized_array<'c>(
        &'c self, array_expr: Expression<'c>, array_type: Reference<'c>,
        newcomer_expr: Expression<'c>, newcomer_type: Reference<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_push_runtime_sized_array(
                    array_expr.0.as_ptr(), array_type.0.as_ptr(), 0,
                    newcomer_expr.0.as_ptr(), newcomer_type.0.as_ptr(), 0,
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_pop_runtime_sized_array<'c>(
        &'c self, array_expr: Expression<'c>, array_type: Reference<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_pop_runtime_sized_array(
                    array_expr.0.as_ptr(), array_type.0.as_ptr(), 0,
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_destroy_static_sized_array_into_locals<'c>(
        &'c self, array_expr: Expression<'c>, array_type: Reference<'c>,
        local_types: &[Reference<'c>], local_indices: &[Local<'c>],
    ) -> Expression<'c> {
        let ty_ptrs: Vec<*mut c_void> = local_types.iter().map(|r| r.0.as_ptr()).collect();
        let loc_ptrs: Vec<*mut c_void> = local_indices.iter().map(|l| l.0.as_ptr()).collect();
        unsafe {
            Expression(
                NonNull::new(metal_expr_destroy_static_sized_array_into_locals(
                    array_expr.0.as_ptr(), array_type.0.as_ptr(),
                    ty_ptrs.as_ptr(), ty_ptrs.len(),
                    loc_ptrs.as_ptr(), loc_ptrs.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_destroy_static_sized_array_into_function<'c>(
        &'c self, array_expr: Expression<'c>, array_type: Reference<'c>, array_kind: Kind<'c>,
        consumer_expr: Expression<'c>, consumer_type: Reference<'c>,
        consumer_method: Prototype<'c>, array_element_type: Reference<'c>, array_size: i32,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_destroy_static_sized_array_into_function(
                    array_expr.0.as_ptr(), array_type.0.as_ptr(), array_kind.0.as_ptr(),
                    consumer_expr.0.as_ptr(), consumer_type.0.as_ptr(),
                    consumer_method.0.as_ptr(), 0,
                    array_element_type.0.as_ptr(), array_size,
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_destroy_imm_runtime_sized_array<'c>(
        &'c self, array_expr: Expression<'c>, array_type: Reference<'c>, array_kind: Kind<'c>,
        consumer_expr: Expression<'c>, consumer_type: Reference<'c>, consumer_kind: Kind<'c>,
        consumer_method: Prototype<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_destroy_imm_runtime_sized_array(
                    array_expr.0.as_ptr(), array_type.0.as_ptr(), array_kind.0.as_ptr(),
                    consumer_expr.0.as_ptr(), consumer_type.0.as_ptr(), consumer_kind.0.as_ptr(),
                    consumer_method.0.as_ptr(), 0,
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_destroy_mut_runtime_sized_array<'c>(
        &'c self, array_expr: Expression<'c>, array_type: Reference<'c>, array_kind: Kind<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_destroy_mut_runtime_sized_array(
                    array_expr.0.as_ptr(), array_type.0.as_ptr(), array_kind.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_weak_alias<'c>(
        &'c self, source_expr: Expression<'c>, source_type: Reference<'c>,
        source_kind: Kind<'c>, result_type: Reference<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_weak_alias(
                    source_expr.0.as_ptr(), source_type.0.as_ptr(),
                    source_kind.0.as_ptr(), result_type.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_lock_weak<'c>(
        &'c self, source_expr: Expression<'c>, source_type: Reference<'c>,
        some_constructor: Prototype<'c>, some_type: Reference<'c>, some_kind: Kind<'c>,
        none_constructor: Prototype<'c>, none_type: Reference<'c>, none_kind: Kind<'c>,
        result_opt_type: Reference<'c>, result_opt_kind: Kind<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_lock_weak(
                    source_expr.0.as_ptr(), source_type.0.as_ptr(), 0,
                    some_constructor.0.as_ptr(), some_type.0.as_ptr(), some_kind.0.as_ptr(),
                    none_constructor.0.as_ptr(), none_type.0.as_ptr(), none_kind.0.as_ptr(),
                    result_opt_type.0.as_ptr(), result_opt_kind.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn expr_as_subtype<'c>(
        &'c self, source_expr: Expression<'c>, source_type: Reference<'c>,
        target_kind: Kind<'c>,
        ok_constructor: Prototype<'c>, ok_type: Reference<'c>, ok_kind: Kind<'c>,
        err_constructor: Prototype<'c>, err_type: Reference<'c>, err_kind: Kind<'c>,
        result_result_type: Reference<'c>, result_result_kind: Kind<'c>,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_as_subtype(
                    source_expr.0.as_ptr(), source_type.0.as_ptr(), 0,
                    target_kind.0.as_ptr(),
                    ok_constructor.0.as_ptr(), ok_type.0.as_ptr(), ok_kind.0.as_ptr(),
                    err_constructor.0.as_ptr(), err_type.0.as_ptr(), err_kind.0.as_ptr(),
                    result_result_type.0.as_ptr(), result_result_kind.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_destroy<'c>(
        &'c self, struct_expr: Expression<'c>, struct_type: Reference<'c>,
        local_types: &[Reference<'c>], local_indices: &[Local<'c>],
        locals_known_lives: &[bool],
    ) -> Expression<'c> {
        let ty_ptrs: Vec<*mut c_void> = local_types.iter().map(|r| r.0.as_ptr()).collect();
        let loc_ptrs: Vec<*mut c_void> = local_indices.iter().map(|l| l.0.as_ptr()).collect();
        let lives: Vec<i32> = locals_known_lives.iter().map(|b| *b as i32).collect();
        unsafe {
            Expression(
                NonNull::new(metal_expr_destroy(
                    struct_expr.0.as_ptr(), struct_type.0.as_ptr(),
                    ty_ptrs.as_ptr(), ty_ptrs.len(),
                    loc_ptrs.as_ptr(), loc_ptrs.len(),
                    lives.as_ptr(), lives.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_member_store<'c>(
        &'c self, struct_expr: Expression<'c>, struct_type: Reference<'c>,
        struct_known_live: bool, member_index: i32,
        source_expr: Expression<'c>, result_type: Reference<'c>, member_name: &str,
    ) -> Expression<'c> {
        unsafe {
            Expression(
                NonNull::new(metal_expr_member_store(
                    struct_expr.0.as_ptr(), struct_type.0.as_ptr(),
                    struct_known_live as i32, member_index,
                    source_expr.0.as_ptr(), result_type.0.as_ptr(),
                    member_name.as_ptr() as *const c_char, member_name.len(),
                )).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn expr_array_length<'c>(&'c self, source_expr: Expression<'c>, source_type: Reference<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_array_length(source_expr.0.as_ptr(), source_type.0.as_ptr(), 0)).unwrap(), PhantomData) }
    }
    pub fn expr_array_capacity<'c>(&'c self, source_expr: Expression<'c>, source_type: Reference<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_array_capacity(source_expr.0.as_ptr(), source_type.0.as_ptr(), 0)).unwrap(), PhantomData) }
    }
    pub fn expr_pre_check_borrow<'c>(&'c self, source_expr: Expression<'c>, source_result_type: Reference<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_pre_check_borrow(source_expr.0.as_ptr(), source_result_type.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn expr_mutabilify<'c>(&'c self, source_expr: Expression<'c>, source_type: Reference<'c>, result_type: Reference<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_mutabilify(source_expr.0.as_ptr(), source_type.0.as_ptr(), result_type.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn expr_immutabilify<'c>(&'c self, source_expr: Expression<'c>, source_type: Reference<'c>, result_type: Reference<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_immutabilify(source_expr.0.as_ptr(), source_type.0.as_ptr(), result_type.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn expr_is_same_instance<'c>(&'c self, left_expr: Expression<'c>, left_type: Reference<'c>, right_expr: Expression<'c>, right_type: Reference<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_is_same_instance(left_expr.0.as_ptr(), left_type.0.as_ptr(), right_expr.0.as_ptr(), right_type.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn expr_borrow_to_pointer<'c>(&'c self, source_expr: Expression<'c>, result_type: Reference<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_borrow_to_pointer(source_expr.0.as_ptr(), result_type.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn expr_pointer_to_borrow<'c>(&'c self, source_expr: Expression<'c>, result_type: Reference<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_pointer_to_borrow(source_expr.0.as_ptr(), result_type.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn expr_narrow_permission<'c>(&'c self, source_expr: Expression<'c>) -> Expression<'c> {
        unsafe { Expression(NonNull::new(metal_expr_narrow_permission(source_expr.0.as_ptr())).unwrap(), PhantomData) }
    }
    pub fn expr_restackify<'c>(&'c self, source_expr: Expression<'c>, local: Local<'c>, known_live: bool, maybe_name: Option<&str>) -> Expression<'c> {
        let (ptr, len) = match maybe_name {
            Some(n) => (n.as_ptr() as *const c_char, n.len()),
            None => (std::ptr::null(), 0),
        };
        unsafe {
            Expression(
                NonNull::new(metal_expr_restackify(source_expr.0.as_ptr(), local.0.as_ptr(), known_live as i32, ptr, len)).unwrap(),
                PhantomData,
            )
        }
    }

    pub fn new_package_builder<'c>(&'c self, package_coord: PackageCoord<'c>) -> PackageBuilder<'c> {
        let raw = unsafe { metal_package_builder_new(self.raw, package_coord.0.as_ptr()) };
        assert!(!raw.is_null());
        PackageBuilder { raw, _cache: PhantomData }
    }

    pub fn new_program_builder<'c>(&'c self) -> ProgramBuilder<'c> {
        let raw = unsafe { metal_program_builder_new(self.raw) };
        assert!(!raw.is_null());
        ProgramBuilder { raw, _cache: PhantomData }
    }
}

impl Drop for MetalCache {
    fn drop(&mut self) {
        unsafe { metal_cache_free(self.raw) };
    }
}

/// Accumulates Package contents (functions, structs, interfaces, exports,
/// externs) for one PackageCoordinate. `finish` consumes the builder and
/// returns the constructed Package; the builder is also freed.
pub struct PackageBuilder<'cache> {
    raw: *mut c_void,
    _cache: PhantomData<&'cache ()>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct Package<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct StaticSizedArrayDef<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct RuntimeSizedArrayDef<'cache>(NonNull<c_void>, PhantomData<&'cache ()>);

impl MetalCache {
    pub fn new_static_sized_array_def<'c>(
        &'c self, name: Name<'c>, kind: Kind<'c>, size: i32,
        region_id: RegionId<'c>, mutability: Mutability, variability: Variability,
        element_type: Reference<'c>,
    ) -> StaticSizedArrayDef<'c> {
        unsafe {
            StaticSizedArrayDef(
                NonNull::new(metal_static_sized_array_def_new(
                    name.0.as_ptr(), kind.0.as_ptr(), size,
                    region_id.0.as_ptr(), mutability as u32, variability as u32,
                    element_type.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
    pub fn new_runtime_sized_array_def<'c>(
        &'c self, name: Name<'c>, kind: Kind<'c>,
        region_id: RegionId<'c>, mutability: Mutability,
        element_type: Reference<'c>,
    ) -> RuntimeSizedArrayDef<'c> {
        unsafe {
            RuntimeSizedArrayDef(
                NonNull::new(metal_runtime_sized_array_def_new(
                    name.0.as_ptr(), kind.0.as_ptr(),
                    region_id.0.as_ptr(), mutability as u32,
                    element_type.0.as_ptr(),
                )).unwrap(),
                PhantomData,
            )
        }
    }
}

impl<'cache> PackageBuilder<'cache> {
    pub fn add_interface(&self, name: &str, v: InterfaceDef<'cache>) {
        unsafe { metal_package_builder_add_interface(self.raw, name.as_ptr() as *const c_char, name.len(), v.0.as_ptr()) }
    }
    pub fn add_struct(&self, name: &str, v: StructDef<'cache>) {
        unsafe { metal_package_builder_add_struct(self.raw, name.as_ptr() as *const c_char, name.len(), v.0.as_ptr()) }
    }
    pub fn add_function(&self, name: &str, v: Function<'cache>) {
        unsafe { metal_package_builder_add_function(self.raw, name.as_ptr() as *const c_char, name.len(), v.0.as_ptr()) }
    }
    pub fn add_static_sized_array(&self, name: &str, v: StaticSizedArrayDef<'cache>) {
        unsafe { metal_package_builder_add_static_sized_array(self.raw, name.as_ptr() as *const c_char, name.len(), v.0.as_ptr()) }
    }
    pub fn add_runtime_sized_array(&self, name: &str, v: RuntimeSizedArrayDef<'cache>) {
        unsafe { metal_package_builder_add_runtime_sized_array(self.raw, name.as_ptr() as *const c_char, name.len(), v.0.as_ptr()) }
    }
    pub fn add_export_function(&self, name: &str, v: Prototype<'cache>) {
        unsafe { metal_package_builder_add_export_function(self.raw, name.as_ptr() as *const c_char, name.len(), v.0.as_ptr()) }
    }
    pub fn add_export_kind(&self, name: &str, v: Kind<'cache>) {
        unsafe { metal_package_builder_add_export_kind(self.raw, name.as_ptr() as *const c_char, name.len(), v.0.as_ptr()) }
    }
    pub fn add_extern_function(&self, name: &str, v: Prototype<'cache>) {
        unsafe { metal_package_builder_add_extern_function(self.raw, name.as_ptr() as *const c_char, name.len(), v.0.as_ptr()) }
    }
    pub fn add_extern_kind(&self, name: &str, v: Kind<'cache>) {
        unsafe { metal_package_builder_add_extern_kind(self.raw, name.as_ptr() as *const c_char, name.len(), v.0.as_ptr()) }
    }

    pub fn finish(self) -> Package<'cache> {
        let pkg = unsafe { metal_package_builder_finish(self.raw) };
        // raw is consumed by finish (which deletes the builder), so we MUST
        // NOT run our Drop after this. Forget self.
        std::mem::forget(self);
        Package(NonNull::new(pkg).unwrap(), PhantomData)
    }
}

impl<'cache> Drop for PackageBuilder<'cache> {
    fn drop(&mut self) {
        // Builder was constructed but never finished — finish to release the
        // C++ allocation, then discard the resulting Package. Not ideal (the
        // Package is leaked) but only happens on panic before finish().
        let pkg = unsafe { metal_package_builder_finish(self.raw) };
        // Free the package via the program_free path is overkill for a
        // single package; just leak. (Tests should always call .finish().)
        let _ = pkg;
    }
}

pub struct ProgramBuilder<'cache> {
    raw: *mut c_void,
    _cache: PhantomData<&'cache ()>,
}

pub struct Program<'cache> {
    raw: *mut c_void,
    _cache: PhantomData<&'cache ()>,
}

impl<'cache> ProgramBuilder<'cache> {
    pub fn add_package(&self, coord: PackageCoord<'cache>, package: Package<'cache>) {
        unsafe { metal_program_builder_add_package(self.raw, coord.0.as_ptr(), package.0.as_ptr()) }
    }

    pub fn finish(self) -> Program<'cache> {
        let prog = unsafe { metal_program_builder_finish(self.raw) };
        std::mem::forget(self);
        Program { raw: NonNull::new(prog).unwrap().as_ptr(), _cache: PhantomData }
    }
}

impl<'cache> Drop for ProgramBuilder<'cache> {
    fn drop(&mut self) {
        let prog = unsafe { metal_program_builder_finish(self.raw) };
        unsafe { metal_program_free(prog) };
    }
}

impl<'cache> Program<'cache> {
    pub fn raw(&self) -> *mut c_void { self.raw }
}

impl<'cache> Drop for Program<'cache> {
    fn drop(&mut self) {
        unsafe { metal_program_free(self.raw) };
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn singletons_match_constructor_inits() {
        let cache = MetalCache::new();

        // i32 singleton is interned as `getInt(rcImmRegionId, 32)` in MetalCache's ctor.
        let i32_via_singleton = cache.i32();
        let i32_via_get = cache.get_int(cache.rcimm_region_id(), 32);
        assert_eq!(i32_via_singleton, i32_via_get, "i32 singleton must dedupe with get_int(rcimm, 32)");

        // Re-getting the same int returns the same pointer (interning).
        let i32_again = cache.get_int(cache.rcimm_region_id(), 32);
        assert_eq!(i32_via_get, i32_again);

        // Different bit width = different Kind.
        let i64 = cache.get_int(cache.rcimm_region_id(), 64);
        assert_ne!(i32_via_get, i64);
    }

    #[test]
    fn name_and_struct_kind_intern() {
        let cache = MetalCache::new();
        let pkg = cache.get_package_coordinate("test", &[]);
        let n1 = cache.get_name(pkg, "Widget");
        let n2 = cache.get_name(pkg, "Widget");
        assert_eq!(n1, n2, "names must intern by (package, string)");

        let n3 = cache.get_name(pkg, "Other");
        assert_ne!(n1, n3);

        let s1 = cache.get_struct_kind(n1);
        let s2 = cache.get_struct_kind(n1);
        assert_eq!(s1, s2, "struct kinds must intern by Name pointer");
    }

    #[test]
    fn reference_interns_on_triple() {
        let cache = MetalCache::new();
        let r1 = cache.get_reference(Ownership::MutableShare, Location::Inline, cache.i32());
        let r2 = cache.get_reference(Ownership::MutableShare, Location::Inline, cache.i32());
        assert_eq!(r1, r2);

        // i32Ref singleton uses exactly this triple.
        assert_eq!(r1, cache.i32_ref());

        // Different (ownership, location) → different Reference. Use the
        // imm-str triple, which satisfies Backend's INLINE/YONDER assertions.
        let r3 = cache.get_reference(Ownership::ImmutableShare, Location::Yonder, cache.str_kind());
        assert_ne!(r1, r3);
        assert_eq!(r3, cache.imm_str_ref());
    }

    #[test]
    fn prototype_interns_on_signature() {
        let cache = MetalCache::new();
        let pkg = cache.get_package_coordinate("test", &[]);
        let main_name = cache.get_name(pkg, "main");
        let p1 = cache.get_prototype(main_name, cache.i32_ref(), &[]);
        let p2 = cache.get_prototype(main_name, cache.i32_ref(), &[]);
        assert_eq!(p1, p2);
    }

    #[test]
    fn interface_method_interns() {
        let cache = MetalCache::new();
        let pkg = cache.get_package_coordinate("test", &[]);
        let foo = cache.get_prototype(cache.get_name(pkg, "foo"), cache.i32_ref(), &[]);
        let m1 = cache.get_interface_method(foo, 0);
        let m2 = cache.get_interface_method(foo, 0);
        assert_eq!(m1, m2);
        let m3 = cache.get_interface_method(foo, 1);
        assert_ne!(m1, m3);
    }

    #[test]
    fn non_interned_constructors_allocate_fresh_each_time() {
        let cache = MetalCache::new();
        let i32_ref = cache.i32_ref();
        let m1 = cache.new_struct_member("x", "x", Variability::Final, i32_ref);
        let m2 = cache.new_struct_member("x", "x", Variability::Final, i32_ref);
        // Members are not interned: same args, different pointers.
        assert_ne!(m1, m2);
    }

    #[test]
    fn build_empty_program() {
        let cache = MetalCache::new();
        let coord = cache.get_package_coordinate("test", &[]);
        let pkg = cache.new_package_builder(coord).finish();
        let pb = cache.new_program_builder();
        pb.add_package(coord, pkg);
        let _program = pb.finish();
        // Drop frees the program; merely getting here exercises the full
        // builder→Program→free path.
    }

    #[test]
    fn build_hello_world_program_structure() {
        // Mirrors the H-AST shape of `exported func main() int { return 7; }`:
        //     Function {
        //       prototype: main(): i32,
        //       body: Block(Return(ConstantInt(7, 32)), i32_ref),
        //     }
        // Constructs the whole tree via FFI, threads it through the Program
        // builder, and frees it. Doesn't execute codegen — that's the next
        // milestone, which needs the `backend_compile_program` entry.
        let cache = MetalCache::new();
        let coord = cache.get_package_coordinate("test", &[]);
        let main_name = cache.get_name(coord, "main");
        let proto = cache.get_prototype(main_name, cache.i32_ref(), &[]);

        let seven = cache.expr_constant_int(7, 32);
        let ret = cache.expr_return(seven, cache.i32_ref());
        let body = cache.expr_block(ret, cache.i32_ref());
        let func = cache.new_function(proto, Some(body));

        let pb = cache.new_package_builder(coord);
        pb.add_function("main", func);
        pb.add_export_function("main", proto);
        let pkg = pb.finish();

        let progb = cache.new_program_builder();
        progb.add_package(coord, pkg);
        let _program = progb.finish();
    }

    #[test]
    fn build_program_with_one_function_no_body() {
        let cache = MetalCache::new();
        let coord = cache.get_package_coordinate("test", &[]);
        let main_name = cache.get_name(coord, "main");
        let proto = cache.get_prototype(main_name, cache.i32_ref(), &[]);
        // Body is None — safe because no test here runs codegen, which is the
        // only consumer that would dereference Function.body.
        let func = cache.new_function(proto, None);

        let pb = cache.new_package_builder(coord);
        pb.add_function("main", func);
        pb.add_export_function("main", proto);
        let pkg = pb.finish();

        let progb = cache.new_program_builder();
        progb.add_package(coord, pkg);
        let _program = progb.finish();
    }
}
