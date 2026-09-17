use std::collections::HashMap;

use crate::interner::StrI;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::range::RangeS;

use crate::postparsing::names::*;

use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::ast::expressions::*;
use crate::typing::hinputs_t::*;
use crate::typing::typing_interner::TypingInterner;
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::typing::names::names::IdValQuery;
use std::hash::Hash;
use std::hash::Hasher;
use std::marker::PhantomData;
use std::ptr::eq;
use std::ptr::hash;

/// Arena-allocated (see @TFITCX)
pub struct ImplT<'s, 't> {
    pub templata: ImplDefinitionTemplataT<'s, 't>,
    pub instantiated_id: IdT<'s, 't>,
    pub template_id: IdT<'s, 't>,
    pub sub_citizen_template_id: IdT<'s, 't>,
    pub sub_citizen: ICitizenTT<'s, 't>,
    pub super_interface: InterfaceTT<'s, 't>,
    pub super_interface_template_id: IdT<'s, 't>,
    pub instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
    pub rune_index_to_independence: &'t [bool],
}

/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct KindExportT<'s, 't> {
    pub range: RangeS<'s>,
    pub tyype: KindT<'s, 't>,
    pub id: IdT<'s, 't>,
    pub exported_name: StrI<'s>,
}

impl<'s, 't> KindExportT<'s, 't> {

}
/// Arena-allocated (see @TFITCX)
pub struct FunctionExportT<'s, 't> {
    pub range: RangeS<'s>,
    pub prototype: PrototypeT<'s, 't>,
    pub export_id: IdT<'s, 't>,
    pub exported_name: StrI<'s>,
}

impl<'s, 't> FunctionExportT<'s, 't> {

}
/// Arena-allocated (see @TFITCX)
pub struct KindExternT<'s, 't> {
    pub tyype: KindT<'s, 't>,
    pub package_coordinate: PackageCoordinate<'s>,
    pub extern_name: StrI<'s>,
}

impl<'s, 't> KindExternT<'s, 't> {

}
/// Arena-allocated (see @TFITCX)
pub struct FunctionExternT<'s, 't> {
    pub range: RangeS<'s>,
    pub extern_placeholdered_id: IdT<'s, 't>,
    pub prototype: PrototypeT<'s, 't>,
    pub extern_name: StrI<'s>,
    pub generic_parameter_inheritance: Option<GenericParametersInheritance>,
}

impl<'s, 't> FunctionExternT<'s, 't> {

}
/// Arena-allocated (see @TFITCX)
pub struct InterfaceEdgeBlueprintT<'s, 't> {
    pub interface: IdT<'s, 't>,
    pub super_family_root_headers: &'t [(PrototypeT<'s, 't>, i32)],
}

impl<'s, 't> InterfaceEdgeBlueprintT<'s, 't> {

}
/// Arena-allocated (see @TFITCX)
pub struct OverrideT<'s, 't> {
    pub dispatcher_call_id: IdT<'s, 't>,
    pub impl_placeholder_to_dispatcher_placeholder: &'t [(IdT<'s, 't>, ITemplataT<'s, 't>)],
    pub impl_placeholder_to_case_placeholder: &'t [(IdT<'s, 't>, ITemplataT<'s, 't>)],
    pub dispatcher_and_case_placeholdered_impl_reachable_prototypes: ArenaIndexMap<'t, IRuneS<'s>, ArenaIndexMap<'t, IRuneS<'s>, PrototypeT<'s, 't>>>,
    pub case_id: IdT<'s, 't>,
    pub override_prototype: PrototypeT<'s, 't>,
    pub dispatcher_instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
}

/// Arena-allocated (see @TFITCX)
pub struct EdgeT<'s, 't> {
    pub edge_id: IdT<'s, 't>,
    pub sub_citizen: ICitizenTT<'s, 't>,
    pub super_interface: IdT<'s, 't>,
    pub instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
    pub abstract_func_to_override_func: ArenaIndexMap<'t, IdT<'s, 't>, &'t OverrideT<'s, 't>>,
}

impl<'s, 't> EdgeT<'s, 't> {

}
/// Arena-allocated (see @TFITCX)
pub struct FunctionDefinitionT<'s, 't> {
    pub header: &'t FunctionHeaderT<'s, 't>,
    pub instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
    pub body: ReferenceExpressionTE<'s, 't>,
}

impl<'s, 't> FunctionDefinitionT<'s, 't> {

}
impl<'s, 't> FunctionDefinitionT<'s, 't> where 's: 't, {
    fn new(
        header: FunctionHeaderT<'s, 't>,
        instantiation_bound_params: InstantiationBoundArgumentsT<'s, 't>,
        body: ReferenceExpressionTE<'s, 't>,
    ) -> FunctionDefinitionT<'s, 't> { panic!("Unimplemented: FunctionDefinitionT::new"); }

}
impl<'s, 't> FunctionDefinitionT<'s, 't> {
    fn is_pure(&self) -> bool { panic!("Unimplemented: is_pure"); }

}
fn get_function_last_name_unapply<'s, 't>(f: &'t FunctionDefinitionT<'s, 't>) -> Option<&'t IFunctionNameT<'s, 't>> { panic!("Unimplemented: unapply"); }

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct LocationInFunctionEnvironmentT<'t> {
    pub path: &'t [i32],
}

impl<'t> LocationInFunctionEnvironmentT<'t> {

    pub fn add<'s>(&self, interner: &TypingInterner<'s, 't>, sub_location: i32) -> LocationInFunctionEnvironmentT<'t> {
        let mut new_path: Vec<i32> = self.path.to_vec();
        new_path.push(sub_location);
        LocationInFunctionEnvironmentT { path: interner.alloc_slice_from_vec(new_path) }
    }

    fn to_string(&self) -> String { panic!("Unimplemented: to_string"); }

}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Debug)]
pub struct AbstractT;

/// Arena-allocated (see @TFITCX)
#[derive(Clone, Debug)]
pub struct ParameterT<'s, 't> {
    pub name: IVarNameT<'s, 't>,
    pub virtuality: Option<AbstractT>,
    pub pre_checked: bool,
    pub tyype: CoordT<'s, 't>,
}

impl<'s, 't> ParameterT<'s, 't> {

    fn same(&self, that: &ParameterT<'s, 't>) -> bool { panic!("Unimplemented: same"); }

}
/// Temporary state (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ICalleeCandidate<'s, 't> {
    Function(FunctionCalleeCandidate<'s, 't>),
    Header(&'t HeaderCalleeCandidate<'s, 't>),
    PrototypeTemplata(PrototypeTemplataCalleeCandidate<'s, 't>),
}

/// Temporary state (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FunctionCalleeCandidate<'s, 't> {
    pub ft: FunctionTemplataT<'s, 't>,
}

impl<'s, 't> FunctionCalleeCandidate<'s, 't> {

}
/// Temporary state (see @TFITCX)
#[derive(PartialEq, Eq, Hash, Debug)]
pub struct HeaderCalleeCandidate<'s, 't> {
    pub header: FunctionHeaderT<'s, 't>,
}

impl<'s, 't> HeaderCalleeCandidate<'s, 't> {

}
/// Temporary state (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PrototypeTemplataCalleeCandidate<'s, 't> {
    pub prototype_t: PrototypeT<'s, 't>,
}

impl<'s, 't> PrototypeTemplataCalleeCandidate<'s, 't> {

}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct SignatureT<'s, 't> {
    pub id: IdT<'s, 't>,
}

impl<'s, 't> SignatureT<'s, 't> {

    fn param_types(&self) -> Vec<CoordT<'s, 't>> { panic!("Unimplemented: param_types"); }

}

// (no scala counterpart — Rust-only interning scaffolding)
// Transient Val for interning: holds a stack-borrowed IdValT<'s, 't, 'tmp> so
// callers can construct a lookup key without first arena-allocating init_steps.
/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct SignatureValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub id: IdValT<'s, 't, 'tmp>,
}

/// Interning transient (see @TFITCX)
pub struct SignatureValQuery<'a, 's, 't, 'tmp>(pub &'a SignatureValT<'s, 't, 'tmp>)
where 's: 't, 't: 'tmp;

impl<'a, 's, 't, 'tmp> Hash for SignatureValQuery<'a, 's, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    fn hash<H: Hasher>(&self, state: &mut H) { self.0.hash(state); }
    
}

impl<'a, 's, 't, 'tmp> hashbrown::Equivalent<SignatureValT<'s, 't, 't>> for SignatureValQuery<'a, 's, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    fn equivalent(&self, key: &SignatureValT<'s, 't, 't>) -> bool {
        IdValQuery(&self.0.id).equivalent(&key.id)
    }
    
}
/// Value-type (see @TFITCX)
pub struct FunctionBannerT<'s, 't> {
    pub origin_function_templata: Option<FunctionTemplataT<'s, 't>>,
    pub name: IdT<'s, 't>,
}

impl<'s, 't> FunctionBannerT<'s, 't> {

    pub fn same(&self, that: &FunctionBannerT<'s, 't>) -> bool {
        let self_func = self.origin_function_templata.map(|t| t.function as *const _);
        let that_func = that.origin_function_templata.map(|t| t.function as *const _);
        self_func == that_func && self.name == that.name
    }

    fn to_string(&self) -> String { panic!("Unimplemented: to_string"); }

}
/// Arena-allocated (see @TFITCX)
#[derive(Clone, PartialEq, Debug)]
pub enum IFunctionAttributeT<'s> {
    Extern(ExternT<'s>),
    Pure,
    Additive,
    UserFunction,
}

/// Arena-allocated (see @TFITCX)
pub enum ICitizenAttributeT<'s> {
    Extern(ExternT<'s>),
    Sealed,
}

/// Arena-allocated (see @TFITCX)
#[derive(Clone, PartialEq, Debug)]
pub struct ExternT<'s> {
    pub package_coord: PackageCoordinate<'s>,
}

impl<'s> ExternT<'s> {

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct FunctionHeaderT<'s, 't> {
    pub id: IdT<'s, 't>,
    pub attributes: &'t [IFunctionAttributeT<'s>],
    pub params: &'t [ParameterT<'s, 't>],
    pub return_type: CoordT<'s, 't>,
    pub maybe_origin_function_templata: Option<FunctionTemplataT<'s, 't>>,
}

// Identity equality per @IEOIBZ — `FunctionHeaderT` is arena-allocated.
impl<'s, 't> PartialEq for FunctionHeaderT<'s, 't> {
    fn eq(&self, other: &Self) -> bool { eq(self, other) }
    
}
impl<'s, 't> Eq for FunctionHeaderT<'s, 't> {}
impl<'s, 't> Hash for FunctionHeaderT<'s, 't> {
    fn hash<H: Hasher>(&self, state: &mut H) { hash(self, state) }
    
}
impl<'s, 't> FunctionHeaderT<'s, 't> {

    fn new(
        id: IdT<'s, 't>,
        attributes: Vec<IFunctionAttributeT<'s>>,
        params: Vec<ParameterT<'s, 't>>,
        return_type: CoordT<'s, 't>,
        maybe_origin_function_templata: Option<FunctionTemplataT<'s, 't>>,
    ) -> FunctionHeaderT<'s, 't> { panic!("Unimplemented: FunctionHeaderT::new"); }

    fn is_extern(&self) -> bool { panic!("Unimplemented: is_extern"); }

    pub fn is_user_function(&self) -> bool { self.attributes.contains(&IFunctionAttributeT::UserFunction) }

    pub fn get_abstract_interface(&self) -> Option<InterfaceTT<'s, 't>> {
        let abstract_interfaces: Vec<InterfaceTT<'s, 't>> =
            self.params.iter().filter_map(|param| {
                match param {
                    ParameterT { virtuality: Some(AbstractT), tyype: CoordT { kind: KindT::Interface(ir), .. }, .. } => Some(**ir),
                    _ => None,
                }
            }).collect();
        assert!(abstract_interfaces.len() <= 1);
        abstract_interfaces.into_iter().next()
    }

    pub fn get_virtual_index(&self) -> Option<usize> {
        let indices: Vec<usize> = self.params.iter().enumerate()
            .filter_map(|(index, p)| if p.virtuality.is_some() { Some(index) } else { None })
            .collect();
        assert!(indices.len() <= 1);
        indices.into_iter().next()
    }

    pub fn to_banner(&self) -> FunctionBannerT<'s, 't> {
        FunctionBannerT { origin_function_templata: self.maybe_origin_function_templata, name: self.id }
    }

    pub fn to_prototype(&self) -> PrototypeT<'s, 't> {
        PrototypeT { id: self.id, return_type: self.return_type }
    }

    pub fn to_signature(&self) -> SignatureT<'s, 't> {
        self.to_prototype().to_signature()
    }

    fn param_types(&self) -> Vec<CoordT<'s, 't>> { panic!("Unimplemented: param_types"); }

}
fn function_header_unapply<'a, 's, 't>(arg: &'a FunctionHeaderT<'s, 't>) -> Option<(&'a IdT<'s, 't>, &'a Vec<ParameterT<'s, 't>>, &'a CoordT<'s, 't>)> { panic!("Unimplemented: unapply"); }

impl<'s, 't> FunctionHeaderT<'s, 't> {
    fn is_pure(&self) -> bool { panic!("Unimplemented: is_pure"); }

}
// Monomorphic per `docs/reasoning/idt-typed-view-alternatives.md` (same
// treatment as IdT). Scala's `PrototypeT[+T <: IFunctionNameT]` phantom
// parameter is erased in Rust.
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PrototypeT<'s, 't>
where 's: 't,
{
    pub id: IdT<'s, 't>,
    pub return_type: CoordT<'s, 't>,
}

impl<'s, 't> PrototypeT<'s, 't> where 's: 't, {

    pub fn param_types(&self) -> &'t [CoordT<'s, 't>] {
        IFunctionNameT::try_from(self.id.local_name)
            .unwrap_or_else(|_| panic!("param_types called on non-function name: {:?}", self.id.local_name))
            .parameters()
    }

    pub fn to_signature(&self) -> SignatureT<'s, 't> {
        SignatureT { id: self.id }
    }

}

// (no scala counterpart — Rust-only interning scaffolding)
// Transient Val for interning: inner IdValT borrows its init_steps slice from
// a stack-local builder via 'tmp, so construction doesn't arena-allocate.
/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct PrototypeValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub id: IdValT<'s, 't, 'tmp>,
    pub return_type: CoordT<'s, 't>,
}

/// Interning transient (see @TFITCX)
pub struct PrototypeValQuery<'a, 's, 't, 'tmp>(pub &'a PrototypeValT<'s, 't, 'tmp>)
where 's: 't, 't: 'tmp;

impl<'a, 's, 't, 'tmp> Hash for PrototypeValQuery<'a, 's, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    fn hash<H: Hasher>(&self, state: &mut H) { self.0.hash(state); }
    
}

impl<'a, 's, 't, 'tmp> hashbrown::Equivalent<PrototypeValT<'s, 't, 't>> for PrototypeValQuery<'a, 's, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    fn equivalent(&self, key: &PrototypeValT<'s, 't, 't>) -> bool {
        IdValQuery(&self.0.id).equivalent(&key.id)
            && self.0.return_type == key.return_type
    }
    
}
