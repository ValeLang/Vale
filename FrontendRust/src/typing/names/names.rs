
use std::hash::{Hash, Hasher};
use crate::interner::StrI;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::range::{CodeLocationS, RangeS};
use crate::postparsing::names::IRuneS;
use crate::typing::types::types::{CoordT, IRegionT, RegionT, ICitizenTT};
use crate::typing::templata::templata::{ITemplataT, expect_mutability, expect_variability, expect_integer, expect_coord_templata};
use crate::typing::ast::ast::LocationInFunctionEnvironmentT;
use crate::typing::typing_interner::{MustIntern, TypingInterner};
use crate::Keywords;
use INameValT::*;
use std::marker::PhantomData;
use std::ptr::eq;
use std::ptr::hash;

// Monomorphic per `docs/reasoning/idt-typed-view-alternatives.md`. Scala's
// `IdT[+T <: INameT]` phantom outer parameter is erased in Rust — callers
// pattern-match on `local_name` at the point they need narrowing.
/// Interned (see @TFITCX)
#[derive(Copy, Clone, Debug)]
pub struct IdT<'s, 't>
where 's: 't,
{
    pub package_coord: &'s PackageCoordinate<'s>,
    pub init_steps: &'t [INameT<'s, 't>],
    pub local_name: INameT<'s, 't>,
    pub _must_intern: MustIntern,
}

impl<'s, 't> IdT<'s, 't> {
    pub fn new() -> Self {
        panic!("Unimplemented IdT new");
    }
    
    fn package_id() {
        panic!("Unimplemented IdT package ID");
    }
    
    pub fn init_id(&self, interner: &TypingInterner<'s, 't>) -> IdT<'s, 't> {
        if self.init_steps.is_empty() {
            let top_level = interner.alloc(PackageTopLevelNameT { });
            *interner.intern_id(IdValT {
                package_coord: self.package_coord,
                init_steps: &[],
                local_name: INameT::PackageTopLevel(top_level),
            })
        } else {
            let last = *self.init_steps.last().unwrap();
            let prefix = &self.init_steps[..self.init_steps.len() - 1];
            *interner.intern_id(IdValT {
                package_coord: self.package_coord,
                init_steps: prefix,
                local_name: last,
            })
        }
    }
    
    pub fn init_non_package_id(&self, interner: &TypingInterner<'s, 't>) -> Option<IdT<'s, 't>> {
        if self.init_steps.is_empty() {
            None
        } else {
            let (last, init) = self.init_steps.split_last().unwrap();
            Some(*interner.intern_id(IdValT {
                package_coord: self.package_coord,
                init_steps: init,
                local_name: *last,
            }))
        }
    }
    
    pub fn steps(&self) -> Vec<INameT<'s, 't>> {
        match self.local_name {
            INameT::PackageTopLevel(_) => self.init_steps.to_vec(),
            _ => {
                let mut v = self.init_steps.to_vec();
                v.push(self.local_name);
                v
            }
        }
    }
    
    pub fn add_step(&self, interner: &TypingInterner<'s, 't>, new_last: INameT<'s, 't>) -> &'t IdT<'s, 't> {
        let steps = self.steps();
        interner.intern_id(IdValT {
            package_coord: self.package_coord,
            init_steps: &steps,
            local_name: new_last,
        })
    }
    
}

// (no scala counterpart — custom Hash/PartialEq/Eq: pointer-eq on package_coord
// and init_steps slice (canonicalized by the typing interner per IDEPFL),
// structural compare on local_name (inline-owned INameT).)
impl<'s, 't> Hash for IdT<'s, 't>
where 's: 't,
{
    fn hash<H: Hasher>(&self, state: &mut H) {
        hash(self.package_coord, state);
        hash(self.init_steps.as_ptr(), state);
        self.init_steps.len().hash(state);
        self.local_name.hash(state);
    }
}
// Per @IEOIBZ, identity-equality on the canonical slice pointer. Soundness
// requires `init_steps` to come from the canonical arena allocation in
// `intern_id` — guaranteed by sealing per @SICZ.
impl<'s, 't> PartialEq for IdT<'s, 't>
where 's: 't,
{
    fn eq(&self, other: &Self) -> bool {
        eq(self.package_coord, other.package_coord)
            && eq(self.init_steps.as_ptr(), other.init_steps.as_ptr())
            && self.init_steps.len() == other.init_steps.len()
            && self.local_name == other.local_name
    }
}
impl<'s, 't> Eq for IdT<'s, 't> where 's: 't, {}
// Widen/narrow conversion methods removed with the move to monomorphic IdT.
// Callers that need a specific leaf-name pattern-match on `local_name` directly,
// like Scala does. See `docs/reasoning/idt-typed-view-alternatives.md`.

/// Polyvalue (see @TFITCX) — derive Eq/Hash; never hand-roll `ptr::eq` on the outer `&self` (see @PVECFPZ).
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum INameT<'s, 't> {
    ExportTemplate(&'t ExportTemplateNameT<'s>),
    Export(&'t ExportNameT<'s, 't>),
    ImplTemplate(&'t ImplTemplateNameT<'s>),
    Impl(&'t ImplNameT<'s, 't>),
    ImplBoundTemplate(&'t ImplBoundTemplateNameT<'s>),
    ImplBound(&'t ImplBoundNameT<'s, 't>),
    Let(&'t LetNameT<'s>),
    ExportAs(&'t ExportAsNameT<'s>),
    RawArray(&'t RawArrayNameT<'s, 't>),
    ReachablePrototype(&'t ReachablePrototypeNameT),
    StaticSizedArrayTemplate(&'t StaticSizedArrayTemplateNameT),
    StaticSizedArray(&'t StaticSizedArrayNameT<'s, 't>),
    RuntimeSizedArrayTemplate(&'t RuntimeSizedArrayTemplateNameT),
    RuntimeSizedArray(&'t RuntimeSizedArrayNameT<'s, 't>),
    KindPlaceholderTemplate(&'t KindPlaceholderTemplateNameT<'s>),
    KindPlaceholder(&'t KindPlaceholderNameT<'s, 't>),
    NonKindNonRegionPlaceholder(&'t NonKindNonRegionPlaceholderNameT<'s>),
    OverrideDispatcherTemplate(&'t OverrideDispatcherTemplateNameT<'s, 't>),
    OverrideDispatcher(&'t OverrideDispatcherNameT<'s, 't>),
    OverrideDispatcherCase(&'t OverrideDispatcherCaseNameT<'s, 't>),
    TypingPassBlockResultVar(&'t TypingPassBlockResultVarNameT<'t>),
    TypingPassFunctionResultVar(&'t TypingPassFunctionResultVarNameT),
    TypingPassTemporaryVar(&'t TypingPassTemporaryVarNameT<'t>),
    TypingPassPatternMember(&'t TypingPassPatternMemberNameT<'t>),
    TypingIgnoredParam(&'t TypingIgnoredParamNameT),
    TypingPassPatternDestructuree(&'t TypingPassPatternDestructureeNameT<'t>),
    UnnamedLocal(&'t UnnamedLocalNameT<'s>),
    ClosureParam(&'t ClosureParamNameT<'s>),
    ConstructingMember(&'t ConstructingMemberNameT<'s>),
    WhileCondResult(&'t WhileCondResultNameT<'s>),
    Iterable(&'t IterableNameT<'s>),
    Iterator(&'t IteratorNameT<'s>),
    IterationOption(&'t IterationOptionNameT<'s>),
    MagicParam(&'t MagicParamNameT<'s>),
    CodeVar(&'t CodeVarNameT<'s>),
    AnonymousSubstructMember(&'t AnonymousSubstructMemberNameT),
    Primitive(&'t PrimitiveNameT<'s>),
    PackageTopLevel(&'t PackageTopLevelNameT),
    Project(&'t ProjectNameT<'s>),
    Package(&'t PackageNameT<'s>),
    Rune(&'t RuneNameT<'s>),
    BuildingFunctionNameWithClosureds(&'t BuildingFunctionNameWithClosuredsT<'s, 't>),
    ExternTemplate(&'t ExternTemplateNameT<'s>),
    Extern(&'t ExternNameT<'s, 't>),
    ExternFunction(&'t ExternFunctionNameT<'s, 't>),
    Function(&'t FunctionNameT<'s, 't>),
    ForwarderFunction(&'t ForwarderFunctionNameT<'s, 't>),
    FunctionBoundTemplate(&'t FunctionBoundTemplateNameT<'s>),
    FunctionBound(&'t FunctionBoundNameT<'s, 't>),
    PredictedFunctionTemplate(&'t PredictedFunctionTemplateNameT<'s>),
    PredictedFunction(&'t PredictedFunctionNameT<'s, 't>),
    FunctionTemplate(&'t FunctionTemplateNameT<'s>),
    LambdaCallFunctionTemplate(&'t LambdaCallFunctionTemplateNameT<'s, 't>),
    LambdaCallFunction(&'t LambdaCallFunctionNameT<'s, 't>),
    ForwarderFunctionTemplate(&'t ForwarderFunctionTemplateNameT<'s, 't>),
    ConstructorTemplate(&'t ConstructorTemplateNameT<'s>),
    Self_(&'t SelfNameT),
    Arbitrary(&'t ArbitraryNameT),
    Struct(&'t StructNameT<'s, 't>),
    Interface(&'t InterfaceNameT<'s, 't>),
    LambdaCitizenTemplate(&'t LambdaCitizenTemplateNameT<'s>),
    LambdaCitizen(&'t LambdaCitizenNameT<'s, 't>),
    StructTemplate(&'t StructTemplateNameT<'s>),
    InterfaceTemplate(&'t InterfaceTemplateNameT<'s>),
    AnonymousSubstructImplTemplate(&'t AnonymousSubstructImplTemplateNameT<'s, 't>),
    AnonymousSubstructImpl(&'t AnonymousSubstructImplNameT<'s, 't>),
    AnonymousSubstructTemplate(&'t AnonymousSubstructTemplateNameT<'s, 't>),
    AnonymousSubstructConstructorTemplate(&'t AnonymousSubstructConstructorTemplateNameT<'s, 't>),
    AnonymousSubstructConstructor(&'t AnonymousSubstructConstructorNameT<'s, 't>),
    AnonymousSubstruct(&'t AnonymousSubstructNameT<'s, 't>),
    ResolvingEnv(&'t ResolvingEnvNameT),
    CallEnv(&'t CallEnvNameT),
}

// (Rust adaptation: Scala expression `idT.localName.parameters` works
// because Scala types `localName` as `IFunctionNameT` via `IdT[IFunctionNameT]`'s
// type parameter. Rust's `IdT.local_name: INameT` loses that narrowing, so we
// expose `parameters()` on the broad enum and panic for non-function variants.
// Same shape as `PrototypeT::param_types` at ast.rs:1020.)
impl<'s, 't> INameT<'s, 't> where 's: 't {
    pub fn parameters(&self) -> &'t [CoordT<'s, 't>] {
        match self {
            INameT::OverrideDispatcher(f) => f.parameters,
            INameT::ExternFunction(f) => f.parameters,
            INameT::Function(f) => f.parameters,
            INameT::ForwarderFunction(f) => f.inner.parameters(),
            INameT::FunctionBound(f) => f.parameters,
            INameT::PredictedFunction(f) => f.parameters,
            INameT::LambdaCallFunction(f) => f.parameters,
            INameT::AnonymousSubstructConstructor(f) => f.parameters,
            other => panic!("INameT::parameters called on non-function name: {:?}", other),
        }
    }
    
}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ITemplateNameT<'s, 't> {
    ExportTemplate(&'t ExportTemplateNameT<'s>),
    ImplTemplate(&'t ImplTemplateNameT<'s>),
    ImplBoundTemplate(&'t ImplBoundTemplateNameT<'s>),
    StaticSizedArrayTemplate(&'t StaticSizedArrayTemplateNameT),
    RuntimeSizedArrayTemplate(&'t RuntimeSizedArrayTemplateNameT),
    KindPlaceholderTemplate(&'t KindPlaceholderTemplateNameT<'s>),
    OverrideDispatcherTemplate(&'t OverrideDispatcherTemplateNameT<'s, 't>),
    OverrideDispatcherCase(&'t OverrideDispatcherCaseNameT<'s, 't>),
    ExternTemplate(&'t ExternTemplateNameT<'s>),
    ExternFunction(&'t ExternFunctionNameT<'s, 't>),
    FunctionBoundTemplate(&'t FunctionBoundTemplateNameT<'s>),
    PredictedFunctionTemplate(&'t PredictedFunctionTemplateNameT<'s>),
    FunctionTemplate(&'t FunctionTemplateNameT<'s>),
    LambdaCallFunctionTemplate(&'t LambdaCallFunctionTemplateNameT<'s, 't>),
    ForwarderFunctionTemplate(&'t ForwarderFunctionTemplateNameT<'s, 't>),
    ConstructorTemplate(&'t ConstructorTemplateNameT<'s>),
    LambdaCitizenTemplate(&'t LambdaCitizenTemplateNameT<'s>),
    StructTemplate(&'t StructTemplateNameT<'s>),
    InterfaceTemplate(&'t InterfaceTemplateNameT<'s>),
    AnonymousSubstructImplTemplate(&'t AnonymousSubstructImplTemplateNameT<'s, 't>),
    AnonymousSubstructTemplate(&'t AnonymousSubstructTemplateNameT<'s, 't>),
    AnonymousSubstructConstructorTemplate(&'t AnonymousSubstructConstructorTemplateNameT<'s, 't>),
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IFunctionTemplateNameT<'s, 't> {
    OverrideDispatcherTemplate(&'t OverrideDispatcherTemplateNameT<'s, 't>),
    ExternFunction(&'t ExternFunctionNameT<'s, 't>),
    FunctionBoundTemplate(&'t FunctionBoundTemplateNameT<'s>),
    PredictedFunctionTemplate(&'t PredictedFunctionTemplateNameT<'s>),
    FunctionTemplate(&'t FunctionTemplateNameT<'s>),
    LambdaCallFunctionTemplate(&'t LambdaCallFunctionTemplateNameT<'s, 't>),
    ForwarderFunctionTemplate(&'t ForwarderFunctionTemplateNameT<'s, 't>),
    ConstructorTemplate(&'t ConstructorTemplateNameT<'s>),
    AnonymousSubstructConstructorTemplate(&'t AnonymousSubstructConstructorTemplateNameT<'s, 't>),
}

// Scala trait method: def makeFunctionName(...): IFunctionNameT
// Each variant overrides — see names.scala lines 265, 345, 376, 400, 415, 424, 441, 487, 666
impl<'s, 't> IFunctionTemplateNameT<'s, 't> where 's: 't {
  pub fn make_function_name(
    &self,
    interner: &TypingInterner<'s, 't>,
    _keywords: &Keywords<'_>,
    template_args: &[ITemplataT<'s, 't>],
    params: &[CoordT<'s, 't>],
  ) -> INameT<'s, 't> {
    match self {
      IFunctionTemplateNameT::FunctionTemplate(tmpl) => {
        interner.intern_name(INameValT::Function(FunctionNameValT {
          template: tmpl,
          template_args,
          parameters: params,
        }))
      }
      IFunctionTemplateNameT::OverrideDispatcherTemplate(tmpl) => {
        interner.intern_name(INameValT::OverrideDispatcher(OverrideDispatcherNameValT {
          template: tmpl,
          template_args,
          parameters: params,
        }))
      }
      IFunctionTemplateNameT::ExternFunction(e) => {
        INameT::ExternFunction(e)
      }
      IFunctionTemplateNameT::FunctionBoundTemplate(tmpl) => {
        interner.intern_name(INameValT::FunctionBound(FunctionBoundNameValT {
          template: tmpl,
          template_args,
          parameters: params,
        }))
      }
      IFunctionTemplateNameT::PredictedFunctionTemplate(tmpl) => {
        interner.intern_name(INameValT::PredictedFunction(PredictedFunctionNameValT {
          template: tmpl,
          template_args,
          parameters: params,
        }))
      }
      IFunctionTemplateNameT::LambdaCallFunctionTemplate(tmpl) => {
        interner.intern_name(INameValT::LambdaCallFunction(LambdaCallFunctionNameValT {
          template: tmpl,
          template_args,
          parameters: params,
        }))
      }
      IFunctionTemplateNameT::ForwarderFunctionTemplate(tmpl) => {
        let inner_name = tmpl.inner.make_function_name(interner, _keywords, template_args, params);
        let inner_func_name: IFunctionNameT<'s, 't> = inner_name.try_into()
            .unwrap_or_else(|_| panic!("ForwarderFunctionTemplate inner should produce a function name"));
        interner.intern_name(INameValT::ForwarderFunction(ForwarderFunctionNameT {
          template: tmpl,
          inner: inner_func_name,
        }))
      }
      IFunctionTemplateNameT::ConstructorTemplate(_) => {
        panic!("Unimplemented: make_function_name for ConstructorTemplate")
      }
      IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(tmpl) => {
        interner.intern_name(INameValT::AnonymousSubstructConstructor(AnonymousSubstructConstructorNameValT {
          template: tmpl,
          template_args,
          parameters: params,
        }))
      }
    }
  }
  
// Proactive dispatch method (TL.md "Proactively Add Inherited Dispatch
// Methods"): Scala accesses `template.humanName` on IFunctionTemplateNameT
// at InferCompiler.scala:314 and elsewhere. Most concrete subtypes carry a
// `humanName: StrI` field; the few that don't (OverrideDispatcher,
// LambdaCallFunction, Constructor, AnonymousSubstructConstructor) panic
// until a test path requires them.
  pub fn human_name(&self) -> StrI<'s> {
    match self {
      IFunctionTemplateNameT::FunctionTemplate(x) => x.human_name,
      IFunctionTemplateNameT::FunctionBoundTemplate(x) => x.human_name,
      IFunctionTemplateNameT::PredictedFunctionTemplate(x) => x.human_name,
      IFunctionTemplateNameT::ExternFunction(x) => x.human_name,
      IFunctionTemplateNameT::ForwarderFunctionTemplate(x) => x.inner.human_name(),
      IFunctionTemplateNameT::OverrideDispatcherTemplate(_) => panic!("Unimplemented: human_name on OverrideDispatcherTemplate (no humanName field in Scala)"),
      IFunctionTemplateNameT::LambdaCallFunctionTemplate(_) => panic!("Unimplemented: human_name on LambdaCallFunctionTemplate (no humanName field in Scala)"),
      IFunctionTemplateNameT::ConstructorTemplate(_) => panic!("Unimplemented: human_name on ConstructorTemplate (no humanName field in Scala)"),
      IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(_) => panic!("Unimplemented: human_name on AnonymousSubstructConstructor (no humanName field in Scala)"),
    }
  }
  
}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IInstantiationNameT<'s, 't> {
    Export(&'t ExportNameT<'s, 't>),
    Impl(&'t ImplNameT<'s, 't>),
    ImplBound(&'t ImplBoundNameT<'s, 't>),
    StaticSizedArray(&'t StaticSizedArrayNameT<'s, 't>),
    RuntimeSizedArray(&'t RuntimeSizedArrayNameT<'s, 't>),
    KindPlaceholder(&'t KindPlaceholderNameT<'s, 't>),
    OverrideDispatcher(&'t OverrideDispatcherNameT<'s, 't>),
    OverrideDispatcherCase(&'t OverrideDispatcherCaseNameT<'s, 't>),
    Extern(&'t ExternNameT<'s, 't>),
    ExternFunction(&'t ExternFunctionNameT<'s, 't>),
    Function(&'t FunctionNameT<'s, 't>),
    ForwarderFunction(&'t ForwarderFunctionNameT<'s, 't>),
    FunctionBound(&'t FunctionBoundNameT<'s, 't>),
    PredictedFunction(&'t PredictedFunctionNameT<'s, 't>),
    LambdaCallFunction(&'t LambdaCallFunctionNameT<'s, 't>),
    Struct(&'t StructNameT<'s, 't>),
    Interface(&'t InterfaceNameT<'s, 't>),
    LambdaCitizen(&'t LambdaCitizenNameT<'s, 't>),
    AnonymousSubstructImpl(&'t AnonymousSubstructImplNameT<'s, 't>),
    AnonymousSubstructConstructor(&'t AnonymousSubstructConstructorNameT<'s, 't>),
    AnonymousSubstruct(&'t AnonymousSubstructNameT<'s, 't>),
}

impl<'s, 't> IInstantiationNameT<'s, 't> where 's: 't {
    pub fn template(&self) -> ITemplateNameT<'s, 't> {
        match self {
            IInstantiationNameT::Export(x) => ITemplateNameT::ExportTemplate(x.template),
            IInstantiationNameT::Impl(x) => ITemplateNameT::ImplTemplate(x.template),
            IInstantiationNameT::ImplBound(x) => ITemplateNameT::ImplBoundTemplate(x.template),
            IInstantiationNameT::StaticSizedArray(x) => ITemplateNameT::StaticSizedArrayTemplate(x.template),
            IInstantiationNameT::RuntimeSizedArray(x) => ITemplateNameT::RuntimeSizedArrayTemplate(x.template),
            IInstantiationNameT::KindPlaceholder(x) => ITemplateNameT::KindPlaceholderTemplate(x.template),
            IInstantiationNameT::OverrideDispatcher(x) => ITemplateNameT::OverrideDispatcherTemplate(x.template),
            IInstantiationNameT::OverrideDispatcherCase(x) => ITemplateNameT::OverrideDispatcherCase(x),
            IInstantiationNameT::Extern(x) => ITemplateNameT::ExternTemplate(x.template),
            IInstantiationNameT::ExternFunction(x) => ITemplateNameT::ExternFunction(x),
            IInstantiationNameT::Function(x) => ITemplateNameT::FunctionTemplate(x.template),
            IInstantiationNameT::ForwarderFunction(x) => ITemplateNameT::ForwarderFunctionTemplate(x.template),
            IInstantiationNameT::FunctionBound(x) => ITemplateNameT::FunctionBoundTemplate(x.template),
            IInstantiationNameT::PredictedFunction(x) => ITemplateNameT::PredictedFunctionTemplate(x.template),
            IInstantiationNameT::LambdaCallFunction(x) => ITemplateNameT::LambdaCallFunctionTemplate(x.template),
            IInstantiationNameT::Struct(x) => match x.template {
                IStructTemplateNameT::StructTemplate(t) => ITemplateNameT::StructTemplate(t),
                IStructTemplateNameT::LambdaCitizenTemplate(t) => ITemplateNameT::LambdaCitizenTemplate(t),
                IStructTemplateNameT::AnonymousSubstructTemplate(t) => ITemplateNameT::AnonymousSubstructTemplate(t),
            },
            IInstantiationNameT::Interface(x) => ITemplateNameT::InterfaceTemplate(x.template),
            IInstantiationNameT::LambdaCitizen(x) => ITemplateNameT::LambdaCitizenTemplate(x.template),
            IInstantiationNameT::AnonymousSubstructImpl(x) => ITemplateNameT::AnonymousSubstructImplTemplate(x.template),
            IInstantiationNameT::AnonymousSubstructConstructor(x) => ITemplateNameT::AnonymousSubstructConstructorTemplate(x.template),
            IInstantiationNameT::AnonymousSubstruct(x) => ITemplateNameT::AnonymousSubstructTemplate(x.template),
        }
    }
    
    pub fn template_args(&self) -> &'t [ITemplataT<'s, 't>] {
        match self {
            IInstantiationNameT::Export(_) => &[],
            IInstantiationNameT::Impl(x) => x.template_args,
            IInstantiationNameT::ImplBound(x) => x.template_args,
            IInstantiationNameT::StaticSizedArray(_) => panic!("Unimplemented: template_args on StaticSizedArrayNameT (computed: Vector(size, arr.mutability, variability, CoordTemplataT(arr.elementType)) — needs interner to allocate slice)"),
            IInstantiationNameT::RuntimeSizedArray(_) => panic!("Unimplemented: template_args on RuntimeSizedArrayNameT (computed: Vector(arr.mutability, CoordTemplataT(arr.elementType)) — needs interner to allocate slice)"),
            IInstantiationNameT::KindPlaceholder(_) => &[],
            IInstantiationNameT::OverrideDispatcher(x) => x.template_args,
            IInstantiationNameT::OverrideDispatcherCase(x) => x.independent_impl_template_args,
            IInstantiationNameT::Extern(_) => &[],
            IInstantiationNameT::ExternFunction(x) => x.template_args,
            IInstantiationNameT::Function(x) => x.template_args,
            IInstantiationNameT::ForwarderFunction(x) => x.inner.template_args(),
            IInstantiationNameT::FunctionBound(x) => x.template_args,
            IInstantiationNameT::PredictedFunction(x) => x.template_args,
            IInstantiationNameT::LambdaCallFunction(x) => x.template_args,
            IInstantiationNameT::Struct(x) => x.template_args,
            IInstantiationNameT::Interface(x) => x.template_args,
            IInstantiationNameT::LambdaCitizen(_) => &[],
            IInstantiationNameT::AnonymousSubstructImpl(x) => x.template_args,
            IInstantiationNameT::AnonymousSubstructConstructor(x) => x.template_args,
            IInstantiationNameT::AnonymousSubstruct(x) => x.template_args,
        }
    }
    
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IFunctionNameT<'s, 't> {
    OverrideDispatcher(&'t OverrideDispatcherNameT<'s, 't>),
    ExternFunction(&'t ExternFunctionNameT<'s, 't>),
    Function(&'t FunctionNameT<'s, 't>),
    ForwarderFunction(&'t ForwarderFunctionNameT<'s, 't>),
    FunctionBound(&'t FunctionBoundNameT<'s, 't>),
    PredictedFunction(&'t PredictedFunctionNameT<'s, 't>),
    LambdaCallFunction(&'t LambdaCallFunctionNameT<'s, 't>),
    AnonymousSubstructConstructor(&'t AnonymousSubstructConstructorNameT<'s, 't>),
}

impl<'s, 't> IFunctionNameT<'s, 't> where 's: 't {
    pub fn template(&self) -> IFunctionTemplateNameT<'s, 't> {
        match self {
            IFunctionNameT::OverrideDispatcher(x) => IFunctionTemplateNameT::OverrideDispatcherTemplate(x.template),
            IFunctionNameT::ExternFunction(_) => panic!("Unimplemented: template on ExternFunctionNameT"),
            IFunctionNameT::Function(x) => IFunctionTemplateNameT::FunctionTemplate(x.template),
            IFunctionNameT::ForwarderFunction(x) => IFunctionTemplateNameT::ForwarderFunctionTemplate(x.template),
            IFunctionNameT::FunctionBound(x) => IFunctionTemplateNameT::FunctionBoundTemplate(x.template),
            IFunctionNameT::PredictedFunction(x) => IFunctionTemplateNameT::PredictedFunctionTemplate(x.template),
            IFunctionNameT::LambdaCallFunction(x) => IFunctionTemplateNameT::LambdaCallFunctionTemplate(x.template),
            IFunctionNameT::AnonymousSubstructConstructor(x) => IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(x.template),
        }
    }
    
    pub fn template_args(&self) -> &'t [ITemplataT<'s, 't>] {
        match self {
            IFunctionNameT::OverrideDispatcher(x) => x.template_args,
            IFunctionNameT::ExternFunction(_) => &[],
            IFunctionNameT::Function(x) => x.template_args,
            IFunctionNameT::ForwarderFunction(f) => f.inner.template_args(),
            IFunctionNameT::FunctionBound(x) => x.template_args,
            IFunctionNameT::PredictedFunction(x) => x.template_args,
            IFunctionNameT::LambdaCallFunction(x) => x.template_args,
            IFunctionNameT::AnonymousSubstructConstructor(x) => x.template_args,
        }
    }
    
    pub fn parameters(&self) -> &'t [CoordT<'s, 't>] {
        match self {
            IFunctionNameT::OverrideDispatcher(f) => f.parameters,
            IFunctionNameT::ExternFunction(f) => f.parameters,
            IFunctionNameT::Function(f) => f.parameters,
            IFunctionNameT::ForwarderFunction(f) => f.inner.parameters(),
            IFunctionNameT::FunctionBound(f) => f.parameters,
            IFunctionNameT::PredictedFunction(f) => f.parameters,
            IFunctionNameT::LambdaCallFunction(f) => f.parameters,
            IFunctionNameT::AnonymousSubstructConstructor(f) => f.parameters,
        }
    }
    
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ISuperKindTemplateNameT<'s, 't> {
    KindPlaceholderTemplate(&'t KindPlaceholderTemplateNameT<'s>),
    InterfaceTemplate(&'t InterfaceTemplateNameT<'s>),
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ISubKindTemplateNameT<'s, 't> {
    StaticSizedArrayTemplate(&'t StaticSizedArrayTemplateNameT),
    RuntimeSizedArrayTemplate(&'t RuntimeSizedArrayTemplateNameT),
    KindPlaceholderTemplate(&'t KindPlaceholderTemplateNameT<'s>),
    LambdaCitizenTemplate(&'t LambdaCitizenTemplateNameT<'s>),
    StructTemplate(&'t StructTemplateNameT<'s>),
    InterfaceTemplate(&'t InterfaceTemplateNameT<'s>),
    AnonymousSubstructTemplate(&'t AnonymousSubstructTemplateNameT<'s, 't>),
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ICitizenTemplateNameT<'s, 't> {
    StaticSizedArrayTemplate(&'t StaticSizedArrayTemplateNameT),
    RuntimeSizedArrayTemplate(&'t RuntimeSizedArrayTemplateNameT),
    LambdaCitizenTemplate(&'t LambdaCitizenTemplateNameT<'s>),
    StructTemplate(&'t StructTemplateNameT<'s>),
    InterfaceTemplate(&'t InterfaceTemplateNameT<'s>),
    AnonymousSubstructTemplate(&'t AnonymousSubstructTemplateNameT<'s, 't>),
}

impl<'s, 't> ICitizenTemplateNameT<'s, 't> {
    pub fn make_citizen_name(
        &self,
        interner: &TypingInterner<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
    ) -> INameT<'s, 't> {
        match self {
            ICitizenTemplateNameT::StaticSizedArrayTemplate(t) =>
                t.make_citizen_name(interner, template_args),
            ICitizenTemplateNameT::RuntimeSizedArrayTemplate(t) =>
                t.make_citizen_name(interner, template_args),
            ICitizenTemplateNameT::LambdaCitizenTemplate(tmpl) => {
                assert!(template_args.is_empty());
                interner.intern_name(INameValT::LambdaCitizen(LambdaCitizenNameT {
                  template: tmpl,
                }))
            }
            ICitizenTemplateNameT::StructTemplate(tmpl) => {
                interner.intern_name(INameValT::Struct(StructNameValT {
                  template: IStructTemplateNameT::StructTemplate(tmpl),
                  template_args,
                }))
            }
            ICitizenTemplateNameT::InterfaceTemplate(tmpl) => {
                interner.intern_name(INameValT::Interface(InterfaceNameValT {
                  template: tmpl,
                  template_args,
                }))
            }
            ICitizenTemplateNameT::AnonymousSubstructTemplate(tmpl) => {
                interner.intern_name(INameValT::AnonymousSubstruct(AnonymousSubstructNameValT {
                  template: tmpl,
                  template_args,
                }))
            }
        }
    }

}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IStructTemplateNameT<'s, 't> {
    LambdaCitizenTemplate(&'t LambdaCitizenTemplateNameT<'s>),
    StructTemplate(&'t StructTemplateNameT<'s>),
    AnonymousSubstructTemplate(&'t AnonymousSubstructTemplateNameT<'s, 't>),
}

// Scala trait method: def makeStructName(...): IStructNameT
// Overrides: LambdaCitizenTemplate (line 569), StructTemplate (line 618), AnonymousSubstructTemplate (line 659)
impl<'s, 't> IStructTemplateNameT<'s, 't> where 's: 't {
  pub fn make_struct_name(
    &self,
    interner: &TypingInterner<'s, 't>,
    template_args: &[ITemplataT<'s, 't>],
  ) -> INameT<'s, 't> {
    match self {
      IStructTemplateNameT::LambdaCitizenTemplate(tmpl) => {
        assert!(template_args.is_empty());
        interner.intern_name(INameValT::LambdaCitizen(LambdaCitizenNameT {
          template: tmpl,
        }))
      }
      IStructTemplateNameT::StructTemplate(tmpl) => {
        interner.intern_name(INameValT::Struct(StructNameValT {
          template: IStructTemplateNameT::StructTemplate(tmpl),
          template_args,
        }))
      }
      IStructTemplateNameT::AnonymousSubstructTemplate(tmpl) => {
        interner.intern_name(INameValT::AnonymousSubstruct(AnonymousSubstructNameValT {
          template: tmpl,
          template_args,
        }))
      }
    }
  }
  
}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IInterfaceTemplateNameT<'s, 't> {
    InterfaceTemplate(&'t InterfaceTemplateNameT<'s>),
}

// Scala trait method: def makeInterfaceName(...): IInterfaceNameT
// Override: InterfaceTemplate (line 632)
impl<'s, 't> IInterfaceTemplateNameT<'s, 't> where 's: 't {
  pub fn make_interface_name(
    &self,
    interner: &TypingInterner<'s, 't>,
    template_args: &[ITemplataT<'s, 't>],
  ) -> INameT<'s, 't> {
    match self {
      IInterfaceTemplateNameT::InterfaceTemplate(tmpl) => {
        interner.intern_name(INameValT::Interface(InterfaceNameValT {
          template: tmpl,
          template_args,
        }))
      }
    }
  }
  
}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ISuperKindNameT<'s, 't> {
    KindPlaceholder(&'t KindPlaceholderNameT<'s, 't>),
    Interface(&'t InterfaceNameT<'s, 't>),
}

impl<'s, 't> ISuperKindNameT<'s, 't> where 's: 't {
    pub fn template(&self) -> ISuperKindTemplateNameT<'s, 't> {
        match self {
            ISuperKindNameT::KindPlaceholder(x) => ISuperKindTemplateNameT::KindPlaceholderTemplate(x.template),
            ISuperKindNameT::Interface(x) => ISuperKindTemplateNameT::InterfaceTemplate(x.template),
        }
    }
    
    pub fn template_args(&self) -> &'t [ITemplataT<'s, 't>] {
        match self {
            ISuperKindNameT::KindPlaceholder(_) => &[],
            ISuperKindNameT::Interface(x) => x.template_args,
        }
    }
    
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ISubKindNameT<'s, 't> {
    StaticSizedArray(&'t StaticSizedArrayNameT<'s, 't>),
    RuntimeSizedArray(&'t RuntimeSizedArrayNameT<'s, 't>),
    KindPlaceholder(&'t KindPlaceholderNameT<'s, 't>),
    Struct(&'t StructNameT<'s, 't>),
    Interface(&'t InterfaceNameT<'s, 't>),
    LambdaCitizen(&'t LambdaCitizenNameT<'s, 't>),
    AnonymousSubstruct(&'t AnonymousSubstructNameT<'s, 't>),
}

impl<'s, 't> ISubKindNameT<'s, 't> where 's: 't {
    pub fn template(&self) -> ISubKindTemplateNameT<'s, 't> {
        match self {
            ISubKindNameT::StaticSizedArray(x) => ISubKindTemplateNameT::StaticSizedArrayTemplate(x.template),
            ISubKindNameT::RuntimeSizedArray(x) => ISubKindTemplateNameT::RuntimeSizedArrayTemplate(x.template),
            ISubKindNameT::KindPlaceholder(x) => ISubKindTemplateNameT::KindPlaceholderTemplate(x.template),
            ISubKindNameT::Struct(x) => ISubKindTemplateNameT::from(ICitizenTemplateNameT::from(x.template)),
            ISubKindNameT::Interface(x) => ISubKindTemplateNameT::InterfaceTemplate(x.template),
            ISubKindNameT::LambdaCitizen(x) => ISubKindTemplateNameT::LambdaCitizenTemplate(x.template),
            ISubKindNameT::AnonymousSubstruct(x) => ISubKindTemplateNameT::AnonymousSubstructTemplate(x.template),
        }
    }
    
    pub fn template_args(&self) -> &'t [ITemplataT<'s, 't>] {
        match self {
            ISubKindNameT::StaticSizedArray(_) => panic!("Unimplemented: template_args on StaticSizedArrayNameT (computed: Vector(size, arr.mutability, variability, CoordTemplataT(arr.elementType)) — needs interner to allocate slice)"),
            ISubKindNameT::RuntimeSizedArray(_) => panic!("Unimplemented: template_args on RuntimeSizedArrayNameT (computed: Vector(arr.mutability, CoordTemplataT(arr.elementType)) — needs interner to allocate slice)"),
            ISubKindNameT::KindPlaceholder(_) => &[],
            ISubKindNameT::Struct(x) => x.template_args,
            ISubKindNameT::Interface(x) => x.template_args,
            ISubKindNameT::LambdaCitizen(_) => &[],
            ISubKindNameT::AnonymousSubstruct(x) => x.template_args,
        }
    }
    
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ICitizenNameT<'s, 't> {
    StaticSizedArray(&'t StaticSizedArrayNameT<'s, 't>),
    RuntimeSizedArray(&'t RuntimeSizedArrayNameT<'s, 't>),
    Struct(&'t StructNameT<'s, 't>),
    Interface(&'t InterfaceNameT<'s, 't>),
    LambdaCitizen(&'t LambdaCitizenNameT<'s, 't>),
    AnonymousSubstruct(&'t AnonymousSubstructNameT<'s, 't>),
}

impl<'s, 't> ICitizenNameT<'s, 't> where 's: 't {
    pub fn template(&self) -> ICitizenTemplateNameT<'s, 't> {
        match self {
            ICitizenNameT::StaticSizedArray(x) => ICitizenTemplateNameT::StaticSizedArrayTemplate(x.template),
            ICitizenNameT::RuntimeSizedArray(x) => ICitizenTemplateNameT::RuntimeSizedArrayTemplate(x.template),
            ICitizenNameT::Struct(x) => ICitizenTemplateNameT::from(x.template),
            ICitizenNameT::Interface(x) => ICitizenTemplateNameT::InterfaceTemplate(x.template),
            ICitizenNameT::LambdaCitizen(x) => ICitizenTemplateNameT::LambdaCitizenTemplate(x.template),
            ICitizenNameT::AnonymousSubstruct(x) => ICitizenTemplateNameT::AnonymousSubstructTemplate(x.template),
        }
    }
    
    pub fn template_args(&self) -> &'t [ITemplataT<'s, 't>] {
        match self {
            ICitizenNameT::StaticSizedArray(_) => panic!("Unimplemented: template_args on StaticSizedArrayNameT (computed: Vector(size, arr.mutability, variability, CoordTemplataT(arr.elementType)) — needs interner to allocate slice)"),
            ICitizenNameT::RuntimeSizedArray(_) => panic!("Unimplemented: template_args on RuntimeSizedArrayNameT (computed: Vector(arr.mutability, CoordTemplataT(arr.elementType)) — needs interner to allocate slice)"),
            ICitizenNameT::Struct(x) => x.template_args,
            ICitizenNameT::Interface(x) => x.template_args,
            ICitizenNameT::LambdaCitizen(_) => &[],
            ICitizenNameT::AnonymousSubstruct(x) => x.template_args,
        }
    }
    
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IStructNameT<'s, 't> {
    Struct(&'t StructNameT<'s, 't>),
    LambdaCitizen(&'t LambdaCitizenNameT<'s, 't>),
    AnonymousSubstruct(&'t AnonymousSubstructNameT<'s, 't>),
}

impl<'s, 't> IStructNameT<'s, 't> where 's: 't {
    pub fn template(&self) -> IStructTemplateNameT<'s, 't> {
        match self {
            IStructNameT::Struct(x) => x.template,
            IStructNameT::LambdaCitizen(x) => IStructTemplateNameT::LambdaCitizenTemplate(x.template),
            IStructNameT::AnonymousSubstruct(x) => IStructTemplateNameT::AnonymousSubstructTemplate(x.template),
        }
    }
    
    pub fn template_args(&self) -> &'t [ITemplataT<'s, 't>] {
        match self {
            IStructNameT::Struct(x) => x.template_args,
            IStructNameT::LambdaCitizen(_) => &[],
            IStructNameT::AnonymousSubstruct(x) => x.template_args,
        }
    }
    
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IInterfaceNameT<'s, 't> {
    Interface(&'t InterfaceNameT<'s, 't>),
}

impl<'s, 't> IInterfaceNameT<'s, 't> where 's: 't {
    pub fn template(&self) -> &'t InterfaceTemplateNameT<'s> {
        match self {
            IInterfaceNameT::Interface(x) => x.template,
        }
    }
    
    pub fn template_args(&self) -> &'t [ITemplataT<'s, 't>] {
        match self {
            IInterfaceNameT::Interface(x) => x.template_args,
        }
    }
    
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IImplTemplateNameT<'s, 't> {
    ImplTemplate(&'t ImplTemplateNameT<'s>),
    ImplBoundTemplate(&'t ImplBoundTemplateNameT<'s>),
    AnonymousSubstructImplTemplate(&'t AnonymousSubstructImplTemplateNameT<'s, 't>),
}

// Scala trait method: def makeImplName(...): IImplNameT
// Overrides: ImplTemplate (line 160), ImplBoundTemplate (line 175), AnonymousSubstructImplTemplate (line 643)
impl<'s, 't> IImplTemplateNameT<'s, 't> where 's: 't {
  pub fn make_impl_name(
    &self,
    interner: &TypingInterner<'s, 't>,
    template_args: &[ITemplataT<'s, 't>],
    sub_citizen: ICitizenTT<'s, 't>,
  ) -> INameT<'s, 't> {
    match self {
      IImplTemplateNameT::ImplTemplate(tmpl) => {
        interner.intern_name(INameValT::Impl(ImplNameValT {
          template: tmpl,
          template_args,
          sub_citizen,
        }))
      }
      IImplTemplateNameT::ImplBoundTemplate(tmpl) => {
        interner.intern_name(INameValT::ImplBound(ImplBoundNameValT {
          template: tmpl,
          template_args,
        }))
      }
      IImplTemplateNameT::AnonymousSubstructImplTemplate(tmpl) => {
        interner.intern_name(INameValT::AnonymousSubstructImpl(AnonymousSubstructImplNameValT {
          template: tmpl,
          template_args,
          sub_citizen,
        }))
      }
    }
  }
  
}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IImplNameT<'s, 't> {
    Impl(&'t ImplNameT<'s, 't>),
    ImplBound(&'t ImplBoundNameT<'s, 't>),
    AnonymousSubstructImpl(&'t AnonymousSubstructImplNameT<'s, 't>),
}

impl<'s, 't> IImplNameT<'s, 't> where 's: 't {
    pub fn template(&self) -> IImplTemplateNameT<'s, 't> {
        match self {
            IImplNameT::Impl(x) => IImplTemplateNameT::ImplTemplate(x.template),
            IImplNameT::ImplBound(x) => IImplTemplateNameT::ImplBoundTemplate(x.template),
            IImplNameT::AnonymousSubstructImpl(x) => IImplTemplateNameT::AnonymousSubstructImplTemplate(x.template),
        }
    }
    
    pub fn template_args(&self) -> &'t [ITemplataT<'s, 't>] {
        match self {
            IImplNameT::Impl(x) => x.template_args,
            IImplNameT::ImplBound(x) => x.template_args,
            IImplNameT::AnonymousSubstructImpl(x) => x.template_args,
        }
    }
    
}

// TODO: placeholder PhantomData — replace with real fields during body migration
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IRegionNameT<'s, 't> { _Phantom(PhantomData<(&'s (), &'t ())>) }

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ExportTemplateNameT<'s> {
    pub code_loc: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ExportNameT<'s, 't> {
    pub template: &'t ExportTemplateNameT<'s>,
    pub region: RegionT,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ImplTemplateNameT<'s> {
    pub code_location_s: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ImplNameT<'s, 't> {
    pub template: &'t ImplTemplateNameT<'s>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub sub_citizen: ICitizenTT<'s, 't>,
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ImplBoundTemplateNameT<'s> {
    pub code_location_s: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ImplBoundNameT<'s, 't> {
    pub template: &'t ImplBoundTemplateNameT<'s>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct LetNameT<'s> {
    pub code_location: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ExportAsNameT<'s> {
    pub code_location: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RawArrayNameT<'s, 't> {
    pub mutability: ITemplataT<'s, 't>,
    pub element_type: CoordT<'s, 't>,
    pub self_region: RegionT,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ReachablePrototypeNameT {
    pub num: i32,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StaticSizedArrayTemplateNameT {
}

impl StaticSizedArrayTemplateNameT {
    pub fn make_citizen_name<'s, 't>(
        &self,
        interner: &TypingInterner<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
    ) -> INameT<'s, 't> {
        // vassert(templateArgs.size == 4)
        assert!(template_args.len() == 4);
        // val size = expectInteger(templateArgs(0))
        let size = expect_integer(template_args[0]);
        // val mutability = expectMutability(templateArgs(1))
        let mutability = expect_mutability(template_args[1]);
        // val variability = expectVariability(templateArgs(2))
        let variability = expect_variability(template_args[2]);
        // val elementType = expectCoordTemplata(templateArgs(3)).coord
        let element_type = expect_coord_templata(template_args[3]).coord;
        // val selfRegion = vregionmut(RegionT(DefaultRegionT))
        let self_region = RegionT { region: IRegionT::Default };
        // interner.intern(StaticSizedArrayNameT(this, size, variability, interner.intern(RawArrayNameT(mutability, elementType, selfRegion))))
        let raw_array_name = interner.intern_raw_array_name(RawArrayNameT {
            mutability,
            element_type,
            self_region,
        });
        let ssa_name = interner.intern_static_sized_array_name(StaticSizedArrayNameT {
            template: interner.alloc(*self),
            size,
            variability,
            arr: raw_array_name,
        });
        INameT::StaticSizedArray(ssa_name)
    }

}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StaticSizedArrayNameT<'s, 't> {
    pub template: &'t StaticSizedArrayTemplateNameT,
    pub size: ITemplataT<'s, 't>,
    pub variability: ITemplataT<'s, 't>,
    pub arr: &'t RawArrayNameT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RuntimeSizedArrayTemplateNameT {
}

impl RuntimeSizedArrayTemplateNameT {
    pub fn make_citizen_name<'s, 't>(
        &self,
        interner: &TypingInterner<'s, 't>,
        template_args: &[ITemplataT<'s, 't>],
    ) -> INameT<'s, 't> {
        // vassert(templateArgs.size == 2)
        assert!(template_args.len() == 2);
        // val mutability = expectMutability(templateArgs(0))
        let mutability = expect_mutability(template_args[0]);
        // val elementType = expectCoordTemplata(templateArgs(1)).coord
        let element_type = expect_coord_templata(template_args[1]).coord;
        // val region = vregionmut(RegionT(DefaultRegionT))
        let region = RegionT { region: IRegionT::Default };
        // interner.intern(RuntimeSizedArrayNameT(this, interner.intern(RawArrayNameT(mutability, elementType, region))))
        let raw_array_name = interner.intern_raw_array_name(RawArrayNameT {
            mutability,
            element_type: element_type,
            self_region: region,
        });
        let rsa_name = interner.intern_runtime_sized_array_name(RuntimeSizedArrayNameT {
            template: interner.alloc(*self),
            arr: raw_array_name,
        });
        INameT::RuntimeSizedArray(rsa_name)
    }

}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RuntimeSizedArrayNameT<'s, 't> {
    pub template: &'t RuntimeSizedArrayTemplateNameT,
    pub arr: &'t RawArrayNameT<'s, 't>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IPlaceholderNameT<'s, 't> {
    KindPlaceholder(&'t KindPlaceholderNameT<'s, 't>),
    NonKindNonRegionPlaceholder(&'t NonKindNonRegionPlaceholderNameT<'s>),
}

impl<'s, 't> IPlaceholderNameT<'s, 't> {
    pub fn index(&self) -> i32 {
        match self {
            IPlaceholderNameT::KindPlaceholder(x) => x.template.index,
            IPlaceholderNameT::NonKindNonRegionPlaceholder(x) => x.index,
        }
    }
    
    pub fn rune(&self) -> IRuneS<'s> {
        match self {
            IPlaceholderNameT::KindPlaceholder(x) => x.template.rune,
            IPlaceholderNameT::NonKindNonRegionPlaceholder(x) => x.rune,
        }
    }
    
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct KindPlaceholderTemplateNameT<'s> {
    pub index: i32,
    pub rune: IRuneS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct KindPlaceholderNameT<'s, 't> {
    pub template: &'t KindPlaceholderTemplateNameT<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct NonKindNonRegionPlaceholderNameT<'s> {
    pub index: i32,
    pub rune: IRuneS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct OverrideDispatcherTemplateNameT<'s, 't> {
    pub impl_id: IdT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct OverrideDispatcherNameT<'s, 't> {
    pub template: &'t OverrideDispatcherTemplateNameT<'s, 't>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub parameters: &'t [CoordT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct OverrideDispatcherCaseNameT<'s, 't> {
    pub independent_impl_template_args: &'t [ITemplataT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IVarNameT<'s, 't> {
    TypingPassBlockResultVar(&'t TypingPassBlockResultVarNameT<'t>),
    TypingPassFunctionResultVar(&'t TypingPassFunctionResultVarNameT),
    TypingPassTemporaryVar(&'t TypingPassTemporaryVarNameT<'t>),
    TypingPassPatternMember(&'t TypingPassPatternMemberNameT<'t>),
    TypingIgnoredParam(&'t TypingIgnoredParamNameT),
    TypingPassPatternDestructuree(&'t TypingPassPatternDestructureeNameT<'t>),
    UnnamedLocal(&'t UnnamedLocalNameT<'s>),
    ClosureParam(&'t ClosureParamNameT<'s>),
    ConstructingMember(&'t ConstructingMemberNameT<'s>),
    WhileCondResult(&'t WhileCondResultNameT<'s>),
    Iterable(&'t IterableNameT<'s>),
    Iterator(&'t IteratorNameT<'s>),
    IterationOption(&'t IterationOptionNameT<'s>),
    MagicParam(&'t MagicParamNameT<'s>),
    CodeVar(&'t CodeVarNameT<'s>),
    AnonymousSubstructMember(&'t AnonymousSubstructMemberNameT),
    Self_(&'t SelfNameT),
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct TypingPassBlockResultVarNameT<'t> {
    pub life: LocationInFunctionEnvironmentT<'t>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct TypingPassFunctionResultVarNameT {
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct TypingPassTemporaryVarNameT<'t> {
    pub life: LocationInFunctionEnvironmentT<'t>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct TypingPassPatternMemberNameT<'t> {
    pub life: LocationInFunctionEnvironmentT<'t>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct TypingIgnoredParamNameT {
    pub num: i32,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct TypingPassPatternDestructureeNameT<'t> {
    pub life: LocationInFunctionEnvironmentT<'t>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct UnnamedLocalNameT<'s> {
    pub code_location: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ClosureParamNameT<'s> {
    pub code_location: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ConstructingMemberNameT<'s> {
    pub name: StrI<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct WhileCondResultNameT<'s> {
    pub range: RangeS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IterableNameT<'s> {
    pub range: RangeS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IteratorNameT<'s> {
    pub range: RangeS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct IterationOptionNameT<'s> {
    pub range: RangeS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct MagicParamNameT<'s> {
    pub code_location2: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CodeVarNameT<'s> {
    pub name: StrI<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AnonymousSubstructMemberNameT {
    pub index: i32,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PrimitiveNameT<'s> {
    pub human_name: StrI<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PackageTopLevelNameT {
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ProjectNameT<'s> {
    pub name: StrI<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PackageNameT<'s> {
    pub name: StrI<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct RuneNameT<'s> {
    pub rune: IRuneS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct BuildingFunctionNameWithClosuredsT<'s, 't> {
    pub template_name: IFunctionTemplateNameT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ExternTemplateNameT<'s> {
    pub code_loc: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ExternNameT<'s, 't> {
    pub template: &'t ExternTemplateNameT<'s>,
    pub template_arg: RegionT,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ExternFunctionNameT<'s, 't> {
    pub human_name: StrI<'s>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub parameters: &'t [CoordT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FunctionNameT<'s, 't> {
    pub template: &'t FunctionTemplateNameT<'s>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub parameters: &'t [CoordT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ForwarderFunctionNameT<'s, 't> {
    pub template: &'t ForwarderFunctionTemplateNameT<'s, 't>,
    pub inner: IFunctionNameT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FunctionBoundTemplateNameT<'s> {
    pub human_name: StrI<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FunctionBoundNameT<'s, 't> {
    pub template: &'t FunctionBoundTemplateNameT<'s>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub parameters: &'t [CoordT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PredictedFunctionTemplateNameT<'s> {
    pub human_name: StrI<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct PredictedFunctionNameT<'s, 't> {
    pub template: &'t PredictedFunctionTemplateNameT<'s>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub parameters: &'t [CoordT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct FunctionTemplateNameT<'s> {
    pub human_name: StrI<'s>,
    pub code_location: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct LambdaCallFunctionTemplateNameT<'s, 't> {
    pub code_location: CodeLocationS<'s>,
    pub param_types: &'t [CoordT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct LambdaCallFunctionNameT<'s, 't> {
    pub template: &'t LambdaCallFunctionTemplateNameT<'s, 't>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub parameters: &'t [CoordT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ForwarderFunctionTemplateNameT<'s, 't> {
    pub inner: IFunctionTemplateNameT<'s, 't>,
    pub index: i32,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ConstructorTemplateNameT<'s> {
    pub code_location: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct SelfNameT {
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ArbitraryNameT {
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum CitizenNameT<'s, 't> {
    Struct(&'t StructNameT<'s, 't>),
    Interface(&'t InterfaceNameT<'s, 't>),
}

fn citizen_name_unapply() { panic!("Unmigrated unapply"); }

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructNameT<'s, 't> {
    pub template: IStructTemplateNameT<'s, 't>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct InterfaceNameT<'s, 't> {
    pub template: &'t InterfaceTemplateNameT<'s>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct LambdaCitizenTemplateNameT<'s> {
    pub code_location: CodeLocationS<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct LambdaCitizenNameT<'s, 't> {
    pub template: &'t LambdaCitizenTemplateNameT<'s>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum CitizenTemplateNameT<'s, 't> {
    StructTemplate(&'t StructTemplateNameT<'s>),
    InterfaceTemplate(&'t InterfaceTemplateNameT<'s>),
}
impl<'s, 't> CitizenTemplateNameT<'s, 't> where 's: 't {
  pub fn human_name(&self) -> StrI<'s> {
    match self {
      CitizenTemplateNameT::StructTemplate(x) => x.human_name,
      CitizenTemplateNameT::InterfaceTemplate(x) => x.human_namee,
    }
  }
  
}

fn citizen_template_name_unapply() { panic!("Unmigrated unapply"); }

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct StructTemplateNameT<'s> {
    pub human_name: StrI<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct InterfaceTemplateNameT<'s> {
    pub human_namee: StrI<'s>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AnonymousSubstructImplTemplateNameT<'s, 't> {
    pub interface: IInterfaceTemplateNameT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AnonymousSubstructImplNameT<'s, 't> {
    pub template: &'t AnonymousSubstructImplTemplateNameT<'s, 't>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub sub_citizen: ICitizenTT<'s, 't>,
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AnonymousSubstructTemplateNameT<'s, 't> {
    pub interface: IInterfaceTemplateNameT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AnonymousSubstructConstructorTemplateNameT<'s, 't> {
    pub substruct: ICitizenTemplateNameT<'s, 't>,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AnonymousSubstructConstructorNameT<'s, 't> {
    pub template: &'t AnonymousSubstructConstructorTemplateNameT<'s, 't>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub parameters: &'t [CoordT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AnonymousSubstructNameT<'s, 't> {
    pub template: &'t AnonymousSubstructTemplateNameT<'s, 't>,
    pub template_args: &'t [ITemplataT<'s, 't>],
    pub _must_intern: MustIntern,
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ResolvingEnvNameT {
}

/// Interned (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct CallEnvNameT {
}

// ============================================================================
// From / TryFrom bridges between sub-enums and concrete names.
//
// No Scala counterpart — Scala's sealed-trait hierarchy handled all of these
// implicitly. Rust needs them spelled out.
//
// - From<&'t XxxNameT> for IYyyNameT  — wrap a concrete ref as a sub-enum.
// - From<&'t INarrowT> for IWideT     — upcast a narrow sub-enum to a wider one.
// - TryFrom<&'t INameT> for &'t IYyyNameT — narrow (arena ref) form; panic
//   stub per handoff §6.3 Gotcha — this path requires TypingInterner to intern
//   the narrower sub-enum, which is Slab 3+ work (intern_* are still `panic!()`).
// ============================================================================

// -- Concrete → INameT -------------------------------------------------------
impl<'s, 't> From<&'t ExportTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t ExportTemplateNameT<'s>) -> Self { INameT::ExportTemplate(x) }
}
impl<'s, 't> From<&'t ExportNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t ExportNameT<'s, 't>) -> Self { INameT::Export(x) }
}
impl<'s, 't> From<&'t ImplTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t ImplTemplateNameT<'s>) -> Self { INameT::ImplTemplate(x) }
}
impl<'s, 't> From<&'t ImplNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t ImplNameT<'s, 't>) -> Self { INameT::Impl(x) }
}
impl<'s, 't> From<&'t ImplBoundTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t ImplBoundTemplateNameT<'s>) -> Self { INameT::ImplBoundTemplate(x) }
}
impl<'s, 't> From<&'t ImplBoundNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t ImplBoundNameT<'s, 't>) -> Self { INameT::ImplBound(x) }
}
impl<'s, 't> From<&'t LetNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t LetNameT<'s>) -> Self { INameT::Let(x) }
}
impl<'s, 't> From<&'t ExportAsNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t ExportAsNameT<'s>) -> Self { INameT::ExportAs(x) }
}
impl<'s, 't> From<&'t RawArrayNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t RawArrayNameT<'s, 't>) -> Self { INameT::RawArray(x) }
}
impl<'s, 't> From<&'t ReachablePrototypeNameT> for INameT<'s, 't> {
    fn from(x: &'t ReachablePrototypeNameT) -> Self { INameT::ReachablePrototype(x) }
}
impl<'s, 't> From<&'t StaticSizedArrayTemplateNameT> for INameT<'s, 't> {
    fn from(x: &'t StaticSizedArrayTemplateNameT) -> Self { INameT::StaticSizedArrayTemplate(x) }
}
impl<'s, 't> From<&'t StaticSizedArrayNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t StaticSizedArrayNameT<'s, 't>) -> Self { INameT::StaticSizedArray(x) }
}
impl<'s, 't> From<&'t RuntimeSizedArrayTemplateNameT> for INameT<'s, 't> {
    fn from(x: &'t RuntimeSizedArrayTemplateNameT) -> Self { INameT::RuntimeSizedArrayTemplate(x) }
}
impl<'s, 't> From<&'t RuntimeSizedArrayNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t RuntimeSizedArrayNameT<'s, 't>) -> Self { INameT::RuntimeSizedArray(x) }
}
impl<'s, 't> From<&'t KindPlaceholderTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t KindPlaceholderTemplateNameT<'s>) -> Self { INameT::KindPlaceholderTemplate(x) }
}
impl<'s, 't> From<&'t KindPlaceholderNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t KindPlaceholderNameT<'s, 't>) -> Self { INameT::KindPlaceholder(x) }
}
impl<'s, 't> From<&'t NonKindNonRegionPlaceholderNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t NonKindNonRegionPlaceholderNameT<'s>) -> Self { INameT::NonKindNonRegionPlaceholder(x) }
}
impl<'s, 't> From<&'t OverrideDispatcherTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t OverrideDispatcherTemplateNameT<'s, 't>) -> Self { INameT::OverrideDispatcherTemplate(x) }
}
impl<'s, 't> From<&'t OverrideDispatcherNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t OverrideDispatcherNameT<'s, 't>) -> Self { INameT::OverrideDispatcher(x) }
}
impl<'s, 't> From<&'t OverrideDispatcherCaseNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t OverrideDispatcherCaseNameT<'s, 't>) -> Self { INameT::OverrideDispatcherCase(x) }
}
impl<'s, 't> From<&'t TypingPassBlockResultVarNameT<'t>> for INameT<'s, 't> {
    fn from(x: &'t TypingPassBlockResultVarNameT<'t>) -> Self { INameT::TypingPassBlockResultVar(x) }
}
impl<'s, 't> From<&'t TypingPassFunctionResultVarNameT> for INameT<'s, 't> {
    fn from(x: &'t TypingPassFunctionResultVarNameT) -> Self { INameT::TypingPassFunctionResultVar(x) }
}
impl<'s, 't> From<&'t TypingPassTemporaryVarNameT<'t>> for INameT<'s, 't> {
    fn from(x: &'t TypingPassTemporaryVarNameT<'t>) -> Self { INameT::TypingPassTemporaryVar(x) }
}
impl<'s, 't> From<&'t TypingPassPatternMemberNameT<'t>> for INameT<'s, 't> {
    fn from(x: &'t TypingPassPatternMemberNameT<'t>) -> Self { INameT::TypingPassPatternMember(x) }
}
impl<'s, 't> From<&'t TypingIgnoredParamNameT> for INameT<'s, 't> {
    fn from(x: &'t TypingIgnoredParamNameT) -> Self { INameT::TypingIgnoredParam(x) }
}
impl<'s, 't> From<&'t TypingPassPatternDestructureeNameT<'t>> for INameT<'s, 't> {
    fn from(x: &'t TypingPassPatternDestructureeNameT<'t>) -> Self { INameT::TypingPassPatternDestructuree(x) }
}
impl<'s, 't> From<&'t UnnamedLocalNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t UnnamedLocalNameT<'s>) -> Self { INameT::UnnamedLocal(x) }
}
impl<'s, 't> From<&'t ClosureParamNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t ClosureParamNameT<'s>) -> Self { INameT::ClosureParam(x) }
}
impl<'s, 't> From<&'t ConstructingMemberNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t ConstructingMemberNameT<'s>) -> Self { INameT::ConstructingMember(x) }
}
impl<'s, 't> From<&'t WhileCondResultNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t WhileCondResultNameT<'s>) -> Self { INameT::WhileCondResult(x) }
}
impl<'s, 't> From<&'t IterableNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t IterableNameT<'s>) -> Self { INameT::Iterable(x) }
}
impl<'s, 't> From<&'t IteratorNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t IteratorNameT<'s>) -> Self { INameT::Iterator(x) }
}
impl<'s, 't> From<&'t IterationOptionNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t IterationOptionNameT<'s>) -> Self { INameT::IterationOption(x) }
}
impl<'s, 't> From<&'t MagicParamNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t MagicParamNameT<'s>) -> Self { INameT::MagicParam(x) }
}
impl<'s, 't> From<&'t CodeVarNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t CodeVarNameT<'s>) -> Self { INameT::CodeVar(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructMemberNameT> for INameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructMemberNameT) -> Self { INameT::AnonymousSubstructMember(x) }
}
impl<'s, 't> From<&'t PrimitiveNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t PrimitiveNameT<'s>) -> Self { INameT::Primitive(x) }
}
impl<'s, 't> From<&'t PackageTopLevelNameT> for INameT<'s, 't> {
    fn from(x: &'t PackageTopLevelNameT) -> Self { INameT::PackageTopLevel(x) }
}
impl<'s, 't> From<&'t ProjectNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t ProjectNameT<'s>) -> Self { INameT::Project(x) }
}
impl<'s, 't> From<&'t PackageNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t PackageNameT<'s>) -> Self { INameT::Package(x) }
}
impl<'s, 't> From<&'t RuneNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t RuneNameT<'s>) -> Self { INameT::Rune(x) }
}
impl<'s, 't> From<&'t BuildingFunctionNameWithClosuredsT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t BuildingFunctionNameWithClosuredsT<'s, 't>) -> Self { INameT::BuildingFunctionNameWithClosureds(x) }
}
impl<'s, 't> From<&'t ExternTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t ExternTemplateNameT<'s>) -> Self { INameT::ExternTemplate(x) }
}
impl<'s, 't> From<&'t ExternNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t ExternNameT<'s, 't>) -> Self { INameT::Extern(x) }
}
impl<'s, 't> From<&'t ExternFunctionNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t ExternFunctionNameT<'s, 't>) -> Self { INameT::ExternFunction(x) }
}
impl<'s, 't> From<&'t FunctionNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t FunctionNameT<'s, 't>) -> Self { INameT::Function(x) }
}
impl<'s, 't> From<&'t ForwarderFunctionNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t ForwarderFunctionNameT<'s, 't>) -> Self { INameT::ForwarderFunction(x) }
}
impl<'s, 't> From<&'t FunctionBoundTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t FunctionBoundTemplateNameT<'s>) -> Self { INameT::FunctionBoundTemplate(x) }
}
impl<'s, 't> From<&'t FunctionBoundNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t FunctionBoundNameT<'s, 't>) -> Self { INameT::FunctionBound(x) }
}
impl<'s, 't> From<&'t PredictedFunctionTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t PredictedFunctionTemplateNameT<'s>) -> Self { INameT::PredictedFunctionTemplate(x) }
}
impl<'s, 't> From<&'t PredictedFunctionNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t PredictedFunctionNameT<'s, 't>) -> Self { INameT::PredictedFunction(x) }
}
impl<'s, 't> From<&'t FunctionTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t FunctionTemplateNameT<'s>) -> Self { INameT::FunctionTemplate(x) }
}
impl<'s, 't> From<&'t LambdaCallFunctionTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t LambdaCallFunctionTemplateNameT<'s, 't>) -> Self { INameT::LambdaCallFunctionTemplate(x) }
}
impl<'s, 't> From<&'t LambdaCallFunctionNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t LambdaCallFunctionNameT<'s, 't>) -> Self { INameT::LambdaCallFunction(x) }
}
impl<'s, 't> From<&'t ForwarderFunctionTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t ForwarderFunctionTemplateNameT<'s, 't>) -> Self { INameT::ForwarderFunctionTemplate(x) }
}
impl<'s, 't> From<&'t ConstructorTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t ConstructorTemplateNameT<'s>) -> Self { INameT::ConstructorTemplate(x) }
}
impl<'s, 't> From<&'t SelfNameT> for INameT<'s, 't> {
    fn from(x: &'t SelfNameT) -> Self { INameT::Self_(x) }
}
impl<'s, 't> From<&'t ArbitraryNameT> for INameT<'s, 't> {
    fn from(x: &'t ArbitraryNameT) -> Self { INameT::Arbitrary(x) }
}
impl<'s, 't> From<&'t StructNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t StructNameT<'s, 't>) -> Self { INameT::Struct(x) }
}
impl<'s, 't> From<&'t InterfaceNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t InterfaceNameT<'s, 't>) -> Self { INameT::Interface(x) }
}
impl<'s, 't> From<&'t LambdaCitizenTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t LambdaCitizenTemplateNameT<'s>) -> Self { INameT::LambdaCitizenTemplate(x) }
}
impl<'s, 't> From<&'t LambdaCitizenNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t LambdaCitizenNameT<'s, 't>) -> Self { INameT::LambdaCitizen(x) }
}
impl<'s, 't> From<&'t StructTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t StructTemplateNameT<'s>) -> Self { INameT::StructTemplate(x) }
}
impl<'s, 't> From<&'t InterfaceTemplateNameT<'s>> for INameT<'s, 't> {
    fn from(x: &'t InterfaceTemplateNameT<'s>) -> Self { INameT::InterfaceTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructImplTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructImplTemplateNameT<'s, 't>) -> Self { INameT::AnonymousSubstructImplTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructImplNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructImplNameT<'s, 't>) -> Self { INameT::AnonymousSubstructImpl(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructTemplateNameT<'s, 't>) -> Self { INameT::AnonymousSubstructTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructConstructorTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructConstructorTemplateNameT<'s, 't>) -> Self { INameT::AnonymousSubstructConstructorTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructConstructorNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructConstructorNameT<'s, 't>) -> Self { INameT::AnonymousSubstructConstructor(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructNameT<'s, 't>> for INameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructNameT<'s, 't>) -> Self { INameT::AnonymousSubstruct(x) }
}
impl<'s, 't> From<&'t ResolvingEnvNameT> for INameT<'s, 't> {
    fn from(x: &'t ResolvingEnvNameT) -> Self { INameT::ResolvingEnv(x) }
}
impl<'s, 't> From<&'t CallEnvNameT> for INameT<'s, 't> {
    fn from(x: &'t CallEnvNameT) -> Self { INameT::CallEnv(x) }
}

// -- Concrete → ITemplateNameT -----------------------------------------------
impl<'s, 't> From<&'t ExportTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t ExportTemplateNameT<'s>) -> Self { ITemplateNameT::ExportTemplate(x) }
}
impl<'s, 't> From<&'t ImplTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t ImplTemplateNameT<'s>) -> Self { ITemplateNameT::ImplTemplate(x) }
}
impl<'s, 't> From<&'t ImplBoundTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t ImplBoundTemplateNameT<'s>) -> Self { ITemplateNameT::ImplBoundTemplate(x) }
}
impl<'s, 't> From<&'t StaticSizedArrayTemplateNameT> for ITemplateNameT<'s, 't> {
    fn from(x: &'t StaticSizedArrayTemplateNameT) -> Self { ITemplateNameT::StaticSizedArrayTemplate(x) }
}
impl<'s, 't> From<&'t RuntimeSizedArrayTemplateNameT> for ITemplateNameT<'s, 't> {
    fn from(x: &'t RuntimeSizedArrayTemplateNameT) -> Self { ITemplateNameT::RuntimeSizedArrayTemplate(x) }
}
impl<'s, 't> From<&'t KindPlaceholderTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t KindPlaceholderTemplateNameT<'s>) -> Self { ITemplateNameT::KindPlaceholderTemplate(x) }
}
impl<'s, 't> From<&'t OverrideDispatcherTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t OverrideDispatcherTemplateNameT<'s, 't>) -> Self { ITemplateNameT::OverrideDispatcherTemplate(x) }
}
impl<'s, 't> From<&'t OverrideDispatcherCaseNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t OverrideDispatcherCaseNameT<'s, 't>) -> Self { ITemplateNameT::OverrideDispatcherCase(x) }
}
impl<'s, 't> From<&'t ExternTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t ExternTemplateNameT<'s>) -> Self { ITemplateNameT::ExternTemplate(x) }
}
impl<'s, 't> From<&'t ExternFunctionNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t ExternFunctionNameT<'s, 't>) -> Self { ITemplateNameT::ExternFunction(x) }
}
impl<'s, 't> From<&'t FunctionBoundTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t FunctionBoundTemplateNameT<'s>) -> Self { ITemplateNameT::FunctionBoundTemplate(x) }
}
impl<'s, 't> From<&'t PredictedFunctionTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t PredictedFunctionTemplateNameT<'s>) -> Self { ITemplateNameT::PredictedFunctionTemplate(x) }
}
impl<'s, 't> From<&'t FunctionTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t FunctionTemplateNameT<'s>) -> Self { ITemplateNameT::FunctionTemplate(x) }
}
impl<'s, 't> From<&'t LambdaCallFunctionTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t LambdaCallFunctionTemplateNameT<'s, 't>) -> Self { ITemplateNameT::LambdaCallFunctionTemplate(x) }
}
impl<'s, 't> From<&'t ForwarderFunctionTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t ForwarderFunctionTemplateNameT<'s, 't>) -> Self { ITemplateNameT::ForwarderFunctionTemplate(x) }
}
impl<'s, 't> From<&'t ConstructorTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t ConstructorTemplateNameT<'s>) -> Self { ITemplateNameT::ConstructorTemplate(x) }
}
impl<'s, 't> From<&'t LambdaCitizenTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t LambdaCitizenTemplateNameT<'s>) -> Self { ITemplateNameT::LambdaCitizenTemplate(x) }
}
impl<'s, 't> From<&'t StructTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t StructTemplateNameT<'s>) -> Self { ITemplateNameT::StructTemplate(x) }
}
impl<'s, 't> From<&'t InterfaceTemplateNameT<'s>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t InterfaceTemplateNameT<'s>) -> Self { ITemplateNameT::InterfaceTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructImplTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructImplTemplateNameT<'s, 't>) -> Self { ITemplateNameT::AnonymousSubstructImplTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructTemplateNameT<'s, 't>) -> Self { ITemplateNameT::AnonymousSubstructTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructConstructorTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructConstructorTemplateNameT<'s, 't>) -> Self { ITemplateNameT::AnonymousSubstructConstructorTemplate(x) }
}

// -- Concrete → IInstantiationNameT ------------------------------------------
impl<'s, 't> From<&'t ExportNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t ExportNameT<'s, 't>) -> Self { IInstantiationNameT::Export(x) }
}
impl<'s, 't> From<&'t ImplNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t ImplNameT<'s, 't>) -> Self { IInstantiationNameT::Impl(x) }
}
impl<'s, 't> From<&'t ImplBoundNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t ImplBoundNameT<'s, 't>) -> Self { IInstantiationNameT::ImplBound(x) }
}
impl<'s, 't> From<&'t StaticSizedArrayNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t StaticSizedArrayNameT<'s, 't>) -> Self { IInstantiationNameT::StaticSizedArray(x) }
}
impl<'s, 't> From<&'t RuntimeSizedArrayNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t RuntimeSizedArrayNameT<'s, 't>) -> Self { IInstantiationNameT::RuntimeSizedArray(x) }
}
impl<'s, 't> From<&'t KindPlaceholderNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t KindPlaceholderNameT<'s, 't>) -> Self { IInstantiationNameT::KindPlaceholder(x) }
}
impl<'s, 't> From<&'t OverrideDispatcherNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t OverrideDispatcherNameT<'s, 't>) -> Self { IInstantiationNameT::OverrideDispatcher(x) }
}
impl<'s, 't> From<&'t OverrideDispatcherCaseNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t OverrideDispatcherCaseNameT<'s, 't>) -> Self { IInstantiationNameT::OverrideDispatcherCase(x) }
}
impl<'s, 't> From<&'t ExternNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t ExternNameT<'s, 't>) -> Self { IInstantiationNameT::Extern(x) }
}
impl<'s, 't> From<&'t ExternFunctionNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t ExternFunctionNameT<'s, 't>) -> Self { IInstantiationNameT::ExternFunction(x) }
}
impl<'s, 't> From<&'t FunctionNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t FunctionNameT<'s, 't>) -> Self { IInstantiationNameT::Function(x) }
}
impl<'s, 't> From<&'t ForwarderFunctionNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t ForwarderFunctionNameT<'s, 't>) -> Self { IInstantiationNameT::ForwarderFunction(x) }
}
impl<'s, 't> From<&'t FunctionBoundNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t FunctionBoundNameT<'s, 't>) -> Self { IInstantiationNameT::FunctionBound(x) }
}
impl<'s, 't> From<&'t PredictedFunctionNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t PredictedFunctionNameT<'s, 't>) -> Self { IInstantiationNameT::PredictedFunction(x) }
}
impl<'s, 't> From<&'t LambdaCallFunctionNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t LambdaCallFunctionNameT<'s, 't>) -> Self { IInstantiationNameT::LambdaCallFunction(x) }
}
impl<'s, 't> From<&'t StructNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t StructNameT<'s, 't>) -> Self { IInstantiationNameT::Struct(x) }
}
impl<'s, 't> From<&'t InterfaceNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t InterfaceNameT<'s, 't>) -> Self { IInstantiationNameT::Interface(x) }
}
impl<'s, 't> From<&'t LambdaCitizenNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t LambdaCitizenNameT<'s, 't>) -> Self { IInstantiationNameT::LambdaCitizen(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructImplNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructImplNameT<'s, 't>) -> Self { IInstantiationNameT::AnonymousSubstructImpl(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructConstructorNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructConstructorNameT<'s, 't>) -> Self { IInstantiationNameT::AnonymousSubstructConstructor(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructNameT<'s, 't>) -> Self { IInstantiationNameT::AnonymousSubstruct(x) }
}

// -- Concrete → IFunctionTemplateNameT --------------------------------------
impl<'s, 't> From<&'t OverrideDispatcherTemplateNameT<'s, 't>> for IFunctionTemplateNameT<'s, 't> {
    fn from(x: &'t OverrideDispatcherTemplateNameT<'s, 't>) -> Self { IFunctionTemplateNameT::OverrideDispatcherTemplate(x) }
}
impl<'s, 't> From<&'t ExternFunctionNameT<'s, 't>> for IFunctionTemplateNameT<'s, 't> {
    fn from(x: &'t ExternFunctionNameT<'s, 't>) -> Self { IFunctionTemplateNameT::ExternFunction(x) }
}
impl<'s, 't> From<&'t FunctionBoundTemplateNameT<'s>> for IFunctionTemplateNameT<'s, 't> {
    fn from(x: &'t FunctionBoundTemplateNameT<'s>) -> Self { IFunctionTemplateNameT::FunctionBoundTemplate(x) }
}
impl<'s, 't> From<&'t PredictedFunctionTemplateNameT<'s>> for IFunctionTemplateNameT<'s, 't> {
    fn from(x: &'t PredictedFunctionTemplateNameT<'s>) -> Self { IFunctionTemplateNameT::PredictedFunctionTemplate(x) }
}
impl<'s, 't> From<&'t FunctionTemplateNameT<'s>> for IFunctionTemplateNameT<'s, 't> {
    fn from(x: &'t FunctionTemplateNameT<'s>) -> Self { IFunctionTemplateNameT::FunctionTemplate(x) }
}
impl<'s, 't> From<&'t LambdaCallFunctionTemplateNameT<'s, 't>> for IFunctionTemplateNameT<'s, 't> {
    fn from(x: &'t LambdaCallFunctionTemplateNameT<'s, 't>) -> Self { IFunctionTemplateNameT::LambdaCallFunctionTemplate(x) }
}
impl<'s, 't> From<&'t ForwarderFunctionTemplateNameT<'s, 't>> for IFunctionTemplateNameT<'s, 't> {
    fn from(x: &'t ForwarderFunctionTemplateNameT<'s, 't>) -> Self { IFunctionTemplateNameT::ForwarderFunctionTemplate(x) }
}
impl<'s, 't> From<&'t ConstructorTemplateNameT<'s>> for IFunctionTemplateNameT<'s, 't> {
    fn from(x: &'t ConstructorTemplateNameT<'s>) -> Self { IFunctionTemplateNameT::ConstructorTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructConstructorTemplateNameT<'s, 't>> for IFunctionTemplateNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructConstructorTemplateNameT<'s, 't>) -> Self { IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(x) }
}

// -- Concrete → IFunctionNameT -----------------------------------------------
impl<'s, 't> From<&'t OverrideDispatcherNameT<'s, 't>> for IFunctionNameT<'s, 't> {
    fn from(x: &'t OverrideDispatcherNameT<'s, 't>) -> Self { IFunctionNameT::OverrideDispatcher(x) }
}
impl<'s, 't> From<&'t ExternFunctionNameT<'s, 't>> for IFunctionNameT<'s, 't> {
    fn from(x: &'t ExternFunctionNameT<'s, 't>) -> Self { IFunctionNameT::ExternFunction(x) }
}
impl<'s, 't> From<&'t FunctionNameT<'s, 't>> for IFunctionNameT<'s, 't> {
    fn from(x: &'t FunctionNameT<'s, 't>) -> Self { IFunctionNameT::Function(x) }
}
impl<'s, 't> From<&'t ForwarderFunctionNameT<'s, 't>> for IFunctionNameT<'s, 't> {
    fn from(x: &'t ForwarderFunctionNameT<'s, 't>) -> Self { IFunctionNameT::ForwarderFunction(x) }
}
impl<'s, 't> From<&'t FunctionBoundNameT<'s, 't>> for IFunctionNameT<'s, 't> {
    fn from(x: &'t FunctionBoundNameT<'s, 't>) -> Self { IFunctionNameT::FunctionBound(x) }
}
impl<'s, 't> From<&'t PredictedFunctionNameT<'s, 't>> for IFunctionNameT<'s, 't> {
    fn from(x: &'t PredictedFunctionNameT<'s, 't>) -> Self { IFunctionNameT::PredictedFunction(x) }
}
impl<'s, 't> From<&'t LambdaCallFunctionNameT<'s, 't>> for IFunctionNameT<'s, 't> {
    fn from(x: &'t LambdaCallFunctionNameT<'s, 't>) -> Self { IFunctionNameT::LambdaCallFunction(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructConstructorNameT<'s, 't>> for IFunctionNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructConstructorNameT<'s, 't>) -> Self { IFunctionNameT::AnonymousSubstructConstructor(x) }
}

// -- Concrete → ISuperKindTemplateNameT --------------------------------------
impl<'s, 't> From<&'t KindPlaceholderTemplateNameT<'s>> for ISuperKindTemplateNameT<'s, 't> {
    fn from(x: &'t KindPlaceholderTemplateNameT<'s>) -> Self { ISuperKindTemplateNameT::KindPlaceholderTemplate(x) }
}
impl<'s, 't> From<&'t InterfaceTemplateNameT<'s>> for ISuperKindTemplateNameT<'s, 't> {
    fn from(x: &'t InterfaceTemplateNameT<'s>) -> Self { ISuperKindTemplateNameT::InterfaceTemplate(x) }
}

// -- Concrete → ISubKindTemplateNameT ----------------------------------------
impl<'s, 't> From<&'t StaticSizedArrayTemplateNameT> for ISubKindTemplateNameT<'s, 't> {
    fn from(x: &'t StaticSizedArrayTemplateNameT) -> Self { ISubKindTemplateNameT::StaticSizedArrayTemplate(x) }
}
impl<'s, 't> From<&'t RuntimeSizedArrayTemplateNameT> for ISubKindTemplateNameT<'s, 't> {
    fn from(x: &'t RuntimeSizedArrayTemplateNameT) -> Self { ISubKindTemplateNameT::RuntimeSizedArrayTemplate(x) }
}
impl<'s, 't> From<&'t KindPlaceholderTemplateNameT<'s>> for ISubKindTemplateNameT<'s, 't> {
    fn from(x: &'t KindPlaceholderTemplateNameT<'s>) -> Self { ISubKindTemplateNameT::KindPlaceholderTemplate(x) }
}
impl<'s, 't> From<&'t LambdaCitizenTemplateNameT<'s>> for ISubKindTemplateNameT<'s, 't> {
    fn from(x: &'t LambdaCitizenTemplateNameT<'s>) -> Self { ISubKindTemplateNameT::LambdaCitizenTemplate(x) }
}
impl<'s, 't> From<&'t StructTemplateNameT<'s>> for ISubKindTemplateNameT<'s, 't> {
    fn from(x: &'t StructTemplateNameT<'s>) -> Self { ISubKindTemplateNameT::StructTemplate(x) }
}
impl<'s, 't> From<&'t InterfaceTemplateNameT<'s>> for ISubKindTemplateNameT<'s, 't> {
    fn from(x: &'t InterfaceTemplateNameT<'s>) -> Self { ISubKindTemplateNameT::InterfaceTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructTemplateNameT<'s, 't>> for ISubKindTemplateNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructTemplateNameT<'s, 't>) -> Self { ISubKindTemplateNameT::AnonymousSubstructTemplate(x) }
}

// -- Concrete → ICitizenTemplateNameT ----------------------------------------
impl<'s, 't> From<&'t StaticSizedArrayTemplateNameT> for ICitizenTemplateNameT<'s, 't> {
    fn from(x: &'t StaticSizedArrayTemplateNameT) -> Self { ICitizenTemplateNameT::StaticSizedArrayTemplate(x) }
}
impl<'s, 't> From<&'t RuntimeSizedArrayTemplateNameT> for ICitizenTemplateNameT<'s, 't> {
    fn from(x: &'t RuntimeSizedArrayTemplateNameT) -> Self { ICitizenTemplateNameT::RuntimeSizedArrayTemplate(x) }
}
impl<'s, 't> From<&'t LambdaCitizenTemplateNameT<'s>> for ICitizenTemplateNameT<'s, 't> {
    fn from(x: &'t LambdaCitizenTemplateNameT<'s>) -> Self { ICitizenTemplateNameT::LambdaCitizenTemplate(x) }
}
impl<'s, 't> From<&'t StructTemplateNameT<'s>> for ICitizenTemplateNameT<'s, 't> {
    fn from(x: &'t StructTemplateNameT<'s>) -> Self { ICitizenTemplateNameT::StructTemplate(x) }
}
impl<'s, 't> From<&'t InterfaceTemplateNameT<'s>> for ICitizenTemplateNameT<'s, 't> {
    fn from(x: &'t InterfaceTemplateNameT<'s>) -> Self { ICitizenTemplateNameT::InterfaceTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructTemplateNameT<'s, 't>> for ICitizenTemplateNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructTemplateNameT<'s, 't>) -> Self { ICitizenTemplateNameT::AnonymousSubstructTemplate(x) }
}

// -- Concrete → IStructTemplateNameT -----------------------------------------
impl<'s, 't> From<&'t LambdaCitizenTemplateNameT<'s>> for IStructTemplateNameT<'s, 't> {
    fn from(x: &'t LambdaCitizenTemplateNameT<'s>) -> Self { IStructTemplateNameT::LambdaCitizenTemplate(x) }
}
impl<'s, 't> From<&'t StructTemplateNameT<'s>> for IStructTemplateNameT<'s, 't> {
    fn from(x: &'t StructTemplateNameT<'s>) -> Self { IStructTemplateNameT::StructTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructTemplateNameT<'s, 't>> for IStructTemplateNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructTemplateNameT<'s, 't>) -> Self { IStructTemplateNameT::AnonymousSubstructTemplate(x) }
}

// -- Concrete → IInterfaceTemplateNameT --------------------------------------
impl<'s, 't> From<&'t InterfaceTemplateNameT<'s>> for IInterfaceTemplateNameT<'s, 't> {
    fn from(x: &'t InterfaceTemplateNameT<'s>) -> Self { IInterfaceTemplateNameT::InterfaceTemplate(x) }
}

// -- Concrete → ISuperKindNameT ----------------------------------------------
impl<'s, 't> From<&'t KindPlaceholderNameT<'s, 't>> for ISuperKindNameT<'s, 't> {
    fn from(x: &'t KindPlaceholderNameT<'s, 't>) -> Self { ISuperKindNameT::KindPlaceholder(x) }
}
impl<'s, 't> From<&'t InterfaceNameT<'s, 't>> for ISuperKindNameT<'s, 't> {
    fn from(x: &'t InterfaceNameT<'s, 't>) -> Self { ISuperKindNameT::Interface(x) }
}

// -- Concrete → ISubKindNameT ------------------------------------------------
impl<'s, 't> From<&'t StaticSizedArrayNameT<'s, 't>> for ISubKindNameT<'s, 't> {
    fn from(x: &'t StaticSizedArrayNameT<'s, 't>) -> Self { ISubKindNameT::StaticSizedArray(x) }
}
impl<'s, 't> From<&'t RuntimeSizedArrayNameT<'s, 't>> for ISubKindNameT<'s, 't> {
    fn from(x: &'t RuntimeSizedArrayNameT<'s, 't>) -> Self { ISubKindNameT::RuntimeSizedArray(x) }
}
impl<'s, 't> From<&'t KindPlaceholderNameT<'s, 't>> for ISubKindNameT<'s, 't> {
    fn from(x: &'t KindPlaceholderNameT<'s, 't>) -> Self { ISubKindNameT::KindPlaceholder(x) }
}
impl<'s, 't> From<&'t StructNameT<'s, 't>> for ISubKindNameT<'s, 't> {
    fn from(x: &'t StructNameT<'s, 't>) -> Self { ISubKindNameT::Struct(x) }
}
impl<'s, 't> From<&'t InterfaceNameT<'s, 't>> for ISubKindNameT<'s, 't> {
    fn from(x: &'t InterfaceNameT<'s, 't>) -> Self { ISubKindNameT::Interface(x) }
}
impl<'s, 't> From<&'t LambdaCitizenNameT<'s, 't>> for ISubKindNameT<'s, 't> {
    fn from(x: &'t LambdaCitizenNameT<'s, 't>) -> Self { ISubKindNameT::LambdaCitizen(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructNameT<'s, 't>> for ISubKindNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructNameT<'s, 't>) -> Self { ISubKindNameT::AnonymousSubstruct(x) }
}

// -- Concrete → ICitizenNameT ------------------------------------------------
impl<'s, 't> From<&'t StaticSizedArrayNameT<'s, 't>> for ICitizenNameT<'s, 't> {
    fn from(x: &'t StaticSizedArrayNameT<'s, 't>) -> Self { ICitizenNameT::StaticSizedArray(x) }
}
impl<'s, 't> From<&'t RuntimeSizedArrayNameT<'s, 't>> for ICitizenNameT<'s, 't> {
    fn from(x: &'t RuntimeSizedArrayNameT<'s, 't>) -> Self { ICitizenNameT::RuntimeSizedArray(x) }
}
impl<'s, 't> From<&'t StructNameT<'s, 't>> for ICitizenNameT<'s, 't> {
    fn from(x: &'t StructNameT<'s, 't>) -> Self { ICitizenNameT::Struct(x) }
}
impl<'s, 't> From<&'t InterfaceNameT<'s, 't>> for ICitizenNameT<'s, 't> {
    fn from(x: &'t InterfaceNameT<'s, 't>) -> Self { ICitizenNameT::Interface(x) }
}
impl<'s, 't> From<&'t LambdaCitizenNameT<'s, 't>> for ICitizenNameT<'s, 't> {
    fn from(x: &'t LambdaCitizenNameT<'s, 't>) -> Self { ICitizenNameT::LambdaCitizen(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructNameT<'s, 't>> for ICitizenNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructNameT<'s, 't>) -> Self { ICitizenNameT::AnonymousSubstruct(x) }
}

// -- Concrete → IStructNameT -------------------------------------------------
impl<'s, 't> From<&'t StructNameT<'s, 't>> for IStructNameT<'s, 't> {
    fn from(x: &'t StructNameT<'s, 't>) -> Self { IStructNameT::Struct(x) }
}
impl<'s, 't> From<&'t LambdaCitizenNameT<'s, 't>> for IStructNameT<'s, 't> {
    fn from(x: &'t LambdaCitizenNameT<'s, 't>) -> Self { IStructNameT::LambdaCitizen(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructNameT<'s, 't>> for IStructNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructNameT<'s, 't>) -> Self { IStructNameT::AnonymousSubstruct(x) }
}

// -- Concrete → IInterfaceNameT ----------------------------------------------
impl<'s, 't> From<&'t InterfaceNameT<'s, 't>> for IInterfaceNameT<'s, 't> {
    fn from(x: &'t InterfaceNameT<'s, 't>) -> Self { IInterfaceNameT::Interface(x) }
}

// -- Concrete → IImplTemplateNameT -------------------------------------------
impl<'s, 't> From<&'t ImplTemplateNameT<'s>> for IImplTemplateNameT<'s, 't> {
    fn from(x: &'t ImplTemplateNameT<'s>) -> Self { IImplTemplateNameT::ImplTemplate(x) }
}
impl<'s, 't> From<&'t ImplBoundTemplateNameT<'s>> for IImplTemplateNameT<'s, 't> {
    fn from(x: &'t ImplBoundTemplateNameT<'s>) -> Self { IImplTemplateNameT::ImplBoundTemplate(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructImplTemplateNameT<'s, 't>> for IImplTemplateNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructImplTemplateNameT<'s, 't>) -> Self { IImplTemplateNameT::AnonymousSubstructImplTemplate(x) }
}

// -- Concrete → IImplNameT ---------------------------------------------------
impl<'s, 't> From<&'t ImplNameT<'s, 't>> for IImplNameT<'s, 't> {
    fn from(x: &'t ImplNameT<'s, 't>) -> Self { IImplNameT::Impl(x) }
}
impl<'s, 't> From<&'t ImplBoundNameT<'s, 't>> for IImplNameT<'s, 't> {
    fn from(x: &'t ImplBoundNameT<'s, 't>) -> Self { IImplNameT::ImplBound(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructImplNameT<'s, 't>> for IImplNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructImplNameT<'s, 't>) -> Self { IImplNameT::AnonymousSubstructImpl(x) }
}

// -- Concrete → IPlaceholderNameT --------------------------------------------
impl<'s, 't> From<&'t KindPlaceholderNameT<'s, 't>> for IPlaceholderNameT<'s, 't> {
    fn from(x: &'t KindPlaceholderNameT<'s, 't>) -> Self { IPlaceholderNameT::KindPlaceholder(x) }
}
impl<'s, 't> From<&'t NonKindNonRegionPlaceholderNameT<'s>> for IPlaceholderNameT<'s, 't> {
    fn from(x: &'t NonKindNonRegionPlaceholderNameT<'s>) -> Self { IPlaceholderNameT::NonKindNonRegionPlaceholder(x) }
}

// -- Concrete → IVarNameT ----------------------------------------------------
impl<'s, 't> From<&'t TypingPassBlockResultVarNameT<'t>> for IVarNameT<'s, 't> {
    fn from(x: &'t TypingPassBlockResultVarNameT<'t>) -> Self { IVarNameT::TypingPassBlockResultVar(x) }
}
impl<'s, 't> From<&'t TypingPassFunctionResultVarNameT> for IVarNameT<'s, 't> {
    fn from(x: &'t TypingPassFunctionResultVarNameT) -> Self { IVarNameT::TypingPassFunctionResultVar(x) }
}
impl<'s, 't> From<&'t TypingPassTemporaryVarNameT<'t>> for IVarNameT<'s, 't> {
    fn from(x: &'t TypingPassTemporaryVarNameT<'t>) -> Self { IVarNameT::TypingPassTemporaryVar(x) }
}
impl<'s, 't> From<&'t TypingPassPatternMemberNameT<'t>> for IVarNameT<'s, 't> {
    fn from(x: &'t TypingPassPatternMemberNameT<'t>) -> Self { IVarNameT::TypingPassPatternMember(x) }
}
impl<'s, 't> From<&'t TypingIgnoredParamNameT> for IVarNameT<'s, 't> {
    fn from(x: &'t TypingIgnoredParamNameT) -> Self { IVarNameT::TypingIgnoredParam(x) }
}
impl<'s, 't> From<&'t TypingPassPatternDestructureeNameT<'t>> for IVarNameT<'s, 't> {
    fn from(x: &'t TypingPassPatternDestructureeNameT<'t>) -> Self { IVarNameT::TypingPassPatternDestructuree(x) }
}
impl<'s, 't> From<&'t UnnamedLocalNameT<'s>> for IVarNameT<'s, 't> {
    fn from(x: &'t UnnamedLocalNameT<'s>) -> Self { IVarNameT::UnnamedLocal(x) }
}
impl<'s, 't> From<&'t ClosureParamNameT<'s>> for IVarNameT<'s, 't> {
    fn from(x: &'t ClosureParamNameT<'s>) -> Self { IVarNameT::ClosureParam(x) }
}
impl<'s, 't> From<&'t ConstructingMemberNameT<'s>> for IVarNameT<'s, 't> {
    fn from(x: &'t ConstructingMemberNameT<'s>) -> Self { IVarNameT::ConstructingMember(x) }
}
impl<'s, 't> From<&'t WhileCondResultNameT<'s>> for IVarNameT<'s, 't> {
    fn from(x: &'t WhileCondResultNameT<'s>) -> Self { IVarNameT::WhileCondResult(x) }
}
impl<'s, 't> From<&'t IterableNameT<'s>> for IVarNameT<'s, 't> {
    fn from(x: &'t IterableNameT<'s>) -> Self { IVarNameT::Iterable(x) }
}
impl<'s, 't> From<&'t IteratorNameT<'s>> for IVarNameT<'s, 't> {
    fn from(x: &'t IteratorNameT<'s>) -> Self { IVarNameT::Iterator(x) }
}
impl<'s, 't> From<&'t IterationOptionNameT<'s>> for IVarNameT<'s, 't> {
    fn from(x: &'t IterationOptionNameT<'s>) -> Self { IVarNameT::IterationOption(x) }
}
impl<'s, 't> From<&'t MagicParamNameT<'s>> for IVarNameT<'s, 't> {
    fn from(x: &'t MagicParamNameT<'s>) -> Self { IVarNameT::MagicParam(x) }
}
impl<'s, 't> From<&'t CodeVarNameT<'s>> for IVarNameT<'s, 't> {
    fn from(x: &'t CodeVarNameT<'s>) -> Self { IVarNameT::CodeVar(x) }
}
impl<'s, 't> From<&'t AnonymousSubstructMemberNameT> for IVarNameT<'s, 't> {
    fn from(x: &'t AnonymousSubstructMemberNameT) -> Self { IVarNameT::AnonymousSubstructMember(x) }
}
impl<'s, 't> From<&'t SelfNameT> for IVarNameT<'s, 't> {
    fn from(x: &'t SelfNameT) -> Self { IVarNameT::Self_(x) }
}

// -- Concrete → CitizenNameT / CitizenTemplateNameT --------------------------
impl<'s, 't> From<&'t StructNameT<'s, 't>> for CitizenNameT<'s, 't> {
    fn from(x: &'t StructNameT<'s, 't>) -> Self { CitizenNameT::Struct(x) }
}
impl<'s, 't> From<&'t InterfaceNameT<'s, 't>> for CitizenNameT<'s, 't> {
    fn from(x: &'t InterfaceNameT<'s, 't>) -> Self { CitizenNameT::Interface(x) }
}
impl<'s, 't> From<&'t StructTemplateNameT<'s>> for CitizenTemplateNameT<'s, 't> {
    fn from(x: &'t StructTemplateNameT<'s>) -> Self { CitizenTemplateNameT::StructTemplate(x) }
}
impl<'s, 't> From<&'t InterfaceTemplateNameT<'s>> for CitizenTemplateNameT<'s, 't> {
    fn from(x: &'t InterfaceTemplateNameT<'s>) -> Self { CitizenTemplateNameT::InterfaceTemplate(x) }
}

// -- Sub-enum → wider sub-enum (owned input, cascade via .into() on inner ref) --

impl<'s, 't> From<IFunctionTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(f: IFunctionTemplateNameT<'s, 't>) -> Self {
        match f {
            IFunctionTemplateNameT::OverrideDispatcherTemplate(x) => x.into(),
            IFunctionTemplateNameT::ExternFunction(x) => x.into(),
            IFunctionTemplateNameT::FunctionBoundTemplate(x) => x.into(),
            IFunctionTemplateNameT::PredictedFunctionTemplate(x) => x.into(),
            IFunctionTemplateNameT::FunctionTemplate(x) => x.into(),
            IFunctionTemplateNameT::LambdaCallFunctionTemplate(x) => x.into(),
            IFunctionTemplateNameT::ForwarderFunctionTemplate(x) => x.into(),
            IFunctionTemplateNameT::ConstructorTemplate(x) => x.into(),
            IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IFunctionNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(f: IFunctionNameT<'s, 't>) -> Self {
        match f {
            IFunctionNameT::OverrideDispatcher(x) => x.into(),
            IFunctionNameT::ExternFunction(x) => x.into(),
            IFunctionNameT::Function(x) => x.into(),
            IFunctionNameT::ForwarderFunction(x) => x.into(),
            IFunctionNameT::FunctionBound(x) => x.into(),
            IFunctionNameT::PredictedFunction(x) => x.into(),
            IFunctionNameT::LambdaCallFunction(x) => x.into(),
            IFunctionNameT::AnonymousSubstructConstructor(x) => x.into(),
        }
    }
}

impl<'s, 't> From<ISuperKindTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(f: ISuperKindTemplateNameT<'s, 't>) -> Self {
        match f {
            ISuperKindTemplateNameT::KindPlaceholderTemplate(x) => x.into(),
            ISuperKindTemplateNameT::InterfaceTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<ISubKindTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(f: ISubKindTemplateNameT<'s, 't>) -> Self {
        match f {
            ISubKindTemplateNameT::StaticSizedArrayTemplate(x) => x.into(),
            ISubKindTemplateNameT::RuntimeSizedArrayTemplate(x) => x.into(),
            ISubKindTemplateNameT::KindPlaceholderTemplate(x) => x.into(),
            ISubKindTemplateNameT::LambdaCitizenTemplate(x) => x.into(),
            ISubKindTemplateNameT::StructTemplate(x) => x.into(),
            ISubKindTemplateNameT::InterfaceTemplate(x) => x.into(),
            ISubKindTemplateNameT::AnonymousSubstructTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<ICitizenTemplateNameT<'s, 't>> for ISubKindTemplateNameT<'s, 't> {
    fn from(f: ICitizenTemplateNameT<'s, 't>) -> Self {
        match f {
            ICitizenTemplateNameT::StaticSizedArrayTemplate(x) => x.into(),
            ICitizenTemplateNameT::RuntimeSizedArrayTemplate(x) => x.into(),
            ICitizenTemplateNameT::LambdaCitizenTemplate(x) => x.into(),
            ICitizenTemplateNameT::StructTemplate(x) => x.into(),
            ICitizenTemplateNameT::InterfaceTemplate(x) => x.into(),
            ICitizenTemplateNameT::AnonymousSubstructTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IStructTemplateNameT<'s, 't>> for ICitizenTemplateNameT<'s, 't> {
    fn from(f: IStructTemplateNameT<'s, 't>) -> Self {
        match f {
            IStructTemplateNameT::LambdaCitizenTemplate(x) => x.into(),
            IStructTemplateNameT::StructTemplate(x) => x.into(),
            IStructTemplateNameT::AnonymousSubstructTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IInterfaceTemplateNameT<'s, 't>> for ICitizenTemplateNameT<'s, 't> {
    fn from(f: IInterfaceTemplateNameT<'s, 't>) -> Self {
        match f {
            IInterfaceTemplateNameT::InterfaceTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IInterfaceTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(f: IInterfaceTemplateNameT<'s, 't>) -> Self {
        match f {
            IInterfaceTemplateNameT::InterfaceTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<ISuperKindNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(f: ISuperKindNameT<'s, 't>) -> Self {
        match f {
            ISuperKindNameT::KindPlaceholder(x) => x.into(),
            ISuperKindNameT::Interface(x) => x.into(),
        }
    }
}

impl<'s, 't> From<ISubKindNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(f: ISubKindNameT<'s, 't>) -> Self {
        match f {
            ISubKindNameT::StaticSizedArray(x) => x.into(),
            ISubKindNameT::RuntimeSizedArray(x) => x.into(),
            ISubKindNameT::KindPlaceholder(x) => x.into(),
            ISubKindNameT::Struct(x) => x.into(),
            ISubKindNameT::Interface(x) => x.into(),
            ISubKindNameT::LambdaCitizen(x) => x.into(),
            ISubKindNameT::AnonymousSubstruct(x) => x.into(),
        }
    }
}

impl<'s, 't> From<ICitizenNameT<'s, 't>> for ISubKindNameT<'s, 't> {
    fn from(f: ICitizenNameT<'s, 't>) -> Self {
        match f {
            ICitizenNameT::StaticSizedArray(x) => x.into(),
            ICitizenNameT::RuntimeSizedArray(x) => x.into(),
            ICitizenNameT::Struct(x) => x.into(),
            ICitizenNameT::Interface(x) => x.into(),
            ICitizenNameT::LambdaCitizen(x) => x.into(),
            ICitizenNameT::AnonymousSubstruct(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IStructNameT<'s, 't>> for ICitizenNameT<'s, 't> {
    fn from(f: IStructNameT<'s, 't>) -> Self {
        match f {
            IStructNameT::Struct(x) => x.into(),
            IStructNameT::LambdaCitizen(x) => x.into(),
            IStructNameT::AnonymousSubstruct(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IInterfaceNameT<'s, 't>> for ICitizenNameT<'s, 't> {
    fn from(f: IInterfaceNameT<'s, 't>) -> Self {
        match f {
            IInterfaceNameT::Interface(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IImplTemplateNameT<'s, 't>> for ITemplateNameT<'s, 't> {
    fn from(f: IImplTemplateNameT<'s, 't>) -> Self {
        match f {
            IImplTemplateNameT::ImplTemplate(x) => x.into(),
            IImplTemplateNameT::ImplBoundTemplate(x) => x.into(),
            IImplTemplateNameT::AnonymousSubstructImplTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IImplNameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    fn from(f: IImplNameT<'s, 't>) -> Self {
        match f {
            IImplNameT::Impl(x) => x.into(),
            IImplNameT::ImplBound(x) => x.into(),
            IImplNameT::AnonymousSubstructImpl(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IPlaceholderNameT<'s, 't>> for INameT<'s, 't> {
    fn from(f: IPlaceholderNameT<'s, 't>) -> Self {
        match f {
            IPlaceholderNameT::KindPlaceholder(x) => x.into(),
            IPlaceholderNameT::NonKindNonRegionPlaceholder(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IVarNameT<'s, 't>> for INameT<'s, 't> {
    fn from(f: IVarNameT<'s, 't>) -> Self {
        match f {
            IVarNameT::TypingPassBlockResultVar(x) => x.into(),
            IVarNameT::TypingPassFunctionResultVar(x) => x.into(),
            IVarNameT::TypingPassTemporaryVar(x) => x.into(),
            IVarNameT::TypingPassPatternMember(x) => x.into(),
            IVarNameT::TypingIgnoredParam(x) => x.into(),
            IVarNameT::TypingPassPatternDestructuree(x) => x.into(),
            IVarNameT::UnnamedLocal(x) => x.into(),
            IVarNameT::ClosureParam(x) => x.into(),
            IVarNameT::ConstructingMember(x) => x.into(),
            IVarNameT::WhileCondResult(x) => x.into(),
            IVarNameT::Iterable(x) => x.into(),
            IVarNameT::Iterator(x) => x.into(),
            IVarNameT::IterationOption(x) => x.into(),
            IVarNameT::MagicParam(x) => x.into(),
            IVarNameT::CodeVar(x) => x.into(),
            IVarNameT::AnonymousSubstructMember(x) => x.into(),
            IVarNameT::Self_(x) => x.into(),
        }
    }
}

impl<'s, 't> From<ITemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(f: ITemplateNameT<'s, 't>) -> Self {
        match f {
            ITemplateNameT::ExportTemplate(x) => x.into(),
            ITemplateNameT::ImplTemplate(x) => x.into(),
            ITemplateNameT::ImplBoundTemplate(x) => x.into(),
            ITemplateNameT::StaticSizedArrayTemplate(x) => x.into(),
            ITemplateNameT::RuntimeSizedArrayTemplate(x) => x.into(),
            ITemplateNameT::KindPlaceholderTemplate(x) => x.into(),
            ITemplateNameT::OverrideDispatcherTemplate(x) => x.into(),
            ITemplateNameT::OverrideDispatcherCase(x) => x.into(),
            ITemplateNameT::ExternTemplate(x) => x.into(),
            ITemplateNameT::ExternFunction(x) => x.into(),
            ITemplateNameT::FunctionBoundTemplate(x) => x.into(),
            ITemplateNameT::PredictedFunctionTemplate(x) => x.into(),
            ITemplateNameT::FunctionTemplate(x) => x.into(),
            ITemplateNameT::LambdaCallFunctionTemplate(x) => x.into(),
            ITemplateNameT::ForwarderFunctionTemplate(x) => x.into(),
            ITemplateNameT::ConstructorTemplate(x) => x.into(),
            ITemplateNameT::LambdaCitizenTemplate(x) => x.into(),
            ITemplateNameT::StructTemplate(x) => x.into(),
            ITemplateNameT::InterfaceTemplate(x) => x.into(),
            ITemplateNameT::AnonymousSubstructImplTemplate(x) => x.into(),
            ITemplateNameT::AnonymousSubstructTemplate(x) => x.into(),
            ITemplateNameT::AnonymousSubstructConstructorTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IStructTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(f: IStructTemplateNameT<'s, 't>) -> Self {
        match f {
            IStructTemplateNameT::StructTemplate(x) => x.into(),
            IStructTemplateNameT::LambdaCitizenTemplate(x) => x.into(),
            IStructTemplateNameT::AnonymousSubstructTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IFunctionTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(f: IFunctionTemplateNameT<'s, 't>) -> Self {
        match f {
            IFunctionTemplateNameT::OverrideDispatcherTemplate(x) => x.into(),
            IFunctionTemplateNameT::ExternFunction(x) => x.into(),
            IFunctionTemplateNameT::FunctionBoundTemplate(x) => x.into(),
            IFunctionTemplateNameT::PredictedFunctionTemplate(x) => x.into(),
            IFunctionTemplateNameT::FunctionTemplate(x) => x.into(),
            IFunctionTemplateNameT::LambdaCallFunctionTemplate(x) => x.into(),
            IFunctionTemplateNameT::ForwarderFunctionTemplate(x) => x.into(),
            IFunctionTemplateNameT::ConstructorTemplate(x) => x.into(),
            IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IImplTemplateNameT<'s, 't>> for INameT<'s, 't> {
    fn from(f: IImplTemplateNameT<'s, 't>) -> Self {
        match f {
            IImplTemplateNameT::ImplTemplate(x) => x.into(),
            IImplTemplateNameT::ImplBoundTemplate(x) => x.into(),
            IImplTemplateNameT::AnonymousSubstructImplTemplate(x) => x.into(),
        }
    }
}

impl<'s, 't> From<IInstantiationNameT<'s, 't>> for INameT<'s, 't> {
    fn from(f: IInstantiationNameT<'s, 't>) -> Self {
        match f {
            IInstantiationNameT::Export(x) => x.into(),
            IInstantiationNameT::Impl(x) => x.into(),
            IInstantiationNameT::ImplBound(x) => x.into(),
            IInstantiationNameT::StaticSizedArray(x) => x.into(),
            IInstantiationNameT::RuntimeSizedArray(x) => x.into(),
            IInstantiationNameT::KindPlaceholder(x) => x.into(),
            IInstantiationNameT::OverrideDispatcher(x) => x.into(),
            IInstantiationNameT::OverrideDispatcherCase(x) => x.into(),
            IInstantiationNameT::Extern(x) => x.into(),
            IInstantiationNameT::ExternFunction(x) => x.into(),
            IInstantiationNameT::Function(x) => x.into(),
            IInstantiationNameT::ForwarderFunction(x) => x.into(),
            IInstantiationNameT::FunctionBound(x) => x.into(),
            IInstantiationNameT::PredictedFunction(x) => x.into(),
            IInstantiationNameT::LambdaCallFunction(x) => x.into(),
            IInstantiationNameT::Struct(x) => x.into(),
            IInstantiationNameT::Interface(x) => x.into(),
            IInstantiationNameT::LambdaCitizen(x) => x.into(),
            IInstantiationNameT::AnonymousSubstructImpl(x) => x.into(),
            IInstantiationNameT::AnonymousSubstructConstructor(x) => x.into(),
            IInstantiationNameT::AnonymousSubstruct(x) => x.into(),
        }
    }
}

impl<'s, 't> From<CitizenNameT<'s, 't>> for ICitizenNameT<'s, 't> {
    fn from(f: CitizenNameT<'s, 't>) -> Self {
        match f {
            CitizenNameT::Struct(x) => x.into(),
            CitizenNameT::Interface(x) => x.into(),
        }
    }
}

impl<'s, 't> From<CitizenTemplateNameT<'s, 't>> for ICitizenTemplateNameT<'s, 't> {
    fn from(f: CitizenTemplateNameT<'s, 't>) -> Self {
        match f {
            CitizenTemplateNameT::StructTemplate(x) => x.into(),
            CitizenTemplateNameT::InterfaceTemplate(x) => x.into(),
        }
    }
}

// -- TryFrom<INameT> for IYyyNameT (wide → narrow, owned values, no interner) --
// These are free stack-only conversions under the inline-owned sub-enum design:
// pattern-match INameT, pick the variants that belong to the narrower sub-enum,
// and rewrap. No arena allocation needed.

impl<'s, 't> TryFrom<INameT<'s, 't>> for ITemplateNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::ExportTemplate(x) => Ok(ITemplateNameT::ExportTemplate(x)),
            INameT::ImplTemplate(x) => Ok(ITemplateNameT::ImplTemplate(x)),
            INameT::ImplBoundTemplate(x) => Ok(ITemplateNameT::ImplBoundTemplate(x)),
            INameT::StaticSizedArrayTemplate(x) => Ok(ITemplateNameT::StaticSizedArrayTemplate(x)),
            INameT::RuntimeSizedArrayTemplate(x) => Ok(ITemplateNameT::RuntimeSizedArrayTemplate(x)),
            INameT::KindPlaceholderTemplate(x) => Ok(ITemplateNameT::KindPlaceholderTemplate(x)),
            INameT::OverrideDispatcherTemplate(x) => Ok(ITemplateNameT::OverrideDispatcherTemplate(x)),
            INameT::OverrideDispatcherCase(x) => Ok(ITemplateNameT::OverrideDispatcherCase(x)),
            INameT::ExternTemplate(x) => Ok(ITemplateNameT::ExternTemplate(x)),
            INameT::ExternFunction(x) => Ok(ITemplateNameT::ExternFunction(x)),
            INameT::FunctionBoundTemplate(x) => Ok(ITemplateNameT::FunctionBoundTemplate(x)),
            INameT::PredictedFunctionTemplate(x) => Ok(ITemplateNameT::PredictedFunctionTemplate(x)),
            INameT::FunctionTemplate(x) => Ok(ITemplateNameT::FunctionTemplate(x)),
            INameT::LambdaCallFunctionTemplate(x) => Ok(ITemplateNameT::LambdaCallFunctionTemplate(x)),
            INameT::ForwarderFunctionTemplate(x) => Ok(ITemplateNameT::ForwarderFunctionTemplate(x)),
            INameT::ConstructorTemplate(x) => Ok(ITemplateNameT::ConstructorTemplate(x)),
            INameT::LambdaCitizenTemplate(x) => Ok(ITemplateNameT::LambdaCitizenTemplate(x)),
            INameT::StructTemplate(x) => Ok(ITemplateNameT::StructTemplate(x)),
            INameT::InterfaceTemplate(x) => Ok(ITemplateNameT::InterfaceTemplate(x)),
            INameT::AnonymousSubstructImplTemplate(x) => Ok(ITemplateNameT::AnonymousSubstructImplTemplate(x)),
            INameT::AnonymousSubstructTemplate(x) => Ok(ITemplateNameT::AnonymousSubstructTemplate(x)),
            INameT::AnonymousSubstructConstructorTemplate(x) => Ok(ITemplateNameT::AnonymousSubstructConstructorTemplate(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IInstantiationNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::Export(x) => Ok(IInstantiationNameT::Export(x)),
            INameT::Impl(x) => Ok(IInstantiationNameT::Impl(x)),
            INameT::ImplBound(x) => Ok(IInstantiationNameT::ImplBound(x)),
            INameT::StaticSizedArray(x) => Ok(IInstantiationNameT::StaticSizedArray(x)),
            INameT::RuntimeSizedArray(x) => Ok(IInstantiationNameT::RuntimeSizedArray(x)),
            INameT::KindPlaceholder(x) => Ok(IInstantiationNameT::KindPlaceholder(x)),
            INameT::OverrideDispatcher(x) => Ok(IInstantiationNameT::OverrideDispatcher(x)),
            INameT::OverrideDispatcherCase(x) => Ok(IInstantiationNameT::OverrideDispatcherCase(x)),
            INameT::Extern(x) => Ok(IInstantiationNameT::Extern(x)),
            INameT::ExternFunction(x) => Ok(IInstantiationNameT::ExternFunction(x)),
            INameT::Function(x) => Ok(IInstantiationNameT::Function(x)),
            INameT::ForwarderFunction(x) => Ok(IInstantiationNameT::ForwarderFunction(x)),
            INameT::FunctionBound(x) => Ok(IInstantiationNameT::FunctionBound(x)),
            INameT::PredictedFunction(x) => Ok(IInstantiationNameT::PredictedFunction(x)),
            INameT::LambdaCallFunction(x) => Ok(IInstantiationNameT::LambdaCallFunction(x)),
            INameT::Struct(x) => Ok(IInstantiationNameT::Struct(x)),
            INameT::Interface(x) => Ok(IInstantiationNameT::Interface(x)),
            INameT::LambdaCitizen(x) => Ok(IInstantiationNameT::LambdaCitizen(x)),
            INameT::AnonymousSubstructImpl(x) => Ok(IInstantiationNameT::AnonymousSubstructImpl(x)),
            INameT::AnonymousSubstructConstructor(x) => Ok(IInstantiationNameT::AnonymousSubstructConstructor(x)),
            INameT::AnonymousSubstruct(x) => Ok(IInstantiationNameT::AnonymousSubstruct(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IFunctionTemplateNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::OverrideDispatcherTemplate(x) => Ok(IFunctionTemplateNameT::OverrideDispatcherTemplate(x)),
            INameT::ExternFunction(x) => Ok(IFunctionTemplateNameT::ExternFunction(x)),
            INameT::FunctionBoundTemplate(x) => Ok(IFunctionTemplateNameT::FunctionBoundTemplate(x)),
            INameT::PredictedFunctionTemplate(x) => Ok(IFunctionTemplateNameT::PredictedFunctionTemplate(x)),
            INameT::FunctionTemplate(x) => Ok(IFunctionTemplateNameT::FunctionTemplate(x)),
            INameT::LambdaCallFunctionTemplate(x) => Ok(IFunctionTemplateNameT::LambdaCallFunctionTemplate(x)),
            INameT::ForwarderFunctionTemplate(x) => Ok(IFunctionTemplateNameT::ForwarderFunctionTemplate(x)),
            INameT::ConstructorTemplate(x) => Ok(IFunctionTemplateNameT::ConstructorTemplate(x)),
            INameT::AnonymousSubstructConstructorTemplate(x) => Ok(IFunctionTemplateNameT::AnonymousSubstructConstructorTemplate(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IFunctionNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::OverrideDispatcher(x) => Ok(IFunctionNameT::OverrideDispatcher(x)),
            INameT::ExternFunction(x) => Ok(IFunctionNameT::ExternFunction(x)),
            INameT::Function(x) => Ok(IFunctionNameT::Function(x)),
            INameT::ForwarderFunction(x) => Ok(IFunctionNameT::ForwarderFunction(x)),
            INameT::FunctionBound(x) => Ok(IFunctionNameT::FunctionBound(x)),
            INameT::PredictedFunction(x) => Ok(IFunctionNameT::PredictedFunction(x)),
            INameT::LambdaCallFunction(x) => Ok(IFunctionNameT::LambdaCallFunction(x)),
            INameT::AnonymousSubstructConstructor(x) => Ok(IFunctionNameT::AnonymousSubstructConstructor(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for ISuperKindTemplateNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::KindPlaceholderTemplate(x) => Ok(ISuperKindTemplateNameT::KindPlaceholderTemplate(x)),
            INameT::InterfaceTemplate(x) => Ok(ISuperKindTemplateNameT::InterfaceTemplate(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for ISubKindTemplateNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::StaticSizedArrayTemplate(x) => Ok(ISubKindTemplateNameT::StaticSizedArrayTemplate(x)),
            INameT::RuntimeSizedArrayTemplate(x) => Ok(ISubKindTemplateNameT::RuntimeSizedArrayTemplate(x)),
            INameT::KindPlaceholderTemplate(x) => Ok(ISubKindTemplateNameT::KindPlaceholderTemplate(x)),
            INameT::LambdaCitizenTemplate(x) => Ok(ISubKindTemplateNameT::LambdaCitizenTemplate(x)),
            INameT::StructTemplate(x) => Ok(ISubKindTemplateNameT::StructTemplate(x)),
            INameT::InterfaceTemplate(x) => Ok(ISubKindTemplateNameT::InterfaceTemplate(x)),
            INameT::AnonymousSubstructTemplate(x) => Ok(ISubKindTemplateNameT::AnonymousSubstructTemplate(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for ICitizenTemplateNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::StaticSizedArrayTemplate(x) => Ok(ICitizenTemplateNameT::StaticSizedArrayTemplate(x)),
            INameT::RuntimeSizedArrayTemplate(x) => Ok(ICitizenTemplateNameT::RuntimeSizedArrayTemplate(x)),
            INameT::LambdaCitizenTemplate(x) => Ok(ICitizenTemplateNameT::LambdaCitizenTemplate(x)),
            INameT::StructTemplate(x) => Ok(ICitizenTemplateNameT::StructTemplate(x)),
            INameT::InterfaceTemplate(x) => Ok(ICitizenTemplateNameT::InterfaceTemplate(x)),
            INameT::AnonymousSubstructTemplate(x) => Ok(ICitizenTemplateNameT::AnonymousSubstructTemplate(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IStructTemplateNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::LambdaCitizenTemplate(x) => Ok(IStructTemplateNameT::LambdaCitizenTemplate(x)),
            INameT::StructTemplate(x) => Ok(IStructTemplateNameT::StructTemplate(x)),
            INameT::AnonymousSubstructTemplate(x) => Ok(IStructTemplateNameT::AnonymousSubstructTemplate(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IInterfaceTemplateNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::InterfaceTemplate(x) => Ok(IInterfaceTemplateNameT::InterfaceTemplate(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for ISuperKindNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::KindPlaceholder(x) => Ok(ISuperKindNameT::KindPlaceholder(x)),
            INameT::Interface(x) => Ok(ISuperKindNameT::Interface(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for ISubKindNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::StaticSizedArray(x) => Ok(ISubKindNameT::StaticSizedArray(x)),
            INameT::RuntimeSizedArray(x) => Ok(ISubKindNameT::RuntimeSizedArray(x)),
            INameT::KindPlaceholder(x) => Ok(ISubKindNameT::KindPlaceholder(x)),
            INameT::Struct(x) => Ok(ISubKindNameT::Struct(x)),
            INameT::Interface(x) => Ok(ISubKindNameT::Interface(x)),
            INameT::LambdaCitizen(x) => Ok(ISubKindNameT::LambdaCitizen(x)),
            INameT::AnonymousSubstruct(x) => Ok(ISubKindNameT::AnonymousSubstruct(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for ICitizenNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::StaticSizedArray(x) => Ok(ICitizenNameT::StaticSizedArray(x)),
            INameT::RuntimeSizedArray(x) => Ok(ICitizenNameT::RuntimeSizedArray(x)),
            INameT::Struct(x) => Ok(ICitizenNameT::Struct(x)),
            INameT::Interface(x) => Ok(ICitizenNameT::Interface(x)),
            INameT::LambdaCitizen(x) => Ok(ICitizenNameT::LambdaCitizen(x)),
            INameT::AnonymousSubstruct(x) => Ok(ICitizenNameT::AnonymousSubstruct(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IStructNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::Struct(x) => Ok(IStructNameT::Struct(x)),
            INameT::LambdaCitizen(x) => Ok(IStructNameT::LambdaCitizen(x)),
            INameT::AnonymousSubstruct(x) => Ok(IStructNameT::AnonymousSubstruct(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IInterfaceNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::Interface(x) => Ok(IInterfaceNameT::Interface(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IImplTemplateNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::ImplTemplate(x) => Ok(IImplTemplateNameT::ImplTemplate(x)),
            INameT::ImplBoundTemplate(x) => Ok(IImplTemplateNameT::ImplBoundTemplate(x)),
            INameT::AnonymousSubstructImplTemplate(x) => Ok(IImplTemplateNameT::AnonymousSubstructImplTemplate(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IImplNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::Impl(x) => Ok(IImplNameT::Impl(x)),
            INameT::ImplBound(x) => Ok(IImplNameT::ImplBound(x)),
            INameT::AnonymousSubstructImpl(x) => Ok(IImplNameT::AnonymousSubstructImpl(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IPlaceholderNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::KindPlaceholder(x) => Ok(IPlaceholderNameT::KindPlaceholder(x)),
            INameT::NonKindNonRegionPlaceholder(x) => Ok(IPlaceholderNameT::NonKindNonRegionPlaceholder(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for IVarNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::TypingPassBlockResultVar(x) => Ok(IVarNameT::TypingPassBlockResultVar(x)),
            INameT::TypingPassFunctionResultVar(x) => Ok(IVarNameT::TypingPassFunctionResultVar(x)),
            INameT::TypingPassTemporaryVar(x) => Ok(IVarNameT::TypingPassTemporaryVar(x)),
            INameT::TypingPassPatternMember(x) => Ok(IVarNameT::TypingPassPatternMember(x)),
            INameT::TypingIgnoredParam(x) => Ok(IVarNameT::TypingIgnoredParam(x)),
            INameT::TypingPassPatternDestructuree(x) => Ok(IVarNameT::TypingPassPatternDestructuree(x)),
            INameT::UnnamedLocal(x) => Ok(IVarNameT::UnnamedLocal(x)),
            INameT::ClosureParam(x) => Ok(IVarNameT::ClosureParam(x)),
            INameT::ConstructingMember(x) => Ok(IVarNameT::ConstructingMember(x)),
            INameT::WhileCondResult(x) => Ok(IVarNameT::WhileCondResult(x)),
            INameT::Iterable(x) => Ok(IVarNameT::Iterable(x)),
            INameT::Iterator(x) => Ok(IVarNameT::Iterator(x)),
            INameT::IterationOption(x) => Ok(IVarNameT::IterationOption(x)),
            INameT::MagicParam(x) => Ok(IVarNameT::MagicParam(x)),
            INameT::CodeVar(x) => Ok(IVarNameT::CodeVar(x)),
            INameT::AnonymousSubstructMember(x) => Ok(IVarNameT::AnonymousSubstructMember(x)),
            INameT::Self_(x) => Ok(IVarNameT::Self_(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for CitizenNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::Struct(x) => Ok(CitizenNameT::Struct(x)),
            INameT::Interface(x) => Ok(CitizenNameT::Interface(x)),
            _ => Err(()),
        }
    }
}

impl<'s, 't> TryFrom<INameT<'s, 't>> for CitizenTemplateNameT<'s, 't> {
    type Error = ();
    fn try_from(n: INameT<'s, 't>) -> Result<Self, ()> {
        match n {
            INameT::StructTemplate(x) => Ok(CitizenTemplateNameT::StructTemplate(x)),
            INameT::InterfaceTemplate(x) => Ok(CitizenTemplateNameT::InterfaceTemplate(x)),
            _ => Err(()),
        }
    }
}

// ============================================================================
// IDEPFL *ValT companion types (Slab 2 Step 6).
//
// The typing interner canonicalizes each concrete name struct — two
// structurally-equivalent values share the same `&'t XxxNameT` arena
// allocation. The lookup flow is the IDEPFL transient/permanent split:
//
//   1. Caller builds a transient `XxxNameValT<'s, 't, 'tmp>` on the stack.
//   2. Interner hashes the Val, probes its per-family HashMap.
//   3. On HIT: return the existing `&'t XxxNameT` — transient Val discarded.
//   4. On MISS: promote Val's slice fields into the arena (via
//      `promote_in()`), allocate the permanent `XxxNameT`, install in the
//      HashMap, return the new `&'t XxxNameT`.
//
// Three IDEPFL kinds (see `.claude/rules/postparser/IDEPFL-postparser-interning.md`):
//
// - **Simple** — struct fields are all Copy primitives or scout-lifetime refs
//   (`StrI<'s>`, `CodeLocationS<'s>`, `RangeS<'s>`, `IRuneS<'s>`, `i32`).
//   The struct itself is the Val — no separate type needed. The interner
//   passes the struct by value as its HashMap key.
//
// - **Shallow** — struct holds `&'t` refs to parent interned types, or
//   inline-owned sub-enum values (already canonical since concretes are
//   pointer-interned). The struct itself is still the Val; no 'tmp lifetime
//   is required because there's nothing transient to borrow.
//
// - **Transient-with-'tmp** — struct has `&'t [...]` arena slices
//   (`template_args`, `parameters`, `init_steps`, etc.). The transient Val
//   replaces those with `&'tmp [...]` slices borrowed from a stack-local Vec
//   so lookup can hash/compare without allocating. Slices are canonicalized
//   into `&'t` on miss.
//
// Under the inline-owned sub-enum design (§6.2 / §6.3): sub-enum families
// (`IFunctionNameT`, `IStructNameT`, `INameT`, etc.) are NOT interned — they
// are 16-byte inline Copy values constructed on the stack. Only concrete
// name structs and `IdT` need Val companions.
//
// Simple/shallow concretes below use the struct itself as their Val. The
// 15 transient concretes each get a `*ValT` struct defined explicitly.
// ============================================================================

// -- IdValT: transient Val for IdT --------------------------------------------
// `init_steps: &'tmp [INameT<'s, 't>]` replaces the permanent IdT's `&'t`
// slice so callers can hash a trial IdT against the interner without yet
// arena-allocating the slice. Monomorphic per
// `docs/reasoning/idt-typed-view-alternatives.md`.
// Derive Hash/PartialEq/Eq: content-based (iterates the init_steps slice,
// delegates to &ref's target). This is *required* for heterogeneous lookup:
// the hash must be consistent whether the Val's slice is 'tmp-borrowed (query)
// or 't-arena-allocated (stored). Pointer-based hashing would fail to match
// structurally-equal Vals with different slice pointers.
/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct IdValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub package_coord: &'s PackageCoordinate<'s>,
    pub init_steps: &'tmp [INameT<'s, 't>],
    pub local_name: INameT<'s, 't>,
}

// Query wrapper for heterogeneous lookup (IdValT<'s, 't, 'tmp> against stored
// IdValT<'s, 't, 't>). Mirrors postparsing::names::RuneValQuery.
/// Interning transient (see @TFITCX)
pub struct IdValQuery<'a, 's, 't, 'tmp>(pub &'a IdValT<'s, 't, 'tmp>)
where 's: 't, 't: 'tmp;

impl<'a, 's, 't, 'tmp> Hash for IdValQuery<'a, 's, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    fn hash<H: Hasher>(&self, state: &mut H) { self.0.hash(state); }
}

impl<'a, 's, 't, 'tmp> hashbrown::Equivalent<IdValT<'s, 't, 't>> for IdValQuery<'a, 's, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    fn equivalent(&self, key: &IdValT<'s, 't, 't>) -> bool {
        self.0.package_coord == key.package_coord
            && self.0.init_steps == key.init_steps
            && self.0.local_name == key.local_name
    }
    
}

// -- Transient-with-'tmp Val types for the 15 concrete names with slices ----
// Fields match the permanent struct verbatim, except each `&'t [...]` slice
// is replaced by `&'tmp [...]`.

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct ImplNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t ImplTemplateNameT<'s>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
    pub sub_citizen: ICitizenTT<'s, 't>,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct ImplBoundNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t ImplBoundTemplateNameT<'s>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct OverrideDispatcherNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t OverrideDispatcherTemplateNameT<'s, 't>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
    pub parameters: &'tmp [CoordT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct OverrideDispatcherCaseNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub independent_impl_template_args: &'tmp [ITemplataT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct ExternFunctionNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub human_name: StrI<'s>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
    pub parameters: &'tmp [CoordT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct FunctionNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t FunctionTemplateNameT<'s>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
    pub parameters: &'tmp [CoordT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct FunctionBoundNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t FunctionBoundTemplateNameT<'s>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
    pub parameters: &'tmp [CoordT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct PredictedFunctionNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t PredictedFunctionTemplateNameT<'s>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
    pub parameters: &'tmp [CoordT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct LambdaCallFunctionTemplateNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub code_location: CodeLocationS<'s>,
    pub param_types: &'tmp [CoordT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct LambdaCallFunctionNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t LambdaCallFunctionTemplateNameT<'s, 't>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
    pub parameters: &'tmp [CoordT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct StructNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: IStructTemplateNameT<'s, 't>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct InterfaceNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t InterfaceTemplateNameT<'s>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct AnonymousSubstructImplNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t AnonymousSubstructImplTemplateNameT<'s, 't>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
    pub sub_citizen: ICitizenTT<'s, 't>,
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct AnonymousSubstructConstructorNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t AnonymousSubstructConstructorTemplateNameT<'s, 't>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
    pub parameters: &'tmp [CoordT<'s, 't>],
}

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub struct AnonymousSubstructNameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    pub template: &'t AnonymousSubstructTemplateNameT<'s, 't>,
    pub template_args: &'tmp [ITemplataT<'s, 't>],
}

// -- Simple / shallow concretes (reuse struct itself as Val) ------------------
// The following ~45 concrete name structs have no `&'t [...]` slices, so
// their permanent struct doubles as the Val (they're already `Copy` and
// `Hash + Eq`). No separate `*ValT` type is defined for:
//
//   Fieldless (8): StaticSizedArrayTemplateNameT, RuntimeSizedArrayTemplateNameT,
//   TypingPassFunctionResultVarNameT, PackageTopLevelNameT, SelfNameT,
//   ArbitraryNameT, ResolvingEnvNameT, CallEnvNameT.
//
//   Scout-only fields (31): ExportTemplateNameT, ImplTemplateNameT,
//   ImplBoundTemplateNameT, LetNameT, ExportAsNameT, ReachablePrototypeNameT,
//   KindPlaceholderTemplateNameT, NonKindNonRegionPlaceholderNameT,
//   TypingIgnoredParamNameT, UnnamedLocalNameT, ClosureParamNameT,
//   ConstructingMemberNameT, WhileCondResultNameT, IterableNameT,
//   IteratorNameT, IterationOptionNameT, MagicParamNameT, CodeVarNameT,
//   AnonymousSubstructMemberNameT, PrimitiveNameT, ProjectNameT, PackageNameT,
//   RuneNameT, ExternTemplateNameT, FunctionBoundTemplateNameT,
//   PredictedFunctionTemplateNameT, FunctionTemplateNameT,
//   ConstructorTemplateNameT, LambdaCitizenTemplateNameT, StructTemplateNameT,
//   InterfaceTemplateNameT.
//
//   Typing refs / inline sub-enums (no slices, ~18): ExportNameT, RawArrayNameT,
//   StaticSizedArrayNameT, RuntimeSizedArrayNameT, KindPlaceholderNameT,
//   TypingPassBlockResultVarNameT, TypingPassTemporaryVarNameT,
//   TypingPassPatternMemberNameT, TypingPassPatternDestructureeNameT,
//   BuildingFunctionNameWithClosuredsT, ExternNameT, ForwarderFunctionNameT,
//   ForwarderFunctionTemplateNameT, LambdaCitizenNameT,
//   AnonymousSubstructImplTemplateNameT, AnonymousSubstructTemplateNameT,
//   AnonymousSubstructConstructorTemplateNameT, OverrideDispatcherTemplateNameT.
//
// (OverrideDispatcherTemplateNameT is shallow because it holds an inline
// `IdT<'s, 't>` — the IdT's own init_steps slice
// must be canonicalized via IdValT before this Val is constructed.)

// ============================================================================
// Hash/PartialEq/Eq + Query wrappers for the 15 transient Vals.
//
// Pattern per IDEPFL: stored values live at `'tmp = 't` and compare/hash via
// pointer identity for their slices (the interner canonicalizes them). The
// Query wrapper lets a `'tmp`-borrowed Val look up against a stored key by
// comparing slice CONTENTS, not pointers.
// ============================================================================

// Each transient Val uses derived Hash/PartialEq/Eq (added below via struct attr).
// This macro emits only the Query wrapper + its Equivalent impl for heterogeneous
// lookup; the derive on the Val struct itself gives content-based hash+eq that's
// consistent across 'tmp differences.
macro_rules! transient_name_val_impls {
    (
        $val:ident, $query:ident,
        refs = [ $( $r:ident ),* ],
        slices = [ $( $s:ident ),* ],
        inline = [ $( $i:ident ),* ]
    ) => {
        pub struct $query<'a, 's, 't, 'tmp>(pub &'a $val<'s, 't, 'tmp>)
        where 's: 't, 't: 'tmp;

        impl<'a, 's, 't, 'tmp> Hash for $query<'a, 's, 't, 'tmp>
        where 's: 't, 't: 'tmp,
        {
            fn hash<H: Hasher>(&self, state: &mut H) { self.0.hash(state); }
        }

        impl<'a, 's, 't, 'tmp> hashbrown::Equivalent<$val<'s, 't, 't>> for $query<'a, 's, 't, 'tmp>
        where 's: 't, 't: 'tmp,
        {
            #[allow(unused_mut)]
            fn equivalent(&self, key: &$val<'s, 't, 't>) -> bool {
                let mut ok = true;
                $( ok = ok && self.0.$r == key.$r; )*
                $( ok = ok && self.0.$s == key.$s; )*
                $( ok = ok && self.0.$i == key.$i; )*
                ok
            }
            
        }
    };
}

transient_name_val_impls!(ImplNameValT, ImplNameValQuery,
    refs = [template], slices = [template_args], inline = [sub_citizen]);
transient_name_val_impls!(ImplBoundNameValT, ImplBoundNameValQuery,
    refs = [template], slices = [template_args], inline = []);
transient_name_val_impls!(OverrideDispatcherNameValT, OverrideDispatcherNameValQuery,
    refs = [template], slices = [template_args, parameters], inline = []);
transient_name_val_impls!(OverrideDispatcherCaseNameValT, OverrideDispatcherCaseNameValQuery,
    refs = [], slices = [independent_impl_template_args], inline = []);
transient_name_val_impls!(ExternFunctionNameValT, ExternFunctionNameValQuery,
    refs = [], slices = [parameters], inline = [human_name]);
transient_name_val_impls!(FunctionNameValT, FunctionNameValQuery,
    refs = [template], slices = [template_args, parameters], inline = []);
transient_name_val_impls!(FunctionBoundNameValT, FunctionBoundNameValQuery,
    refs = [template], slices = [template_args, parameters], inline = []);
transient_name_val_impls!(PredictedFunctionNameValT, PredictedFunctionNameValQuery,
    refs = [template], slices = [template_args, parameters], inline = []);
transient_name_val_impls!(LambdaCallFunctionTemplateNameValT, LambdaCallFunctionTemplateNameValQuery,
    refs = [], slices = [param_types], inline = [code_location]);
transient_name_val_impls!(LambdaCallFunctionNameValT, LambdaCallFunctionNameValQuery,
    refs = [template], slices = [template_args, parameters], inline = []);
transient_name_val_impls!(StructNameValT, StructNameValQuery,
    refs = [], slices = [template_args], inline = [template]);
transient_name_val_impls!(InterfaceNameValT, InterfaceNameValQuery,
    refs = [template], slices = [template_args], inline = []);
transient_name_val_impls!(AnonymousSubstructImplNameValT, AnonymousSubstructImplNameValQuery,
    refs = [template], slices = [template_args], inline = [sub_citizen]);
transient_name_val_impls!(AnonymousSubstructConstructorNameValT, AnonymousSubstructConstructorNameValQuery,
    refs = [template], slices = [template_args, parameters], inline = []);
transient_name_val_impls!(AnonymousSubstructNameValT, AnonymousSubstructNameValQuery,
    refs = [template], slices = [template_args], inline = []);

// ============================================================================
// INameValT — the union Val enum for the name-interning family.
//
// Per handoff-slab-4.md Gotcha 2 (6-family-map design mirroring scout's
// INameValS/INameS). One variant per concrete name in INameT. For simple names
// the variant payload is the concrete struct by value; for transient names
// (15, carrying slices) the payload is the concrete `*ValT` struct.
//
// Hash is derived (content-based; iterates slice contents). Query wrapper
// provides heterogeneous lookup (`'tmp` → `'t`) via Equivalent.
// ============================================================================

/// Interning transient (see @TFITCX)
#[derive(Copy, Clone, Hash, PartialEq, Eq, Debug)]
pub enum INameValT<'s, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    ExportTemplate(ExportTemplateNameT<'s>),
    Export(ExportNameT<'s, 't>),
    ImplTemplate(ImplTemplateNameT<'s>),
    Impl(ImplNameValT<'s, 't, 'tmp>),
    ImplBoundTemplate(ImplBoundTemplateNameT<'s>),
    ImplBound(ImplBoundNameValT<'s, 't, 'tmp>),
    Let(LetNameT<'s>),
    ExportAs(ExportAsNameT<'s>),
    RawArray(RawArrayNameT<'s, 't>),
    ReachablePrototype(ReachablePrototypeNameT),
    StaticSizedArrayTemplate(StaticSizedArrayTemplateNameT),
    StaticSizedArray(StaticSizedArrayNameT<'s, 't>),
    RuntimeSizedArrayTemplate(RuntimeSizedArrayTemplateNameT),
    RuntimeSizedArray(RuntimeSizedArrayNameT<'s, 't>),
    KindPlaceholderTemplate(KindPlaceholderTemplateNameT<'s>),
    KindPlaceholder(KindPlaceholderNameT<'s, 't>),
    NonKindNonRegionPlaceholder(NonKindNonRegionPlaceholderNameT<'s>),
    OverrideDispatcherTemplate(OverrideDispatcherTemplateNameT<'s, 't>),
    OverrideDispatcher(OverrideDispatcherNameValT<'s, 't, 'tmp>),
    OverrideDispatcherCase(OverrideDispatcherCaseNameValT<'s, 't, 'tmp>),
    TypingPassBlockResultVar(TypingPassBlockResultVarNameT<'t>),
    TypingPassFunctionResultVar(TypingPassFunctionResultVarNameT),
    TypingPassTemporaryVar(TypingPassTemporaryVarNameT<'t>),
    TypingPassPatternMember(TypingPassPatternMemberNameT<'t>),
    TypingIgnoredParam(TypingIgnoredParamNameT),
    TypingPassPatternDestructuree(TypingPassPatternDestructureeNameT<'t>),
    UnnamedLocal(UnnamedLocalNameT<'s>),
    ClosureParam(ClosureParamNameT<'s>),
    ConstructingMember(ConstructingMemberNameT<'s>),
    WhileCondResult(WhileCondResultNameT<'s>),
    Iterable(IterableNameT<'s>),
    Iterator(IteratorNameT<'s>),
    IterationOption(IterationOptionNameT<'s>),
    MagicParam(MagicParamNameT<'s>),
    CodeVar(CodeVarNameT<'s>),
    AnonymousSubstructMember(AnonymousSubstructMemberNameT),
    Primitive(PrimitiveNameT<'s>),
    PackageTopLevel(PackageTopLevelNameT),
    Project(ProjectNameT<'s>),
    Package(PackageNameT<'s>),
    Rune(RuneNameT<'s>),
    BuildingFunctionNameWithClosureds(BuildingFunctionNameWithClosuredsT<'s, 't>),
    ExternTemplate(ExternTemplateNameT<'s>),
    Extern(ExternNameT<'s, 't>),
    ExternFunction(ExternFunctionNameValT<'s, 't, 'tmp>),
    Function(FunctionNameValT<'s, 't, 'tmp>),
    ForwarderFunction(ForwarderFunctionNameT<'s, 't>),
    FunctionBoundTemplate(FunctionBoundTemplateNameT<'s>),
    FunctionBound(FunctionBoundNameValT<'s, 't, 'tmp>),
    PredictedFunctionTemplate(PredictedFunctionTemplateNameT<'s>),
    PredictedFunction(PredictedFunctionNameValT<'s, 't, 'tmp>),
    FunctionTemplate(FunctionTemplateNameT<'s>),
    LambdaCallFunctionTemplate(LambdaCallFunctionTemplateNameValT<'s, 't, 'tmp>),
    LambdaCallFunction(LambdaCallFunctionNameValT<'s, 't, 'tmp>),
    ForwarderFunctionTemplate(ForwarderFunctionTemplateNameT<'s, 't>),
    ConstructorTemplate(ConstructorTemplateNameT<'s>),
    Self_(SelfNameT),
    Arbitrary(ArbitraryNameT),
    Struct(StructNameValT<'s, 't, 'tmp>),
    Interface(InterfaceNameValT<'s, 't, 'tmp>),
    LambdaCitizenTemplate(LambdaCitizenTemplateNameT<'s>),
    LambdaCitizen(LambdaCitizenNameT<'s, 't>),
    StructTemplate(StructTemplateNameT<'s>),
    InterfaceTemplate(InterfaceTemplateNameT<'s>),
    AnonymousSubstructImplTemplate(AnonymousSubstructImplTemplateNameT<'s, 't>),
    AnonymousSubstructImpl(AnonymousSubstructImplNameValT<'s, 't, 'tmp>),
    AnonymousSubstructTemplate(AnonymousSubstructTemplateNameT<'s, 't>),
    AnonymousSubstructConstructorTemplate(AnonymousSubstructConstructorTemplateNameT<'s, 't>),
    AnonymousSubstructConstructor(AnonymousSubstructConstructorNameValT<'s, 't, 'tmp>),
    AnonymousSubstruct(AnonymousSubstructNameValT<'s, 't, 'tmp>),
    ResolvingEnv(ResolvingEnvNameT),
    CallEnv(CallEnvNameT),
}

/// Interning transient (see @TFITCX)
pub struct INameValQuery<'a, 's, 't, 'tmp>(pub &'a INameValT<'s, 't, 'tmp>)
where 's: 't, 't: 'tmp;

impl<'a, 's, 't, 'tmp> Hash for INameValQuery<'a, 's, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    fn hash<H: Hasher>(&self, state: &mut H) { self.0.hash(state); }
}

impl<'a, 's, 't, 'tmp> hashbrown::Equivalent<INameValT<'s, 't, 't>> for INameValQuery<'a, 's, 't, 'tmp>
where 's: 't, 't: 'tmp,
{
    fn equivalent(&self, key: &INameValT<'s, 't, 't>) -> bool {
        match (self.0, key) {
            // 15 transient variants: delegate to per-concrete Query wrapper.
            (Impl(a), Impl(b)) => ImplNameValQuery(a).equivalent(b),
            (ImplBound(a), ImplBound(b)) => ImplBoundNameValQuery(a).equivalent(b),
            (OverrideDispatcher(a), OverrideDispatcher(b)) => OverrideDispatcherNameValQuery(a).equivalent(b),
            (OverrideDispatcherCase(a), OverrideDispatcherCase(b)) => OverrideDispatcherCaseNameValQuery(a).equivalent(b),
            (ExternFunction(a), ExternFunction(b)) => ExternFunctionNameValQuery(a).equivalent(b),
            (Function(a), Function(b)) => FunctionNameValQuery(a).equivalent(b),
            (FunctionBound(a), FunctionBound(b)) => FunctionBoundNameValQuery(a).equivalent(b),
            (PredictedFunction(a), PredictedFunction(b)) => PredictedFunctionNameValQuery(a).equivalent(b),
            (LambdaCallFunctionTemplate(a), LambdaCallFunctionTemplate(b)) => LambdaCallFunctionTemplateNameValQuery(a).equivalent(b),
            (LambdaCallFunction(a), LambdaCallFunction(b)) => LambdaCallFunctionNameValQuery(a).equivalent(b),
            (Struct(a), Struct(b)) => StructNameValQuery(a).equivalent(b),
            (Interface(a), Interface(b)) => InterfaceNameValQuery(a).equivalent(b),
            (AnonymousSubstructImpl(a), AnonymousSubstructImpl(b)) => AnonymousSubstructImplNameValQuery(a).equivalent(b),
            (AnonymousSubstructConstructor(a), AnonymousSubstructConstructor(b)) => AnonymousSubstructConstructorNameValQuery(a).equivalent(b),
            (AnonymousSubstruct(a), AnonymousSubstruct(b)) => AnonymousSubstructNameValQuery(a).equivalent(b),
            // 57 simple variants: payload types match (no 'tmp), direct ==.
            (ExportTemplate(a), ExportTemplate(b)) => a == b,
            (Export(a), Export(b)) => a == b,
            (ImplTemplate(a), ImplTemplate(b)) => a == b,
            (ImplBoundTemplate(a), ImplBoundTemplate(b)) => a == b,
            (Let(a), Let(b)) => a == b,
            (ExportAs(a), ExportAs(b)) => a == b,
            (RawArray(a), RawArray(b)) => a == b,
            (ReachablePrototype(a), ReachablePrototype(b)) => a == b,
            (StaticSizedArrayTemplate(a), StaticSizedArrayTemplate(b)) => a == b,
            (StaticSizedArray(a), StaticSizedArray(b)) => a == b,
            (RuntimeSizedArrayTemplate(a), RuntimeSizedArrayTemplate(b)) => a == b,
            (RuntimeSizedArray(a), RuntimeSizedArray(b)) => a == b,
            (KindPlaceholderTemplate(a), KindPlaceholderTemplate(b)) => a == b,
            (KindPlaceholder(a), KindPlaceholder(b)) => a == b,
            (NonKindNonRegionPlaceholder(a), NonKindNonRegionPlaceholder(b)) => a == b,
            (OverrideDispatcherTemplate(a), OverrideDispatcherTemplate(b)) => a == b,
            (TypingPassBlockResultVar(a), TypingPassBlockResultVar(b)) => a == b,
            (TypingPassFunctionResultVar(a), TypingPassFunctionResultVar(b)) => a == b,
            (TypingPassTemporaryVar(a), TypingPassTemporaryVar(b)) => a == b,
            (TypingPassPatternMember(a), TypingPassPatternMember(b)) => a == b,
            (TypingIgnoredParam(a), TypingIgnoredParam(b)) => a == b,
            (TypingPassPatternDestructuree(a), TypingPassPatternDestructuree(b)) => a == b,
            (UnnamedLocal(a), UnnamedLocal(b)) => a == b,
            (ClosureParam(a), ClosureParam(b)) => a == b,
            (ConstructingMember(a), ConstructingMember(b)) => a == b,
            (WhileCondResult(a), WhileCondResult(b)) => a == b,
            (Iterable(a), Iterable(b)) => a == b,
            (Iterator(a), Iterator(b)) => a == b,
            (IterationOption(a), IterationOption(b)) => a == b,
            (MagicParam(a), MagicParam(b)) => a == b,
            (CodeVar(a), CodeVar(b)) => a == b,
            (AnonymousSubstructMember(a), AnonymousSubstructMember(b)) => a == b,
            (Primitive(a), Primitive(b)) => a == b,
            (PackageTopLevel(a), PackageTopLevel(b)) => a == b,
            (Project(a), Project(b)) => a == b,
            (Package(a), Package(b)) => a == b,
            (Rune(a), Rune(b)) => a == b,
            (BuildingFunctionNameWithClosureds(a), BuildingFunctionNameWithClosureds(b)) => a == b,
            (ExternTemplate(a), ExternTemplate(b)) => a == b,
            (Extern(a), Extern(b)) => a == b,
            (ForwarderFunction(a), ForwarderFunction(b)) => a == b,
            (FunctionBoundTemplate(a), FunctionBoundTemplate(b)) => a == b,
            (PredictedFunctionTemplate(a), PredictedFunctionTemplate(b)) => a == b,
            (FunctionTemplate(a), FunctionTemplate(b)) => a == b,
            (ForwarderFunctionTemplate(a), ForwarderFunctionTemplate(b)) => a == b,
            (ConstructorTemplate(a), ConstructorTemplate(b)) => a == b,
            (Self_(a), Self_(b)) => a == b,
            (Arbitrary(a), Arbitrary(b)) => a == b,
            (LambdaCitizenTemplate(a), LambdaCitizenTemplate(b)) => a == b,
            (LambdaCitizen(a), LambdaCitizen(b)) => a == b,
            (StructTemplate(a), StructTemplate(b)) => a == b,
            (InterfaceTemplate(a), InterfaceTemplate(b)) => a == b,
            (AnonymousSubstructImplTemplate(a), AnonymousSubstructImplTemplate(b)) => a == b,
            (AnonymousSubstructTemplate(a), AnonymousSubstructTemplate(b)) => a == b,
            (AnonymousSubstructConstructorTemplate(a), AnonymousSubstructConstructorTemplate(b)) => a == b,
            (ResolvingEnv(a), ResolvingEnv(b)) => a == b,
            (CallEnv(a), CallEnv(b)) => a == b,
            _ => false,
        }
    }
    
}
