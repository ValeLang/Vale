use crate::typing::names::names::*;
use crate::typing::types::types::*;
use crate::typing::templata::templata::*;
use crate::typing::hinputs_t::*;
use crate::typing::ast::ast::*;
use crate::postparsing::itemplatatype::ITemplataType;
use crate::scout_arena::ScoutArena;

/// Value-type (see @TFITCX)
pub enum CitizenDefinitionT<'s, 't> {
    Struct(&'t StructDefinitionT<'s, 't>),
    Interface(&'t InterfaceDefinitionT<'s, 't>),
}

impl<'s, 't> CitizenDefinitionT<'s, 't> where 's: 't {
    pub fn template_name(&self) -> IdT<'s, 't> {
        match self {
            CitizenDefinitionT::Struct(s) => panic!("Unimplemented: template_name Struct"),
            CitizenDefinitionT::Interface(i) => panic!("Unimplemented: template_name Interface"),
        }
    }
    
    pub fn generic_param_types(&self, scout_arena: &ScoutArena<'s>) -> Vec<ITemplataType<'s>> {
        match self {
            CitizenDefinitionT::Struct(s) => s.generic_param_types(scout_arena),
            CitizenDefinitionT::Interface(i) => panic!("Unimplemented: generic_param_types Interface"),
        }
    }
    
    pub fn instantiated_citizen(&self) -> ICitizenTT<'s, 't> {
        match self {
            CitizenDefinitionT::Struct(s) => ICitizenTT::Struct(&s.instantiated_citizen),
            CitizenDefinitionT::Interface(i) => ICitizenTT::Interface(&i.instantiated_interface),
        }
    }
    
    pub fn default_region(&self) -> RegionT {
        match self {
            CitizenDefinitionT::Struct(s) => panic!("Unimplemented: default_region Struct"),
            CitizenDefinitionT::Interface(i) => panic!("Unimplemented: default_region Interface"),
        }
    }
    
}
/// Arena-allocated (see @TFITCX)
pub struct StructDefinitionT<'s, 't> {
    pub template_name: IdT<'s, 't>,
    pub instantiated_citizen: StructTT<'s, 't>,
    pub attributes: &'t [ICitizenAttributeT<'s>],
    pub weakable: bool,
    pub mutability: ITemplataT<'s, 't>,
    pub members: &'t [IStructMemberT<'s, 't>],
    pub is_closure: bool,
    pub instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
}

impl<'s, 't> StructDefinitionT<'s, 't> {
    fn default_region(&self) -> RegionT {
        panic!("Unimplemented: default_region");
    }

    fn generic_param_types(&self, scout_arena: &ScoutArena<'s>) -> Vec<ITemplataType<'s>> {
        IStructNameT::try_from(self.instantiated_citizen.id.local_name).unwrap().template_args().iter().map(|t| t.tyype(scout_arena)).collect()
    }

    pub fn get_member_and_index(&self, needle_name: &IVarNameT<'s, 't>) -> Option<(&NormalStructMemberT<'s, 't>, usize)> {
        for (index, member) in self.members.iter().enumerate() {
            match member {
                IStructMemberT::Normal(m) if &m.name == needle_name => {
                    return Some((m, index));
                }
                _ => {}
            }
        }
        None
    }

}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IStructMemberT<'s, 't> {
    Normal(NormalStructMemberT<'s, 't>),
    Variadic(VariadicStructMemberT<'s, 't>),
}

impl<'s, 't> IStructMemberT<'s, 't> where 's: 't {
    pub fn name(&self) -> &IVarNameT<'s, 't> {
        match self {
            IStructMemberT::Normal(m) => &m.name,
            IStructMemberT::Variadic(m) => &m.name,
        }
    }
    
}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct NormalStructMemberT<'s, 't> {
    pub name: IVarNameT<'s, 't>,
    pub variability: VariabilityT,
    pub tyype: IMemberTypeT<'s, 't>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct VariadicStructMemberT<'s, 't> {
    pub name: IVarNameT<'s, 't>,
    pub tyype: PlaceholderTemplataT<'s, 't>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IMemberTypeT<'s, 't> {
    Address(AddressMemberTypeT<'s, 't>),
    Reference(ReferenceMemberTypeT<'s, 't>),
}

impl<'s, 't> IMemberTypeT<'s, 't> where 's: 't {
    pub fn reference(&self) -> CoordT<'s, 't> {
        match self {
            IMemberTypeT::Address(m) => m.reference,
            IMemberTypeT::Reference(m) => m.reference,
        }
    }
    
    pub fn expect_reference_member(&self) -> &ReferenceMemberTypeT<'s, 't> {
        match self {
            IMemberTypeT::Reference(r) => r,
            IMemberTypeT::Address(_) => panic!("Expected reference member, was address member!"),
        }
    }
    
    pub fn expect_address_member(&self) -> &AddressMemberTypeT<'s, 't> {
        match self {
            IMemberTypeT::Reference(_) => panic!("Expected address member, was reference member!"),
            IMemberTypeT::Address(a) => a,
        }
    }
    
}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AddressMemberTypeT<'s, 't> {
    pub reference: CoordT<'s, 't>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ReferenceMemberTypeT<'s, 't> {
    pub reference: CoordT<'s, 't>,
}

/// Arena-allocated (see @TFITCX)
pub struct InterfaceDefinitionT<'s, 't> {
    pub template_name: IdT<'s, 't>,
    pub instantiated_interface: InterfaceTT<'s, 't>,
    pub ref_: InterfaceTT<'s, 't>,
    pub attributes: &'t [ICitizenAttributeT<'s>],
    pub weakable: bool,
    pub mutability: ITemplataT<'s, 't>,
    pub instantiation_bound_params: &'t InstantiationBoundArgumentsT<'s, 't>,
    pub internal_methods: &'t [(PrototypeT<'s, 't>, usize)],
}

impl<'s, 't> InterfaceDefinitionT<'s, 't> {
    fn default_region(&self) -> RegionT {
        panic!("Unimplemented: default_region");
    }

    fn generic_param_types(&self) -> Vec<ITemplataType<'s>> {
        panic!("Unimplemented: generic_param_types");
    }

    fn instantiated_citizen(&self) -> ICitizenTT<'s, 't> {
        panic!("Unimplemented: instantiated_citizen");
    }

}