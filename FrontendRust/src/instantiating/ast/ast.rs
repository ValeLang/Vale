use crate::interner::StrI;
use crate::utils::range::RangeS;
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::postparsing::names::IRuneS;
use crate::instantiating::ast::types::{CoordI, KindIT, ICitizenIT, MutabilityI, VariabilityI, cI, StructIT};
use crate::instantiating::ast::names::{
    IdI, INameI,
    IFunctionNameI, IImplNameI, IInterfaceNameI, IStructNameI, ICitizenNameI,
    IRegionNameI, IVarNameI,
    ExportNameI, FunctionBoundNameI, ImplBoundNameI,
};
use crate::instantiating::instantiating_interner::MustIntern;
use crate::instantiating::instantiating_interner::InstantiatingInterner;
use crate::instantiating::ast::expressions::ReferenceExpressionIE;
use crate::instantiating::ast::types::InterfaceIT;
use crate::utils::code_hierarchy::PackageCoordinate;

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct KindExportI<'s, 'i> {
    pub range: RangeS<'s>,
    pub tyype: KindIT<'s, 'i, cI>,
    pub id: IdI<'s, 'i, cI>,
    pub exported_name: StrI<'s>,
}

// (Realized by `impl PartialEq for KindExportI` below.)

// (Realized by `impl Hash for KindExportI` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FunctionExportI<'s, 'i> where 's: 'i {
    pub range: RangeS<'s>,
    pub prototype: &'i PrototypeI<'s, 'i, cI>,
    pub export_id: IdI<'s, 'i, cI>,
    pub exported_name: StrI<'s>,
}

// (Realized by `impl PartialEq for FunctionExportI` below.)

// (Realized by `impl Hash for FunctionExportI` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FunctionExternI<'s, 'i> where 's: 'i {
    pub prototype: &'i PrototypeI<'s, 'i, cI>,
    // How many of the function's trailing generic-arg slots were inherited from a parent
    // citizen template, per @PRIIROZ (0 = no inheritance / top-level extern). Hammer uses
    // this to reshape the wire-format SimpleId so container template args land on the
    // citizen step (e.g. Vec<i32>::capacity rather than Vec::capacity<i32>), which is
    // what the Backend's rustifySimpleId expects per @SMLRZ.
    pub num_inherited_generic_parameters: i32,
}

// (Realized by `impl PartialEq for FunctionExternI` below.)

// (Canonical groups equals/hashCode on one physical line — see the eq block above.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct KindExternI<'s, 'i> where 's: 'i {
    pub r#struct: &'i StructIT<'s, 'i, cI>,
}

// (Realized by the #[derive(PartialEq, Eq)] above.)

// (Canonical groups equals/hashCode on one physical line — see the eq block above.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct InterfaceEdgeBlueprintI<'s, 'i> where 's: 'i {
    pub interface: IdI<'s, 'i, cI>,
    pub super_family_root_headers: &'i [(&'i PrototypeI<'s, 'i, cI>, i32)],
}

// (Realized by `impl Hash for InterfaceEdgeBlueprintI` below.)

// (Realized by `impl PartialEq for InterfaceEdgeBlueprintI` below.)

/// Temporary state
#[derive(PartialEq, Eq, Debug)]
pub struct EdgeI<'s, 'i> where 's: 'i {
    pub edge_id: IdI<'s, 'i, cI>,
    pub sub_citizen: ICitizenIT<'s, 'i, cI>,
    pub super_interface: IdI<'s, 'i, cI>,
    pub rune_to_func_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
    pub rune_to_impl_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
    pub abstract_func_to_override_func: ArenaIndexMap<'i, IdI<'s, 'i, cI>, &'i PrototypeI<'s, 'i, cI>>,
}

// (Realized by `impl Hash for EdgeI` below.)

// (Realized by `impl PartialEq for EdgeI` below.)

/// Temporary state
#[derive(Debug)]
pub struct FunctionDefinitionI<'s, 'i> where 's: 'i {
    pub header: FunctionHeaderI<'s, 'i>,
    pub rune_to_func_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
    pub rune_to_impl_bound: ArenaIndexMap<'i, IRuneS<'s>, IdI<'s, 'i, cI>>,
    pub body: ReferenceExpressionIE<'s, 'i, cI>,
}

// (Realized by `impl PartialEq for FunctionDefinitionI` below.)

// (Realized by `impl Hash for FunctionDefinitionI` below.)

impl<'s, 'i> FunctionDefinitionI<'s, 'i> {
    pub fn is_pure(&self) -> bool {
        panic!("Unimplemented: is_pure")
    }
}

// (Realized via `impl TryFrom<FunctionDefinitionI> for IFunctionNameI` or inline match.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct LocationInFunctionEnvironmentI<'i> {
    pub path: &'i [i32],
}

// (Realized by `impl Hash for LocationInFunctionEnvironmentI` below.)

impl<'i> LocationInFunctionEnvironmentI<'i> {
    pub fn add(&self, sub_location: i32) -> LocationInFunctionEnvironmentI<'i> {
        panic!("Unimplemented: add")
    }

    pub fn to_string(&self) -> String {
        self.path.iter().map(|x| x.to_string()).collect::<Vec<_>>().join(".")
    }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AbstractI;

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ParameterI<'s, 'i> where 's: 'i {
    pub name: IVarNameI<'s, 'i, cI>,
    pub virtuality: Option<AbstractI>,
    pub pre_checked: bool,
    pub tyype: CoordI<'s, 'i, cI>,
}

// (Realized by `impl Hash for ParameterI` below.)

// (Realized by `impl PartialEq for ParameterI` below.)

impl<'s, 'i> ParameterI<'s, 'i> {
    pub fn same(&self, that: &ParameterI<'_, '_>) -> bool {
        panic!("Unimplemented: same")
    }
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct SignatureI<'s, 'i, R> {
    pub id: IdI<'s, 'i, R>,
    pub _must_intern: MustIntern,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct SignatureIValI<'s, 'i, R> {
    pub id: IdI<'s, 'i, R>,
}

// (Realized by `impl Hash for SignatureI` below.)

impl<'s, 'i, R> SignatureI<'s, 'i, R> {
    pub fn param_types(&self) -> Vec<()> {
        panic!("Unimplemented: param_types")
    }
}

/// Polyvalue
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IFunctionAttributeI<'s> {
    PureI,
    UserFunctionI,
    ExternI(ExternI<'s>),
}

/// Polyvalue
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ICitizenAttributeI<'s> {
    SealedI,
    ExternI(ExternI<'s>),
}

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ExternI<'s> {
    pub package_coord: PackageCoordinate<'s>,
}

// (Realized by `impl Hash for ExternI` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RegionI<'s, 'i> where 's: 'i {
    pub name: IRegionNameI<'s, 'i, cI>,
    pub mutable: bool,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FunctionHeaderI<'s, 'i> where 's: 'i {
    // This one little name field can illuminate much of how the compiler works, see UINIT.
    pub id: IdI<'s, 'i, cI>,
    pub attributes: &'i [IFunctionAttributeI<'s>],
//  regions: Vector[cIegionI],
    pub params: &'i [ParameterI<'s, 'i>],
    pub return_type: CoordI<'s, 'i, cI>,
}

// (Realized by `impl Hash for FunctionHeaderI` below.)

// (Realized by `impl PartialEq for FunctionHeaderI` below.)

impl<'s, 'i> FunctionHeaderI<'s, 'i> {
    pub fn is_extern(&self) -> bool {
        panic!("Unimplemented: is_extern")
    }

    pub fn is_user_function(&self) -> bool {
        self.attributes.contains(&IFunctionAttributeI::UserFunctionI)
    }

    pub fn get_abstract_interface(&self) -> Option<&'i InterfaceIT<'s, 'i, cI>> {
        let abstract_interfaces: Vec<_> = self.params.iter().filter_map(|p| match (p.virtuality, p.tyype.kind) {
            (Some(AbstractI), KindIT::InterfaceIT(ir)) => Some(ir),
            _ => None,
        }).collect();
        assert!(abstract_interfaces.len() <= 1);
        abstract_interfaces.into_iter().next()
    }

    pub fn get_virtual_index(&self) -> Option<i32> {
        panic!("Unimplemented: get_virtual_index")
    }

    pub fn to_prototype(&self, interner: &InstantiatingInterner<'s, 'i>) -> PrototypeI<'s, 'i, cI> {
        //    val substituter = TemplataCompiler.getPlaceholderSubstituter(interner, fullName, templateArgs)
        //    val paramTypes = params.map(_.tyype).map(substituter.substituteForCoord)
        //    val newLastStep = fullName.last.makeFunctionName(interner, keywords, templateArgs, paramTypes)
        //    val newName = FullNameI(fullName.packageCoord, fullName.initSteps, newLastStep)
        *interner.intern_prototype_ci(PrototypeIValI { id: self.id, return_type: self.return_type })
    }

    pub fn to_signature(&self) -> SignatureI<'_, '_, ()> {
        panic!("Unimplemented: to_signature")
    }
}

impl<'s, 'i> FunctionHeaderI<'s, 'i> where 's: 'i {
    pub fn param_types(&self) -> Vec<CoordI<'s, 'i, cI>> {
        IFunctionNameI::try_from(self.id.local_name).unwrap().parameters().to_vec()
    }
}

// (Realized via `impl TryFrom<FunctionHeaderI> for (IdI, Vec<ParameterI>, CoordI)` or inline match.)

impl<'s, 'i> FunctionHeaderI<'s, 'i> {
    pub fn is_pure(&self) -> bool {
        panic!("Unimplemented: is_pure")
    }
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PrototypeI<'s, 'i, R> {
    pub id: IdI<'s, 'i, R>,
    pub return_type: CoordI<'s, 'i, R>,
    pub _must_intern: MustIntern,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PrototypeIValI<'s, 'i, R> {
    pub id: IdI<'s, 'i, R>,
    pub return_type: CoordI<'s, 'i, R>,
}

// (Realized by `impl Hash for PrototypeI` below.)

impl<'s, 'i, R: Copy> PrototypeI<'s, 'i, R> where 's: 'i {
    pub fn param_types(&self) -> Vec<CoordI<'s, 'i, R>> {
        IFunctionNameI::try_from(self.id.local_name).unwrap().parameters().to_vec()
    }
}

impl<'s, 'i, R: Copy> PrototypeI<'s, 'i, R> {
    pub fn to_signature(&self) -> SignatureIValI<'s, 'i, R> {
        SignatureIValI { id: self.id }
    }
}

/// Polyvalue
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IVariableI<'s, 'i> where 's: 'i {
    AddressibleLocalVariableI(&'i AddressibleLocalVariableI<'s, 'i>),
    ReferenceLocalVariableI(&'i ReferenceLocalVariableI<'s, 'i>),
    AddressibleClosureVariableI(&'i AddressibleClosureVariableI<'s, 'i>),
    ReferenceClosureVariableI(&'i ReferenceClosureVariableI<'s, 'i>),
}

impl<'s, 'i> IVariableI<'s, 'i> {
    pub fn name(&self) -> () {
        panic!("Unimplemented: name")
    }

    pub fn variability(&self) -> () {
        panic!("Unimplemented: variability")
    }

    pub fn collapsed_coord(&self) -> () {
        panic!("Unimplemented: collapsed_coord")
    }
}

/// Polyvalue
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ILocalVariableI<'s, 'i> where 's: 'i {
    AddressibleLocalVariableI(&'i AddressibleLocalVariableI<'s, 'i>),
    ReferenceLocalVariableI(&'i ReferenceLocalVariableI<'s, 'i>),
}

impl<'s, 'i> ILocalVariableI<'s, 'i> {
    pub fn name(&self) -> IVarNameI<'s, 'i, cI> {
        match self {
            ILocalVariableI::ReferenceLocalVariableI(rlv) => rlv.name,
            ILocalVariableI::AddressibleLocalVariableI(alv) => alv.name,
        }
    }

    pub fn collapsed_coord(&self) -> CoordI<'s, 'i, cI> {
        match self {
            ILocalVariableI::AddressibleLocalVariableI(alv) => alv.collapsed_coord,
            ILocalVariableI::ReferenceLocalVariableI(rlv) => rlv.collapsed_coord,
        }
    }

    pub fn variability(&self) -> VariabilityI {
        match self {
            ILocalVariableI::AddressibleLocalVariableI(alv) => alv.variability,
            ILocalVariableI::ReferenceLocalVariableI(rlv) => rlv.variability,
        }
    }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AddressibleLocalVariableI<'s, 'i> where 's: 'i {
    pub name: IVarNameI<'s, 'i, cI>,
    pub variability: VariabilityI,
    pub collapsed_coord: CoordI<'s, 'i, cI>,
}

// (Realized by `impl Hash for AddressibleLocalVariableI` below.)

// (Realized by `impl PartialEq for AddressibleLocalVariableI` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ReferenceLocalVariableI<'s, 'i> where 's: 'i {
    pub name: IVarNameI<'s, 'i, cI>,
    pub variability: VariabilityI,
    pub collapsed_coord: CoordI<'s, 'i, cI>,
}

// (Realized by `impl Hash for ReferenceLocalVariableI` below.)

// (Realized by `impl PartialEq for ReferenceLocalVariableI` below.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AddressibleClosureVariableI<'s, 'i> where 's: 'i {
    pub name: IVarNameI<'s, 'i, cI>,
    pub closured_vars_struct_type: StructIT<'s, 'i, cI>,
    pub variability: VariabilityI,
    pub collapsed_coord: CoordI<'s, 'i, cI>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ReferenceClosureVariableI<'s, 'i> where 's: 'i {
    pub name: IVarNameI<'s, 'i, cI>,
    pub closured_vars_struct_type: StructIT<'s, 'i, cI>,
    pub variability: VariabilityI,
    pub collapsed_coord: CoordI<'s, 'i, cI>,
}

// (Realized by `impl Hash for ReferenceClosureVariableI` below.)

// (Realized by `impl PartialEq for ReferenceClosureVariableI` below.)
