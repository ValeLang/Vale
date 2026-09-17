//
// Central `Hammer` god struct (typing-pass `Compiler` precedent — sub-hammers
// like `BlockHammer`/`ExpressionHammer`/etc. are NOT held as Rust struct
// fields. Their methods become `impl Hammer { ... }` blocks colocated in
// per-area files for organization).
//
// `LocalsBox` collapsed into single mutable `Locals` (architect-blessed
// pattern, same as `HamutsBox` → `Hamuts`).

use crate::final_ast::ast::{IdH, ProgramH, PrototypeH};
use crate::final_ast::instructions::{
    ConsecutorH, ConstantVoidH, ExpressionH, Local, StackifyH, VariableIdH,
};
use crate::final_ast::types::{CoordH, KindHT, NeverHT, Variability, VoidHT};
use crate::instantiating::ast::ast::{FunctionDefinitionI, FunctionExportI, FunctionExternI};
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::ast::names::{IdI, INameI, IVarNameI};
use crate::instantiating::ast::templata::ITemplataI;
use crate::instantiating::ast::types::{cI, CoordI, KindIT};
use crate::keywords::Keywords;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::simplifying::hamuts::Hamuts;
use std::collections::{HashMap, HashSet};
use crate::final_ast::ast::FunctionH;
use crate::final_ast::ast::InterfaceDefinitionH;
use crate::final_ast::ast::PackageH;
use crate::final_ast::ast::StructDefinitionH;
use crate::final_ast::types::RuntimeSizedArrayDefinitionHT;
use crate::final_ast::types::SimpleId;
use crate::final_ast::types::SimpleIdStep;
use crate::final_ast::types::StaticSizedArrayDefinitionHT;
use crate::instantiating::ast::ast::KindExportI;
use crate::instantiating::instantiating_interner::InstantiatingInterner;
use crate::scout_arena::ScoutArena;
use crate::simplifying::name_hammer::simplify_id;
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::code_hierarchy::PackageCoordinateMap;
use std::mem::discriminant;

// in `Hammer.scala`, but it's a pure data type that colocates with the other
// finalast H-side AST. Field shape `{ prototype: &'h PrototypeH<...> }` per Scala.)

// Scala's LocalsBox was a mutable wrapper around an immutable Locals. Per
// architect directive (matching typing-pass `CompilerOutputs` precedent and
// the `HamutsBox` → `Hamuts` collapse), the Rust port has a single mutable
// `Locals` accumulator. The LocalsBox methods become &mut self/&self methods on
// Locals (below); the immutable `Locals.foo` functional-update methods collapse
// into those (preserved as audit-trail under `(collapsed)` markers).

impl<'s, 'i, 'h> Locals<'s, 'i, 'h>
where 's: 'i, 'i: 'h,
{
    pub fn snapshot(&self) -> Locals<'s, 'i, 'h> {
        Locals {
            typing_pass_locals: self.typing_pass_locals.clone(),
            unstackified_vars: self.unstackified_vars.clone(),
            locals: self.locals.clone(),
            next_local_id_number: self.next_local_id_number,
        }
    }

    pub fn typing_pass_locals(&self) -> &HashMap<&'i IVarNameI<'s, 'i, cI>, VariableIdH<'s, 'h>> {
        panic!("Unimplemented: typing_pass_locals");
    }

    pub fn unstackified_vars(&self) -> &HashSet<VariableIdH<'s, 'h>> {
        panic!("Unimplemented: unstackified_vars");
    }

    pub fn locals(&self) -> &HashMap<VariableIdH<'s, 'h>, Local<'s, 'h>> {
        panic!("Unimplemented: locals");
    }

    pub fn next_local_id_number(&self) -> i32 {
        panic!("Unimplemented: next_local_id_number");
    }

// disambiguated per instantiating overload-suffix pattern.)
    pub fn get_by_var_name(&self, id: &IVarNameI<'s, 'i, cI>) -> Option<Local<'s, 'h>> {
        self.typing_pass_locals.get(id).copied().and_then(|var_id| self.locals.get(&var_id).copied())
    }

    pub fn get(&self, id: VariableIdH<'s, 'h>) -> Option<Local<'s, 'h>> {
        panic!("Unimplemented: get");
    }

    pub fn mark_unstackified_by_var_name(&mut self, var_id: &'i IVarNameI<'s, 'i, cI>) {
        let var_id_h = *self.typing_pass_locals.get(var_id).expect("typing_pass_locals missing");
        self.mark_unstackified(var_id_h);
    }

    pub fn mark_restackified_by_var_name(&mut self, var_id: &'i IVarNameI<'s, 'i, cI>) {
        let var_id_h = *self.typing_pass_locals.get(var_id).expect("typing_pass_locals missing");
        self.mark_restackified(var_id_h);
    }

    pub fn mark_unstackified(&mut self, var_id_h: VariableIdH<'s, 'h>) {
        assert!(self.locals.contains_key(&var_id_h));
        if self.unstackified_vars.contains(&var_id_h) {
            panic!("Already unstackified {:?}", var_id_h);
        }
        self.unstackified_vars.insert(var_id_h);
    }

    pub fn set_next_local_id_number(&mut self, next_local_id_number: i32) {
        self.next_local_id_number = next_local_id_number;
    }

    pub fn add_hammer_local(
        &mut self,
        tyype: CoordH<'s, 'h>,
        variability: Variability,
    ) -> Local<'s, 'h> {
        let new_local_height = self.locals.len() as i32;
        let new_local_id_number = self.next_local_id_number;
        let new_local_id = VariableIdH { number: new_local_id_number, height: new_local_height, name: None };
        let new_local = Local { id: new_local_id, variability, type_h: tyype };
        self.locals.insert(new_local_id, new_local);
        self.next_local_id_number = new_local_id_number + 1;
        new_local
    }

    pub fn add_typing_pass_local(
        &mut self,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        var_id_name_h: &'h IdH<'s>,
        variability: Variability,
        tyype: CoordH<'s, 'h>,
    ) -> Local<'s, 'h> {
        self.add_compiler_local(var_id, var_id_name_h, variability, tyype)
    }
}

/// Temporary state
pub struct Locals<'s, 'i, 'h>
where 's: 'i, 'i: 'h,
{
    pub typing_pass_locals: HashMap<&'i IVarNameI<'s, 'i, cI>, VariableIdH<'s, 'h>>,
    pub unstackified_vars: HashSet<VariableIdH<'s, 'h>>,
    pub locals: HashMap<VariableIdH<'s, 'h>, Local<'s, 'h>>,
    pub next_local_id_number: i32,
}

// add_typing_pass_local on LocalsBox in Scala. With LocalsBox collapsed, both
// methods live here.)
impl<'s, 'i, 'h> Locals<'s, 'i, 'h>
where 's: 'i, 'i: 'h,
{
    pub fn add_compiler_local(
        &mut self,
        var_id: &'i IVarNameI<'s, 'i, cI>,
        var_id_name_h: &'h IdH<'s>,
        variability: Variability,
        tyype: CoordH<'s, 'h>,
    ) -> Local<'s, 'h> {
        if self.typing_pass_locals.contains_key(&var_id) {
            panic!("There's already a typingpass local named: {:?}", var_id);
        }
        let new_local_height = self.locals.len() as i32;
        let new_local_id_number = self.next_local_id_number;
        let new_local_id = VariableIdH { number: new_local_id_number, height: new_local_height, name: Some(var_id_name_h) };
        let new_local = Local { id: new_local_id, variability, type_h: tyype };
        self.typing_pass_locals.insert(var_id, new_local_id);
        self.locals.insert(new_local_id, new_local);
        self.next_local_id_number = new_local_id_number + 1;
        new_local
    }

    pub fn mark_restackified(&mut self, var_id_h: VariableIdH<'s, 'h>) {
        // Make sure it existed and was unstackified
        assert!(self.locals.contains_key(&var_id_h));
        if !self.unstackified_vars.contains(&var_id_h) {
            panic!("Already unstackified {:?}", var_id_h);
        }
        self.unstackified_vars.remove(&var_id_h);
    }
}

/// Temporary state
//
// Central god struct (typing-pass `Compiler` precedent). Sub-hammer
// fields from Scala (`nameHammer`, `structHammer`, `typeHammer`,
// `functionHammer`, `vonHammer`) NOT held as Rust fields — their methods
// become `impl Hammer { ... }` blocks colocated in per-area files.
pub struct Hammer<'s, 'i, 'h, 'ctx>
where 's: 'i, 's: 'h, 'i: 'h,
{
    pub interner: &'ctx HammerInterner<'s, 'h>,
    pub keywords: &'ctx Keywords<'s>,
    pub scout_arena: &'ctx ScoutArena<'s>,
    pub instantiating_interner: &'ctx InstantiatingInterner<'s, 'i>,
}

impl<'s, 'i, 'h, 'ctx> Hammer<'s, 'i, 'h, 'ctx>
where 's: 'h, 's: 'i, 'i: 'h,
{
    pub fn mangle_func(&self, id: &IdI<'s, 'i, cI>) -> String {
        panic!("Unimplemented: mangle_func");
    }

    pub fn mangle_name(&self, name: &INameI<'s, 'i, cI>, stuff_after: bool) -> String {
        panic!("Unimplemented: mangle_name");
    }

    pub fn mangle_struct(&self, id: &IdI<'s, 'i, cI>) -> String {
        String::new()
    }

    pub fn mangle_kind(&self, kind: &KindIT<'s, 'i, cI>) -> String {
        panic!("Unimplemented: mangle_kind");
    }

    pub fn mangle_coord(&self, coord: &CoordI<'s, 'i, cI>) -> String {
        panic!("Unimplemented: mangle_coord");
    }

    pub fn mangle_templata(&self, templata: &ITemplataI<'s, 'i, cI>) -> String {
        panic!("Unimplemented: mangle_templata");
    }

    pub fn translate(&self, hinputs: &HinputsI<'s, 'i>) -> &'h ProgramH<'s, 'h> {
        let HinputsI {
            interfaces,
            structs,
            functions,
            interface_to_edge_blueprints,
            interface_to_sub_citizen_to_edge: edges,
            kind_exports,
            function_exports,
            kind_externs,
            function_externs,
        } = hinputs;

        let mut hamuts = Hamuts {
            human_name_to_full_name_to_id: HashMap::new(),
            struct_t_to_opaque_h: HashMap::new(),
            struct_t_to_struct_h: HashMap::new(),
            struct_t_to_struct_def_h: HashMap::new(),
            struct_defs: Vec::new(),
            static_sized_arrays: HashMap::new(),
            runtime_sized_arrays: HashMap::new(),
            interface_t_to_interface_h: HashMap::new(),
            interface_t_to_interface_def_h: HashMap::new(),
            function_refs: HashMap::new(),
            function_defs: HashMap::new(),
            package_coord_to_export_name_to_function: HashMap::new(),
            package_coord_to_export_name_to_kind: HashMap::new(),
            package_coord_to_prototype_to_extern: HashMap::new(),
            package_coord_to_kind_to_extern: HashMap::new(),
        };

        for KindExportI { range: _, tyype, id: export_id, exported_name } in kind_exports.iter() {
            let kind_h = self.translate_kind(hinputs, &mut hamuts, *tyype);
            hamuts.add_kind_export(kind_h, *export_id.package_coord, *exported_name);
        }

        for FunctionExportI { range: _, prototype, export_id, exported_name } in function_exports.iter() {
            let prototype_h = self.translate_prototype(hinputs, &mut hamuts, prototype);
            hamuts.add_function_export(prototype_h, *export_id.package_coord, *exported_name);
        }

        for (r#struct, _kind_extern) in kind_externs.iter() {
            let export_name = self.mangle_struct(&r#struct.id);
            let export_simplified_id = simplify_id(self.interner, self.scout_arena, &r#struct.id);
            let opaque_h = self.translate_opaque_i(hinputs, &mut hamuts, *r#struct);
            hamuts.add_kind_extern(self.scout_arena, opaque_h, export_simplified_id, export_name);
        }

        for FunctionExternI { prototype, num_inherited_generic_parameters } in function_externs.iter() {
            let num_inherited = *num_inherited_generic_parameters;
            let export_name = if prototype.id.package_coord.module.0 == "rust" {
                panic!("translate functionExterns: rust-package empty-name branch")
            } else {
                match prototype.id.local_name {
                    INameI::ExternFunction(extern_fn) => extern_fn.human_name,
                    other => panic!("translate functionExterns: unexpected local_name variant {:?}", discriminant(&other)),
                }
            };
            let raw_simple_id = simplify_id(self.interner, self.scout_arena, &prototype.id);
            let export_simplified_id = if num_inherited == 0 {
                raw_simple_id
            } else {
                // Per @PRIIROZ, inherited generic params come AFTER own ones in the leaf step's
                // templateArgs (e.g. `zork<N, K, V>` where N is own and K, V are inherited).
                // Move the trailing `numInherited` args off the leaf step onto the immediately
                // preceding (parent citizen) step, producing e.g. `[..., Vec<i32>, capacity]`
                // instead of `[..., Vec, capacity<i32>]`. This is the shape Backend's
                // rustifySimpleId expects per @SMLRZ.
                let steps = raw_simple_id.steps;
                let leaf = steps.last().unwrap();
                let parent_idx = steps.len() - 2;
                let parent = &steps[parent_idx];
                let split_point = leaf.template_args.len() - num_inherited as usize;
                let (own, inherited) = leaf.template_args.split_at(split_point);
                let new_parent_template_args: Vec<SimpleId<'s, 'h>> = parent.template_args.iter().copied().chain(inherited.iter().copied()).collect();
                let new_parent = SimpleIdStep {
                    name: parent.name,
                    template_args: self.interner.bump().alloc_slice_copy(&new_parent_template_args),
                };
                let new_leaf = SimpleIdStep {
                    name: leaf.name,
                    template_args: self.interner.bump().alloc_slice_copy(own),
                };
                let mut new_steps: Vec<SimpleIdStep<'s, 'h>> = steps.to_vec();
                new_steps[parent_idx] = new_parent;
                let last_idx = new_steps.len() - 1;
                new_steps[last_idx] = new_leaf;
                SimpleId { steps: self.interner.bump().alloc_slice_copy(&new_steps) }
            };
            let prototype_h = self.translate_prototype(hinputs, &mut hamuts, prototype);
            hamuts.add_function_extern(prototype_h, export_simplified_id, export_name);
        }

        self.translate_interfaces(hinputs, &mut hamuts);
        self.translate_structs(hinputs, &mut hamuts);
        let user_functions: Vec<&FunctionDefinitionI> = functions.iter().filter(|f| f.header.is_user_function()).copied().collect();
        let non_user_functions: Vec<&FunctionDefinitionI> = functions.iter().filter(|f| !f.header.is_user_function()).copied().collect();
        self.translate_functions(hinputs, &mut hamuts, &user_functions);
        self.translate_functions(hinputs, &mut hamuts, &non_user_functions);

        let mut package_to_interface_defs: HashMap<PackageCoordinate<'s>, Vec<InterfaceDefinitionH<'s, 'h>>> = HashMap::new();
        for (it, idh) in hamuts.interface_t_to_interface_def_h.iter() {
            package_to_interface_defs.entry(*it.id.package_coord).or_insert_with(Vec::new).push(*idh);
        }
        let mut package_to_struct_defs: HashMap<PackageCoordinate<'s>, Vec<StructDefinitionH<'s, 'h>>> = HashMap::new();
        for sd in hamuts.struct_defs.iter() {
            package_to_struct_defs.entry(sd.id.package_coordinate).or_insert_with(Vec::new).push(*sd);
        }
        let mut package_to_function_defs: HashMap<PackageCoordinate<'s>, Vec<FunctionH<'s, 'h>>> = HashMap::new();
        for (proto, fdh) in hamuts.function_defs.iter() {
            package_to_function_defs.entry(*proto.id.package_coord).or_insert_with(Vec::new).push(*fdh);
        }
        let mut package_to_static_sized_arrays: HashMap<PackageCoordinate<'s>, Vec<StaticSizedArrayDefinitionHT<'s, 'h>>> = HashMap::new();
        for (_, ssad) in hamuts.static_sized_arrays.iter() {
            package_to_static_sized_arrays.entry(ssad.name.package_coordinate).or_insert_with(Vec::new).push(*ssad);
        }
        let mut package_to_runtime_sized_arrays: HashMap<PackageCoordinate<'s>, Vec<RuntimeSizedArrayDefinitionHT<'s, 'h>>> = HashMap::new();
        for (_, rsad) in hamuts.runtime_sized_arrays.iter() {
            package_to_runtime_sized_arrays.entry(rsad.name.package_coordinate).or_insert_with(Vec::new).push(*rsad);
        }
        let mut all_package_coords: HashSet<PackageCoordinate<'s>> = HashSet::new();
        all_package_coords.extend(package_to_interface_defs.keys());
        all_package_coords.extend(package_to_struct_defs.keys());
        all_package_coords.extend(package_to_function_defs.keys());
        all_package_coords.extend(package_to_static_sized_arrays.keys());
        all_package_coords.extend(package_to_runtime_sized_arrays.keys());
        all_package_coords.extend(hamuts.package_coord_to_export_name_to_function.keys());
        all_package_coords.extend(hamuts.package_coord_to_export_name_to_kind.keys());
        all_package_coords.extend(hamuts.package_coord_to_prototype_to_extern.keys());
        all_package_coords.extend(hamuts.package_coord_to_kind_to_extern.keys());
        let mut packages: PackageCoordinateMap<'s, PackageH<'s, 'h>> = PackageCoordinateMap::new();
        for package_coord in all_package_coords.into_iter() {
            let interfaces = self.interner.alloc_slice_from_vec(package_to_interface_defs.get(&package_coord).cloned().unwrap_or_default());
            let structs = self.interner.alloc_slice_from_vec(package_to_struct_defs.get(&package_coord).cloned().unwrap_or_default());
            let functions = self.interner.alloc_slice_from_vec(package_to_function_defs.get(&package_coord).cloned().unwrap_or_default());
            let static_sized_arrays = self.interner.alloc_slice_from_vec(package_to_static_sized_arrays.get(&package_coord).cloned().unwrap_or_default());
            let runtime_sized_arrays = self.interner.alloc_slice_from_vec(package_to_runtime_sized_arrays.get(&package_coord).cloned().unwrap_or_default());
            let export_name_to_function = self.interner.alloc(ArenaIndexMap::from_iter_in(hamuts.package_coord_to_export_name_to_function.get(&package_coord).into_iter().flat_map(|m| m.iter().map(|(k, v)| (*k, *v))), self.interner.bump()));
            let export_name_to_kind = self.interner.alloc(ArenaIndexMap::from_iter_in(hamuts.package_coord_to_export_name_to_kind.get(&package_coord).into_iter().flat_map(|m| m.iter().map(|(k, v)| (*k, *v))), self.interner.bump()));
            let prototype_to_extern = self.interner.alloc(ArenaIndexMap::from_iter_in(hamuts.package_coord_to_prototype_to_extern.get(&package_coord).into_iter().flat_map(|m| m.iter().map(|(k, v)| (*k, *v))), self.interner.bump()));
            let kind_to_extern = self.interner.alloc(ArenaIndexMap::from_iter_in(hamuts.package_coord_to_kind_to_extern.get(&package_coord).into_iter().flat_map(|m| m.iter().map(|(k, v)| (*k, *v))), self.interner.bump()));
            let package_coord_ref = self.scout_arena.intern_package_coordinate(package_coord.module, package_coord.packages.as_slice());
            packages.put(package_coord_ref, PackageH {
                interfaces, structs, functions, static_sized_arrays, runtime_sized_arrays,
                export_name_to_function, export_name_to_kind, prototype_to_extern, kind_to_extern,
            });
        }
        self.interner.alloc(ProgramH { packages })
    }
}

pub fn flatten_and_filter_voids<'s, 'h>(
    unfiltered_exprs_h: &[ExpressionH<'s, 'h>],
) -> Vec<ExpressionH<'s, 'h>>
where 's: 'h,
{
    let mut flattened_exprs_he: Vec<ExpressionH<'s, 'h>> = Vec::new();
    for e in unfiltered_exprs_h.iter() {
        match e {
            ExpressionH::ConsecutorH(c) => { flattened_exprs_he.extend_from_slice(c.exprs); }
            other => { flattened_exprs_he.push(*other); }
        }
    }
    if let Some((_, init)) = flattened_exprs_he.split_last() {
        for expr_he in init.iter() {
            match expr_he.result_type().kind {
                KindHT::NeverHT(_) => panic!("flatten_and_filter_voids: vwat NeverHT in init"),
                _ => {}
            }
        }
    }
    let filtered_flattened_exprs_he: Vec<ExpressionH<'s, 'h>> = if flattened_exprs_he.len() <= 1 {
        flattened_exprs_he
    } else {
        let (last, init) = flattened_exprs_he.split_last().unwrap();
        let mut result: Vec<_> = init.iter().filter(|e| !matches!(e, ExpressionH::ConstantVoidH(_))).copied().collect();
        result.push(*last);
        result
    };
    assert!(!filtered_flattened_exprs_he.is_empty());
    filtered_flattened_exprs_he
}

pub fn consecutive<'s, 'h>(
    interner: &HammerInterner<'s, 'h>,
    unfiltered_exprs_h: &[ExpressionH<'s, 'h>],
) -> ExpressionH<'s, 'h>
where 's: 'h,
{
    let filtered_flattened_exprs_he = flatten_and_filter_voids(unfiltered_exprs_h);
    match filtered_flattened_exprs_he.as_slice() {
        [] => panic!("Cant have empty consecutive"),
        [only] => *only,
        multiple => ExpressionH::ConsecutorH(interner.alloc(ConsecutorH { exprs: interner.alloc_slice_copy(multiple) })),
    }
}

pub fn consecrash<'s, 'i, 'h>(
    interner: &HammerInterner<'s, 'h>,
    locals: &mut Locals<'s, 'i, 'h>,
    unfiltered_exprs_h: &[ExpressionH<'s, 'h>],
) -> ExpressionH<'s, 'h>
where 's: 'i, 'i: 'h,
{
    match unfiltered_exprs_h.last().expect("consecrash empty").result_type().kind {
        KindHT::NeverHT(_) => {}
        _ => panic!("consecrash: vwat last not Never"),
    }
    let exprs_he = flatten_and_filter_voids(unfiltered_exprs_h);
    let (last, init) = exprs_he.split_last().expect("consecrash flat empty");
    let mut exprs_with_stackified_init_he: Vec<ExpressionH<'s, 'h>> = init.iter().map(|expr| {
        if expr.result_type().kind == KindHT::VoidHT(VoidHT) {
            *expr
        } else {
            let local = locals.add_hammer_local(expr.result_type(), Variability::Final);
            ExpressionH::StackifyH(interner.bump().alloc(StackifyH { source_expr: *expr, local, name: None }))
        }
    }).collect();
    exprs_with_stackified_init_he.push(*last);
    // We'll never need to unstackify them because we're about to crash.
    match exprs_with_stackified_init_he.len() {
        0 => panic!("Cant have empty consecutive"),
        1 => exprs_with_stackified_init_he.into_iter().next().unwrap(),
        _ => ExpressionH::ConsecutorH(interner.bump().alloc(ConsecutorH { exprs: interner.bump().alloc_slice_fill_iter(exprs_with_stackified_init_he.into_iter()) })),
    }
}

