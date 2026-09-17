use crate::interner::StrI;
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::parsing::MutabilityP;
use crate::postparsing::ast::{
    GenericParameterS, ICitizenAttributeS, IFunctionAttributeS,
    IStructMemberS, ParameterS, IBodyS,
};
use crate::postparsing::itemplatatype::{ITemplataType, TemplateTemplataType};
use crate::postparsing::names::{
    INameS, IRuneS, IStructDeclarationNameS, IImplDeclarationNameS,
    IImpreciseNameS, IFunctionDeclarationNameS, TopLevelInterfaceDeclarationNameS,
};
use crate::postparsing::rules::{IRulexSR, RuneUsage};
use crate::utils::range::RangeS;
use std::any::Any;
use std::hash::Hash;
use std::hash::Hasher;
use std::ptr::eq;
use std::ptr::hash;

#[derive(Debug)]
pub struct ProgramA<'s> {
    pub structs: &'s [&'s StructA<'s>],
    pub interfaces: &'s [&'s InterfaceA<'s>],
    pub impls: &'s [&'s ImplA<'s>],
    pub functions: &'s [&'s FunctionA<'s>],
    pub exports: &'s [&'s ExportAsA<'s>],
}

impl<'s> ProgramA<'s> {

pub fn lookup_function_by_name(&self, _name: &INameS<'s>) -> &FunctionA<'s> {
    panic!("Unimplemented: lookup_function_by_name");
}

pub fn lookup_function_by_str(&self, name: &str) -> &'s FunctionA<'s> {
    let matches: Vec<_> = self.functions.iter().filter(|function| {
      match &function.name {
        IFunctionDeclarationNameS::FunctionName(n) => n.name.as_str() == name,
        _ => false,
      }
    }).collect();
    assert!(matches.len() == 1);
    matches[0]
}

pub fn lookup_interface(&self, _name: &INameS<'s>) -> &InterfaceA<'s> {
    panic!("Unimplemented: lookup_interface");
}

pub fn lookup_struct_by_name(&self, _name: &INameS<'s>) -> &StructA<'s> {
    panic!("Unimplemented: lookup_struct_by_name");
}

pub fn lookup_struct_by_str(&self, name: &str) -> &StructA<'s> {
    let matches: Vec<_> = self.structs.iter().filter(|s| {
      match &s.name {
        IStructDeclarationNameS::TopLevelStructDeclarationName(n) => n.name.as_str() == name,
        _ => false,
      }
    }).collect();
    assert!(matches.len() == 1);
    matches[0]
}
}

#[derive(Debug)]
pub struct StructA<'s> {
    pub range: RangeS<'s>,
    pub name: IStructDeclarationNameS<'s>,
    pub attributes: &'s [ICitizenAttributeS<'s>],
    pub weakable: bool,
    pub mutability_rune: RuneUsage<'s>,
    pub maybe_predicted_mutability: Option<MutabilityP>,
    pub tyype: TemplateTemplataType<'s>,
    pub generic_parameters: &'s [&'s GenericParameterS<'s>],
    pub header_rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    pub header_rules: &'s [IRulexSR<'s>],
    pub members_rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    pub member_rules: &'s [IRulexSR<'s>],
    pub members: &'s [IStructMemberS<'s>],
    pub internal_methods: &'s [&'s FunctionA<'s>],
}

impl<'s> StructA<'s> {
pub fn new(
    range: RangeS<'s>,
    name: IStructDeclarationNameS<'s>,
    attributes: &'s [ICitizenAttributeS<'s>],
    weakable: bool,
    mutability_rune: RuneUsage<'s>,
    maybe_predicted_mutability: Option<MutabilityP>,
    tyype: TemplateTemplataType<'s>,
    generic_parameters: &'s [&'s GenericParameterS<'s>],
    header_rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    header_rules: &'s [IRulexSR<'s>],
    members_rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    member_rules: &'s [IRulexSR<'s>],
    members: &'s [IStructMemberS<'s>],
    internal_methods: &'s [&'s FunctionA<'s>],
) -> Self {
    // These should be removed by the higher typer
    for rule in header_rules.iter() {
        match rule {
            IRulexSR::MaybeCoercingCall(_) => panic!("vwat: MaybeCoercingCallSR in header rules"),
            IRulexSR::MaybeCoercingLookup(_) => panic!("vwat: MaybeCoercingLookupSR in header rules"),
            _ => {}
        }
    }
    for rule in member_rules.iter() {
        match rule {
            IRulexSR::MaybeCoercingCall(_) => panic!("vwat: MaybeCoercingCallSR in member rules"),
            IRulexSR::MaybeCoercingLookup(_) => panic!("vwat: MaybeCoercingLookupSR in member rules"),
            _ => {}
        }
    }
    assert!(
        !generic_parameters.iter().any(|x| matches!(x.rune.rune, IRuneS::DenizenDefaultRegionRune(_))),
        "vassert: generic_parameters should not contain DenizenDefaultRegionRuneS"
    );
    assert!(
        !header_rune_to_type.keys().any(|rune| matches!(rune, IRuneS::DenizenDefaultRegionRune(_))),
        "vassert: header_rune_to_type should not contain DenizenDefaultRegionRuneS"
    );
    assert!(
        !members_rune_to_type.keys().any(|rune| matches!(rune, IRuneS::DenizenDefaultRegionRune(_))),
        "vassert: members_rune_to_type should not contain DenizenDefaultRegionRuneS"
    );
    Self { range, name, attributes, weakable, mutability_rune, maybe_predicted_mutability, tyype, generic_parameters, header_rune_to_type, header_rules, members_rune_to_type, member_rules, members, internal_methods }
}

}

#[derive(Debug)]
pub struct ImplA<'s> {
    pub range: RangeS<'s>,
    pub name: IImplDeclarationNameS<'s>,
    pub generic_params: &'s [&'s GenericParameterS<'s>],
    pub rules: &'s [IRulexSR<'s>],
    pub rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    pub sub_citizen_rune: RuneUsage<'s>,
    pub sub_citizen_imprecise_name: IImpreciseNameS<'s>,
    pub interface_kind_rune: RuneUsage<'s>,
    pub super_interface_imprecise_name: IImpreciseNameS<'s>,
}

impl<'s> ImplA<'s> {
pub fn new(
    range: RangeS<'s>,
    name: IImplDeclarationNameS<'s>,
    generic_params: &'s [&'s GenericParameterS<'s>],
    rules: &'s [IRulexSR<'s>],
    rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    sub_citizen_rune: RuneUsage<'s>,
    sub_citizen_imprecise_name: IImpreciseNameS<'s>,
    interface_kind_rune: RuneUsage<'s>,
    super_interface_imprecise_name: IImpreciseNameS<'s>,
) -> Self {
    // These should be removed by the higher typer
    for rule in rules.iter() {
        match rule {
            IRulexSR::MaybeCoercingCall(_) => panic!("vwat: MaybeCoercingCallSR should be removed by higher typer"),
            IRulexSR::MaybeCoercingLookup(_) => panic!("vwat: MaybeCoercingLookupSR should be removed by higher typer"),
            _ => {}
        }
    }
    Self { range, name, generic_params, rules, rune_to_type, sub_citizen_rune, sub_citizen_imprecise_name, interface_kind_rune, super_interface_imprecise_name }
}

}

#[derive(Debug)]
pub struct ExportAsA<'s> {
    pub range: RangeS<'s>,
    pub exported_name: StrI<'s>,
    pub rules: &'s [IRulexSR<'s>],
    pub rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    pub type_rune: RuneUsage<'s>,
}

impl<'s> ExportAsA<'s> {

}

pub trait CitizenA<'s> {

fn tyype(&self) -> &TemplateTemplataType<'s>;

fn generic_parameters(&self) -> &[GenericParameterS<'s>];

}

#[derive(Debug)]
pub struct InterfaceA<'s> {
    pub range: RangeS<'s>,
    pub name: &'s TopLevelInterfaceDeclarationNameS<'s>,
    pub attributes: &'s [ICitizenAttributeS<'s>],
    pub weakable: bool,
    pub mutability_rune: RuneUsage<'s>,
    pub maybe_predicted_mutability: Option<MutabilityP>,
    pub tyype: TemplateTemplataType<'s>,
    pub generic_parameters: &'s [&'s GenericParameterS<'s>],
    pub rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    pub rules: &'s [IRulexSR<'s>],
    pub internal_methods: &'s [&'s FunctionA<'s>],
}

impl<'s> InterfaceA<'s> {
pub fn new(
    range: RangeS<'s>,
    name: &'s TopLevelInterfaceDeclarationNameS<'s>,
    attributes: &'s [ICitizenAttributeS<'s>],
    weakable: bool,
    mutability_rune: RuneUsage<'s>,
    maybe_predicted_mutability: Option<MutabilityP>,
    tyype: TemplateTemplataType<'s>,
    generic_parameters: &'s [&'s GenericParameterS<'s>],
    rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    rules: &'s [IRulexSR<'s>],
    internal_methods: &'s [&'s FunctionA<'s>],
) -> Self {
    // These should be removed by the higher typer
    for rule in rules.iter() {
        match rule {
            IRulexSR::MaybeCoercingCall(_) => panic!("vwat: MaybeCoercingCallSR should be removed by higher typer"),
            IRulexSR::MaybeCoercingLookup(_) => panic!("vwat: MaybeCoercingLookupSR should be removed by higher typer"),
            _ => {}
        }
    }
    assert!(
        !generic_parameters.iter().any(|x| matches!(x.rune.rune, IRuneS::DenizenDefaultRegionRune(_))),
        "vassert: generic_parameters should not contain DenizenDefaultRegionRuneS"
    );
    assert!(
        !rune_to_type.keys().any(|rune| matches!(rune, IRuneS::DenizenDefaultRegionRune(_))),
        "vassert: rune_to_type should not contain DenizenDefaultRegionRuneS"
    );
    for internal_method in internal_methods.iter() {
        assert!(
            generic_parameters == internal_method.generic_parameters,
            "vassert: internal method generic_parameters must match interface generic_parameters"
        );
    }
    Self { range, name, attributes, weakable, mutability_rune, maybe_predicted_mutability, tyype, generic_parameters, rune_to_type, rules, internal_methods }
}

}

pub mod interface_name {
    use super::*;

pub fn unapply<'s>(_interface_a: &'s InterfaceA<'s>) -> Option<&'s TopLevelInterfaceDeclarationNameS<'s>> {
    panic!("Unimplemented: unapply");
}
}

pub mod struct_name {
    use super::*;

pub fn unapply<'s>(_struct_a: &'s StructA<'s>) -> Option<&'s IStructDeclarationNameS<'s>> {
    panic!("Unimplemented: unapply");
}
}

#[derive(Debug)]
pub struct FunctionA<'s> {
    pub range: RangeS<'s>,
    pub name: IFunctionDeclarationNameS<'s>,
    pub attributes: &'s [IFunctionAttributeS<'s>],
    pub tyype: TemplateTemplataType<'s>,
    pub generic_parameters: &'s [&'s GenericParameterS<'s>],
    pub rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    pub params: &'s [ParameterS<'s>],
    pub maybe_ret_coord_rune: Option<RuneUsage<'s>>,
    pub rules: &'s [IRulexSR<'s>],
    pub body: IBodyS<'s>,
}

impl<'s> FunctionA<'s> {
pub fn new(
    range: RangeS<'s>,
    name: IFunctionDeclarationNameS<'s>,
    attributes: &'s [IFunctionAttributeS<'s>],
    tyype: TemplateTemplataType<'s>,
    generic_parameters: &'s [&'s GenericParameterS<'s>],
    rune_to_type: ArenaIndexMap<'s, IRuneS<'s>, ITemplataType<'s>>,
    params: &'s [ParameterS<'s>],
    maybe_ret_coord_rune: Option<RuneUsage<'s>>,
    rules: &'s [IRulexSR<'s>],
    body: IBodyS<'s>,
) -> Self {
    // These should be removed by the higher typer
    for rule in rules.iter() {
        match rule {
            IRulexSR::MaybeCoercingCall(_) => panic!("vwat: MaybeCoercingCallSR should be removed by higher typer"),
            IRulexSR::MaybeCoercingLookup(_) => panic!("vwat: MaybeCoercingLookupSR should be removed by higher typer"),
            _ => {}
        }
    }
    assert!(
        !generic_parameters.iter().any(|x| matches!(x.rune.rune, IRuneS::DenizenDefaultRegionRune(_))),
        "vassert: generic_parameters should not contain DenizenDefaultRegionRuneS"
    );
    assert!(
        !rune_to_type.keys().any(|rune| matches!(rune, IRuneS::DenizenDefaultRegionRune(_))),
        "vassert: rune_to_type should not contain DenizenDefaultRegionRuneS"
    );
    assert!(
        range.begin.file.package_coord == name.package_coordinate(),
        "vassert: range.begin.file.package_coord must equal name.package_coordinate()"
    );
    Self { range, name, attributes, tyype, generic_parameters, rune_to_type, params, maybe_ret_coord_rune, rules, body }
}

pub fn is_light(&self) -> bool {
    match &self.body {
        IBodyS::ExternBody(_) | IBodyS::AbstractBody(_) | IBodyS::GeneratedBody(_) => true,
        IBodyS::CodeBody(code_body) => code_body.body.closured_names.is_empty(),
    }
}

pub fn is_lambda(&self) -> bool {
    match &self.name {
        IFunctionDeclarationNameS::LambdaDeclarationName(_) => true,
        _ => false,
    }
}
}

// Identity-equality impls per @IEOIBZ. These types are arena-allocated and
// accessed by reference; two distinct allocations are distinct identities,
// so `==` and `Hash` use `std::ptr::eq`/`std::ptr::hash` on `&self`.

impl<'s> PartialEq for StructA<'s> {
    fn eq(&self, other: &Self) -> bool { eq(self, other) }
    
}
impl<'s> Eq for StructA<'s> {}
impl<'s> Hash for StructA<'s> {
    fn hash<H: Hasher>(&self, state: &mut H) { hash(self, state) }
    
}

impl<'s> PartialEq for InterfaceA<'s> {
    fn eq(&self, other: &Self) -> bool { eq(self, other) }
    
}
impl<'s> Eq for InterfaceA<'s> {}
impl<'s> Hash for InterfaceA<'s> {
    fn hash<H: Hasher>(&self, state: &mut H) { hash(self, state) }
    
}

impl<'s> PartialEq for ImplA<'s> {
    fn eq(&self, other: &Self) -> bool { eq(self, other) }
    
}
impl<'s> Eq for ImplA<'s> {}
impl<'s> Hash for ImplA<'s> {
    fn hash<H: Hasher>(&self, state: &mut H) { hash(self, state) }
    
}

impl<'s> PartialEq for FunctionA<'s> {
    fn eq(&self, other: &Self) -> bool { eq(self, other) }
    
}
impl<'s> Eq for FunctionA<'s> {}
impl<'s> Hash for FunctionA<'s> {
    fn hash<H: Hasher>(&self, state: &mut H) { hash(self, state) }
    
}
