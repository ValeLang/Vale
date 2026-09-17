use std::collections::HashMap;
use crate::typing::compiler::Compiler;
use crate::typing::types::types::*;
use crate::typing::ast::ast::*;
use crate::typing::compiler_outputs::*;
use crate::typing::ast::ast::InterfaceEdgeBlueprintT;
use std::collections::HashSet;

pub struct Reachables<'s, 't> {
    pub functions: HashSet<SignatureT<'s, 't>>,
    pub structs: HashSet<StructTT<'s, 't>>,
    pub static_sized_arrays: HashSet<StaticSizedArrayTT<'s, 't>>,
    pub runtime_sized_arrays: HashSet<RuntimeSizedArrayTT<'s, 't>>,
    pub interfaces: HashSet<InterfaceTT<'s, 't>>,
    pub edges: HashSet<EdgeT<'s, 't>>,
}

impl<'s, 't> Reachables<'s, 't> {

pub fn size(&self) -> usize {
    panic!("Unimplemented: Slab 15 — body migration");
}

}

impl<'s, 'ctx, 't> Compiler<'s, 'ctx, 't>
where 's: 't,
{
    pub fn find_reachables(
        &self,
        program: &CompilerOutputs<'s, 't>,
        edge_blueprints: &[&'t InterfaceEdgeBlueprintT<'s, 't>],
        edges: &HashMap<InterfaceTT<'s, 't>, HashMap<StructTT<'s, 't>, Vec<&'t PrototypeT<'s, 't>>>>,
    ) -> Reachables<'s, 't> {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn visit_function(
        &self,
        program: &CompilerOutputs<'s, 't>,
        edge_blueprints: &[&'t InterfaceEdgeBlueprintT<'s, 't>],
        edges: &HashMap<InterfaceTT<'s, 't>, HashMap<StructTT<'s, 't>, Vec<&'t PrototypeT<'s, 't>>>>,
        reachables: &mut Reachables<'s, 't>,
        callee_signature: SignatureT<'s, 't>,
    ) {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn visit_struct(
        &self,
        program: &CompilerOutputs<'s, 't>,
        edge_blueprints: &[&'t InterfaceEdgeBlueprintT<'s, 't>],
        edges: &HashMap<InterfaceTT<'s, 't>, HashMap<StructTT<'s, 't>, Vec<&'t PrototypeT<'s, 't>>>>,
        reachables: &mut Reachables<'s, 't>,
        struct_tt: StructTT<'s, 't>,
    ) {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn visit_interface(
        &self,
        program: &CompilerOutputs<'s, 't>,
        edge_blueprints: &[&'t InterfaceEdgeBlueprintT<'s, 't>],
        edges: &HashMap<InterfaceTT<'s, 't>, HashMap<StructTT<'s, 't>, Vec<&'t PrototypeT<'s, 't>>>>,
        reachables: &mut Reachables<'s, 't>,
        interface_tt: InterfaceTT<'s, 't>,
    ) {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn visit_impl(
        &self,
        program: &CompilerOutputs<'s, 't>,
        edge_blueprints: &[&'t InterfaceEdgeBlueprintT<'s, 't>],
        edges: &HashMap<InterfaceTT<'s, 't>, HashMap<StructTT<'s, 't>, Vec<&'t PrototypeT<'s, 't>>>>,
        reachables: &mut Reachables<'s, 't>,
        interface_tt: InterfaceTT<'s, 't>,
        struct_tt: StructTT<'s, 't>,
        methods: &[&'t PrototypeT<'s, 't>],
    ) {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn visit_static_sized_array(
        &self,
        program: &CompilerOutputs<'s, 't>,
        edge_blueprints: &[&'t InterfaceEdgeBlueprintT<'s, 't>],
        edges: &HashMap<InterfaceTT<'s, 't>, HashMap<StructTT<'s, 't>, Vec<&'t PrototypeT<'s, 't>>>>,
        reachables: &mut Reachables<'s, 't>,
        ssa: StaticSizedArrayTT<'s, 't>,
    ) {
        panic!("Unimplemented: Slab 15 — body migration");
    }

    pub fn visit_runtime_sized_array(
        &self,
        program: &CompilerOutputs<'s, 't>,
        edge_blueprints: &[&'t InterfaceEdgeBlueprintT<'s, 't>],
        edges: &HashMap<InterfaceTT<'s, 't>, HashMap<StructTT<'s, 't>, Vec<&'t PrototypeT<'s, 't>>>>,
        reachables: &mut Reachables<'s, 't>,
        rsa: RuntimeSizedArrayTT<'s, 't>,
    ) {
        panic!("Unimplemented: Slab 15 — body migration");
    }

}
