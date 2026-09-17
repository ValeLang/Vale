
use std::collections::HashMap;
use crate::postparsing::names::IRuneS;
use crate::typing::ast::ast::{
    EdgeT, FunctionDefinitionT, FunctionExportT, FunctionExternT,
    InterfaceEdgeBlueprintT, KindExportT, KindExternT, PrototypeT, SignatureT,
};
use crate::typing::ast::citizens::{CitizenDefinitionT, InterfaceDefinitionT, StructDefinitionT};
use crate::typing::names::names::{
    FunctionTemplateNameT, INameT, IdT, ImplTemplateNameT, InterfaceTemplateNameT, StructTemplateNameT,
};
use crate::typing::typing_interner::TypingInterner;
use crate::utils::arena_index_map::ArenaIndexMap;
/// Arena-allocated (see @TFITCX)
// Structural-equality opt-in: Scala uses case-class `==` on this type via
// `vassert(existing == instantiationBoundArgs)` in addInstantiationBounds.
// TFITCX/IEOIBZ ptr-eq is for identity types; this is a value-bag.
#[derive(PartialEq, Eq)]
pub struct InstantiationReachableBoundArgumentsT<'s, 't> {
    pub citizen_rune_to_reachable_prototype: ArenaIndexMap<'t, IRuneS<'s>, PrototypeT<'s, 't>>,
}

pub fn make<'s, 't>(
    interner: &TypingInterner<'s, 't>,
    rune_to_bound_prototype: Vec<(IRuneS<'s>, PrototypeT<'s, 't>)>,
    rune_to_citizen_rune_to_reachable_prototype: Vec<(IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>)>,
    rune_to_bound_impl: Vec<(IRuneS<'s>, IdT<'s, 't>)>,
) -> &'t InstantiationBoundArgumentsT<'s, 't> {
    interner.alloc(InstantiationBoundArgumentsT {
        rune_to_bound_prototype: interner.alloc_index_map_from_iter(rune_to_bound_prototype.into_iter()),
        rune_to_citizen_rune_to_reachable_prototype: interner.alloc_index_map_from_iter(rune_to_citizen_rune_to_reachable_prototype.into_iter()),
        rune_to_bound_impl: interner.alloc_index_map_from_iter(rune_to_bound_impl.into_iter()),
    })
}

/// Arena-allocated (see @TFITCX)
// Structural-equality opt-in: Scala uses case-class `==` on this type via
// `vassert(existing == instantiationBoundArgs)` in addInstantiationBounds.
// TFITCX/IEOIBZ ptr-eq is for identity types; this is a value-bag.
#[derive(PartialEq, Eq)]
pub struct InstantiationBoundArgumentsT<'s, 't> {
    pub rune_to_bound_prototype: ArenaIndexMap<'t, IRuneS<'s>, PrototypeT<'s, 't>>,
    pub rune_to_citizen_rune_to_reachable_prototype: ArenaIndexMap<'t, IRuneS<'s>, &'t InstantiationReachableBoundArgumentsT<'s, 't>>,
    pub rune_to_bound_impl: ArenaIndexMap<'t, IRuneS<'s>, IdT<'s, 't>>,
}

impl<'s, 't> InstantiationBoundArgumentsT<'s, 't> {
    pub fn new() -> Self {
        panic!("Unimplemented: new");
    }

}
/// Temporary state (see @TFITCX)
pub struct HinputsT<'s, 't> {
    pub interfaces: Vec<&'t InterfaceDefinitionT<'s, 't>>,
    pub structs: Vec<&'t StructDefinitionT<'s, 't>>,
    pub functions: Vec<&'t FunctionDefinitionT<'s, 't>>,

    pub interface_to_edge_blueprints: HashMap<
        IdT<'s, 't>,
        &'t InterfaceEdgeBlueprintT<'s, 't>,
    >,
    pub interface_to_sub_citizen_to_edge: HashMap<
        IdT<'s, 't>,
        HashMap<IdT<'s, 't>, &'t EdgeT<'s, 't>>,
    >,

    pub instantiation_name_to_instantiation_bounds: HashMap<
        IdT<'s, 't>,
        &'t InstantiationBoundArgumentsT<'s, 't>,
    >,

    pub kind_exports: Vec<&'t KindExportT<'s, 't>>,
    pub function_exports: Vec<&'t FunctionExportT<'s, 't>>,
    pub kind_externs: Vec<&'t KindExternT<'s, 't>>,
    pub function_externs: Vec<&'t FunctionExternT<'s, 't>>,

    pub sub_citizen_to_interface_to_edge: HashMap<
        IdT<'s, 't>,
        HashMap<IdT<'s, 't>, &'t EdgeT<'s, 't>>,
    >,
}

impl<'s, 't> HinputsT<'s, 't> {
    pub fn new() -> Self {
        panic!("Unimplemented: new");
    }
    
    pub fn lookup_struct(&self, struct_id: IdT<'s, 't>) -> &'t StructDefinitionT<'s, 't> {
        *self.structs.iter().find(|s| s.instantiated_citizen.id == struct_id).expect("lookup_struct: missing")
    }
    
    pub fn lookup_struct_by_template(&self, struct_template_name: StructTemplateNameT) -> StructDefinitionT<'s, 't> {
        panic!("Unimplemented: lookup_struct_by_template");
    }
    
    pub fn lookup_interface_by_template(&self, interface_template_name: InterfaceTemplateNameT) -> InterfaceDefinitionT<'s, 't> {
        panic!("Unimplemented: lookup_interface_by_template");
    }
    
    pub fn lookup_impl_by_template(&self, impl_template_name: ImplTemplateNameT) -> EdgeT<'s, 't> {
        panic!("Unimplemented: lookup_impl_by_template");
    }
    
    pub fn lookup_interface(&self, interface_id: IdT<'s, 't>) -> InterfaceDefinitionT<'s, 't> {
        panic!("Unimplemented: lookup_interface");
    }
    
    pub fn lookup_edge(&self, impl_id: IdT<'s, 't>) -> &'t EdgeT<'s, 't> {
        let matches: Vec<&&'t EdgeT<'s, 't>> = self.interface_to_sub_citizen_to_edge
            .values()
            .flat_map(|m| m.values())
            .filter(|edge| edge.edge_id == impl_id)
            .collect();
        assert!(matches.len() == 1, "vassertOne: expected exactly one edge for impl_id {:?}, got {}", impl_id, matches.len());
        *matches[0]
    }
    
    pub fn get_instantiation_bound_args(&self, instantiation_name: IdT<'s, 't>) -> &'t InstantiationBoundArgumentsT<'s, 't> {
        *self.instantiation_name_to_instantiation_bounds.get(&instantiation_name).unwrap()
    }
    
    pub fn lookup_struct_by_template_id(&self, struct_template_id: IdT<'s, 't>) -> StructDefinitionT<'s, 't> {
        panic!("Unimplemented: lookup_struct_by_template_id");
    }
    
    pub fn lookup_interface_by_template_id(&self, interface_template_id: IdT<'s, 't>) -> InterfaceDefinitionT<'s, 't> {
        panic!("Unimplemented: lookup_interface_by_template_id");
    }
    
    pub fn lookup_citizen_by_template_id(&self, citizen_template_id: IdT<'s, 't>) -> CitizenDefinitionT<'s, 't> {
        panic!("Unimplemented: lookup_citizen_by_template_id");
    }
    
    pub fn lookup_struct_by_template_name(&self, struct_template_name: StructTemplateNameT<'s>) -> &'t StructDefinitionT<'s, 't> {
        let matches: Vec<&'t StructDefinitionT<'s, 't>> = self.structs.iter()
            .filter(|s| match s.template_name.local_name {
                INameT::StructTemplate(t) => *t == struct_template_name,
                _ => false,
            })
            .copied()
            .collect();
        match matches.len() {
            1 => matches[0],
            0 => panic!("lookup_struct_by_template_name: not found: {:?}", struct_template_name),
            _ => panic!("lookup_struct_by_template_name: multiple found: {:?}", struct_template_name),
        }
    }
    
    pub fn lookup_interface_by_template_name(&self, interface_template_name: &'t InterfaceTemplateNameT<'s>) -> &'t InterfaceDefinitionT<'s, 't> {
        self.interfaces.iter().copied()
            .find(|i| i.template_name.local_name == INameT::InterfaceTemplate(interface_template_name))
            .unwrap_or_else(|| panic!("lookup_interface_by_template_name: not found"))
    }
    
    pub fn lookup_function_by_signature(&self, signature2: SignatureT<'s, 't>) -> Option<&'t FunctionDefinitionT<'s, 't>> {
        self.functions.iter().copied().find(|f| f.header.to_signature() == signature2)
    }
    
    pub fn lookup_function_by_template(&self, func_template_name: FunctionTemplateNameT) -> Option<&'t FunctionDefinitionT<'s, 't>> {
        panic!("Unimplemented: lookup_function_by_template");
    }
    
    pub fn lookup_function_by_str(&self, human_name: &str) -> &'t FunctionDefinitionT<'s, 't> {
        let matches: Vec<_> = self.functions.iter().filter(|f| {
            match &f.header.id.local_name {
                INameT::Function(func_name) if func_name.template.human_name.as_str() == human_name => true,
                _ => false,
            }
        }).collect();
        if matches.is_empty() {
            panic!("Function \"{}\" not found!", human_name);
        } else if matches.len() > 1 {
            panic!("Multiple found!");
        }
        matches[0]
    }
    
    pub fn lookup_struct_by_str(&self, human_name: &str) -> &'t StructDefinitionT<'s, 't> {
        let matches: Vec<_> = self.structs.iter().filter(|s| {
            match &s.template_name.local_name {
                INameT::StructTemplate(t) if t.human_name.as_str() == human_name => true,
                _ => false,
            }
        }).copied().collect();
        if matches.is_empty() {
            panic!("Struct \"{}\" not found!", human_name);
        } else if matches.len() > 1 {
            panic!("Multiple found!");
        }
        matches[0]
    }
    
    pub fn lookup_impl(&self, sub_citizen_tt: IdT<'s, 't>, interface_tt: IdT<'s, 't>) -> &'t EdgeT<'s, 't> {
        self.interface_to_sub_citizen_to_edge
            .get(&interface_tt)
            .unwrap_or_else(|| panic!("lookup_impl: interface not found"))
            .get(&sub_citizen_tt)
            .unwrap_or_else(|| panic!("lookup_impl: sub citizen not found"))
    }
    
    pub fn lookup_interface_by_human_name(&self, human_name: &str) -> &'t InterfaceDefinitionT<'s, 't> {
        let matches: Vec<_> = self.interfaces.iter().filter(|i| {
            match &i.template_name.local_name {
                INameT::InterfaceTemplate(t) if t.human_namee.as_str() == human_name => true,
                _ => false,
            }
        }).copied().collect();
        if matches.is_empty() {
            panic!("Interface \"{}\" not found!", human_name);
        } else if matches.len() > 1 {
            panic!("Multiple found!");
        }
        matches[0]
    }
    
    pub fn lookup_user_function(&self, human_name: &str) -> FunctionDefinitionT<'s, 't> {
        panic!("Unimplemented: lookup_user_function");
    }
    
    pub fn name_is_lambda_in(&self, name: IdT<'s, 't>, needle_function_human_name: &str) -> bool {
        let steps = name.steps();
        let first = steps[0];
        let last_two = &steps[steps.len().saturating_sub(2)..steps.len()];
        match (first, last_two) {
            (
                INameT::Function(f),
                [
                    INameT::LambdaCitizenTemplate(_),
                    INameT::LambdaCallFunction(_),
                ],
            ) if f.template.human_name.0 == needle_function_human_name => true,
            _ => false,
        }
    }
    
    pub fn lookup_lambdas_in(&self, needle_function_human_name: &str) -> Vec<&'t FunctionDefinitionT<'s, 't>> {
        self.functions.iter().copied().filter(|f| self.name_is_lambda_in(f.header.id, needle_function_human_name)).collect()
    }
    
    pub fn lookup_lambda_in(&self, needle_function_human_name: &str) -> &'t FunctionDefinitionT<'s, 't> {
        let lambdas = self.lookup_lambdas_in(needle_function_human_name);
        assert_eq!(lambdas.len(), 1);
        lambdas[0]
    }
    
    pub fn get_all_user_functions(&self) -> Vec<&'t FunctionDefinitionT<'s, 't>> {
        self.functions.iter().copied().filter(|f| f.header.is_user_function()).collect()
    }
    
}