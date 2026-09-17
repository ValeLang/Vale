use crate::interner::StrI;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::range::{CodeLocationS, RangeS};
use crate::postparsing::names::IRuneS;
use crate::instantiating::ast::types::{CoordI, ICitizenIT, MutabilityI, VariabilityI};
use crate::instantiating::ast::templata::{ITemplataI, CoordTemplataI, RegionTemplataI};
use crate::instantiating::ast::ast::LocationInFunctionEnvironmentI;
use crate::instantiating::instantiating_interner::InstantiatingInterner;
use crate::typing::types::types::CoordT;
use std::marker::PhantomData;

#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IdI<'s, 'i, R> {
    pub package_coord: &'s PackageCoordinate<'s>,
    pub init_steps: &'i [INameI<'s, 'i, R>],
    pub local_name: INameI<'s, 'i, R>,
}

// (Realized by `impl PartialEq for IdI` above.)

// (was cfg-gated)
impl<'s, 'i, R> IdI<'s, 'i, R> {
    pub fn package_id(&self) -> IdI<'s, 'i, R> { panic!("Unimplemented: package_id"); }

// (was cfg-gated)
    pub fn init_id(&self) -> IdI<'s, 'i, R> { panic!("Unimplemented: init_id"); }
}

// (was cfg-gated)
impl<'s, 'i, R: Copy> IdI<'s, 'i, R> {
    pub fn init_non_package_id(&self) -> Option<IdI<'s, 'i, R>> {
        if self.init_steps.is_empty() {
            None
        } else {
            let (last, init) = self.init_steps.split_last().unwrap();
            Some(IdI { package_coord: self.package_coord, init_steps: init, local_name: *last })
        }
    }
}

// (was cfg-gated)
impl<'s, 'i, R> IdI<'s, 'i, R> {
    pub fn steps(&self) -> &'i[INameI<'s, 'i, R>] { panic!("Unimplemented: steps"); }
}

// (was cfg-gated)
pub fn add_step<'s, 'i, R>(old: &IdI<'s, 'i, R>, new_last: INameI<'s, 'i, R>) -> IdI<'s, 'i, R> {
    IdI { package_coord: old.package_coord, init_steps: old.init_steps, local_name: new_last }
}

/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum INameI<'s, 'i, R> {
    RegionName(&'i RegionNameI<'s, R>),
    DenizenDefaultRegionName(&'i DenizenDefaultRegionNameI<R>),
    ExportTemplate(&'i ExportTemplateNameI<'s, R>),
    Export(&'i ExportNameI<'s, R>),
    ExternTemplate(&'i ExternTemplateNameI<'s, R>),
    Extern(&'i ExternNameI<'s, R>),
    ImplTemplate(&'i ImplTemplateNameI<'s, R>),
    Impl(&'i ImplNameI<'s, 'i, R>),
    ImplBoundTemplate(&'i ImplBoundTemplateNameI<'s, R>),
    ImplBound(&'i ImplBoundNameI<'s, 'i, R>),
    Let(&'i LetNameI<'s, R>),
    ExportAs(&'i ExportAsNameI<'s, R>),
    RawArray(&'i RawArrayNameI<'s, 'i, R>),
    ReachablePrototype(&'i ReachablePrototypeNameI<R>),
    StaticSizedArrayTemplate(&'i StaticSizedArrayTemplateNameI<R>),
    StaticSizedArray(&'i StaticSizedArrayNameI<'s, 'i, R>),
    RuntimeSizedArrayTemplate(&'i RuntimeSizedArrayTemplateNameI<R>),
    RuntimeSizedArray(&'i RuntimeSizedArrayNameI<'s, 'i, R>),
    OverrideDispatcherTemplate(&'i OverrideDispatcherTemplateNameI<'s, 'i, R>),
    OverrideDispatcher(&'i OverrideDispatcherNameI<'s, 'i, R>),
    OverrideDispatcherCase(&'i OverrideDispatcherCaseNameI<'s, 'i, R>),
    CaseFunctionFromImpl(&'i CaseFunctionFromImplNameI<'s, 'i, R>),
    CaseFunctionFromImplTemplate(&'i CaseFunctionFromImplTemplateNameI<'s, R>),
    TypingPassBlockResultVar(&'i TypingPassBlockResultVarNameI<'i, R>),
    TypingPassFunctionResultVar(&'i TypingPassFunctionResultVarNameI<R>),
    TypingPassTemporaryVar(&'i TypingPassTemporaryVarNameI<'i, R>),
    TypingPassPatternMember(&'i TypingPassPatternMemberNameI<'i, R>),
    TypingIgnoredParam(&'i TypingIgnoredParamNameI<R>),
    TypingPassPatternDestructuree(&'i TypingPassPatternDestructureeNameI<'i, R>),
    UnnamedLocal(&'i UnnamedLocalNameI<'s, R>),
    ClosureParam(&'i ClosureParamNameI<'s, R>),
    ConstructingMember(&'i ConstructingMemberNameI<'s, R>),
    WhileCondResult(&'i WhileCondResultNameI<'s, R>),
    Iterable(&'i IterableNameI<'s, R>),
    Iterator(&'i IteratorNameI<'s, R>),
    IterationOption(&'i IterationOptionNameI<'s, R>),
    MagicParam(&'i MagicParamNameI<'s, R>),
    CodeVar(&'i CodeVarNameI<'s, R>),
    AnonymousSubstructMember(&'i AnonymousSubstructMemberNameI<R>),
    Primitive(&'i PrimitiveNameI<'s, R>),
    PackageTopLevel(&'i PackageTopLevelNameI<R>),
    Project(&'i ProjectNameI<'s, R>),
    Package(&'i PackageNameI<'s, R>),
    Rune(&'i RuneNameI<'s, R>),
    BuildingFunctionNameWithClosureds(&'i BuildingFunctionNameWithClosuredsI<'s, 'i, R>),
    ExternFunction(&'i ExternFunctionNameI<'s, 'i, R>),
    FunctionNameIX(&'i FunctionNameIX<'s, 'i, R>),
    ForwarderFunction(&'i ForwarderFunctionNameI<'s, 'i, R>),
    FunctionBoundTemplate(&'i FunctionBoundTemplateNameI<'s, R>),
    FunctionBound(&'i FunctionBoundNameI<'s, 'i, R>),
    ReachableFunctionTemplate(&'i ReachableFunctionTemplateNameI<'s, R>),
    ReachableFunction(&'i ReachableFunctionNameI<'s, 'i, R>),
    FunctionTemplate(&'i FunctionTemplateNameI<'s, R>),
    LambdaCallFunctionTemplate(&'i LambdaCallFunctionTemplateNameI<'s, 'i, R>),
    LambdaCallFunction(&'i LambdaCallFunctionNameI<'s, 'i, R>),
    ForwarderFunctionTemplate(&'i ForwarderFunctionTemplateNameI<'s, 'i, R>),
    ConstructorTemplate(&'i ConstructorTemplateNameI<'s, R>),
    Self_(&'i SelfNameI<R>),
    Arbitrary(&'i ArbitraryNameI<R>),
    StructName(&'i StructNameI<'s, 'i, R>),
    InterfaceName(&'i InterfaceNameI<'s, 'i, R>),
    LambdaCitizenTemplate(&'i LambdaCitizenTemplateNameI<'s, R>),
    LambdaCitizen(&'i LambdaCitizenNameI<'s, R>),
    StructTemplate(&'i StructTemplateNameI<'s, R>),
    InterfaceTemplate(&'i InterfaceTemplateNameI<'s, R>),
    AnonymousSubstructImplTemplate(&'i AnonymousSubstructImplTemplateNameI<'s, 'i, R>),
    AnonymousSubstructImpl(&'i AnonymousSubstructImplNameI<'s, 'i, R>),
    AnonymousSubstructTemplate(&'i AnonymousSubstructTemplateNameI<'s, 'i, R>),
    AnonymousSubstructConstructorTemplate(&'i AnonymousSubstructConstructorTemplateNameI<'s, 'i, R>),
    AnonymousSubstructConstructor(&'i AnonymousSubstructConstructorNameI<'s, 'i, R>),
    AnonymousSubstruct(&'i AnonymousSubstructNameI<'s, 'i, R>),
    ResolvingEnv(&'i ResolvingEnvNameI<R>),
    CallEnv(&'i CallEnvNameI<R>),
}

/// Interning transient (see @TFITCX) — mirror of INameI holding payloads by value
/// (used as HashMap lookup key in the interner). Per typing-pass parity (INameValT).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum INameValI<'s, 'i, R> {
    RegionName(RegionNameI<'s, R>),
    DenizenDefaultRegionName(DenizenDefaultRegionNameI<R>),
    ExportTemplate(ExportTemplateNameI<'s, R>),
    Export(ExportNameI<'s, R>),
    ExternTemplate(ExternTemplateNameI<'s, R>),
    Extern(ExternNameI<'s, R>),
    ImplTemplate(ImplTemplateNameI<'s, R>),
    Impl(ImplNameI<'s, 'i, R>),
    ImplBoundTemplate(ImplBoundTemplateNameI<'s, R>),
    ImplBound(ImplBoundNameI<'s, 'i, R>),
    Let(LetNameI<'s, R>),
    ExportAs(ExportAsNameI<'s, R>),
    RawArray(RawArrayNameI<'s, 'i, R>),
    ReachablePrototype(ReachablePrototypeNameI<R>),
    StaticSizedArrayTemplate(StaticSizedArrayTemplateNameI<R>),
    StaticSizedArray(StaticSizedArrayNameI<'s, 'i, R>),
    RuntimeSizedArrayTemplate(RuntimeSizedArrayTemplateNameI<R>),
    RuntimeSizedArray(RuntimeSizedArrayNameI<'s, 'i, R>),
    OverrideDispatcherTemplate(OverrideDispatcherTemplateNameI<'s, 'i, R>),
    OverrideDispatcher(OverrideDispatcherNameI<'s, 'i, R>),
    OverrideDispatcherCase(OverrideDispatcherCaseNameI<'s, 'i, R>),
    CaseFunctionFromImpl(CaseFunctionFromImplNameI<'s, 'i, R>),
    CaseFunctionFromImplTemplate(CaseFunctionFromImplTemplateNameI<'s, R>),
    TypingPassBlockResultVar(TypingPassBlockResultVarNameI<'i, R>),
    TypingPassFunctionResultVar(TypingPassFunctionResultVarNameI<R>),
    TypingPassTemporaryVar(TypingPassTemporaryVarNameI<'i, R>),
    TypingPassPatternMember(TypingPassPatternMemberNameI<'i, R>),
    TypingIgnoredParam(TypingIgnoredParamNameI<R>),
    TypingPassPatternDestructuree(TypingPassPatternDestructureeNameI<'i, R>),
    UnnamedLocal(UnnamedLocalNameI<'s, R>),
    ClosureParam(ClosureParamNameI<'s, R>),
    ConstructingMember(ConstructingMemberNameI<'s, R>),
    WhileCondResult(WhileCondResultNameI<'s, R>),
    Iterable(IterableNameI<'s, R>),
    Iterator(IteratorNameI<'s, R>),
    IterationOption(IterationOptionNameI<'s, R>),
    MagicParam(MagicParamNameI<'s, R>),
    CodeVar(CodeVarNameI<'s, R>),
    AnonymousSubstructMember(AnonymousSubstructMemberNameI<R>),
    Primitive(PrimitiveNameI<'s, R>),
    PackageTopLevel(PackageTopLevelNameI<R>),
    Project(ProjectNameI<'s, R>),
    Package(PackageNameI<'s, R>),
    Rune(RuneNameI<'s, R>),
    BuildingFunctionNameWithClosureds(BuildingFunctionNameWithClosuredsI<'s, 'i, R>),
    ExternFunction(ExternFunctionNameI<'s, 'i, R>),
    FunctionNameIX(FunctionNameIX<'s, 'i, R>),
    ForwarderFunction(ForwarderFunctionNameI<'s, 'i, R>),
    FunctionBoundTemplate(FunctionBoundTemplateNameI<'s, R>),
    FunctionBound(FunctionBoundNameI<'s, 'i, R>),
    ReachableFunctionTemplate(ReachableFunctionTemplateNameI<'s, R>),
    ReachableFunction(ReachableFunctionNameI<'s, 'i, R>),
    FunctionTemplate(FunctionTemplateNameI<'s, R>),
    LambdaCallFunctionTemplate(LambdaCallFunctionTemplateNameI<'s, 'i, R>),
    LambdaCallFunction(LambdaCallFunctionNameI<'s, 'i, R>),
    ForwarderFunctionTemplate(ForwarderFunctionTemplateNameI<'s, 'i, R>),
    ConstructorTemplate(ConstructorTemplateNameI<'s, R>),
    Self_(SelfNameI<R>),
    Arbitrary(ArbitraryNameI<R>),
    StructName(StructNameI<'s, 'i, R>),
    InterfaceName(InterfaceNameI<'s, 'i, R>),
    LambdaCitizenTemplate(LambdaCitizenTemplateNameI<'s, R>),
    LambdaCitizen(LambdaCitizenNameI<'s, R>),
    StructTemplate(StructTemplateNameI<'s, R>),
    InterfaceTemplate(InterfaceTemplateNameI<'s, R>),
    AnonymousSubstructImplTemplate(AnonymousSubstructImplTemplateNameI<'s, 'i, R>),
    AnonymousSubstructImpl(AnonymousSubstructImplNameI<'s, 'i, R>),
    AnonymousSubstructTemplate(AnonymousSubstructTemplateNameI<'s, 'i, R>),
    AnonymousSubstructConstructorTemplate(AnonymousSubstructConstructorTemplateNameI<'s, 'i, R>),
    AnonymousSubstructConstructor(AnonymousSubstructConstructorNameI<'s, 'i, R>),
    AnonymousSubstruct(AnonymousSubstructNameI<'s, 'i, R>),
    ResolvingEnv(ResolvingEnvNameI<R>),
    CallEnv(CallEnvNameI<R>),
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum ITemplateNameI<'s, 'i, R> {
    ExportTemplate(&'i ExportTemplateNameI<'s, R>),
    ImplTemplate(&'i ImplTemplateNameI<'s, R>),
    ImplBoundTemplate(&'i ImplBoundTemplateNameI<'s, R>),
    StaticSizedArrayTemplate(&'i StaticSizedArrayTemplateNameI<R>),
    RuntimeSizedArrayTemplate(&'i RuntimeSizedArrayTemplateNameI<R>),
    OverrideDispatcherTemplate(&'i OverrideDispatcherTemplateNameI<'s, 'i, R>),
    OverrideDispatcherCase(&'i OverrideDispatcherCaseNameI<'s, 'i, R>),
    ExternTemplate(&'i ExternTemplateNameI<'s, R>),
    ExternFunction(&'i ExternFunctionNameI<'s, 'i, R>),
    FunctionBoundTemplate(&'i FunctionBoundTemplateNameI<'s, R>),
    FunctionTemplate(&'i FunctionTemplateNameI<'s, R>),
    LambdaCallFunctionTemplate(&'i LambdaCallFunctionTemplateNameI<'s, 'i, R>),
    ForwarderFunctionTemplate(&'i ForwarderFunctionTemplateNameI<'s, 'i, R>),
    ConstructorTemplate(&'i ConstructorTemplateNameI<'s, R>),
    LambdaCitizenTemplate(&'i LambdaCitizenTemplateNameI<'s, R>),
    StructTemplate(&'i StructTemplateNameI<'s, R>),
    InterfaceTemplate(&'i InterfaceTemplateNameI<'s, R>),
    AnonymousSubstructImplTemplate(&'i AnonymousSubstructImplTemplateNameI<'s, 'i, R>),
    AnonymousSubstructTemplate(&'i AnonymousSubstructTemplateNameI<'s, 'i, R>),
    AnonymousSubstructConstructorTemplate(&'i AnonymousSubstructConstructorTemplateNameI<'s, 'i, R>),
}

// Rust-only narrowing INameI -> ITemplateNameI (mirrors T-side TryFrom<INameT>).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for ITemplateNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::ExportTemplate(x) => Ok(ITemplateNameI::ExportTemplate(x)),
            INameI::ImplTemplate(x) => Ok(ITemplateNameI::ImplTemplate(x)),
            INameI::ImplBoundTemplate(x) => Ok(ITemplateNameI::ImplBoundTemplate(x)),
            INameI::StaticSizedArrayTemplate(x) => Ok(ITemplateNameI::StaticSizedArrayTemplate(x)),
            INameI::RuntimeSizedArrayTemplate(x) => Ok(ITemplateNameI::RuntimeSizedArrayTemplate(x)),
            INameI::OverrideDispatcherTemplate(x) => Ok(ITemplateNameI::OverrideDispatcherTemplate(x)),
            INameI::OverrideDispatcherCase(x) => Ok(ITemplateNameI::OverrideDispatcherCase(x)),
            INameI::ExternTemplate(x) => Ok(ITemplateNameI::ExternTemplate(x)),
            INameI::ExternFunction(x) => Ok(ITemplateNameI::ExternFunction(x)),
            INameI::FunctionBoundTemplate(x) => Ok(ITemplateNameI::FunctionBoundTemplate(x)),
            INameI::FunctionTemplate(x) => Ok(ITemplateNameI::FunctionTemplate(x)),
            INameI::LambdaCallFunctionTemplate(x) => Ok(ITemplateNameI::LambdaCallFunctionTemplate(x)),
            INameI::ForwarderFunctionTemplate(x) => Ok(ITemplateNameI::ForwarderFunctionTemplate(x)),
            INameI::ConstructorTemplate(x) => Ok(ITemplateNameI::ConstructorTemplate(x)),
            INameI::LambdaCitizenTemplate(x) => Ok(ITemplateNameI::LambdaCitizenTemplate(x)),
            INameI::StructTemplate(x) => Ok(ITemplateNameI::StructTemplate(x)),
            INameI::InterfaceTemplate(x) => Ok(ITemplateNameI::InterfaceTemplate(x)),
            INameI::AnonymousSubstructImplTemplate(x) => Ok(ITemplateNameI::AnonymousSubstructImplTemplate(x)),
            INameI::AnonymousSubstructTemplate(x) => Ok(ITemplateNameI::AnonymousSubstructTemplate(x)),
            INameI::AnonymousSubstructConstructorTemplate(x) => Ok(ITemplateNameI::AnonymousSubstructConstructorTemplate(x)),
            _ => Err(()),
        }
    }
}
/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IFunctionTemplateNameI<'s, 'i, R> {
    OverrideDispatcherTemplate(&'i OverrideDispatcherTemplateNameI<'s, 'i, R>),
    ExternFunction(&'i ExternFunctionNameI<'s, 'i, R>),
    FunctionBoundTemplate(&'i FunctionBoundTemplateNameI<'s, R>),
    FunctionTemplate(&'i FunctionTemplateNameI<'s, R>),
    LambdaCallFunctionTemplate(&'i LambdaCallFunctionTemplateNameI<'s, 'i, R>),
    ForwarderFunctionTemplate(&'i ForwarderFunctionTemplateNameI<'s, 'i, R>),
    ConstructorTemplate(&'i ConstructorTemplateNameI<'s, R>),
    AnonymousSubstructConstructorTemplate(&'i AnonymousSubstructConstructorTemplateNameI<'s, 'i, R>),
}

// Rust-only widening IFunctionTemplateNameI -> INameI (mirrors T-side).
impl<'s, 'i, R> From<IFunctionTemplateNameI<'s, 'i, R>> for INameI<'s, 'i, R> {
    fn from(t: IFunctionTemplateNameI<'s, 'i, R>) -> Self {
        match t {
            IFunctionTemplateNameI::OverrideDispatcherTemplate(x) => INameI::OverrideDispatcherTemplate(x),
            IFunctionTemplateNameI::ExternFunction(x) => INameI::ExternFunction(x),
            IFunctionTemplateNameI::FunctionBoundTemplate(x) => INameI::FunctionBoundTemplate(x),
            IFunctionTemplateNameI::FunctionTemplate(x) => INameI::FunctionTemplate(x),
            IFunctionTemplateNameI::LambdaCallFunctionTemplate(x) => INameI::LambdaCallFunctionTemplate(x),
            IFunctionTemplateNameI::ForwarderFunctionTemplate(x) => INameI::ForwarderFunctionTemplate(x),
            IFunctionTemplateNameI::ConstructorTemplate(x) => INameI::ConstructorTemplate(x),
            IFunctionTemplateNameI::AnonymousSubstructConstructorTemplate(x) => INameI::AnonymousSubstructConstructorTemplate(x),
        }
    }
}

// Rust-only narrowing INameI -> IFunctionTemplateNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for IFunctionTemplateNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::OverrideDispatcherTemplate(x) => Ok(IFunctionTemplateNameI::OverrideDispatcherTemplate(x)),
            INameI::ExternFunction(x) => Ok(IFunctionTemplateNameI::ExternFunction(x)),
            INameI::FunctionBoundTemplate(x) => Ok(IFunctionTemplateNameI::FunctionBoundTemplate(x)),
            INameI::FunctionTemplate(x) => Ok(IFunctionTemplateNameI::FunctionTemplate(x)),
            INameI::LambdaCallFunctionTemplate(x) => Ok(IFunctionTemplateNameI::LambdaCallFunctionTemplate(x)),
            INameI::ForwarderFunctionTemplate(x) => Ok(IFunctionTemplateNameI::ForwarderFunctionTemplate(x)),
            INameI::ConstructorTemplate(x) => Ok(IFunctionTemplateNameI::ConstructorTemplate(x)),
            INameI::AnonymousSubstructConstructorTemplate(x) => Ok(IFunctionTemplateNameI::AnonymousSubstructConstructorTemplate(x)),
            _ => Err(()),
        }
    }
}
/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
pub enum IInstantiationNameI<'s, 'i, R> {
    Export(&'i ExportNameI<'s, R>),
    Impl(&'i ImplNameI<'s, 'i, R>),
    ImplBound(&'i ImplBoundNameI<'s, 'i, R>),
    StaticSizedArray(&'i StaticSizedArrayNameI<'s, 'i, R>),
    RuntimeSizedArray(&'i RuntimeSizedArrayNameI<'s, 'i, R>),
    OverrideDispatcher(&'i OverrideDispatcherNameI<'s, 'i, R>),
    OverrideDispatcherCase(&'i OverrideDispatcherCaseNameI<'s, 'i, R>),
    Extern(&'i ExternNameI<'s, R>),
    ExternFunction(&'i ExternFunctionNameI<'s, 'i, R>),
    Function(&'i FunctionNameIX<'s, 'i, R>),
    ForwarderFunction(&'i ForwarderFunctionNameI<'s, 'i, R>),
    FunctionBound(&'i FunctionBoundNameI<'s, 'i, R>),
    LambdaCallFunction(&'i LambdaCallFunctionNameI<'s, 'i, R>),
    Struct(&'i StructNameI<'s, 'i, R>),
    Interface(&'i InterfaceNameI<'s, 'i, R>),
    LambdaCitizen(&'i LambdaCitizenNameI<'s, R>),
    AnonymousSubstructImpl(&'i AnonymousSubstructImplNameI<'s, 'i, R>),
    AnonymousSubstructConstructor(&'i AnonymousSubstructConstructorNameI<'s, 'i, R>),
    AnonymousSubstruct(&'i AnonymousSubstructNameI<'s, 'i, R>),
}

impl<'s, 'i, R> IInstantiationNameI<'s, 'i, R> where 's: 'i, R: Copy {
    pub fn template_args(&self, interner: &InstantiatingInterner<'s, 'i>) -> &'i [ITemplataI<'s, 'i, R>] {
        match self {
            IInstantiationNameI::Export(x) => interner.alloc_slice_from_vec(vec![ITemplataI::Region(x.region)]),
            IInstantiationNameI::Impl(x) => x.template_args,
            IInstantiationNameI::ImplBound(x) => x.template_args,
            IInstantiationNameI::StaticSizedArray(_) => panic!("Unimplemented: template_args on StaticSizedArrayNameI (computed: needs interner to allocate slice)"),
            IInstantiationNameI::RuntimeSizedArray(_) => panic!("Unimplemented: template_args on RuntimeSizedArrayNameI (computed: needs interner to allocate slice)"),
            IInstantiationNameI::OverrideDispatcher(x) => x.template_args,
            IInstantiationNameI::OverrideDispatcherCase(x) => x.independent_impl_template_args,
            IInstantiationNameI::Extern(x) => interner.alloc_slice_from_vec(vec![ITemplataI::Region(x.region)]),
            IInstantiationNameI::ExternFunction(_) => panic!("Unimplemented: template_args on ExternFunctionNameI"),
            IInstantiationNameI::Function(x) => x.template_args,
            IInstantiationNameI::ForwarderFunction(f) => f.inner.template_args(),
            IInstantiationNameI::FunctionBound(x) => x.template_args,
            IInstantiationNameI::LambdaCallFunction(x) => x.template_args,
            IInstantiationNameI::Struct(x) => x.template_args,
            IInstantiationNameI::Interface(x) => x.template_args,
            IInstantiationNameI::LambdaCitizen(_) => &[],
            IInstantiationNameI::AnonymousSubstructImpl(x) => x.template_args,
            IInstantiationNameI::AnonymousSubstructConstructor(x) => x.template_args,
            IInstantiationNameI::AnonymousSubstruct(x) => x.template_args,
        }
    }
}

impl<'s, 'i, R> IInstantiationNameI<'s, 'i, R> where 's: 'i {
    pub fn template(&self) -> ITemplateNameI<'s, 'i, R> {
        match self {
            IInstantiationNameI::Export(x) => ITemplateNameI::ExportTemplate(&x.template),
            IInstantiationNameI::Impl(x) => match x.template {
                IImplTemplateNameI::ImplTemplate(t) => ITemplateNameI::ImplTemplate(t),
                IImplTemplateNameI::ImplBoundTemplate(t) => ITemplateNameI::ImplBoundTemplate(t),
                IImplTemplateNameI::AnonymousSubstructImplTemplate(t) => ITemplateNameI::AnonymousSubstructImplTemplate(t),
            },
            IInstantiationNameI::ImplBound(x) => ITemplateNameI::ImplBoundTemplate(&x.template),
            IInstantiationNameI::StaticSizedArray(x) => ITemplateNameI::StaticSizedArrayTemplate(&x.template),
            IInstantiationNameI::RuntimeSizedArray(x) => ITemplateNameI::RuntimeSizedArrayTemplate(&x.template),
            IInstantiationNameI::OverrideDispatcher(x) => ITemplateNameI::OverrideDispatcherTemplate(&x.template),
            IInstantiationNameI::OverrideDispatcherCase(x) => ITemplateNameI::OverrideDispatcherCase(x),
            IInstantiationNameI::Extern(x) => ITemplateNameI::ExternTemplate(&x.template),
            IInstantiationNameI::ExternFunction(x) => ITemplateNameI::ExternFunction(x),
            IInstantiationNameI::Function(x) => ITemplateNameI::FunctionTemplate(&x.template),
            IInstantiationNameI::ForwarderFunction(x) => ITemplateNameI::ForwarderFunctionTemplate(&x.template),
            IInstantiationNameI::FunctionBound(x) => ITemplateNameI::FunctionBoundTemplate(&x.template),
            IInstantiationNameI::LambdaCallFunction(x) => ITemplateNameI::LambdaCallFunctionTemplate(&x.template),
            IInstantiationNameI::Struct(x) => match x.template {
                IStructTemplateNameI::StructTemplate(t) => ITemplateNameI::StructTemplate(t),
                IStructTemplateNameI::LambdaCitizenTemplate(t) => ITemplateNameI::LambdaCitizenTemplate(t),
                IStructTemplateNameI::AnonymousSubstructTemplate(t) => ITemplateNameI::AnonymousSubstructTemplate(t),
            },
            IInstantiationNameI::Interface(x) => match x.template {
                IInterfaceTemplateNameI::InterfaceTemplate(t) => ITemplateNameI::InterfaceTemplate(t),
            },
            IInstantiationNameI::LambdaCitizen(x) => ITemplateNameI::LambdaCitizenTemplate(&x.template),
            IInstantiationNameI::AnonymousSubstructImpl(x) => ITemplateNameI::AnonymousSubstructImplTemplate(&x.template),
            IInstantiationNameI::AnonymousSubstructConstructor(x) => ITemplateNameI::AnonymousSubstructConstructorTemplate(&x.template),
            IInstantiationNameI::AnonymousSubstruct(x) => ITemplateNameI::AnonymousSubstructTemplate(&x.template),
        }
    }
}
// Rust-only narrowing from the wide INameI to the IInstantiationNameI subset
// (mirrors the T-side `TryFrom<INameT> for IInstantiationNameT`).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for IInstantiationNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::Export(x) => Ok(IInstantiationNameI::Export(x)),
            INameI::Impl(x) => Ok(IInstantiationNameI::Impl(x)),
            INameI::ImplBound(x) => Ok(IInstantiationNameI::ImplBound(x)),
            INameI::StaticSizedArray(x) => Ok(IInstantiationNameI::StaticSizedArray(x)),
            INameI::RuntimeSizedArray(x) => Ok(IInstantiationNameI::RuntimeSizedArray(x)),
            INameI::OverrideDispatcher(x) => Ok(IInstantiationNameI::OverrideDispatcher(x)),
            INameI::OverrideDispatcherCase(x) => Ok(IInstantiationNameI::OverrideDispatcherCase(x)),
            INameI::Extern(x) => Ok(IInstantiationNameI::Extern(x)),
            INameI::ExternFunction(x) => Ok(IInstantiationNameI::ExternFunction(x)),
            INameI::FunctionNameIX(x) => Ok(IInstantiationNameI::Function(x)),
            INameI::ForwarderFunction(x) => Ok(IInstantiationNameI::ForwarderFunction(x)),
            INameI::FunctionBound(x) => Ok(IInstantiationNameI::FunctionBound(x)),
            INameI::LambdaCallFunction(x) => Ok(IInstantiationNameI::LambdaCallFunction(x)),
            INameI::StructName(x) => Ok(IInstantiationNameI::Struct(x)),
            INameI::InterfaceName(x) => Ok(IInstantiationNameI::Interface(x)),
            INameI::LambdaCitizen(x) => Ok(IInstantiationNameI::LambdaCitizen(x)),
            INameI::AnonymousSubstructImpl(x) => Ok(IInstantiationNameI::AnonymousSubstructImpl(x)),
            INameI::AnonymousSubstructConstructor(x) => Ok(IInstantiationNameI::AnonymousSubstructConstructor(x)),
            INameI::AnonymousSubstruct(x) => Ok(IInstantiationNameI::AnonymousSubstruct(x)),
            _ => Err(()),
        }
    }
}
/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IFunctionNameI<'s, 'i, R> {
    OverrideDispatcher(&'i OverrideDispatcherNameI<'s, 'i, R>),
    ExternFunction(&'i ExternFunctionNameI<'s, 'i, R>),
    Function(&'i FunctionNameIX<'s, 'i, R>),
    ForwarderFunction(&'i ForwarderFunctionNameI<'s, 'i, R>),
    FunctionBound(&'i FunctionBoundNameI<'s, 'i, R>),
    LambdaCallFunction(&'i LambdaCallFunctionNameI<'s, 'i, R>),
    AnonymousSubstructConstructor(&'i AnonymousSubstructConstructorNameI<'s, 'i, R>),
}

impl<'s, 'i, R> IFunctionNameI<'s, 'i, R> where 's: 'i {
    pub fn template_args(&self) -> &'i [ITemplataI<'s, 'i, R>] {
        match self {
            IFunctionNameI::OverrideDispatcher(x) => x.template_args,
            IFunctionNameI::ExternFunction(_) => panic!("Unimplemented: template_args on ExternFunctionNameI"),
            IFunctionNameI::Function(x) => x.template_args,
            IFunctionNameI::ForwarderFunction(f) => f.inner.template_args(),
            IFunctionNameI::FunctionBound(x) => x.template_args,
            IFunctionNameI::LambdaCallFunction(x) => x.template_args,
            IFunctionNameI::AnonymousSubstructConstructor(x) => x.template_args,
        }
    }
    pub fn template(&self) -> IFunctionTemplateNameI<'s, 'i, R> {
        match self {
            IFunctionNameI::OverrideDispatcher(x) => IFunctionTemplateNameI::OverrideDispatcherTemplate(&x.template),
            IFunctionNameI::ExternFunction(_) => panic!("Unimplemented: template on ExternFunctionNameI"),
            IFunctionNameI::Function(x) => IFunctionTemplateNameI::FunctionTemplate(&x.template),
            IFunctionNameI::ForwarderFunction(x) => IFunctionTemplateNameI::ForwarderFunctionTemplate(&x.template),
            IFunctionNameI::FunctionBound(x) => IFunctionTemplateNameI::FunctionBoundTemplate(&x.template),
            IFunctionNameI::LambdaCallFunction(x) => IFunctionTemplateNameI::LambdaCallFunctionTemplate(&x.template),
            IFunctionNameI::AnonymousSubstructConstructor(x) => IFunctionTemplateNameI::AnonymousSubstructConstructorTemplate(&x.template),
        }
    }
    pub fn parameters(&self) -> &'i [CoordI<'s, 'i, R>] {
        match self {
            IFunctionNameI::OverrideDispatcher(f) => f.parameters,
            IFunctionNameI::ExternFunction(f) => f.parameters,
            IFunctionNameI::Function(f) => f.parameters,
            IFunctionNameI::ForwarderFunction(f) => f.inner.parameters(),
            IFunctionNameI::FunctionBound(f) => f.parameters,
            IFunctionNameI::LambdaCallFunction(f) => f.parameters,
            IFunctionNameI::AnonymousSubstructConstructor(f) => f.parameters,
        }
    }
}
// Rust-only narrowing INameI -> IFunctionNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for IFunctionNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::OverrideDispatcher(x) => Ok(IFunctionNameI::OverrideDispatcher(x)),
            INameI::ExternFunction(x) => Ok(IFunctionNameI::ExternFunction(x)),
            INameI::FunctionNameIX(x) => Ok(IFunctionNameI::Function(x)),
            INameI::ForwarderFunction(x) => Ok(IFunctionNameI::ForwarderFunction(x)),
            INameI::FunctionBound(x) => Ok(IFunctionNameI::FunctionBound(x)),
            INameI::LambdaCallFunction(x) => Ok(IFunctionNameI::LambdaCallFunction(x)),
            INameI::AnonymousSubstructConstructor(x) => Ok(IFunctionNameI::AnonymousSubstructConstructor(x)),
            _ => Err(()),
        }
    }
}
// Rust-only widening IFunctionNameI -> INameI (mirrors Scala `IFunctionNameI extends INameI`
// subtyping; reverse of the TryFrom above, same as the template-name From widenings below).
impl<'s, 'i, R> From<IFunctionNameI<'s, 'i, R>> for INameI<'s, 'i, R> where 's: 'i {
    fn from(name: IFunctionNameI<'s, 'i, R>) -> Self {
        match name {
            IFunctionNameI::OverrideDispatcher(x) => INameI::OverrideDispatcher(x),
            IFunctionNameI::ExternFunction(x) => INameI::ExternFunction(x),
            IFunctionNameI::Function(x) => INameI::FunctionNameIX(x),
            IFunctionNameI::ForwarderFunction(x) => INameI::ForwarderFunction(x),
            IFunctionNameI::FunctionBound(x) => INameI::FunctionBound(x),
            IFunctionNameI::LambdaCallFunction(x) => INameI::LambdaCallFunction(x),
            IFunctionNameI::AnonymousSubstructConstructor(x) => INameI::AnonymousSubstructConstructor(x),
        }
    }
}
/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum ISuperKindTemplateNameI<'s, 'i, R> {
    InterfaceTemplate(&'i InterfaceTemplateNameI<'s, R>),
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum ISubKindTemplateNameI<'s, 'i, R> {
    StaticSizedArrayTemplate(&'i StaticSizedArrayTemplateNameI<R>),
    RuntimeSizedArrayTemplate(&'i RuntimeSizedArrayTemplateNameI<R>),
    LambdaCitizenTemplate(&'i LambdaCitizenTemplateNameI<'s, R>),
    StructTemplate(&'i StructTemplateNameI<'s, R>),
    InterfaceTemplate(&'i InterfaceTemplateNameI<'s, R>),
    AnonymousSubstructTemplate(&'i AnonymousSubstructTemplateNameI<'s, 'i, R>),
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum ICitizenTemplateNameI<'s, 'i, R> {
    StaticSizedArrayTemplate(&'i StaticSizedArrayTemplateNameI<R>),
    RuntimeSizedArrayTemplate(&'i RuntimeSizedArrayTemplateNameI<R>),
    LambdaCitizenTemplate(&'i LambdaCitizenTemplateNameI<'s, R>),
    StructTemplate(&'i StructTemplateNameI<'s, R>),
    InterfaceTemplate(&'i InterfaceTemplateNameI<'s, R>),
    AnonymousSubstructTemplate(&'i AnonymousSubstructTemplateNameI<'s, 'i, R>),
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IStructTemplateNameI<'s, 'i, R> {
    LambdaCitizenTemplate(&'i LambdaCitizenTemplateNameI<'s, R>),
    StructTemplate(&'i StructTemplateNameI<'s, R>),
    AnonymousSubstructTemplate(&'i AnonymousSubstructTemplateNameI<'s, 'i, R>),
}
// Widening conversions mirroring the Scala `extends` hierarchy of the template-name traits
// (IStructTemplateNameI <: ICitizenTemplateNameI <: ISubKindTemplateNameI). Rust-only (no Scala
// counterpart — Scala uses subtyping); the T-side encodes the same widenings as `From` impls.
impl<'s, 'i, R> From<IStructTemplateNameI<'s, 'i, R>> for ICitizenTemplateNameI<'s, 'i, R> {
    fn from(t: IStructTemplateNameI<'s, 'i, R>) -> Self {
        match t {
            IStructTemplateNameI::LambdaCitizenTemplate(x) => ICitizenTemplateNameI::LambdaCitizenTemplate(x),
            IStructTemplateNameI::StructTemplate(x) => ICitizenTemplateNameI::StructTemplate(x),
            IStructTemplateNameI::AnonymousSubstructTemplate(x) => ICitizenTemplateNameI::AnonymousSubstructTemplate(x),
        }
    }
}
impl<'s, 'i, R> From<ICitizenTemplateNameI<'s, 'i, R>> for ISubKindTemplateNameI<'s, 'i, R> {
    fn from(t: ICitizenTemplateNameI<'s, 'i, R>) -> Self {
        match t {
            ICitizenTemplateNameI::StaticSizedArrayTemplate(x) => ISubKindTemplateNameI::StaticSizedArrayTemplate(x),
            ICitizenTemplateNameI::RuntimeSizedArrayTemplate(x) => ISubKindTemplateNameI::RuntimeSizedArrayTemplate(x),
            ICitizenTemplateNameI::LambdaCitizenTemplate(x) => ISubKindTemplateNameI::LambdaCitizenTemplate(x),
            ICitizenTemplateNameI::StructTemplate(x) => ISubKindTemplateNameI::StructTemplate(x),
            ICitizenTemplateNameI::InterfaceTemplate(x) => ISubKindTemplateNameI::InterfaceTemplate(x),
            ICitizenTemplateNameI::AnonymousSubstructTemplate(x) => ISubKindTemplateNameI::AnonymousSubstructTemplate(x),
        }
    }
}
// Rust-only narrowing INameI -> ICitizenTemplateNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for ICitizenTemplateNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::StaticSizedArrayTemplate(x) => Ok(ICitizenTemplateNameI::StaticSizedArrayTemplate(x)),
            INameI::RuntimeSizedArrayTemplate(x) => Ok(ICitizenTemplateNameI::RuntimeSizedArrayTemplate(x)),
            INameI::LambdaCitizenTemplate(x) => Ok(ICitizenTemplateNameI::LambdaCitizenTemplate(x)),
            INameI::StructTemplate(x) => Ok(ICitizenTemplateNameI::StructTemplate(x)),
            INameI::InterfaceTemplate(x) => Ok(ICitizenTemplateNameI::InterfaceTemplate(x)),
            INameI::AnonymousSubstructTemplate(x) => Ok(ICitizenTemplateNameI::AnonymousSubstructTemplate(x)),
            _ => Err(()),
        }
    }
}

// Rust-only widening IInterfaceTemplateNameI -> ICitizenTemplateNameI
// (IInterfaceTemplateNameI <: ICitizenTemplateNameI). Rust-only (no Scala counterpart — Scala uses subtyping).
impl<'s, 'i, R> From<IInterfaceTemplateNameI<'s, 'i, R>> for ICitizenTemplateNameI<'s, 'i, R> {
    fn from(t: IInterfaceTemplateNameI<'s, 'i, R>) -> Self {
        match t {
            IInterfaceTemplateNameI::InterfaceTemplate(x) => ICitizenTemplateNameI::InterfaceTemplate(x),
        }
    }
}

// Rust-only narrowing ICitizenTemplateNameI -> IStructTemplateNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<ICitizenTemplateNameI<'s, 'i, R>> for IStructTemplateNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: ICitizenTemplateNameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            ICitizenTemplateNameI::StructTemplate(x) => Ok(IStructTemplateNameI::StructTemplate(x)),
            ICitizenTemplateNameI::AnonymousSubstructTemplate(x) => Ok(IStructTemplateNameI::AnonymousSubstructTemplate(x)),
            ICitizenTemplateNameI::LambdaCitizenTemplate(x) => Ok(IStructTemplateNameI::LambdaCitizenTemplate(x)),
            _ => Err(()),
        }
    }
}

// Rust-only narrowing ICitizenTemplateNameI -> IInterfaceTemplateNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<ICitizenTemplateNameI<'s, 'i, R>> for IInterfaceTemplateNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: ICitizenTemplateNameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            ICitizenTemplateNameI::InterfaceTemplate(x) => Ok(IInterfaceTemplateNameI::InterfaceTemplate(x)),
            _ => Err(()),
        }
    }
}

// Rust-only narrowing INameI -> IStructTemplateNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for IStructTemplateNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::StructTemplate(x) => Ok(IStructTemplateNameI::StructTemplate(x)),
            INameI::AnonymousSubstructTemplate(x) => Ok(IStructTemplateNameI::AnonymousSubstructTemplate(x)),
            INameI::LambdaCitizenTemplate(x) => Ok(IStructTemplateNameI::LambdaCitizenTemplate(x)),
            _ => Err(()),
        }
    }
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IInterfaceTemplateNameI<'s, 'i, R> {
    InterfaceTemplate(&'i InterfaceTemplateNameI<'s, R>),
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum ISuperKindNameI<'s, 'i, R> {
    Interface(&'i InterfaceNameI<'s, 'i, R>),
}

impl<'s, 'i, R> ISuperKindNameI<'s, 'i, R> where 's: 'i {
    pub fn template_args(&self) -> &'i [ITemplataI<'s, 'i, R>] {
        match self {
            ISuperKindNameI::Interface(x) => x.template_args,
        }
    }
    pub fn template(&self) -> ISuperKindTemplateNameI<'s, 'i, R> {
        match self {
            ISuperKindNameI::Interface(x) => match x.template {
                IInterfaceTemplateNameI::InterfaceTemplate(t) => ISuperKindTemplateNameI::InterfaceTemplate(t),
            },
        }
    }
}
// Rust-only narrowing INameI -> ISuperKindNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for ISuperKindNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::InterfaceName(x) => Ok(ISuperKindNameI::Interface(x)),
            _ => Err(()),
        }
    }
}
/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum ISubKindNameI<'s, 'i, R> {
    StaticSizedArray(&'i StaticSizedArrayNameI<'s, 'i, R>),
    RuntimeSizedArray(&'i RuntimeSizedArrayNameI<'s, 'i, R>),
    Struct(&'i StructNameI<'s, 'i, R>),
    Interface(&'i InterfaceNameI<'s, 'i, R>),
    LambdaCitizen(&'i LambdaCitizenNameI<'s, R>),
    AnonymousSubstruct(&'i AnonymousSubstructNameI<'s, 'i, R>),
}

impl<'s, 'i, R> ISubKindNameI<'s, 'i, R> where 's: 'i {
    pub fn template_args(&self) -> &'i [ITemplataI<'s, 'i, R>] {
        match self {
            ISubKindNameI::StaticSizedArray(_) => panic!("Unimplemented: template_args on StaticSizedArrayNameI (computed: needs interner to allocate slice)"),
            ISubKindNameI::RuntimeSizedArray(_) => panic!("Unimplemented: template_args on RuntimeSizedArrayNameI (computed: needs interner to allocate slice)"),
            ISubKindNameI::Struct(x) => x.template_args,
            ISubKindNameI::Interface(x) => x.template_args,
            ISubKindNameI::LambdaCitizen(_) => &[],
            ISubKindNameI::AnonymousSubstruct(x) => x.template_args,
        }
    }
}
impl<'s, 'i, R> ISubKindNameI<'s, 'i, R> where 's: 'i, R: Copy {
    pub fn template(&self) -> ISubKindTemplateNameI<'s, 'i, R> {
        match self {
            ISubKindNameI::StaticSizedArray(x) => ISubKindTemplateNameI::StaticSizedArrayTemplate(&x.template),
            ISubKindNameI::RuntimeSizedArray(x) => ISubKindTemplateNameI::RuntimeSizedArrayTemplate(&x.template),
            ISubKindNameI::Struct(x) => ISubKindTemplateNameI::from(ICitizenTemplateNameI::from(x.template)),
            ISubKindNameI::Interface(x) => match x.template {
                IInterfaceTemplateNameI::InterfaceTemplate(t) => ISubKindTemplateNameI::InterfaceTemplate(t),
            },
            ISubKindNameI::LambdaCitizen(x) => ISubKindTemplateNameI::LambdaCitizenTemplate(&x.template),
            ISubKindNameI::AnonymousSubstruct(x) => ISubKindTemplateNameI::AnonymousSubstructTemplate(&x.template),
        }
    }
}
// Rust-only narrowing INameI -> ISubKindNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for ISubKindNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::StaticSizedArray(x) => Ok(ISubKindNameI::StaticSizedArray(x)),
            INameI::RuntimeSizedArray(x) => Ok(ISubKindNameI::RuntimeSizedArray(x)),
            INameI::StructName(x) => Ok(ISubKindNameI::Struct(x)),
            INameI::InterfaceName(x) => Ok(ISubKindNameI::Interface(x)),
            INameI::LambdaCitizen(x) => Ok(ISubKindNameI::LambdaCitizen(x)),
            INameI::AnonymousSubstruct(x) => Ok(ISubKindNameI::AnonymousSubstruct(x)),
            _ => Err(()),
        }
    }
}
/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum ICitizenNameI<'s, 'i, R> {
    StaticSizedArray(&'i StaticSizedArrayNameI<'s, 'i, R>),
    RuntimeSizedArray(&'i RuntimeSizedArrayNameI<'s, 'i, R>),
    Struct(&'i StructNameI<'s, 'i, R>),
    Interface(&'i InterfaceNameI<'s, 'i, R>),
    LambdaCitizen(&'i LambdaCitizenNameI<'s, R>),
    AnonymousSubstruct(&'i AnonymousSubstructNameI<'s, 'i, R>),
}

impl<'s, 'i, R> ICitizenNameI<'s, 'i, R> where 's: 'i {
    pub fn template_args(&self) -> &'i [ITemplataI<'s, 'i, R>] {
        match self {
            ICitizenNameI::StaticSizedArray(_) => panic!("Unimplemented: template_args on StaticSizedArrayNameI (computed: needs interner to allocate slice)"),
            ICitizenNameI::RuntimeSizedArray(_) => panic!("Unimplemented: template_args on RuntimeSizedArrayNameI (computed: needs interner to allocate slice)"),
            ICitizenNameI::Struct(x) => x.template_args,
            ICitizenNameI::Interface(x) => x.template_args,
            ICitizenNameI::LambdaCitizen(_) => &[],
            ICitizenNameI::AnonymousSubstruct(x) => x.template_args,
        }
    }
}
impl<'s, 'i, R> ICitizenNameI<'s, 'i, R> where 's: 'i, R: Copy {
    pub fn template(&self) -> ICitizenTemplateNameI<'s, 'i, R> {
        match self {
            ICitizenNameI::StaticSizedArray(x) => ICitizenTemplateNameI::StaticSizedArrayTemplate(&x.template),
            ICitizenNameI::RuntimeSizedArray(x) => ICitizenTemplateNameI::RuntimeSizedArrayTemplate(&x.template),
            ICitizenNameI::Struct(x) => ICitizenTemplateNameI::from(x.template),
            ICitizenNameI::Interface(x) => match x.template {
                IInterfaceTemplateNameI::InterfaceTemplate(t) => ICitizenTemplateNameI::InterfaceTemplate(t),
            },
            ICitizenNameI::LambdaCitizen(x) => ICitizenTemplateNameI::LambdaCitizenTemplate(&x.template),
            ICitizenNameI::AnonymousSubstruct(x) => ICitizenTemplateNameI::AnonymousSubstructTemplate(&x.template),
        }
    }
}
// Rust-only narrowing INameI -> ICitizenNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for ICitizenNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::StaticSizedArray(x) => Ok(ICitizenNameI::StaticSizedArray(x)),
            INameI::RuntimeSizedArray(x) => Ok(ICitizenNameI::RuntimeSizedArray(x)),
            INameI::StructName(x) => Ok(ICitizenNameI::Struct(x)),
            INameI::InterfaceName(x) => Ok(ICitizenNameI::Interface(x)),
            INameI::LambdaCitizen(x) => Ok(ICitizenNameI::LambdaCitizen(x)),
            INameI::AnonymousSubstruct(x) => Ok(ICitizenNameI::AnonymousSubstruct(x)),
            _ => Err(()),
        }
    }
}
// Rust-only widening ICitizenNameI -> INameI (mirrors Scala subtyping; reverse of the TryFrom above,
// same family as the IFunctionNameI widening — feeds translateCitizenId's IdI.local_name).
impl<'s, 'i, R> From<ICitizenNameI<'s, 'i, R>> for INameI<'s, 'i, R> where 's: 'i {
    fn from(name: ICitizenNameI<'s, 'i, R>) -> Self {
        match name {
            ICitizenNameI::StaticSizedArray(x) => INameI::StaticSizedArray(x),
            ICitizenNameI::RuntimeSizedArray(x) => INameI::RuntimeSizedArray(x),
            ICitizenNameI::Struct(x) => INameI::StructName(x),
            ICitizenNameI::Interface(x) => INameI::InterfaceName(x),
            ICitizenNameI::LambdaCitizen(x) => INameI::LambdaCitizen(x),
            ICitizenNameI::AnonymousSubstruct(x) => INameI::AnonymousSubstruct(x),
        }
    }
}
/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IStructNameI<'s, 'i, R> {
    Struct(&'i StructNameI<'s, 'i, R>),
    LambdaCitizen(&'i LambdaCitizenNameI<'s, R>),
    AnonymousSubstruct(&'i AnonymousSubstructNameI<'s, 'i, R>),
}

impl<'s, 'i, R> IStructNameI<'s, 'i, R> where 's: 'i {
    pub fn template_args(&self) -> &'i [ITemplataI<'s, 'i, R>] {
        match self {
            IStructNameI::Struct(x) => x.template_args,
            IStructNameI::LambdaCitizen(_) => &[],
            IStructNameI::AnonymousSubstruct(x) => x.template_args,
        }
    }
}
impl<'s, 'i, R> IStructNameI<'s, 'i, R> where 's: 'i, R: Copy {
    pub fn template(&self) -> IStructTemplateNameI<'s, 'i, R> {
        match self {
            IStructNameI::Struct(x) => x.template,
            IStructNameI::LambdaCitizen(x) => IStructTemplateNameI::LambdaCitizenTemplate(&x.template),
            IStructNameI::AnonymousSubstruct(x) => IStructTemplateNameI::AnonymousSubstructTemplate(&x.template),
        }
    }
}
// Rust-only narrowing INameI -> IStructNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for IStructNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::StructName(x) => Ok(IStructNameI::Struct(x)),
            INameI::LambdaCitizen(x) => Ok(IStructNameI::LambdaCitizen(x)),
            INameI::AnonymousSubstruct(x) => Ok(IStructNameI::AnonymousSubstruct(x)),
            _ => Err(()),
        }
    }
}
// Rust-only widening IStructNameI -> INameI (mirrors Scala subtyping; reverse of the TryFrom above,
// same family as the IFunctionNameI widening — feeds translateStructId's IdI.local_name).
impl<'s, 'i, R> From<IStructNameI<'s, 'i, R>> for INameI<'s, 'i, R> where 's: 'i {
    fn from(name: IStructNameI<'s, 'i, R>) -> Self {
        match name {
            IStructNameI::Struct(x) => INameI::StructName(x),
            IStructNameI::LambdaCitizen(x) => INameI::LambdaCitizen(x),
            IStructNameI::AnonymousSubstruct(x) => INameI::AnonymousSubstruct(x),
        }
    }
}
// Rust-only widening IStructNameI -> ICitizenNameI (mirrors Scala `IStructNameI extends ICitizenNameI`
// subtyping; same shape as the INameI widening just above).
impl<'s, 'i, R> From<IStructNameI<'s, 'i, R>> for ICitizenNameI<'s, 'i, R> where 's: 'i {
    fn from(name: IStructNameI<'s, 'i, R>) -> Self {
        match name {
            IStructNameI::Struct(x) => ICitizenNameI::Struct(x),
            IStructNameI::LambdaCitizen(x) => ICitizenNameI::LambdaCitizen(x),
            IStructNameI::AnonymousSubstruct(x) => ICitizenNameI::AnonymousSubstruct(x),
        }
    }
}

// Rust-only widening IInterfaceNameI -> ICitizenNameI (mirrors Scala `IInterfaceNameI extends
// ICitizenNameI` subtyping; same family as the IStructNameI widening just above).
impl<'s, 'i, R> From<IInterfaceNameI<'s, 'i, R>> for ICitizenNameI<'s, 'i, R> where 's: 'i {
    fn from(name: IInterfaceNameI<'s, 'i, R>) -> Self {
        match name {
            IInterfaceNameI::Interface(x) => ICitizenNameI::Interface(x),
        }
    }
}

// Rust-only widening IStructTemplateNameI -> INameI (mirrors Scala `IStructTemplateNameI extends
// ITemplateNameI extends INameI`; needed so humanize_name can recurse on a struct name's `template`
// without inline matching at the call site).
impl<'s, 'i, R> From<IStructTemplateNameI<'s, 'i, R>> for INameI<'s, 'i, R> where 's: 'i {
    fn from(name: IStructTemplateNameI<'s, 'i, R>) -> Self {
        match name {
            IStructTemplateNameI::StructTemplate(x) => INameI::StructTemplate(x),
            IStructTemplateNameI::AnonymousSubstructTemplate(x) => INameI::AnonymousSubstructTemplate(x),
            IStructTemplateNameI::LambdaCitizenTemplate(x) => INameI::LambdaCitizenTemplate(x),
        }
    }
}

// Rust-only widening IInterfaceTemplateNameI -> INameI.
impl<'s, 'i, R> From<IInterfaceTemplateNameI<'s, 'i, R>> for INameI<'s, 'i, R> where 's: 'i {
    fn from(name: IInterfaceTemplateNameI<'s, 'i, R>) -> Self {
        match name {
            IInterfaceTemplateNameI::InterfaceTemplate(x) => INameI::InterfaceTemplate(x),
        }
    }
}

// Rust-only widening ICitizenTemplateNameI -> INameI.
impl<'s, 'i, R> From<ICitizenTemplateNameI<'s, 'i, R>> for INameI<'s, 'i, R> where 's: 'i {
    fn from(name: ICitizenTemplateNameI<'s, 'i, R>) -> Self {
        match name {
            ICitizenTemplateNameI::StaticSizedArrayTemplate(x) => INameI::StaticSizedArrayTemplate(x),
            ICitizenTemplateNameI::RuntimeSizedArrayTemplate(x) => INameI::RuntimeSizedArrayTemplate(x),
            ICitizenTemplateNameI::LambdaCitizenTemplate(x) => INameI::LambdaCitizenTemplate(x),
            ICitizenTemplateNameI::StructTemplate(x) => INameI::StructTemplate(x),
            ICitizenTemplateNameI::InterfaceTemplate(x) => INameI::InterfaceTemplate(x),
            ICitizenTemplateNameI::AnonymousSubstructTemplate(x) => INameI::AnonymousSubstructTemplate(x),
        }
    }
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IInterfaceNameI<'s, 'i, R> {
    Interface(&'i InterfaceNameI<'s, 'i, R>),
}

impl<'s, 'i, R> IInterfaceNameI<'s, 'i, R> where 's: 'i {
    pub fn template_args(&self) -> &'i [ITemplataI<'s, 'i, R>] {
        match self {
            IInterfaceNameI::Interface(x) => x.template_args,
        }
    }
    pub fn template(&self) -> &'i InterfaceTemplateNameI<'s, R> {
        match self {
            IInterfaceNameI::Interface(x) => match x.template {
                IInterfaceTemplateNameI::InterfaceTemplate(t) => t,
            },
        }
    }
}
// Rust-only narrowing INameI -> IInterfaceNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for IInterfaceNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::InterfaceName(x) => Ok(IInterfaceNameI::Interface(x)),
            _ => Err(()),
        }
    }
}
// Rust-only widening IInterfaceNameI -> INameI (mirrors Scala subtyping; reverse of the TryFrom above,
// same family as the IFunctionNameI widening — feeds translateInterfaceId's IdI.local_name).
impl<'s, 'i, R> From<IInterfaceNameI<'s, 'i, R>> for INameI<'s, 'i, R> where 's: 'i {
    fn from(name: IInterfaceNameI<'s, 'i, R>) -> Self {
        match name {
            IInterfaceNameI::Interface(x) => INameI::InterfaceName(x),
        }
    }
}
/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IImplTemplateNameI<'s, 'i, R> {
    ImplTemplate(&'i ImplTemplateNameI<'s, R>),
    ImplBoundTemplate(&'i ImplBoundTemplateNameI<'s, R>),
    AnonymousSubstructImplTemplate(&'i AnonymousSubstructImplTemplateNameI<'s, 'i, R>),
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IImplNameI<'s, 'i, R> {
    Impl(&'i ImplNameI<'s, 'i, R>),
    ImplBound(&'i ImplBoundNameI<'s, 'i, R>),
    AnonymousSubstructImpl(&'i AnonymousSubstructImplNameI<'s, 'i, R>),
}

impl<'s, 'i, R> IImplNameI<'s, 'i, R> where 's: 'i {
    pub fn template_args(&self) -> &'i [ITemplataI<'s, 'i, R>] {
        match self {
            IImplNameI::Impl(x) => x.template_args,
            IImplNameI::ImplBound(x) => x.template_args,
            IImplNameI::AnonymousSubstructImpl(x) => x.template_args,
        }
    }
}
impl<'s, 'i, R> IImplNameI<'s, 'i, R> where 's: 'i, R: Copy {
    pub fn template(&self) -> IImplTemplateNameI<'s, 'i, R> {
        match self {
            IImplNameI::Impl(x) => x.template,
            IImplNameI::ImplBound(x) => IImplTemplateNameI::ImplBoundTemplate(&x.template),
            IImplNameI::AnonymousSubstructImpl(x) => IImplTemplateNameI::AnonymousSubstructImplTemplate(&x.template),
        }
    }
}
// Rust-only narrowing INameI -> IImplNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for IImplNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::Impl(x) => Ok(IImplNameI::Impl(x)),
            INameI::ImplBound(x) => Ok(IImplNameI::ImplBound(x)),
            INameI::AnonymousSubstructImpl(x) => Ok(IImplNameI::AnonymousSubstructImpl(x)),
            _ => Err(()),
        }
    }
}
// Rust-only widening IImplNameI -> INameI (mirrors Scala subtyping; reverse of the TryFrom above,
// same family as the IFunctionNameI widening — feeds translateImplId's IdI.local_name).
impl<'s, 'i, R> From<IImplNameI<'s, 'i, R>> for INameI<'s, 'i, R> where 's: 'i {
    fn from(name: IImplNameI<'s, 'i, R>) -> Self {
        match name {
            IImplNameI::Impl(x) => INameI::Impl(x),
            IImplNameI::ImplBound(x) => INameI::ImplBound(x),
            IImplNameI::AnonymousSubstructImpl(x) => INameI::AnonymousSubstructImpl(x),
        }
    }
}
/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IRegionNameI<'s, 'i, R> {
    _Phantom(PhantomData<(&'s (), &'i (), R)>),
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct RegionNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub rune: IRuneS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct DenizenDefaultRegionNameI<R>(pub PhantomData<R>);

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ExportTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_loc: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ExportNameI<'s, R> {
    pub template: ExportTemplateNameI<'s, R>,
    pub region: RegionTemplataI<R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ExternTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_loc: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ExternNameI<'s, R> {
    pub template: ExternTemplateNameI<'s, R>,
    pub region: RegionTemplataI<R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ImplTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_location_s: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ImplNameI<'s, 'i, R> {
    pub template: IImplTemplateNameI<'s, 'i, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub sub_citizen: ICitizenIT<'s, 'i, R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ImplBoundTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_location_s: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ImplBoundNameI<'s, 'i, R> {
    pub template: ImplBoundTemplateNameI<'s, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct LetNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_location: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ExportAsNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_location: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct RawArrayNameI<'s, 'i, R> {
    pub mutability: MutabilityI,
    pub element_type: CoordTemplataI<'s, 'i, R>,
    pub self_region: RegionTemplataI<R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ReachablePrototypeNameI<R> {
    pub _marker: PhantomData<R>,
    pub num: i32,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct StaticSizedArrayTemplateNameI<R>(pub PhantomData<R>);

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct StaticSizedArrayNameI<'s, 'i, R> {
    pub template: StaticSizedArrayTemplateNameI<R>,
    pub size: i64,
    pub variability: VariabilityI,
    pub arr: RawArrayNameI<'s, 'i, R>,
}

// (was cfg-gated)
impl<'s, 'i, R> StaticSizedArrayNameI<'s, 'i, R> {
    pub fn template_args(&self) -> &'i[ITemplataI<'s, 'i, R>] { panic!("Unimplemented: template_args"); }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct RuntimeSizedArrayTemplateNameI<R>(pub PhantomData<R>);

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct RuntimeSizedArrayNameI<'s, 'i, R> {
    pub template: RuntimeSizedArrayTemplateNameI<R>,
    pub arr: RawArrayNameI<'s, 'i, R>,
}

// (was cfg-gated)
impl<'s, 'i, R> RuntimeSizedArrayNameI<'s, 'i, R> {
    pub fn template_args(&self) -> &'i[ITemplataI<'s, 'i, R>] { panic!("Unimplemented: template_args"); }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct OverrideDispatcherTemplateNameI<'s, 'i, R> {
    pub impl_id: IdI<'s, 'i, R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct OverrideDispatcherNameI<'s, 'i, R> {
    pub template: OverrideDispatcherTemplateNameI<'s, 'i, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub parameters: &'i[CoordI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct OverrideDispatcherCaseNameI<'s, 'i, R> {
    pub independent_impl_template_args: &'i[ITemplataI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct CaseFunctionFromImplNameI<'s, 'i, R> {
    pub template: CaseFunctionFromImplTemplateNameI<'s, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub parameters: &'i[CoordI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct CaseFunctionFromImplTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub human_name: StrI<'s>,
    pub rune_in_impl: IRuneS<'s>,
    pub rune_in_citizen: IRuneS<'s>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum IVarNameI<'s, 'i, R> {
    TypingPassBlockResultVar(&'i TypingPassBlockResultVarNameI<'i, R>),
    TypingPassFunctionResultVar(&'i TypingPassFunctionResultVarNameI<R>),
    TypingPassTemporaryVar(&'i TypingPassTemporaryVarNameI<'i, R>),
    TypingPassPatternMember(&'i TypingPassPatternMemberNameI<'i, R>),
    TypingIgnoredParam(&'i TypingIgnoredParamNameI<R>),
    TypingPassPatternDestructuree(&'i TypingPassPatternDestructureeNameI<'i, R>),
    UnnamedLocal(&'i UnnamedLocalNameI<'s, R>),
    ClosureParam(&'i ClosureParamNameI<'s, R>),
    ConstructingMember(&'i ConstructingMemberNameI<'s, R>),
    WhileCondResult(&'i WhileCondResultNameI<'s, R>),
    Iterable(&'i IterableNameI<'s, R>),
    Iterator(&'i IteratorNameI<'s, R>),
    IterationOption(&'i IterationOptionNameI<'s, R>),
    MagicParam(&'i MagicParamNameI<'s, R>),
    CodeVar(&'i CodeVarNameI<'s, R>),
    AnonymousSubstructMember(&'i AnonymousSubstructMemberNameI<R>),
    Self_(&'i SelfNameI<R>),
}

// Rust-only narrowing INameI -> IVarNameI (mirrors T-side).
impl<'s, 'i, R> TryFrom<INameI<'s, 'i, R>> for IVarNameI<'s, 'i, R> where 's: 'i {
    type Error = ();
    fn try_from(name: INameI<'s, 'i, R>) -> Result<Self, ()> {
        match name {
            INameI::TypingPassBlockResultVar(x) => Ok(IVarNameI::TypingPassBlockResultVar(x)),
            INameI::TypingPassFunctionResultVar(x) => Ok(IVarNameI::TypingPassFunctionResultVar(x)),
            INameI::TypingPassTemporaryVar(x) => Ok(IVarNameI::TypingPassTemporaryVar(x)),
            INameI::TypingPassPatternMember(x) => Ok(IVarNameI::TypingPassPatternMember(x)),
            INameI::TypingIgnoredParam(x) => Ok(IVarNameI::TypingIgnoredParam(x)),
            INameI::TypingPassPatternDestructuree(x) => Ok(IVarNameI::TypingPassPatternDestructuree(x)),
            INameI::UnnamedLocal(x) => Ok(IVarNameI::UnnamedLocal(x)),
            INameI::ClosureParam(x) => Ok(IVarNameI::ClosureParam(x)),
            INameI::ConstructingMember(x) => Ok(IVarNameI::ConstructingMember(x)),
            INameI::WhileCondResult(x) => Ok(IVarNameI::WhileCondResult(x)),
            INameI::Iterable(x) => Ok(IVarNameI::Iterable(x)),
            INameI::Iterator(x) => Ok(IVarNameI::Iterator(x)),
            INameI::IterationOption(x) => Ok(IVarNameI::IterationOption(x)),
            INameI::MagicParam(x) => Ok(IVarNameI::MagicParam(x)),
            INameI::CodeVar(x) => Ok(IVarNameI::CodeVar(x)),
            INameI::AnonymousSubstructMember(x) => Ok(IVarNameI::AnonymousSubstructMember(x)),
            INameI::Self_(x) => Ok(IVarNameI::Self_(x)),
            _ => Err(()),
        }
    }
}
// Rust-only widening IVarNameI -> INameI (mirrors Scala `IVarNameI extends INameI` subtyping;
// reverse of the TryFrom above, same shape as the From<IFunctionNameI> widening).
impl<'s, 'i, R> From<IVarNameI<'s, 'i, R>> for INameI<'s, 'i, R> where 's: 'i {
    fn from(name: IVarNameI<'s, 'i, R>) -> Self {
        match name {
            IVarNameI::TypingPassBlockResultVar(x) => INameI::TypingPassBlockResultVar(x),
            IVarNameI::TypingPassFunctionResultVar(x) => INameI::TypingPassFunctionResultVar(x),
            IVarNameI::TypingPassTemporaryVar(x) => INameI::TypingPassTemporaryVar(x),
            IVarNameI::TypingPassPatternMember(x) => INameI::TypingPassPatternMember(x),
            IVarNameI::TypingIgnoredParam(x) => INameI::TypingIgnoredParam(x),
            IVarNameI::TypingPassPatternDestructuree(x) => INameI::TypingPassPatternDestructuree(x),
            IVarNameI::UnnamedLocal(x) => INameI::UnnamedLocal(x),
            IVarNameI::ClosureParam(x) => INameI::ClosureParam(x),
            IVarNameI::ConstructingMember(x) => INameI::ConstructingMember(x),
            IVarNameI::WhileCondResult(x) => INameI::WhileCondResult(x),
            IVarNameI::Iterable(x) => INameI::Iterable(x),
            IVarNameI::Iterator(x) => INameI::Iterator(x),
            IVarNameI::IterationOption(x) => INameI::IterationOption(x),
            IVarNameI::MagicParam(x) => INameI::MagicParam(x),
            IVarNameI::CodeVar(x) => INameI::CodeVar(x),
            IVarNameI::AnonymousSubstructMember(x) => INameI::AnonymousSubstructMember(x),
            IVarNameI::Self_(x) => INameI::Self_(x),
        }
    }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct TypingPassBlockResultVarNameI<'i, R> {
    pub _marker: PhantomData<R>,
    pub life: LocationInFunctionEnvironmentI<'i>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct TypingPassFunctionResultVarNameI<R>(pub PhantomData<R>);

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct TypingPassTemporaryVarNameI<'i, R> {
    pub _marker: PhantomData<R>,
    pub life: LocationInFunctionEnvironmentI<'i>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct TypingPassPatternMemberNameI<'i, R> {
    pub _marker: PhantomData<R>,
    pub life: LocationInFunctionEnvironmentI<'i>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct TypingIgnoredParamNameI<R> {
    pub _marker: PhantomData<R>,
    pub num: i32,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct TypingPassPatternDestructureeNameI<'i, R> {
    pub _marker: PhantomData<R>,
    pub life: LocationInFunctionEnvironmentI<'i>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct UnnamedLocalNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_location: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ClosureParamNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_location: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ConstructingMemberNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub name: StrI<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct WhileCondResultNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub range: RangeS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct IterableNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub range: RangeS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct IteratorNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub range: RangeS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct IterationOptionNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub range: RangeS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct MagicParamNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_location_2: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct CodeVarNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub name: StrI<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct AnonymousSubstructMemberNameI<R> {
    pub _marker: PhantomData<R>,
    pub index: i32,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct PrimitiveNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub human_name: StrI<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct PackageTopLevelNameI<R>(pub PhantomData<R>);

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ProjectNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub name: StrI<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct PackageNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub name: StrI<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct RuneNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub rune: IRuneS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct BuildingFunctionNameWithClosuredsI<'s, 'i, R> {
    pub template_name: IFunctionTemplateNameI<'s, 'i, R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ExternFunctionNameI<'s, 'i, R> {
    pub human_name: StrI<'s>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub parameters: &'i[CoordI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct FunctionNameIX<'s, 'i, R> {
    pub template: FunctionTemplateNameI<'s, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub parameters: &'i[CoordI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ForwarderFunctionNameI<'s, 'i, R> {
    pub template: ForwarderFunctionTemplateNameI<'s, 'i, R>,
    pub inner: IFunctionNameI<'s, 'i, R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct FunctionBoundTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub human_name: StrI<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct FunctionBoundNameI<'s, 'i, R> {
    pub template: FunctionBoundTemplateNameI<'s, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub parameters: &'i[CoordI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ReachableFunctionTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub human_name: StrI<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ReachableFunctionNameI<'s, 'i, R> {
    pub template: ReachableFunctionTemplateNameI<'s, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub parameters: &'i[CoordI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct FunctionTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub human_name: StrI<'s>,
    pub code_location: CodeLocationS<'s>,
}

// Per @LAGTNGZ, paramTypes stays baked in (specialization happened earlier).
/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct LambdaCallFunctionTemplateNameI<'s, 'i, R> {
    pub _marker: PhantomData<R>,
    pub code_location: CodeLocationS<'s>,
    pub param_types: &'i[CoordI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct LambdaCallFunctionNameI<'s, 'i, R> {
    pub template: LambdaCallFunctionTemplateNameI<'s, 'i, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub parameters: &'i[CoordI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ForwarderFunctionTemplateNameI<'s, 'i, R> {
    pub inner: IFunctionTemplateNameI<'s, 'i, R>,
    pub index: i32,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ConstructorTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_location: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct SelfNameI<R>(pub PhantomData<R>);

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ArbitraryNameI<R>(pub PhantomData<R>);

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum CitizenNameI<'s, 'i, R> {
    _Phantom(PhantomData<(&'s (), &'i (), R)>),
}

// (Realized via `impl TryFrom<Wide> for Narrow` or inline match.)

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct StructNameI<'s, 'i, R> {
    pub template: IStructTemplateNameI<'s, 'i, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct InterfaceNameI<'s, 'i, R> {
    pub template: IInterfaceTemplateNameI<'s, 'i, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
}

// Per @LAGTNGZ, closure struct isn't parameterized; one struct corresponds to many LambdaCallFunctionNameIs.
/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct LambdaCitizenTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub code_location: CodeLocationS<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct LambdaCitizenNameI<'s, R> {
    pub template: LambdaCitizenTemplateNameI<'s, R>,
}

/// Polyvalue
#[derive(PartialEq, Eq, Hash, Clone, Copy, Debug)]
// (was cfg-gated)
pub enum CitizenTemplateNameI<'s, 'i, R> {
    _Phantom(PhantomData<(&'s (), &'i (), R)>),
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct StructTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub human_name: StrI<'s>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct InterfaceTemplateNameI<'s, R> {
    pub _marker: PhantomData<R>,
    pub human_namee: StrI<'s>,
}

// (was cfg-gated)
impl<'s, R> InterfaceTemplateNameI<'s, R> {
    pub fn human_name(&self) -> StrI<'s> { panic!("Unimplemented: human_name"); }
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct AnonymousSubstructImplTemplateNameI<'s, 'i, R> {
    pub interface: IInterfaceTemplateNameI<'s, 'i, R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct AnonymousSubstructImplNameI<'s, 'i, R> {
    pub template: AnonymousSubstructImplTemplateNameI<'s, 'i, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub sub_citizen: ICitizenIT<'s, 'i, R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct AnonymousSubstructTemplateNameI<'s, 'i, R> {
    pub interface: IInterfaceTemplateNameI<'s, 'i, R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct AnonymousSubstructConstructorTemplateNameI<'s, 'i, R> {
    pub substruct: ICitizenTemplateNameI<'s, 'i, R>,
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct AnonymousSubstructConstructorNameI<'s, 'i, R> {
    pub template: AnonymousSubstructConstructorTemplateNameI<'s, 'i, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
    pub parameters: &'i[CoordI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct AnonymousSubstructNameI<'s, 'i, R> {
    pub template: AnonymousSubstructTemplateNameI<'s, 'i, R>,
    pub template_args: &'i[ITemplataI<'s, 'i, R>],
}

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct ResolvingEnvNameI<R>(pub PhantomData<R>);

/// Temporary state
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
// (was cfg-gated)
pub struct CallEnvNameI<R>(pub PhantomData<R>);

