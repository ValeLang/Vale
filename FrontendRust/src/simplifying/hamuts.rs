//
// Mutable bookkeeping state threaded through every simplifying-pass translation.
// Per the Slab 17 architect directive, the Rust port mirrors typing pass's
// `CompilerOutputs`: a single mutable struct with HashMap fields and `&mut self`
// methods. There is no HamutsBox/Hamuts split — Scala's `HamutsBox` (the mutable
// wrapper) and `Hamuts` (the immutable value) collapse into one `Hamuts` struct.
// The collapsed struct keeps HamutsBox's mutating API (`&mut self` methods) plus
// its field-getters (`&self` accessors). Scala's immutable `Hamuts.foo`
// functional-update methods are preserved in the audit trail under
// `// mig: ... (collapsed)` markers — they map onto the same `&mut self` methods
// above, so they get no separate Rust fn.

use std::collections::HashMap;

use crate::interner::StrI;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::instantiating::ast::types::{
    cI, InterfaceIT, RuntimeSizedArrayIT, StaticSizedArrayIT, StructIT,
};
use crate::instantiating::ast::ast::PrototypeI;
use crate::final_ast::ast::{
    FunctionH, FunctionRefH, InterfaceDefinitionH, PrototypeH, StructDefinitionH,
};
use crate::final_ast::types::{
    HamutsFunctionExtern, HamutsKindExtern, InterfaceHT, KindHT, OpaqueHT,
    RuntimeSizedArrayDefinitionHT, RuntimeSizedArrayHT, SimpleId, StaticSizedArrayDefinitionHT,
    StaticSizedArrayHT, StructHT,
};
use crate::scout_arena::ScoutArena;

// Scala's HamutsBox was a mutable wrapper around an immutable Hamuts. Per
// architect directive, the Rust port mirrors typing pass's `CompilerOutputs`:
// a single mutable struct (`Hamuts` below) with HashMap fields and `&mut self`
// methods. No HamutsBox/Hamuts split. The HamutsBox members below become the
// collapsed struct's accessors (`&self`) and mutating methods (`&mut self`).

impl<'s, 'i, 'h> Hamuts<'s, 'i, 'h> where 's: 'i, 'i: 'h {
    pub fn package_coord_to_export_name_to_function(&self) -> &HashMap<PackageCoordinate<'s>, HashMap<StrI<'s>, &'h PrototypeH<'s, 'h>>> {
        &self.package_coord_to_export_name_to_function
    }

    pub fn package_coord_to_export_name_to_kind(&self) -> &HashMap<PackageCoordinate<'s>, HashMap<StrI<'s>, KindHT<'s, 'h>>> {
        &self.package_coord_to_export_name_to_kind
    }

    pub fn package_coord_to_prototype_to_extern(&self) -> &HashMap<PackageCoordinate<'s>, HashMap<&'h PrototypeH<'s, 'h>, HamutsFunctionExtern<'s, 'h>>> {
        &self.package_coord_to_prototype_to_extern
    }

    pub fn package_coord_to_kind_to_extern(&self) -> &HashMap<PackageCoordinate<'s>, HashMap<&'h OpaqueHT<'s, 'h>, HamutsKindExtern<'s, 'h>>> {
        &self.package_coord_to_kind_to_extern
    }

    pub fn struct_t_to_opaque_h(&self) -> &HashMap<&'i StructIT<'s, 'i, cI>, &'h OpaqueHT<'s, 'h>> {
        &self.struct_t_to_opaque_h
    }

    pub fn struct_t_to_struct_h(&self) -> &HashMap<&'i StructIT<'s, 'i, cI>, &'h StructHT<'s, 'h>> {
        &self.struct_t_to_struct_h
    }

    pub fn struct_t_to_struct_def_h(&self) -> &HashMap<&'i StructIT<'s, 'i, cI>, StructDefinitionH<'s, 'h>> {
        &self.struct_t_to_struct_def_h
    }

    pub fn struct_defs(&self) -> &Vec<StructDefinitionH<'s, 'h>> {
        &self.struct_defs
    }

    pub fn interface_t_to_interface_h(&self) -> &HashMap<&'i InterfaceIT<'s, 'i, cI>, &'h InterfaceHT<'s, 'h>> {
        &self.interface_t_to_interface_h
    }

    pub fn interface_t_to_interface_def_h(&self) -> &HashMap<&'i InterfaceIT<'s, 'i, cI>, InterfaceDefinitionH<'s, 'h>> {
        &self.interface_t_to_interface_def_h
    }

    pub fn function_refs(&self) -> &HashMap<&'i PrototypeI<'s, 'i, cI>, FunctionRefH<'s, 'h>> {
        &self.function_refs
    }

    pub fn function_defs(&self) -> &HashMap<&'i PrototypeI<'s, 'i, cI>, FunctionH<'s, 'h>> {
        &self.function_defs
    }

    pub fn static_sized_arrays(&self) -> &HashMap<&'i StaticSizedArrayIT<'s, 'i, cI>, StaticSizedArrayDefinitionHT<'s, 'h>> {
        &self.static_sized_arrays
    }

    pub fn runtime_sized_arrays(&self) -> &HashMap<&'i RuntimeSizedArrayIT<'s, 'i, cI>, RuntimeSizedArrayDefinitionHT<'s, 'h>> {
        &self.runtime_sized_arrays
    }

    pub fn forward_declare_struct(&mut self, struct_it: &'i StructIT<'s, 'i, cI>, struct_ref_h: &'h StructHT<'s, 'h>) {
        self.struct_t_to_struct_h.insert(struct_it, struct_ref_h);
    }

    pub fn add_struct_originating_from_typing_pass(&mut self, struct_it: &'i StructIT<'s, 'i, cI>, struct_def_h: StructDefinitionH<'s, 'h>) {
        assert!(self.struct_t_to_struct_h.contains_key(&struct_it));
        self.struct_t_to_struct_def_h.insert(struct_it, struct_def_h);
        self.struct_defs.push(struct_def_h);
    }

    pub fn add_opaque(&mut self, struct_it: &'i StructIT<'s, 'i, cI>, opaque_h: &'h OpaqueHT<'s, 'h>) {
        assert!(!self.struct_t_to_opaque_h.contains_key(&struct_it));
        self.struct_t_to_opaque_h.insert(struct_it, opaque_h);
    }

    pub fn add_struct_originating_from_hammer(&mut self, struct_def_h: StructDefinitionH<'s, 'h>) {
        assert!(!self.struct_defs.iter().any(|d| d.id == struct_def_h.id));
        self.struct_defs.push(struct_def_h);
    }

    pub fn forward_declare_interface(&mut self, interface_it: &'i InterfaceIT<'s, 'i, cI>, interface_ref_h: &'h InterfaceHT<'s, 'h>) {
        self.interface_t_to_interface_h.insert(interface_it, interface_ref_h);
    }

    pub fn add_interface(&mut self, interface_it: &'i InterfaceIT<'s, 'i, cI>, interface_def_h: InterfaceDefinitionH<'s, 'h>) {
        self.interface_t_to_interface_def_h.insert(interface_it, interface_def_h);
    }

    pub fn add_static_sized_array(&mut self, ssa_it: &'i StaticSizedArrayIT<'s, 'i, cI>, static_sized_array_definition_th: StaticSizedArrayDefinitionHT<'s, 'h>) {
        self.static_sized_arrays.insert(ssa_it, static_sized_array_definition_th);
    }

    pub fn add_runtime_sized_array(&mut self, rsa_it: &'i RuntimeSizedArrayIT<'s, 'i, cI>, runtime_sized_array_definition_th: RuntimeSizedArrayDefinitionHT<'s, 'h>) {
        self.runtime_sized_arrays.insert(rsa_it, runtime_sized_array_definition_th);
    }

    pub fn forward_declare_function(&mut self, function_ref2: &'i PrototypeI<'s, 'i, cI>, function_ref_h: FunctionRefH<'s, 'h>) {
        assert!(!self.function_refs.contains_key(&function_ref2));
        self.function_refs.insert(function_ref2, function_ref_h);
    }

    pub fn add_function(&mut self, function_ref2: &'i PrototypeI<'s, 'i, cI>, function_def_h: FunctionH<'s, 'h>) {
        assert!(self.function_refs.contains_key(&function_ref2));
        if self.function_defs.values().any(|f| f.prototype.id == function_def_h.prototype.id) {
            panic!("Internal error: Can't add function (already has function with same hammer name)");
        }
        self.function_defs.insert(function_ref2, function_def_h);
    }

    pub fn add_kind_export(&mut self, kind: KindHT<'s, 'h>, package_coordinate: PackageCoordinate<'s>, exported_name: StrI<'s>) {
        let export_name_to_kind = self.package_coord_to_export_name_to_kind.entry(package_coordinate).or_insert_with(HashMap::new);
        if let Some(existing) = export_name_to_kind.get(&exported_name) {
            panic!("Already exported a kind `{:?}` from package `{:?} : {:?}", exported_name, package_coordinate, existing);
        }
        export_name_to_kind.insert(exported_name, kind);
    }

    pub fn add_function_export(&mut self, prototype: &'h PrototypeH<'s, 'h>, package_coordinate: PackageCoordinate<'s>, exported_name: StrI<'s>) {
        let export_name_to_function = self.package_coord_to_export_name_to_function.entry(package_coordinate).or_insert_with(HashMap::new);
        if let Some(existing_full_name) = export_name_to_function.get(&exported_name) {
            panic!("Already exported a `{:?}` from package `{:?} : {:?}", exported_name, package_coordinate, existing_full_name);
        }
        export_name_to_function.insert(exported_name, prototype);
    }

    pub fn add_kind_extern(&mut self, scout_arena: &ScoutArena<'s>, opaque_h: &'h OpaqueHT<'s, 'h>, simple_id: SimpleId<'s, 'h>, exported_name: String) {
        let package_coordinate = opaque_h.package_coord;
        let kind_to_extern = self.package_coord_to_kind_to_extern.entry(package_coordinate).or_insert_with(HashMap::new);
        match kind_to_extern.get(&opaque_h) {
            None => {
                kind_to_extern.insert(opaque_h, HamutsKindExtern {
                    maybe_extern_name: scout_arena.intern_str(&exported_name),
                    kind: KindHT::OpaqueHT(opaque_h),
                    simple_id,
                });
            }
            Some(_existing_full_name) => {
                panic!("Already exported a `{}` from package `{:?}`", exported_name, package_coordinate);
            }
        }
    }

    pub fn add_function_extern(&mut self, prototype: &'h PrototypeH<'s, 'h>, simple_id: SimpleId<'s, 'h>, exported_name: StrI<'s>) {
        let package_coordinate = prototype.id.package_coordinate;
        let prototype_to_extern = self.package_coord_to_prototype_to_extern.entry(package_coordinate).or_insert_with(HashMap::new);
        if let Some(existing) = prototype_to_extern.get(prototype) {
            panic!("Already exported a `{:?}` from package `{:?} : {:?}", exported_name, package_coordinate, existing);
        }
        prototype_to_extern.insert(prototype, HamutsFunctionExtern { maybe_extern_name: exported_name, prototype, simple_id });
    }

    pub fn get_static_sized_array(&self, static_sized_array_th: &'h StaticSizedArrayHT<'s, 'h>) -> StaticSizedArrayDefinitionHT<'s, 'h> {
        *self.static_sized_arrays.iter().find(|(_, def)| std::ptr::eq(def.name as *const _, static_sized_array_th.id as *const _)).expect("get_static_sized_array: not found").1
    }

    pub fn get_runtime_sized_array(&self, runtime_sized_array_th: &'h RuntimeSizedArrayHT<'s, 'h>) -> RuntimeSizedArrayDefinitionHT<'s, 'h> {
        *self.runtime_sized_arrays.iter().find(|(_, def)| std::ptr::eq(def.name as *const _, runtime_sized_array_th.name as *const _)).expect("get_runtime_sized_array: not found").1
    }
}

/// Temporary state
//
// Mutable accumulator. `'s` is the scout/parser arena (matches Scala's
// `PackageCoordinate`/`StrI`). `'i` is the instantiating arena (matches Scala's
// `StructIT[cI]`/`InterfaceIT[cI]`/`PrototypeI[cI]` keys). `'h` is the
// simplifying arena.
pub struct Hamuts<'s, 'i, 'h>
where 's: 'i, 'i: 'h,
{
    pub human_name_to_full_name_to_id: HashMap<String, HashMap<String, i32>>,
    pub struct_t_to_opaque_h: HashMap<&'i StructIT<'s, 'i, cI>, &'h OpaqueHT<'s, 'h>>,
    pub struct_t_to_struct_h: HashMap<&'i StructIT<'s, 'i, cI>, &'h StructHT<'s, 'h>>,
    pub struct_t_to_struct_def_h: HashMap<&'i StructIT<'s, 'i, cI>, StructDefinitionH<'s, 'h>>,
    pub struct_defs: Vec<StructDefinitionH<'s, 'h>>,
    pub static_sized_arrays: HashMap<&'i StaticSizedArrayIT<'s, 'i, cI>, StaticSizedArrayDefinitionHT<'s, 'h>>,
    pub runtime_sized_arrays: HashMap<&'i RuntimeSizedArrayIT<'s, 'i, cI>, RuntimeSizedArrayDefinitionHT<'s, 'h>>,
    pub interface_t_to_interface_h: HashMap<&'i InterfaceIT<'s, 'i, cI>, &'h InterfaceHT<'s, 'h>>,
    pub interface_t_to_interface_def_h: HashMap<&'i InterfaceIT<'s, 'i, cI>, InterfaceDefinitionH<'s, 'h>>,
    pub function_refs: HashMap<&'i PrototypeI<'s, 'i, cI>, FunctionRefH<'s, 'h>>,
    pub function_defs: HashMap<&'i PrototypeI<'s, 'i, cI>, FunctionH<'s, 'h>>,
    pub package_coord_to_export_name_to_function: HashMap<PackageCoordinate<'s>, HashMap<StrI<'s>, &'h PrototypeH<'s, 'h>>>,
    pub package_coord_to_export_name_to_kind: HashMap<PackageCoordinate<'s>, HashMap<StrI<'s>, KindHT<'s, 'h>>>,
    pub package_coord_to_prototype_to_extern: HashMap<PackageCoordinate<'s>, HashMap<&'h PrototypeH<'s, 'h>, HamutsFunctionExtern<'s, 'h>>>,
    pub package_coord_to_kind_to_extern: HashMap<PackageCoordinate<'s>, HashMap<&'h OpaqueHT<'s, 'h>, HamutsKindExtern<'s, 'h>>>,
}

// (Scala inner-Hamuts `addStructOriginatingFromHammer` — Q-collapsed into the `&mut self` method above; no Rust slice anchor here.)

