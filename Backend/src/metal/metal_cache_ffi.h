// C ABI for MetalCache and its interned IR node types. Consumed by
// FrontendRust's `backend_ffi::metal_cache` module to populate the cache
// in-process from the Rust H-AST, replacing the JSON+readjson.cpp path.
//
// Conventions:
//   - All handle types are opaque pointers (`void*` underneath, kept distinct
//     for type safety on the Rust side).
//   - Strings pass as `(const char* ptr, size_t len)` and are NOT NUL-terminated
//     (callers must pass length explicitly).
//   - Enum values mirror the numeric encoding of the C++ `enum class` in
//     metal/types.h (Ownership, Location).
//   - Returned interned pointers are owned by the MetalCache; do NOT free.

#ifndef METAL_CACHE_FFI_H_
#define METAL_CACHE_FFI_H_

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct MetalCacheHandle      MetalCacheHandle;
typedef struct PackageCoordHandle    PackageCoordHandle;
typedef struct RegionIdHandle        RegionIdHandle;
typedef struct NameHandle            NameHandle;
typedef struct KindHandle            KindHandle;       // base; Int*, Bool*, StructKind*, ... all use this
typedef struct ReferenceHandle       ReferenceHandle;
typedef struct PrototypeHandle       PrototypeHandle;
typedef struct VariableIdHandle      VariableIdHandle;
typedef struct LocalHandle           LocalHandle;
typedef struct InterfaceMethodHandle InterfaceMethodHandle;
typedef struct StructMemberHandle    StructMemberHandle;
typedef struct EdgeHandle            EdgeHandle;
typedef struct StructDefHandle       StructDefHandle;
typedef struct InterfaceDefHandle    InterfaceDefHandle;
typedef struct FunctionHandle        FunctionHandle;
typedef struct ExpressionHandle      ExpressionHandle;   // opaque; populated later
typedef struct PackageHandle         PackageHandle;
typedef struct ProgramHandle         ProgramHandle;
typedef struct StaticSizedArrayDefHandle StaticSizedArrayDefHandle;
typedef struct RuntimeSizedArrayDefHandle RuntimeSizedArrayDefHandle;
typedef struct PackageBuilderHandle  PackageBuilderHandle;
typedef struct ProgramBuilderHandle  ProgramBuilderHandle;

// --- Lifecycle ---

MetalCacheHandle* metal_cache_new(void);
void              metal_cache_free(MetalCacheHandle*);

// --- Singletons (mirror the fields auto-initialized in MetalCache's ctor) ---

PackageCoordHandle* metal_cache_builtin_package_coord(MetalCacheHandle*);
RegionIdHandle*     metal_cache_rcimm_region_id(MetalCacheHandle*);
RegionIdHandle*     metal_cache_linear_region_id(MetalCacheHandle*);
RegionIdHandle*     metal_cache_mut_region_id(MetalCacheHandle*);

KindHandle*         metal_cache_i32(MetalCacheHandle*);
ReferenceHandle*    metal_cache_i32_ref(MetalCacheHandle*);
KindHandle*         metal_cache_i64(MetalCacheHandle*);
ReferenceHandle*    metal_cache_i64_ref(MetalCacheHandle*);
KindHandle*         metal_cache_bool(MetalCacheHandle*);
ReferenceHandle*    metal_cache_bool_ref(MetalCacheHandle*);
KindHandle*         metal_cache_float(MetalCacheHandle*);
ReferenceHandle*    metal_cache_float_ref(MetalCacheHandle*);
KindHandle*         metal_cache_str(MetalCacheHandle*);
ReferenceHandle*    metal_cache_mut_str_ref(MetalCacheHandle*);
ReferenceHandle*    metal_cache_imm_str_ref(MetalCacheHandle*);
KindHandle*         metal_cache_never(MetalCacheHandle*);
ReferenceHandle*    metal_cache_never_ref(MetalCacheHandle*);
KindHandle*         metal_cache_void(MetalCacheHandle*);
ReferenceHandle*    metal_cache_void_ref(MetalCacheHandle*);

// --- Interned getters ---
//
// Each mirrors a `MetalCache::get*` method. Calling with structurally-equal
// arguments returns the same pointer (interning). The C++ side hash-cons by
// pointer identity for handles (Name*, Kind*, RegionId*, ...) and by value
// for ints/strings.

PackageCoordHandle* metal_cache_get_package_coordinate(
    MetalCacheHandle*,
    const char* project_name_ptr, size_t project_name_len,
    const char* const* steps_ptrs, const size_t* steps_lens, size_t steps_count);

RegionIdHandle* metal_cache_get_region_id(
    MetalCacheHandle*,
    PackageCoordHandle* package_coord,
    const char* id_ptr, size_t id_len);

NameHandle* metal_cache_get_name(
    MetalCacheHandle*,
    PackageCoordHandle* package_coord,
    const char* name_ptr, size_t name_len);

KindHandle* metal_cache_get_int(MetalCacheHandle*, RegionIdHandle* region, int32_t bits);
KindHandle* metal_cache_get_bool(MetalCacheHandle*, RegionIdHandle* region);
KindHandle* metal_cache_get_str(MetalCacheHandle*, RegionIdHandle* region);
KindHandle* metal_cache_get_float(MetalCacheHandle*, RegionIdHandle* region);
KindHandle* metal_cache_get_void(MetalCacheHandle*, RegionIdHandle* region);
KindHandle* metal_cache_get_never(MetalCacheHandle*, RegionIdHandle* region);

KindHandle* metal_cache_get_struct_kind(MetalCacheHandle*, NameHandle* name);
KindHandle* metal_cache_get_interface_kind(MetalCacheHandle*, NameHandle* name);
KindHandle* metal_cache_get_static_sized_array(MetalCacheHandle*, NameHandle* name);
KindHandle* metal_cache_get_runtime_sized_array(MetalCacheHandle*, NameHandle* name);

// Ownership encoding (matches enum class Ownership in metal/types.h):
//   0=OWN, 1=MUTABLE_BORROW, 2=IMMUTABLE_BORROW, 3=WEAK,
//   4=MUTABLE_SHARE, 5=IMMUTABLE_SHARE
// Location encoding: 0=INLINE, 1=YONDER
ReferenceHandle* metal_cache_get_reference(
    MetalCacheHandle*,
    uint32_t ownership, uint32_t location, KindHandle* kind);

PrototypeHandle* metal_cache_get_prototype(
    MetalCacheHandle*,
    NameHandle* name,
    ReferenceHandle* return_type,
    ReferenceHandle* const* param_types, size_t param_count);

InterfaceMethodHandle* metal_cache_get_interface_method(
    MetalCacheHandle*, PrototypeHandle* prototype, int32_t virtual_param_index);

// Local + VariableId are interned by readjson via the cache's internal maps;
// these expose the same shape under MetalCache's `variableIds` / `locals`.
VariableIdHandle* metal_cache_get_variable_id(
    MetalCacheHandle*, int32_t number, int32_t height,
    const char* maybe_name_ptr, size_t maybe_name_len);
LocalHandle* metal_cache_get_local(
    MetalCacheHandle*, VariableIdHandle*, ReferenceHandle*);

// --- Non-interned constructors (raw `new` on the C++ side) ---
//
// Each constructed object's identity is its pointer; constructors do not
// dedupe. Callers must construct each logical entity exactly once.

// Variability encoding (matches enum class Variability in metal/types.h):
//   0=FINAL, 1=VARYING
StructMemberHandle* metal_struct_member_new(
    const char* full_name_ptr, size_t full_name_len,
    const char* name_ptr, size_t name_len,
    uint32_t variability,
    ReferenceHandle* type);

EdgeHandle* metal_edge_new(
    KindHandle* struct_kind,
    KindHandle* interface_kind,
    InterfaceMethodHandle* const* interface_methods,
    PrototypeHandle* const* struct_prototypes,
    size_t pair_count);

// Mutability encoding: 0=IMMUTABLE, 1=MUTABLE
// Weakability encoding: 0=WEAKABLE, 1=NON_WEAKABLE
StructDefHandle* metal_struct_def_new(
    NameHandle* name,
    KindHandle* struct_kind,
    RegionIdHandle* region_id,
    uint32_t mutability,
    EdgeHandle* const* edges, size_t edge_count,
    StructMemberHandle* const* members, size_t member_count,
    uint32_t weakability);

InterfaceDefHandle* metal_interface_def_new(
    NameHandle* name,
    KindHandle* interface_kind,
    RegionIdHandle* region_id,
    uint32_t mutability,
    NameHandle* const* super_interfaces, size_t super_count,
    InterfaceMethodHandle* const* methods, size_t method_count,
    uint32_t weakability);

FunctionHandle* metal_function_new(PrototypeHandle* prototype, ExpressionHandle* body);

// --- Expression constructors (one per ExpressionH variant) ---
//
// Each produces a freshly-allocated Expression*; no interning. The Function /
// Block / Consecutor / Return / etc. that take Expression* assume single
// ownership and never share children, mirroring Backend's existing IR.

ExpressionHandle* metal_expr_constant_void(void);
ExpressionHandle* metal_expr_constant_int(int64_t value, int32_t bits);
ExpressionHandle* metal_expr_constant_bool(int32_t value /* 0 or 1 */);
ExpressionHandle* metal_expr_constant_f64(double value);
ExpressionHandle* metal_expr_constant_str(const char* value_ptr, size_t value_len);
ExpressionHandle* metal_expr_break(void);
ExpressionHandle* metal_expr_return(ExpressionHandle* source_expr, ReferenceHandle* source_type);
ExpressionHandle* metal_expr_block(ExpressionHandle* inner, ReferenceHandle* inner_type);
ExpressionHandle* metal_expr_consecutor(ExpressionHandle* const* exprs, size_t expr_count);

ExpressionHandle* metal_expr_stackify(
    ExpressionHandle* source_expr, LocalHandle* local,
    int32_t known_live, const char* maybe_name_ptr, size_t maybe_name_len);
ExpressionHandle* metal_expr_unstackify(LocalHandle* local);
ExpressionHandle* metal_expr_local_load(
    LocalHandle* local, uint32_t target_ownership,
    const char* local_name_ptr, size_t local_name_len);
ExpressionHandle* metal_expr_local_store(
    LocalHandle* local, ExpressionHandle* source_expr,
    const char* local_name_ptr, size_t local_name_len, int32_t known_live);
ExpressionHandle* metal_expr_discard(ExpressionHandle* source_expr, ReferenceHandle* source_type);
ExpressionHandle* metal_expr_call(
    PrototypeHandle* function,
    ExpressionHandle* const* arg_exprs, size_t arg_count);
ExpressionHandle* metal_expr_extern_call(
    PrototypeHandle* function,
    ExpressionHandle* const* arg_exprs, size_t arg_count,
    ReferenceHandle* const* arg_types, size_t arg_type_count);
ExpressionHandle* metal_expr_if(
    ExpressionHandle* condition,
    ExpressionHandle* then_expr, ReferenceHandle* then_type,
    ExpressionHandle* else_expr, ReferenceHandle* else_type,
    ReferenceHandle* common_supertype);
ExpressionHandle* metal_expr_while(
    ExpressionHandle* body);
ExpressionHandle* metal_expr_argument(ReferenceHandle* result_type, int32_t argument_index);

ExpressionHandle* metal_expr_member_load(
    ExpressionHandle* struct_expr, KindHandle* struct_id,
    ReferenceHandle* struct_type, int32_t struct_known_live,
    int32_t member_index, uint32_t target_ownership,
    ReferenceHandle* expected_member_type, ReferenceHandle* expected_result_type,
    const char* member_name_ptr, size_t member_name_len);

ExpressionHandle* metal_expr_new_struct(
    ExpressionHandle* const* source_exprs, size_t source_count,
    ReferenceHandle* result_type);

ExpressionHandle* metal_expr_struct_to_interface_upcast(
    ExpressionHandle* source_expr,
    ReferenceHandle* source_struct_type, KindHandle* source_struct_kind,
    ReferenceHandle* target_interface_type, KindHandle* target_interface_kind);

ExpressionHandle* metal_expr_interface_to_interface_upcast(
    ExpressionHandle* source_expr,
    ReferenceHandle* source_interface_type, KindHandle* source_interface_kind,
    ReferenceHandle* target_interface_type, KindHandle* target_interface_kind);

ExpressionHandle* metal_expr_interface_call(
    ExpressionHandle* const* arg_exprs, size_t arg_count,
    int32_t virtual_param_index,
    KindHandle* interface_kind,
    int32_t index_in_edge,
    PrototypeHandle* function_type);

// Array operations. Each derived kind/type field is filled in by MetalLowerer
// from the source expression's result_type to avoid re-deriving it on the
// C++ side. Bools default to 0 (not known live).

ExpressionHandle* metal_expr_static_sized_array_load(
    ExpressionHandle* array_expr, ReferenceHandle* array_type,
    KindHandle* array_kind, int32_t array_known_live,
    ExpressionHandle* index_expr, ReferenceHandle* result_type,
    uint32_t target_ownership, ReferenceHandle* array_element_type,
    int32_t array_size);

ExpressionHandle* metal_expr_static_sized_array_store(
    ExpressionHandle* array_expr, ExpressionHandle* index_expr,
    ExpressionHandle* source_expr);

ExpressionHandle* metal_expr_runtime_sized_array_load(
    ExpressionHandle* array_expr, ReferenceHandle* array_type,
    KindHandle* array_kind, int32_t array_known_live,
    ExpressionHandle* index_expr, ReferenceHandle* index_type,
    KindHandle* index_kind, ReferenceHandle* result_type,
    uint32_t target_ownership, ReferenceHandle* array_element_type);

ExpressionHandle* metal_expr_runtime_sized_array_store(
    ExpressionHandle* array_expr, ReferenceHandle* array_type,
    KindHandle* array_kind, int32_t array_known_live,
    ExpressionHandle* index_expr, ReferenceHandle* index_type,
    KindHandle* index_kind, ExpressionHandle* source_expr,
    ReferenceHandle* source_type, KindHandle* source_kind);

ExpressionHandle* metal_expr_new_array_from_values(
    ExpressionHandle* const* source_exprs, size_t source_count,
    ReferenceHandle* array_ref_type, KindHandle* array_kind);

ExpressionHandle* metal_expr_new_mut_runtime_sized_array(
    ExpressionHandle* size_expr, ReferenceHandle* size_type, KindHandle* size_kind,
    ReferenceHandle* array_ref_type, ReferenceHandle* element_type);

ExpressionHandle* metal_expr_new_imm_runtime_sized_array(
    ExpressionHandle* size_expr, ReferenceHandle* size_type, KindHandle* size_kind,
    ExpressionHandle* generator_expr, ReferenceHandle* generator_type, KindHandle* generator_kind,
    PrototypeHandle* generator_method, int32_t generator_known_live,
    ReferenceHandle* array_ref_type, ReferenceHandle* element_type);

ExpressionHandle* metal_expr_static_array_from_callable(
    ExpressionHandle* generator_expr, ReferenceHandle* generator_type, KindHandle* generator_kind,
    PrototypeHandle* generator_method, int32_t generator_known_live,
    ReferenceHandle* array_ref_type, ReferenceHandle* element_type);

ExpressionHandle* metal_expr_push_runtime_sized_array(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, int32_t array_known_live,
    ExpressionHandle* newcomer_expr, ReferenceHandle* newcomer_type, int32_t newcomer_known_live);

ExpressionHandle* metal_expr_pop_runtime_sized_array(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, int32_t array_known_live);

ExpressionHandle* metal_expr_destroy_static_sized_array_into_locals(
    ExpressionHandle* array_expr, ReferenceHandle* array_type,
    ReferenceHandle* const* local_types, size_t local_type_count,
    LocalHandle* const* local_indices, size_t local_count);

ExpressionHandle* metal_expr_destroy_static_sized_array_into_function(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, KindHandle* array_kind,
    ExpressionHandle* consumer_expr, ReferenceHandle* consumer_type,
    PrototypeHandle* consumer_method, int32_t consumer_known_live,
    ReferenceHandle* array_element_type, int32_t array_size);

ExpressionHandle* metal_expr_destroy_imm_runtime_sized_array(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, KindHandle* array_kind,
    ExpressionHandle* consumer_expr, ReferenceHandle* consumer_type, KindHandle* consumer_kind,
    PrototypeHandle* consumer_method, int32_t consumer_known_live);

ExpressionHandle* metal_expr_destroy_mut_runtime_sized_array(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, KindHandle* array_kind);

// IsSameInstance + InterfaceToInterfaceUpcast: Backend has no LLVM lowering
// (no ctor in instructions.h beyond default fields, and no readjson arm).
// Leave as MetalLowerer panics until Backend learns them.

// Backend's class is `WeakAlias`; Rust H-AST calls it `BorrowToWeakH`.
ExpressionHandle* metal_expr_weak_alias(
    ExpressionHandle* source_expr, ReferenceHandle* source_type,
    KindHandle* source_kind, ReferenceHandle* result_type);

ExpressionHandle* metal_expr_lock_weak(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, int32_t source_known_live,
    PrototypeHandle* some_constructor, ReferenceHandle* some_type, KindHandle* some_kind,
    PrototypeHandle* none_constructor, ReferenceHandle* none_type, KindHandle* none_kind,
    ReferenceHandle* result_opt_type, KindHandle* result_opt_kind);

ExpressionHandle* metal_expr_as_subtype(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, int32_t source_known_live,
    KindHandle* target_kind,
    PrototypeHandle* ok_constructor, ReferenceHandle* ok_type, KindHandle* ok_kind,
    PrototypeHandle* err_constructor, ReferenceHandle* err_type, KindHandle* err_kind,
    ReferenceHandle* result_result_type, KindHandle* result_result_kind);

ExpressionHandle* metal_expr_destroy(
    ExpressionHandle* struct_expr, ReferenceHandle* struct_type,
    ReferenceHandle* const* local_types, size_t local_type_count,
    LocalHandle* const* local_indices, size_t local_count,
    const int32_t* locals_known_lives, size_t known_lives_count);

ExpressionHandle* metal_expr_member_store(
    ExpressionHandle* struct_expr, ReferenceHandle* struct_type,
    int32_t struct_known_live, int32_t member_index,
    ExpressionHandle* source_expr, ReferenceHandle* result_type,
    const char* member_name_ptr, size_t member_name_len);

ExpressionHandle* metal_expr_array_length(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, int32_t source_known_live);
ExpressionHandle* metal_expr_array_capacity(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, int32_t source_known_live);
ExpressionHandle* metal_expr_pre_check_borrow(ExpressionHandle* source_expr, ReferenceHandle* source_result_type);
ExpressionHandle* metal_expr_mutabilify(ExpressionHandle* source_expr, ReferenceHandle* source_type, ReferenceHandle* result_type);
ExpressionHandle* metal_expr_immutabilify(ExpressionHandle* source_expr, ReferenceHandle* source_type, ReferenceHandle* result_type);
ExpressionHandle* metal_expr_is_same_instance(
    ExpressionHandle* left_expr, ReferenceHandle* left_type,
    ExpressionHandle* right_expr, ReferenceHandle* right_type);
ExpressionHandle* metal_expr_borrow_to_pointer(ExpressionHandle* source_expr, ReferenceHandle* result_type);
ExpressionHandle* metal_expr_pointer_to_borrow(ExpressionHandle* source_expr, ReferenceHandle* result_type);
ExpressionHandle* metal_expr_narrow_permission(ExpressionHandle* source_expr);
ExpressionHandle* metal_expr_restackify(
    ExpressionHandle* source_expr, LocalHandle* local,
    int32_t known_live, const char* maybe_name_ptr, size_t maybe_name_len);

// --- Package builder ---
//
// Vale's Package is dominated by name-keyed maps. The builder accumulates
// entries via per-map add_* calls; finish() consumes the builder and returns
// a freshly-constructed Package (which is freed when its owning Program is
// freed; the builder itself is freed by finish() regardless).

PackageBuilderHandle* metal_package_builder_new(
    MetalCacheHandle* cache, PackageCoordHandle* package_coord);

void metal_package_builder_add_interface(
    PackageBuilderHandle*, const char* name_ptr, size_t name_len, InterfaceDefHandle*);
void metal_package_builder_add_struct(
    PackageBuilderHandle*, const char* name_ptr, size_t name_len, StructDefHandle*);
void metal_package_builder_add_function(
    PackageBuilderHandle*, const char* name_ptr, size_t name_len, FunctionHandle*);
void metal_package_builder_add_static_sized_array(
    PackageBuilderHandle*, const char* name_ptr, size_t name_len, StaticSizedArrayDefHandle*);
void metal_package_builder_add_runtime_sized_array(
    PackageBuilderHandle*, const char* name_ptr, size_t name_len, RuntimeSizedArrayDefHandle*);

StaticSizedArrayDefHandle* metal_static_sized_array_def_new(
    NameHandle* name, KindHandle* array_kind, int32_t size,
    RegionIdHandle* region_id, uint32_t mutability, uint32_t variability,
    ReferenceHandle* element_type);

RuntimeSizedArrayDefHandle* metal_runtime_sized_array_def_new(
    NameHandle* name, KindHandle* array_kind,
    RegionIdHandle* region_id, uint32_t mutability,
    ReferenceHandle* element_type);
void metal_package_builder_add_export_function(
    PackageBuilderHandle*, const char* name_ptr, size_t name_len, PrototypeHandle*);
void metal_package_builder_add_export_kind(
    PackageBuilderHandle*, const char* name_ptr, size_t name_len, KindHandle*);
void metal_package_builder_add_extern_function(
    PackageBuilderHandle*, const char* name_ptr, size_t name_len, PrototypeHandle*);
void metal_package_builder_add_extern_kind(
    PackageBuilderHandle*, const char* name_ptr, size_t name_len, KindHandle*);

PackageHandle* metal_package_builder_finish(PackageBuilderHandle*);

// --- Program builder ---

ProgramBuilderHandle* metal_program_builder_new(MetalCacheHandle* cache);
void metal_program_builder_add_package(
    ProgramBuilderHandle*, PackageCoordHandle*, PackageHandle*);
ProgramHandle* metal_program_builder_finish(ProgramBuilderHandle*);

// Ownership: the returned Program owns its Packages. Pass to the (future)
// backend_compile_program entry, which frees it; or call program_free
// explicitly in tests that don't compile.
void metal_program_free(ProgramHandle*);

#ifdef __cplusplus
}
#endif

#endif  // METAL_CACHE_FFI_H_
