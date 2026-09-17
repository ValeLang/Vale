// Implementation of metal_cache_ffi.h. Each function is a thin wrapper that
// reinterpret_casts the opaque handles to the underlying C++ types and
// forwards to MetalCache::get* or returns a singleton field.

#include "metal_cache_ffi.h"

#include <string>
#include <vector>

#include "addresshasher.h"
#include "metal/ast.h"
#include "metal/instructions.h"
#include "metal/metalcache.h"
#include "metal/name.h"
#include "metal/types.h"

namespace {

// Owns both the AddressNumberer (needed by MetalCache's hashers) and the
// MetalCache itself. MetalCache stores raw pointers to its interned objects
// without freeing them — those leaks are bounded to the cache's lifetime and
// reclaimed when the per-compilation cache is destroyed at process exit (or
// when metal_cache_free is called in tests).
struct CacheOwner {
  AddressNumberer addressNumberer;
  MetalCache cache;
  CacheOwner() : addressNumberer(), cache(&addressNumberer) {}
};

inline CacheOwner*       owner(MetalCacheHandle* h)     { return reinterpret_cast<CacheOwner*>(h); }
inline MetalCache*       cache(MetalCacheHandle* h)     { return &owner(h)->cache; }
inline PackageCoordinate* pc(PackageCoordHandle* h)     { return reinterpret_cast<PackageCoordinate*>(h); }
inline RegionId*         rid(RegionIdHandle* h)         { return reinterpret_cast<RegionId*>(h); }
inline Name*             nm(NameHandle* h)              { return reinterpret_cast<Name*>(h); }
inline Kind*             knd(KindHandle* h)             { return reinterpret_cast<Kind*>(h); }
inline Reference*        ref(ReferenceHandle* h)        { return reinterpret_cast<Reference*>(h); }

inline std::string s(const char* p, size_t n) { return std::string(p, n); }

}  // namespace

#define VIS __attribute__((visibility("default")))

// --- Lifecycle ---

extern "C" VIS MetalCacheHandle* metal_cache_new(void) {
  return reinterpret_cast<MetalCacheHandle*>(new CacheOwner());
}

extern "C" VIS void metal_cache_free(MetalCacheHandle* h) {
  delete owner(h);
}

// Internal accessor used by ffi.cpp's backend_compile_program to reach the
// inner MetalCache through the opaque wrapper. Not part of the public C ABI
// (no underscore prefix exception). Visibility default so cross-TU linking
// works under -fvisibility=hidden.
extern "C" __attribute__((visibility("default")))
MetalCache* metal_cache_ffi_inner(MetalCacheHandle* h) {
  return &owner(h)->cache;
}

// --- Singletons ---

extern "C" VIS PackageCoordHandle* metal_cache_builtin_package_coord(MetalCacheHandle* h) {
  return reinterpret_cast<PackageCoordHandle*>(cache(h)->builtinPackageCoord);
}
extern "C" VIS RegionIdHandle* metal_cache_rcimm_region_id(MetalCacheHandle* h) {
  return reinterpret_cast<RegionIdHandle*>(cache(h)->rcImmRegionId);
}
extern "C" VIS RegionIdHandle* metal_cache_linear_region_id(MetalCacheHandle* h) {
  return reinterpret_cast<RegionIdHandle*>(cache(h)->linearRegionId);
}
extern "C" VIS RegionIdHandle* metal_cache_mut_region_id(MetalCacheHandle* h) {
  return reinterpret_cast<RegionIdHandle*>(cache(h)->mutRegionId);
}

extern "C" VIS KindHandle* metal_cache_i32(MetalCacheHandle* h) {
  return reinterpret_cast<KindHandle*>(cache(h)->i32);
}
extern "C" VIS ReferenceHandle* metal_cache_i32_ref(MetalCacheHandle* h) {
  return reinterpret_cast<ReferenceHandle*>(cache(h)->i32Ref);
}
extern "C" VIS KindHandle* metal_cache_i64(MetalCacheHandle* h) {
  return reinterpret_cast<KindHandle*>(cache(h)->i64);
}
extern "C" VIS ReferenceHandle* metal_cache_i64_ref(MetalCacheHandle* h) {
  return reinterpret_cast<ReferenceHandle*>(cache(h)->i64Ref);
}
extern "C" VIS KindHandle* metal_cache_bool(MetalCacheHandle* h) {
  return reinterpret_cast<KindHandle*>(cache(h)->boool);
}
extern "C" VIS ReferenceHandle* metal_cache_bool_ref(MetalCacheHandle* h) {
  return reinterpret_cast<ReferenceHandle*>(cache(h)->boolRef);
}
extern "C" VIS KindHandle* metal_cache_float(MetalCacheHandle* h) {
  return reinterpret_cast<KindHandle*>(cache(h)->flooat);
}
extern "C" VIS ReferenceHandle* metal_cache_float_ref(MetalCacheHandle* h) {
  return reinterpret_cast<ReferenceHandle*>(cache(h)->floatRef);
}
extern "C" VIS KindHandle* metal_cache_str(MetalCacheHandle* h) {
  return reinterpret_cast<KindHandle*>(cache(h)->str);
}
extern "C" VIS ReferenceHandle* metal_cache_mut_str_ref(MetalCacheHandle* h) {
  return reinterpret_cast<ReferenceHandle*>(cache(h)->mutStrRef);
}
extern "C" VIS ReferenceHandle* metal_cache_imm_str_ref(MetalCacheHandle* h) {
  return reinterpret_cast<ReferenceHandle*>(cache(h)->immStrRef);
}
extern "C" VIS KindHandle* metal_cache_never(MetalCacheHandle* h) {
  return reinterpret_cast<KindHandle*>(cache(h)->never);
}
extern "C" VIS ReferenceHandle* metal_cache_never_ref(MetalCacheHandle* h) {
  return reinterpret_cast<ReferenceHandle*>(cache(h)->neverRef);
}
extern "C" VIS KindHandle* metal_cache_void(MetalCacheHandle* h) {
  return reinterpret_cast<KindHandle*>(cache(h)->vooid);
}
extern "C" VIS ReferenceHandle* metal_cache_void_ref(MetalCacheHandle* h) {
  return reinterpret_cast<ReferenceHandle*>(cache(h)->voidRef);
}

// --- Interned getters ---

extern "C" VIS PackageCoordHandle* metal_cache_get_package_coordinate(
    MetalCacheHandle* h,
    const char* project_name_ptr, size_t project_name_len,
    const char* const* steps_ptrs, const size_t* steps_lens, size_t steps_count) {
  std::vector<std::string> steps;
  steps.reserve(steps_count);
  for (size_t i = 0; i < steps_count; i++) {
    steps.emplace_back(steps_ptrs[i], steps_lens[i]);
  }
  return reinterpret_cast<PackageCoordHandle*>(
      cache(h)->getPackageCoordinate(s(project_name_ptr, project_name_len), steps));
}

extern "C" VIS RegionIdHandle* metal_cache_get_region_id(
    MetalCacheHandle* h, PackageCoordHandle* package_coord,
    const char* id_ptr, size_t id_len) {
  return reinterpret_cast<RegionIdHandle*>(
      cache(h)->getRegionId(pc(package_coord), s(id_ptr, id_len)));
}

extern "C" VIS NameHandle* metal_cache_get_name(
    MetalCacheHandle* h, PackageCoordHandle* package_coord,
    const char* name_ptr, size_t name_len) {
  return reinterpret_cast<NameHandle*>(
      cache(h)->getName(pc(package_coord), s(name_ptr, name_len)));
}

extern "C" VIS KindHandle* metal_cache_get_int(MetalCacheHandle* h, RegionIdHandle* region, int32_t bits) {
  return reinterpret_cast<KindHandle*>(cache(h)->getInt(rid(region), bits));
}
extern "C" VIS KindHandle* metal_cache_get_bool(MetalCacheHandle* h, RegionIdHandle* region) {
  return reinterpret_cast<KindHandle*>(cache(h)->getBool(rid(region)));
}
extern "C" VIS KindHandle* metal_cache_get_str(MetalCacheHandle* h, RegionIdHandle* region) {
  return reinterpret_cast<KindHandle*>(cache(h)->getStr(rid(region)));
}
extern "C" VIS KindHandle* metal_cache_get_float(MetalCacheHandle* h, RegionIdHandle* region) {
  return reinterpret_cast<KindHandle*>(cache(h)->getFloat(rid(region)));
}
extern "C" VIS KindHandle* metal_cache_get_void(MetalCacheHandle* h, RegionIdHandle* region) {
  return reinterpret_cast<KindHandle*>(cache(h)->getVoid(rid(region)));
}
extern "C" VIS KindHandle* metal_cache_get_never(MetalCacheHandle* h, RegionIdHandle* region) {
  return reinterpret_cast<KindHandle*>(cache(h)->getNever(rid(region)));
}

extern "C" VIS KindHandle* metal_cache_get_struct_kind(MetalCacheHandle* h, NameHandle* name) {
  return reinterpret_cast<KindHandle*>(cache(h)->getStructKind(nm(name)));
}
extern "C" VIS KindHandle* metal_cache_get_interface_kind(MetalCacheHandle* h, NameHandle* name) {
  return reinterpret_cast<KindHandle*>(cache(h)->getInterfaceKind(nm(name)));
}
extern "C" VIS KindHandle* metal_cache_get_static_sized_array(MetalCacheHandle* h, NameHandle* name) {
  return reinterpret_cast<KindHandle*>(cache(h)->getStaticSizedArray(nm(name)));
}
extern "C" VIS KindHandle* metal_cache_get_runtime_sized_array(MetalCacheHandle* h, NameHandle* name) {
  return reinterpret_cast<KindHandle*>(cache(h)->getRuntimeSizedArray(nm(name)));
}

extern "C" VIS ReferenceHandle* metal_cache_get_reference(
    MetalCacheHandle* h, uint32_t ownership, uint32_t location, KindHandle* kind) {
  return reinterpret_cast<ReferenceHandle*>(
      cache(h)->getReference(
          static_cast<Ownership>(ownership),
          static_cast<Location>(location),
          knd(kind)));
}

extern "C" VIS PrototypeHandle* metal_cache_get_prototype(
    MetalCacheHandle* h, NameHandle* name, ReferenceHandle* return_type,
    ReferenceHandle* const* param_types, size_t param_count) {
  std::vector<Reference*> params;
  params.reserve(param_count);
  for (size_t i = 0; i < param_count; i++) {
    params.push_back(ref(param_types[i]));
  }
  return reinterpret_cast<PrototypeHandle*>(
      cache(h)->getPrototype(nm(name), ref(return_type), std::move(params)));
}

extern "C" VIS InterfaceMethodHandle* metal_cache_get_interface_method(
    MetalCacheHandle* h, PrototypeHandle* prototype, int32_t virtual_param_index) {
  return reinterpret_cast<InterfaceMethodHandle*>(
      cache(h)->getInterfaceMethod(
          reinterpret_cast<Prototype*>(prototype), virtual_param_index));
}

extern "C" VIS VariableIdHandle* metal_cache_get_variable_id(
    MetalCacheHandle* h, int32_t number, int32_t height,
    const char* maybe_name_ptr, size_t maybe_name_len) {
  auto* c = cache(h);
  std::string maybeName = (maybe_name_ptr && maybe_name_len > 0)
      ? std::string(maybe_name_ptr, maybe_name_len) : std::string();
  return reinterpret_cast<VariableIdHandle*>(makeIfNotPresent(
      &c->variableIds[number],
      maybeName,
      [&](){ return new VariableId(number, height, maybeName); }));
}

extern "C" VIS LocalHandle* metal_cache_get_local(
    MetalCacheHandle* h, VariableIdHandle* var_id, ReferenceHandle* ref_type) {
  auto* c = cache(h);
  auto* vid = reinterpret_cast<VariableId*>(var_id);
  auto* r = ref(ref_type);
  return reinterpret_cast<LocalHandle*>(makeIfNotPresent(
      &makeIfNotPresent(
          &c->locals,
          vid,
          [&](){ return MetalCache::LocalByReferenceMap(0, c->addressNumberer->makeHasher<Reference*>()); }),
      r,
      [&](){ return new Local(vid, r); }));
}

// --- Non-interned constructors ---

extern "C" VIS StructMemberHandle* metal_struct_member_new(
    const char* full_name_ptr, size_t full_name_len,
    const char* name_ptr, size_t name_len,
    uint32_t variability,
    ReferenceHandle* type) {
  return reinterpret_cast<StructMemberHandle*>(new StructMember(
      s(full_name_ptr, full_name_len),
      s(name_ptr, name_len),
      static_cast<Variability>(variability),
      ref(type)));
}

extern "C" VIS EdgeHandle* metal_edge_new(
    KindHandle* struct_kind, KindHandle* interface_kind,
    InterfaceMethodHandle* const* interface_methods,
    PrototypeHandle* const* struct_prototypes,
    size_t pair_count) {
  std::vector<std::pair<InterfaceMethod*, Prototype*>> pairs;
  pairs.reserve(pair_count);
  for (size_t i = 0; i < pair_count; i++) {
    pairs.emplace_back(
        reinterpret_cast<InterfaceMethod*>(interface_methods[i]),
        reinterpret_cast<Prototype*>(struct_prototypes[i]));
  }
  return reinterpret_cast<EdgeHandle*>(new Edge(
      reinterpret_cast<StructKind*>(knd(struct_kind)),
      reinterpret_cast<InterfaceKind*>(knd(interface_kind)),
      std::move(pairs)));
}

extern "C" VIS StructDefHandle* metal_struct_def_new(
    NameHandle* name, KindHandle* struct_kind, RegionIdHandle* region_id,
    uint32_t mutability,
    EdgeHandle* const* edges, size_t edge_count,
    StructMemberHandle* const* members, size_t member_count,
    uint32_t weakability) {
  std::vector<Edge*> edge_vec;
  edge_vec.reserve(edge_count);
  for (size_t i = 0; i < edge_count; i++) {
    edge_vec.push_back(reinterpret_cast<Edge*>(edges[i]));
  }
  std::vector<StructMember*> member_vec;
  member_vec.reserve(member_count);
  for (size_t i = 0; i < member_count; i++) {
    member_vec.push_back(reinterpret_cast<StructMember*>(members[i]));
  }
  return reinterpret_cast<StructDefHandle*>(new StructDefinition(
      nm(name),
      reinterpret_cast<StructKind*>(knd(struct_kind)),
      rid(region_id),
      static_cast<Mutability>(mutability),
      std::move(edge_vec),
      std::move(member_vec),
      static_cast<Weakability>(weakability)));
}

extern "C" VIS InterfaceDefHandle* metal_interface_def_new(
    NameHandle* name, KindHandle* interface_kind, RegionIdHandle* region_id,
    uint32_t mutability,
    NameHandle* const* super_interfaces, size_t super_count,
    InterfaceMethodHandle* const* methods, size_t method_count,
    uint32_t weakability) {
  std::vector<Name*> supers;
  supers.reserve(super_count);
  for (size_t i = 0; i < super_count; i++) {
    supers.push_back(nm(super_interfaces[i]));
  }
  std::vector<InterfaceMethod*> method_vec;
  method_vec.reserve(method_count);
  for (size_t i = 0; i < method_count; i++) {
    method_vec.push_back(reinterpret_cast<InterfaceMethod*>(methods[i]));
  }
  return reinterpret_cast<InterfaceDefHandle*>(new InterfaceDefinition(
      nm(name),
      reinterpret_cast<InterfaceKind*>(knd(interface_kind)),
      rid(region_id),
      static_cast<Mutability>(mutability),
      supers,
      method_vec,
      static_cast<Weakability>(weakability)));
}

extern "C" VIS FunctionHandle* metal_function_new(
    PrototypeHandle* prototype, ExpressionHandle* body) {
  return reinterpret_cast<FunctionHandle*>(new Function(
      reinterpret_cast<Prototype*>(prototype),
      reinterpret_cast<Expression*>(body)));
}

// --- Expression constructors ---

extern "C" VIS ExpressionHandle* metal_expr_constant_void(void) {
  return reinterpret_cast<ExpressionHandle*>(new ConstantVoid());
}
extern "C" VIS ExpressionHandle* metal_expr_constant_int(int64_t value, int32_t bits) {
  return reinterpret_cast<ExpressionHandle*>(new ConstantInt(value, bits));
}
extern "C" VIS ExpressionHandle* metal_expr_constant_bool(int32_t value) {
  return reinterpret_cast<ExpressionHandle*>(new ConstantBool(value != 0));
}
extern "C" VIS ExpressionHandle* metal_expr_constant_f64(double value) {
  return reinterpret_cast<ExpressionHandle*>(new ConstantF64(value));
}
extern "C" VIS ExpressionHandle* metal_expr_constant_str(const char* p, size_t n) {
  return reinterpret_cast<ExpressionHandle*>(new ConstantStr(s(p, n)));
}
extern "C" VIS ExpressionHandle* metal_expr_break(void) {
  return reinterpret_cast<ExpressionHandle*>(new Break());
}
extern "C" VIS ExpressionHandle* metal_expr_return(
    ExpressionHandle* source_expr, ReferenceHandle* source_type) {
  return reinterpret_cast<ExpressionHandle*>(new Return(
      reinterpret_cast<Expression*>(source_expr),
      ref(source_type)));
}
extern "C" VIS ExpressionHandle* metal_expr_block(
    ExpressionHandle* inner, ReferenceHandle* inner_type) {
  return reinterpret_cast<ExpressionHandle*>(new Block(
      reinterpret_cast<Expression*>(inner),
      ref(inner_type)));
}
extern "C" VIS ExpressionHandle* metal_expr_consecutor(
    ExpressionHandle* const* exprs, size_t expr_count) {
  std::vector<Expression*> vec;
  vec.reserve(expr_count);
  for (size_t i = 0; i < expr_count; i++) {
    vec.push_back(reinterpret_cast<Expression*>(exprs[i]));
  }
  return reinterpret_cast<ExpressionHandle*>(new Consecutor(std::move(vec)));
}

extern "C" VIS ExpressionHandle* metal_expr_stackify(
    ExpressionHandle* source_expr, LocalHandle* local,
    int32_t known_live, const char* maybe_name_ptr, size_t maybe_name_len) {
  std::string n = (maybe_name_ptr && maybe_name_len > 0)
      ? std::string(maybe_name_ptr, maybe_name_len) : std::string();
  return reinterpret_cast<ExpressionHandle*>(new Stackify(
      reinterpret_cast<Expression*>(source_expr),
      reinterpret_cast<Local*>(local),
      known_live != 0,
      n));
}

extern "C" VIS ExpressionHandle* metal_expr_unstackify(LocalHandle* local) {
  return reinterpret_cast<ExpressionHandle*>(new Unstackify(reinterpret_cast<Local*>(local)));
}

extern "C" VIS ExpressionHandle* metal_expr_local_load(
    LocalHandle* local, uint32_t target_ownership,
    const char* local_name_ptr, size_t local_name_len) {
  return reinterpret_cast<ExpressionHandle*>(new LocalLoad(
      reinterpret_cast<Local*>(local),
      static_cast<Ownership>(target_ownership),
      s(local_name_ptr, local_name_len)));
}

extern "C" VIS ExpressionHandle* metal_expr_local_store(
    LocalHandle* local, ExpressionHandle* source_expr,
    const char* local_name_ptr, size_t local_name_len, int32_t known_live) {
  return reinterpret_cast<ExpressionHandle*>(new LocalStore(
      reinterpret_cast<Local*>(local),
      reinterpret_cast<Expression*>(source_expr),
      s(local_name_ptr, local_name_len),
      known_live != 0));
}

extern "C" VIS ExpressionHandle* metal_expr_discard(
    ExpressionHandle* source_expr, ReferenceHandle* source_type) {
  return reinterpret_cast<ExpressionHandle*>(new Discard(
      reinterpret_cast<Expression*>(source_expr), ref(source_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_call(
    PrototypeHandle* function,
    ExpressionHandle* const* arg_exprs, size_t arg_count) {
  std::vector<Expression*> args;
  args.reserve(arg_count);
  for (size_t i = 0; i < arg_count; i++) {
    args.push_back(reinterpret_cast<Expression*>(arg_exprs[i]));
  }
  return reinterpret_cast<ExpressionHandle*>(new Call(
      reinterpret_cast<Prototype*>(function), std::move(args)));
}

extern "C" VIS ExpressionHandle* metal_expr_extern_call(
    PrototypeHandle* function,
    ExpressionHandle* const* arg_exprs, size_t arg_count,
    ReferenceHandle* const* arg_types, size_t arg_type_count) {
  std::vector<Expression*> args;
  args.reserve(arg_count);
  for (size_t i = 0; i < arg_count; i++) {
    args.push_back(reinterpret_cast<Expression*>(arg_exprs[i]));
  }
  std::vector<Reference*> tys;
  tys.reserve(arg_type_count);
  for (size_t i = 0; i < arg_type_count; i++) {
    tys.push_back(ref(arg_types[i]));
  }
  return reinterpret_cast<ExpressionHandle*>(new ExternCall(
      reinterpret_cast<Prototype*>(function), std::move(args), std::move(tys)));
}

extern "C" VIS ExpressionHandle* metal_expr_if(
    ExpressionHandle* condition,
    ExpressionHandle* then_expr, ReferenceHandle* then_type,
    ExpressionHandle* else_expr, ReferenceHandle* else_type,
    ReferenceHandle* common_supertype) {
  return reinterpret_cast<ExpressionHandle*>(new If(
      reinterpret_cast<Expression*>(condition),
      reinterpret_cast<Expression*>(then_expr), ref(then_type),
      reinterpret_cast<Expression*>(else_expr), ref(else_type),
      ref(common_supertype)));
}

extern "C" VIS ExpressionHandle* metal_expr_while(ExpressionHandle* body) {
  return reinterpret_cast<ExpressionHandle*>(new While(
      reinterpret_cast<Expression*>(body)));
}

extern "C" VIS ExpressionHandle* metal_expr_argument(
    ReferenceHandle* result_type, int32_t argument_index) {
  return reinterpret_cast<ExpressionHandle*>(new Argument(
      ref(result_type), argument_index));
}

extern "C" VIS ExpressionHandle* metal_expr_member_load(
    ExpressionHandle* struct_expr, KindHandle* struct_id,
    ReferenceHandle* struct_type, int32_t struct_known_live,
    int32_t member_index, uint32_t target_ownership,
    ReferenceHandle* expected_member_type, ReferenceHandle* expected_result_type,
    const char* member_name_ptr, size_t member_name_len) {
  return reinterpret_cast<ExpressionHandle*>(new MemberLoad(
      reinterpret_cast<Expression*>(struct_expr),
      reinterpret_cast<StructKind*>(knd(struct_id)),
      ref(struct_type), struct_known_live != 0,
      member_index,
      static_cast<Ownership>(target_ownership),
      ref(expected_member_type), ref(expected_result_type),
      s(member_name_ptr, member_name_len)));
}

extern "C" VIS ExpressionHandle* metal_expr_new_struct(
    ExpressionHandle* const* source_exprs, size_t source_count,
    ReferenceHandle* result_type) {
  std::vector<Expression*> exprs;
  exprs.reserve(source_count);
  for (size_t i = 0; i < source_count; i++) {
    exprs.push_back(reinterpret_cast<Expression*>(source_exprs[i]));
  }
  return reinterpret_cast<ExpressionHandle*>(new NewStruct(
      std::move(exprs), ref(result_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_struct_to_interface_upcast(
    ExpressionHandle* source_expr,
    ReferenceHandle* source_struct_type, KindHandle* source_struct_kind,
    ReferenceHandle* target_interface_type, KindHandle* target_interface_kind) {
  return reinterpret_cast<ExpressionHandle*>(new StructToInterfaceUpcast(
      reinterpret_cast<Expression*>(source_expr),
      ref(source_struct_type),
      reinterpret_cast<StructKind*>(knd(source_struct_kind)),
      ref(target_interface_type),
      reinterpret_cast<InterfaceKind*>(knd(target_interface_kind))));
}

extern "C" VIS ExpressionHandle* metal_expr_interface_to_interface_upcast(
    ExpressionHandle* source_expr,
    ReferenceHandle* source_interface_type, KindHandle* source_interface_kind,
    ReferenceHandle* target_interface_type, KindHandle* target_interface_kind) {
  return reinterpret_cast<ExpressionHandle*>(new InterfaceToInterfaceUpcast(
      reinterpret_cast<Expression*>(source_expr),
      ref(source_interface_type),
      reinterpret_cast<InterfaceKind*>(knd(source_interface_kind)),
      ref(target_interface_type),
      reinterpret_cast<InterfaceKind*>(knd(target_interface_kind))));
}

extern "C" VIS ExpressionHandle* metal_expr_interface_call(
    ExpressionHandle* const* arg_exprs, size_t arg_count,
    int32_t virtual_param_index,
    KindHandle* interface_kind,
    int32_t index_in_edge,
    PrototypeHandle* function_type) {
  std::vector<Expression*> args;
  args.reserve(arg_count);
  for (size_t i = 0; i < arg_count; i++) {
    args.push_back(reinterpret_cast<Expression*>(arg_exprs[i]));
  }
  return reinterpret_cast<ExpressionHandle*>(new InterfaceCall(
      std::move(args), virtual_param_index,
      reinterpret_cast<InterfaceKind*>(knd(interface_kind)),
      index_in_edge,
      reinterpret_cast<Prototype*>(function_type)));
}

// --- Array operations ---

extern "C" VIS ExpressionHandle* metal_expr_static_sized_array_load(
    ExpressionHandle* array_expr, ReferenceHandle* array_type,
    KindHandle* array_kind, int32_t array_known_live,
    ExpressionHandle* index_expr, ReferenceHandle* result_type,
    uint32_t target_ownership, ReferenceHandle* array_element_type,
    int32_t array_size) {
  return reinterpret_cast<ExpressionHandle*>(new StaticSizedArrayLoad(
      reinterpret_cast<Expression*>(array_expr), ref(array_type),
      reinterpret_cast<StaticSizedArrayT*>(knd(array_kind)), array_known_live != 0,
      reinterpret_cast<Expression*>(index_expr), ref(result_type),
      static_cast<Ownership>(target_ownership), ref(array_element_type),
      array_size));
}

extern "C" VIS ExpressionHandle* metal_expr_static_sized_array_store(
    ExpressionHandle* array_expr, ExpressionHandle* index_expr,
    ExpressionHandle* source_expr) {
  auto* p = new StaticSizedArrayStore();
  p->arrayExpr = reinterpret_cast<Expression*>(array_expr);
  p->indexExpr = reinterpret_cast<Expression*>(index_expr);
  p->sourceExpr = reinterpret_cast<Expression*>(source_expr);
  return reinterpret_cast<ExpressionHandle*>(p);
}

extern "C" VIS ExpressionHandle* metal_expr_runtime_sized_array_load(
    ExpressionHandle* array_expr, ReferenceHandle* array_type,
    KindHandle* array_kind, int32_t array_known_live,
    ExpressionHandle* index_expr, ReferenceHandle* index_type,
    KindHandle* index_kind, ReferenceHandle* result_type,
    uint32_t target_ownership, ReferenceHandle* array_element_type) {
  return reinterpret_cast<ExpressionHandle*>(new RuntimeSizedArrayLoad(
      reinterpret_cast<Expression*>(array_expr), ref(array_type),
      reinterpret_cast<RuntimeSizedArrayT*>(knd(array_kind)), array_known_live != 0,
      reinterpret_cast<Expression*>(index_expr), ref(index_type),
      knd(index_kind), ref(result_type),
      static_cast<Ownership>(target_ownership), ref(array_element_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_runtime_sized_array_store(
    ExpressionHandle* array_expr, ReferenceHandle* array_type,
    KindHandle* array_kind, int32_t array_known_live,
    ExpressionHandle* index_expr, ReferenceHandle* index_type,
    KindHandle* index_kind, ExpressionHandle* source_expr,
    ReferenceHandle* source_type, KindHandle* source_kind) {
  return reinterpret_cast<ExpressionHandle*>(new RuntimeSizedArrayStore(
      reinterpret_cast<Expression*>(array_expr), ref(array_type),
      reinterpret_cast<RuntimeSizedArrayT*>(knd(array_kind)), array_known_live != 0,
      reinterpret_cast<Expression*>(index_expr), ref(index_type),
      knd(index_kind),
      reinterpret_cast<Expression*>(source_expr), ref(source_type),
      knd(source_kind)));
}

extern "C" VIS ExpressionHandle* metal_expr_new_array_from_values(
    ExpressionHandle* const* source_exprs, size_t source_count,
    ReferenceHandle* array_ref_type, KindHandle* array_kind) {
  std::vector<Expression*> exprs;
  exprs.reserve(source_count);
  for (size_t i = 0; i < source_count; i++) exprs.push_back(reinterpret_cast<Expression*>(source_exprs[i]));
  return reinterpret_cast<ExpressionHandle*>(new NewArrayFromValues(
      std::move(exprs), ref(array_ref_type),
      reinterpret_cast<StaticSizedArrayT*>(knd(array_kind))));
}

extern "C" VIS ExpressionHandle* metal_expr_new_mut_runtime_sized_array(
    ExpressionHandle* size_expr, ReferenceHandle* size_type, KindHandle* size_kind,
    ReferenceHandle* array_ref_type, ReferenceHandle* element_type) {
  return reinterpret_cast<ExpressionHandle*>(new NewMutRuntimeSizedArray(
      reinterpret_cast<Expression*>(size_expr), ref(size_type), knd(size_kind),
      ref(array_ref_type), ref(element_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_new_imm_runtime_sized_array(
    ExpressionHandle* size_expr, ReferenceHandle* size_type, KindHandle* size_kind,
    ExpressionHandle* generator_expr, ReferenceHandle* generator_type, KindHandle* generator_kind,
    PrototypeHandle* generator_method, int32_t generator_known_live,
    ReferenceHandle* array_ref_type, ReferenceHandle* element_type) {
  return reinterpret_cast<ExpressionHandle*>(new NewImmRuntimeSizedArray(
      reinterpret_cast<Expression*>(size_expr), ref(size_type), knd(size_kind),
      reinterpret_cast<Expression*>(generator_expr), ref(generator_type), knd(generator_kind),
      reinterpret_cast<Prototype*>(generator_method), generator_known_live != 0,
      ref(array_ref_type), ref(element_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_static_array_from_callable(
    ExpressionHandle* generator_expr, ReferenceHandle* generator_type, KindHandle* generator_kind,
    PrototypeHandle* generator_method, int32_t generator_known_live,
    ReferenceHandle* array_ref_type, ReferenceHandle* element_type) {
  return reinterpret_cast<ExpressionHandle*>(new StaticArrayFromCallable(
      reinterpret_cast<Expression*>(generator_expr), ref(generator_type), knd(generator_kind),
      reinterpret_cast<Prototype*>(generator_method), generator_known_live != 0,
      ref(array_ref_type), ref(element_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_push_runtime_sized_array(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, int32_t array_known_live,
    ExpressionHandle* newcomer_expr, ReferenceHandle* newcomer_type, int32_t newcomer_known_live) {
  return reinterpret_cast<ExpressionHandle*>(new PushRuntimeSizedArray(
      reinterpret_cast<Expression*>(array_expr), ref(array_type), array_known_live != 0,
      reinterpret_cast<Expression*>(newcomer_expr), ref(newcomer_type), newcomer_known_live != 0));
}

extern "C" VIS ExpressionHandle* metal_expr_pop_runtime_sized_array(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, int32_t array_known_live) {
  return reinterpret_cast<ExpressionHandle*>(new PopRuntimeSizedArray(
      reinterpret_cast<Expression*>(array_expr), ref(array_type), array_known_live != 0));
}

extern "C" VIS ExpressionHandle* metal_expr_destroy_static_sized_array_into_locals(
    ExpressionHandle* array_expr, ReferenceHandle* array_type,
    ReferenceHandle* const* local_types, size_t local_type_count,
    LocalHandle* const* local_indices, size_t local_count) {
  std::vector<Reference*> tys; tys.reserve(local_type_count);
  for (size_t i = 0; i < local_type_count; i++) tys.push_back(ref(local_types[i]));
  std::vector<Local*> locs; locs.reserve(local_count);
  for (size_t i = 0; i < local_count; i++) locs.push_back(reinterpret_cast<Local*>(local_indices[i]));
  return reinterpret_cast<ExpressionHandle*>(new DestroyStaticSizedArrayIntoLocals(
      reinterpret_cast<Expression*>(array_expr), ref(array_type),
      std::move(tys), std::move(locs)));
}

extern "C" VIS ExpressionHandle* metal_expr_destroy_static_sized_array_into_function(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, KindHandle* array_kind,
    ExpressionHandle* consumer_expr, ReferenceHandle* consumer_type,
    PrototypeHandle* consumer_method, int32_t consumer_known_live,
    ReferenceHandle* array_element_type, int32_t array_size) {
  return reinterpret_cast<ExpressionHandle*>(new DestroyStaticSizedArrayIntoFunction(
      reinterpret_cast<Expression*>(array_expr), ref(array_type),
      reinterpret_cast<StaticSizedArrayT*>(knd(array_kind)),
      reinterpret_cast<Expression*>(consumer_expr), ref(consumer_type),
      reinterpret_cast<Prototype*>(consumer_method), consumer_known_live != 0,
      ref(array_element_type), array_size));
}

extern "C" VIS ExpressionHandle* metal_expr_destroy_imm_runtime_sized_array(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, KindHandle* array_kind,
    ExpressionHandle* consumer_expr, ReferenceHandle* consumer_type, KindHandle* consumer_kind,
    PrototypeHandle* consumer_method, int32_t consumer_known_live) {
  return reinterpret_cast<ExpressionHandle*>(new DestroyImmRuntimeSizedArray(
      reinterpret_cast<Expression*>(array_expr), ref(array_type),
      reinterpret_cast<RuntimeSizedArrayT*>(knd(array_kind)),
      reinterpret_cast<Expression*>(consumer_expr), ref(consumer_type), knd(consumer_kind),
      reinterpret_cast<Prototype*>(consumer_method), consumer_known_live != 0));
}

extern "C" VIS ExpressionHandle* metal_expr_destroy_mut_runtime_sized_array(
    ExpressionHandle* array_expr, ReferenceHandle* array_type, KindHandle* array_kind) {
  return reinterpret_cast<ExpressionHandle*>(new DestroyMutRuntimeSizedArray(
      reinterpret_cast<Expression*>(array_expr), ref(array_type),
      reinterpret_cast<RuntimeSizedArrayT*>(knd(array_kind))));
}

extern "C" VIS ExpressionHandle* metal_expr_weak_alias(
    ExpressionHandle* source_expr, ReferenceHandle* source_type,
    KindHandle* source_kind, ReferenceHandle* result_type) {
  return reinterpret_cast<ExpressionHandle*>(new WeakAlias(
      reinterpret_cast<Expression*>(source_expr), ref(source_type),
      knd(source_kind), ref(result_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_lock_weak(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, int32_t source_known_live,
    PrototypeHandle* some_constructor, ReferenceHandle* some_type, KindHandle* some_kind,
    PrototypeHandle* none_constructor, ReferenceHandle* none_type, KindHandle* none_kind,
    ReferenceHandle* result_opt_type, KindHandle* result_opt_kind) {
  return reinterpret_cast<ExpressionHandle*>(new LockWeak(
      reinterpret_cast<Expression*>(source_expr), ref(source_type), source_known_live != 0,
      reinterpret_cast<Prototype*>(some_constructor), ref(some_type),
      reinterpret_cast<StructKind*>(knd(some_kind)),
      reinterpret_cast<Prototype*>(none_constructor), ref(none_type),
      reinterpret_cast<StructKind*>(knd(none_kind)),
      ref(result_opt_type), reinterpret_cast<InterfaceKind*>(knd(result_opt_kind))));
}

extern "C" VIS ExpressionHandle* metal_expr_as_subtype(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, int32_t source_known_live,
    KindHandle* target_kind,
    PrototypeHandle* ok_constructor, ReferenceHandle* ok_type, KindHandle* ok_kind,
    PrototypeHandle* err_constructor, ReferenceHandle* err_type, KindHandle* err_kind,
    ReferenceHandle* result_result_type, KindHandle* result_result_kind) {
  return reinterpret_cast<ExpressionHandle*>(new AsSubtype(
      reinterpret_cast<Expression*>(source_expr), ref(source_type), source_known_live != 0,
      knd(target_kind),
      reinterpret_cast<Prototype*>(ok_constructor), ref(ok_type),
      reinterpret_cast<StructKind*>(knd(ok_kind)),
      reinterpret_cast<Prototype*>(err_constructor), ref(err_type),
      reinterpret_cast<StructKind*>(knd(err_kind)),
      ref(result_result_type), reinterpret_cast<InterfaceKind*>(knd(result_result_kind))));
}

extern "C" VIS ExpressionHandle* metal_expr_destroy(
    ExpressionHandle* struct_expr, ReferenceHandle* struct_type,
    ReferenceHandle* const* local_types, size_t local_type_count,
    LocalHandle* const* local_indices, size_t local_count,
    const int32_t* locals_known_lives, size_t known_lives_count) {
  std::vector<Reference*> tys; tys.reserve(local_type_count);
  for (size_t i = 0; i < local_type_count; i++) tys.push_back(ref(local_types[i]));
  std::vector<Local*> locs; locs.reserve(local_count);
  for (size_t i = 0; i < local_count; i++) locs.push_back(reinterpret_cast<Local*>(local_indices[i]));
  std::vector<bool> lives; lives.reserve(known_lives_count);
  for (size_t i = 0; i < known_lives_count; i++) lives.push_back(locals_known_lives[i] != 0);
  return reinterpret_cast<ExpressionHandle*>(new Destroy(
      reinterpret_cast<Expression*>(struct_expr), ref(struct_type),
      std::move(tys), std::move(locs), std::move(lives)));
}

extern "C" VIS ExpressionHandle* metal_expr_member_store(
    ExpressionHandle* struct_expr, ReferenceHandle* struct_type,
    int32_t struct_known_live, int32_t member_index,
    ExpressionHandle* source_expr, ReferenceHandle* result_type,
    const char* member_name_ptr, size_t member_name_len) {
  return reinterpret_cast<ExpressionHandle*>(new MemberStore(
      reinterpret_cast<Expression*>(struct_expr), ref(struct_type),
      struct_known_live != 0, member_index,
      reinterpret_cast<Expression*>(source_expr), ref(result_type),
      s(member_name_ptr, member_name_len)));
}

extern "C" VIS ExpressionHandle* metal_expr_array_length(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, int32_t source_known_live) {
  return reinterpret_cast<ExpressionHandle*>(new ArrayLength(
      reinterpret_cast<Expression*>(source_expr), ref(source_type), source_known_live != 0));
}

extern "C" VIS ExpressionHandle* metal_expr_array_capacity(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, int32_t source_known_live) {
  return reinterpret_cast<ExpressionHandle*>(new ArrayCapacity(
      reinterpret_cast<Expression*>(source_expr), ref(source_type), source_known_live != 0));
}

extern "C" VIS ExpressionHandle* metal_expr_pre_check_borrow(
    ExpressionHandle* source_expr, ReferenceHandle* source_result_type) {
  return reinterpret_cast<ExpressionHandle*>(new PreCheckBorrow(
      reinterpret_cast<Expression*>(source_expr), ref(source_result_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_mutabilify(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, ReferenceHandle* result_type) {
  return reinterpret_cast<ExpressionHandle*>(new Mutabilify(
      reinterpret_cast<Expression*>(source_expr), ref(source_type), ref(result_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_immutabilify(
    ExpressionHandle* source_expr, ReferenceHandle* source_type, ReferenceHandle* result_type) {
  return reinterpret_cast<ExpressionHandle*>(new Immutabilify(
      reinterpret_cast<Expression*>(source_expr), ref(source_type), ref(result_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_is_same_instance(
    ExpressionHandle* left_expr, ReferenceHandle* left_type,
    ExpressionHandle* right_expr, ReferenceHandle* right_type) {
  return reinterpret_cast<ExpressionHandle*>(new IsSameInstance(
      reinterpret_cast<Expression*>(left_expr), ref(left_type),
      reinterpret_cast<Expression*>(right_expr), ref(right_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_borrow_to_pointer(
    ExpressionHandle* source_expr, ReferenceHandle* result_type) {
  return reinterpret_cast<ExpressionHandle*>(new BorrowToPointer(
      reinterpret_cast<Expression*>(source_expr), ref(result_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_pointer_to_borrow(
    ExpressionHandle* source_expr, ReferenceHandle* result_type) {
  return reinterpret_cast<ExpressionHandle*>(new PointerToBorrow(
      reinterpret_cast<Expression*>(source_expr), ref(result_type)));
}

extern "C" VIS ExpressionHandle* metal_expr_narrow_permission(ExpressionHandle* source_expr) {
  return reinterpret_cast<ExpressionHandle*>(new NarrowPermission(
      reinterpret_cast<Expression*>(source_expr)));
}

extern "C" VIS ExpressionHandle* metal_expr_restackify(
    ExpressionHandle* source_expr, LocalHandle* local,
    int32_t known_live, const char* maybe_name_ptr, size_t maybe_name_len) {
  std::string n = (maybe_name_ptr && maybe_name_len > 0)
      ? std::string(maybe_name_ptr, maybe_name_len) : std::string();
  return reinterpret_cast<ExpressionHandle*>(new Restackify(
      reinterpret_cast<Expression*>(source_expr),
      reinterpret_cast<Local*>(local), known_live != 0, n));
}

// --- Package builder ---

// (RSA/SSA def constructors are exposed in metal_cache_ffi.h; impls below.)
struct PackageBuilder {
  MetalCache* cache;
  PackageCoordinate* packageCoord;
  std::unordered_map<std::string, InterfaceDefinition*> interfaces;
  std::unordered_map<std::string, StructDefinition*> structs;
  std::unordered_map<std::string, StaticSizedArrayDefinitionT*> staticSizedArrays;
  std::unordered_map<std::string, RuntimeSizedArrayDefinitionT*> runtimeSizedArrays;
  std::unordered_map<std::string, Function*> functions;
  std::unordered_map<std::string, Prototype*> exportNameToFunction;
  std::unordered_map<std::string, Kind*> exportNameToKind;
  std::unordered_map<std::string, Prototype*> externNameToFunction;
  std::unordered_map<std::string, Kind*> externNameToKind;
};

extern "C" VIS PackageBuilderHandle* metal_package_builder_new(
    MetalCacheHandle* h, PackageCoordHandle* package_coord) {
  auto* b = new PackageBuilder();
  b->cache = cache(h);
  b->packageCoord = pc(package_coord);
  return reinterpret_cast<PackageBuilderHandle*>(b);
}

#define PB(h) reinterpret_cast<PackageBuilder*>(h)

extern "C" VIS void metal_package_builder_add_interface(
    PackageBuilderHandle* h, const char* p, size_t n, InterfaceDefHandle* v) {
  PB(h)->interfaces[s(p, n)] = reinterpret_cast<InterfaceDefinition*>(v);
}
extern "C" VIS void metal_package_builder_add_struct(
    PackageBuilderHandle* h, const char* p, size_t n, StructDefHandle* v) {
  PB(h)->structs[s(p, n)] = reinterpret_cast<StructDefinition*>(v);
}
extern "C" VIS void metal_package_builder_add_function(
    PackageBuilderHandle* h, const char* p, size_t n, FunctionHandle* v) {
  PB(h)->functions[s(p, n)] = reinterpret_cast<Function*>(v);
}
extern "C" VIS void metal_package_builder_add_static_sized_array(
    PackageBuilderHandle* h, const char* p, size_t n, StaticSizedArrayDefHandle* v) {
  PB(h)->staticSizedArrays[s(p, n)] = reinterpret_cast<StaticSizedArrayDefinitionT*>(v);
}
extern "C" VIS void metal_package_builder_add_runtime_sized_array(
    PackageBuilderHandle* h, const char* p, size_t n, RuntimeSizedArrayDefHandle* v) {
  PB(h)->runtimeSizedArrays[s(p, n)] = reinterpret_cast<RuntimeSizedArrayDefinitionT*>(v);
}

extern "C" VIS StaticSizedArrayDefHandle* metal_static_sized_array_def_new(
    NameHandle* name, KindHandle* array_kind, int32_t size,
    RegionIdHandle* region_id, uint32_t mutability, uint32_t variability,
    ReferenceHandle* element_type) {
  return reinterpret_cast<StaticSizedArrayDefHandle*>(new StaticSizedArrayDefinitionT(
      nm(name), reinterpret_cast<StaticSizedArrayT*>(knd(array_kind)),
      size, rid(region_id),
      static_cast<Mutability>(mutability), static_cast<Variability>(variability),
      ref(element_type)));
}

extern "C" VIS RuntimeSizedArrayDefHandle* metal_runtime_sized_array_def_new(
    NameHandle* name, KindHandle* array_kind,
    RegionIdHandle* region_id, uint32_t mutability,
    ReferenceHandle* element_type) {
  return reinterpret_cast<RuntimeSizedArrayDefHandle*>(new RuntimeSizedArrayDefinitionT(
      nm(name), reinterpret_cast<RuntimeSizedArrayT*>(knd(array_kind)),
      rid(region_id), static_cast<Mutability>(mutability),
      ref(element_type)));
}
extern "C" VIS void metal_package_builder_add_export_function(
    PackageBuilderHandle* h, const char* p, size_t n, PrototypeHandle* v) {
  PB(h)->exportNameToFunction[s(p, n)] = reinterpret_cast<Prototype*>(v);
}
extern "C" VIS void metal_package_builder_add_export_kind(
    PackageBuilderHandle* h, const char* p, size_t n, KindHandle* v) {
  PB(h)->exportNameToKind[s(p, n)] = knd(v);
}
extern "C" VIS void metal_package_builder_add_extern_function(
    PackageBuilderHandle* h, const char* p, size_t n, PrototypeHandle* v) {
  PB(h)->externNameToFunction[s(p, n)] = reinterpret_cast<Prototype*>(v);
}
extern "C" VIS void metal_package_builder_add_extern_kind(
    PackageBuilderHandle* h, const char* p, size_t n, KindHandle* v) {
  PB(h)->externNameToKind[s(p, n)] = knd(v);
}

extern "C" VIS PackageHandle* metal_package_builder_finish(PackageBuilderHandle* h) {
  auto* b = PB(h);
  auto* pkg = new Package(
      b->cache->addressNumberer,
      b->packageCoord,
      std::move(b->interfaces),
      std::move(b->structs),
      std::move(b->staticSizedArrays),
      std::move(b->runtimeSizedArrays),
      std::move(b->functions),
      std::move(b->exportNameToFunction),
      std::move(b->exportNameToKind),
      std::move(b->externNameToFunction),
      std::move(b->externNameToKind));
  delete b;
  return reinterpret_cast<PackageHandle*>(pkg);
}

// --- Program builder ---

struct ProgramBuilder {
  MetalCache* cache;
  std::unordered_map<PackageCoordinate*, Package*,
      AddressHasher<PackageCoordinate*>, std::equal_to<PackageCoordinate*>> packages;

  ProgramBuilder(MetalCache* cache_)
    : cache(cache_),
      packages(0, cache_->addressNumberer->makeHasher<PackageCoordinate*>(),
               std::equal_to<PackageCoordinate*>()) {}
};

extern "C" VIS ProgramBuilderHandle* metal_program_builder_new(MetalCacheHandle* h) {
  return reinterpret_cast<ProgramBuilderHandle*>(new ProgramBuilder(cache(h)));
}

#define ProgB(h) reinterpret_cast<ProgramBuilder*>(h)

extern "C" VIS void metal_program_builder_add_package(
    ProgramBuilderHandle* h, PackageCoordHandle* coord, PackageHandle* package) {
  ProgB(h)->packages[pc(coord)] = reinterpret_cast<Package*>(package);
}

extern "C" VIS ProgramHandle* metal_program_builder_finish(ProgramBuilderHandle* h) {
  auto* b = ProgB(h);
  auto* program = new Program(std::move(b->packages));
  delete b;
  return reinterpret_cast<ProgramHandle*>(program);
}

extern "C" VIS void metal_program_free(ProgramHandle* h) {
  // The Program owns its Packages (`new`-allocated via the builder); but
  // Package's destructor doesn't recursively free StructDefinitions /
  // Functions / etc. — those leak until the MetalCache is freed. That mirrors
  // Backend's current ownership story (everything is freed at process exit).
  auto* p = reinterpret_cast<Program*>(h);
  for (auto& [_, pkg] : p->packages) {
    delete pkg;
  }
  delete p;
}
