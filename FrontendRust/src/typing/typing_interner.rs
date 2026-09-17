
use std::cell::RefCell;
use std::collections::HashMap as StdHashMap;

use bumpalo::Bump;

/// Construction-witness token for interned types (per @SICZ). The inner
/// unit field is private to this module, so only code in `typing_interner`
/// can construct one (specifically, the `intern_*` methods). Stored as a
/// `_must_intern` field on every TFITCX-Interned type, this makes it a
/// compile error to build such a literal anywhere outside the interner —
/// every instance is therefore canonical, which is what pointer-comparison
/// equality semantics (e.g. `IdT::eq`) require.
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct MustIntern(());

use crate::utils::arena_index_map::ArenaIndexMap;
use crate::typing::ast::ast::{
    PrototypeT, PrototypeValQuery, PrototypeValT, SignatureT, SignatureValQuery, SignatureValT,
};
use crate::typing::names::names::*;
// Templata payload interner removed; types are TFITCX Value-type per Scala parity
// and constructed directly via `bump.alloc(FooTemplataT { ... })` at call sites.
use crate::typing::types::types::{
    InterfaceTT, InterfaceTTValT, InternedKindPayloadT, InternedKindPayloadValT, KindPlaceholderT,
    OverloadSetT, OverloadSetTValT, RuntimeSizedArrayTT, RuntimeSizedArrayTTValT,
    StaticSizedArrayTT, StaticSizedArrayTTValT, StructTT, StructTTValT,
};
use std::hash::Hash;

// 6-family HashMap design mirroring scout_arena.rs. Values with subcollections
// or that need to fit behind &'t in a Copy enum are interned here (see @WVSBIZ).
// Per-concrete intern methods are thin wrappers that dispatch through the
// family method and unwrap the result.
/// Temporary state (see @TFITCX)
pub struct TypingInterner<'s, 't>
where 's: 't,
{
    bump: &'t Bump,
    inner: RefCell<Inner<'s, 't>>,
}

struct Inner<'s, 't>
where 's: 't,
{
    // 5 family-level HashMaps. Family 6 (templata payload) was removed once the
    // Templata family was reclassified Value-type per Scala parity — those types
    // are now constructed directly via `bump.alloc(...)` at call sites.
    name_val_to_ref: hashbrown::HashMap<INameValT<'s, 't, 't>, INameT<'s, 't>>,
    id_val_to_ref: hashbrown::HashMap<IdValT<'s, 't, 't>, &'t IdT<'s, 't>>,
    prototype_val_to_ref: hashbrown::HashMap<PrototypeValT<'s, 't, 't>, &'t PrototypeT<'s, 't>>,
    signature_val_to_ref: hashbrown::HashMap<SignatureValT<'s, 't, 't>, &'t SignatureT<'s, 't>>,
    kind_payload_val_to_ref:
        StdHashMap<InternedKindPayloadValT<'s, 't>, InternedKindPayloadT<'s, 't>>,
}

// --- Per-concrete wrapper macros (used below to generate ~75 thin wrappers) -

macro_rules! impl_intern_name_wrapper_simple {
    ($method:ident, $variant:ident, $payload_ty:ident) => {
        pub fn $method(&self, val: $payload_ty<'s, 't>) -> &'t $payload_ty<'s, 't> {
            match self.intern_name(INameValT::$variant(val)) {
                INameT::$variant(r) => r,
                _ => unreachable!(),
            }
        }
    };
}

// Arity-trimmed variants of impl_intern_name_wrapper_simple, introduced by the
// phantom-lifetime removal sweep. The original macro above still applies to
// T-types that kept <'s, 't>; the two below cover the post-sweep arities
// <'s> (most ex-`<'s,'t>` types) and `` (empty, no lifetimes).

macro_rules! impl_intern_name_wrapper_simple_s_only {
    ($method:ident, $variant:ident, $payload_ty:ident) => {
        pub fn $method(&self, val: $payload_ty<'s>) -> &'t $payload_ty<'s> {
            match self.intern_name(INameValT::$variant(val)) {
                INameT::$variant(r) => r,
                _ => unreachable!(),
            }
        }
    };
}

macro_rules! impl_intern_name_wrapper_simple_t_only {
    ($method:ident, $variant:ident, $payload_ty:ident) => {
        pub fn $method(&self, val: $payload_ty<'t>) -> &'t $payload_ty<'t> {
            match self.intern_name(INameValT::$variant(val)) {
                INameT::$variant(r) => r,
                _ => unreachable!(),
            }
        }
    };
}

macro_rules! impl_intern_name_wrapper_simple_none {
    ($method:ident, $variant:ident, $payload_ty:ident) => {
        pub fn $method(&self, val: $payload_ty) -> &'t $payload_ty {
            match self.intern_name(INameValT::$variant(val)) {
                INameT::$variant(r) => r,
                _ => unreachable!(),
            }
        }
    };
}

macro_rules! impl_intern_name_wrapper_transient {
    ($method:ident, $variant:ident, $val_ty:ident, $canonical_ty:ident) => {
        pub fn $method<'tmp>(&self, val: $val_ty<'s, 't, 'tmp>) -> &'t $canonical_ty<'s, 't> {
            match self.intern_name(INameValT::$variant(val)) {
                INameT::$variant(r) => r,
                _ => unreachable!(),
            }
        }
    };
}

macro_rules! impl_intern_kind_wrapper {
    ($method:ident, $variant:ident, $val_ty:ident, $canonical_ty:ident) => {
        pub fn $method(&self, val: $val_ty<'s, 't>) -> &'t $canonical_ty<'s, 't> {
            match self.intern_kind_payload(InternedKindPayloadValT::$variant(val)) {
                InternedKindPayloadT::$variant(r) => r,
                _ => unreachable!(),
            }
        }
    };
}

impl<'s, 't> TypingInterner<'s, 't>
where 's: 't,
{
    pub fn new(bump: &'t Bump) -> Self {
        TypingInterner {
            bump,
            inner: RefCell::new(Inner {
                name_val_to_ref: hashbrown::HashMap::new(),
                id_val_to_ref: hashbrown::HashMap::new(),
                prototype_val_to_ref: hashbrown::HashMap::new(),
                signature_val_to_ref: hashbrown::HashMap::new(),
                kind_payload_val_to_ref: StdHashMap::new(),
            }),
        }
    }

    // --- Arena access ---
    pub fn bump(&self) -> &'t Bump { self.bump }
    pub fn alloc<T>(&self, val: T) -> &'t mut T { self.bump.alloc(val) }
    pub fn alloc_slice_copy<T: Copy>(&self, src: &[T]) -> &'t [T] {
        self.bump.alloc_slice_copy(src)
    }
    pub fn alloc_slice_from_vec<T>(&self, vec: Vec<T>) -> &'t [T] {
        self.bump.alloc_slice_fill_iter(vec.into_iter())
    }

    // Per @IIIOZ, arena maps use ArenaIndexMap (insertion-ordered) rather than HashMap for cross-run determinism.
    pub fn alloc_index_map<K: Hash + Eq + Clone, V>(&self) -> ArenaIndexMap<'t, K, V> {
        ArenaIndexMap::new_in(self.bump)
    }

    pub fn alloc_index_map_from_iter<K, V, I>(&self, iter: I) -> ArenaIndexMap<'t, K, V>
    where K: Hash + Eq + Clone, I: IntoIterator<Item = (K, V)>
    {
        ArenaIndexMap::from_iter_in(iter, self.bump)
    }

    // =========================================================================
    // Family 1: Name interning
    // =========================================================================

    pub fn intern_name<'tmp>(&self, val: INameValT<'s, 't, 'tmp>) -> INameT<'s, 't> {
        {
            let inner = self.inner.borrow();
            let query = INameValQuery(&val);
            if let Some(existing) = inner.name_val_to_ref.get(&query) {
                return *existing;
            }
        }
        let (stored_key, canonical) = self.alloc_name_canonical(val);
        let mut inner = self.inner.borrow_mut();
        inner.name_val_to_ref.insert(stored_key, canonical);
        canonical
    }

    fn alloc_name_canonical<'tmp>(
        &self,
        val: INameValT<'s, 't, 'tmp>,
    ) -> (INameValT<'s, 't, 't>, INameT<'s, 't>) {
        use INameT as T;
        use INameValT as V;
        match val {
            // 15 transient variants: promote slices, build canonical + stored_key.
            V::Impl(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let canonical = ImplNameT { template: v.template, template_args, sub_citizen: v.sub_citizen, _must_intern: MustIntern(()) };
                let key = ImplNameValT { template: v.template, template_args, sub_citizen: v.sub_citizen };
                (V::Impl(key), T::Impl(self.bump.alloc(canonical)))
            }
            V::ImplBound(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let canonical = ImplBoundNameT { template: v.template, template_args, _must_intern: MustIntern(()) };
                let key = ImplBoundNameValT { template: v.template, template_args };
                (V::ImplBound(key), T::ImplBound(self.bump.alloc(canonical)))
            }
            V::OverrideDispatcher(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let parameters = self.bump.alloc_slice_copy(v.parameters);
                let canonical = OverrideDispatcherNameT { template: v.template, template_args, parameters, _must_intern: MustIntern(()) };
                let key = OverrideDispatcherNameValT { template: v.template, template_args, parameters };
                (V::OverrideDispatcher(key), T::OverrideDispatcher(self.bump.alloc(canonical)))
            }
            V::OverrideDispatcherCase(v) => {
                let independent_impl_template_args = self.bump.alloc_slice_copy(v.independent_impl_template_args);
                let canonical = OverrideDispatcherCaseNameT { independent_impl_template_args, _must_intern: MustIntern(()) };
                let key = OverrideDispatcherCaseNameValT { independent_impl_template_args };
                (V::OverrideDispatcherCase(key), T::OverrideDispatcherCase(self.bump.alloc(canonical)))
            }
            V::ExternFunction(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let parameters = self.bump.alloc_slice_copy(v.parameters);
                let canonical = ExternFunctionNameT { human_name: v.human_name, template_args, parameters, _must_intern: MustIntern(()) };
                let key = ExternFunctionNameValT { human_name: v.human_name, template_args, parameters };
                (V::ExternFunction(key), T::ExternFunction(self.bump.alloc(canonical)))
            }
            V::Function(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let parameters = self.bump.alloc_slice_copy(v.parameters);
                let canonical = FunctionNameT { template: v.template, template_args, parameters, _must_intern: MustIntern(()) };
                let key = FunctionNameValT { template: v.template, template_args, parameters };
                (V::Function(key), T::Function(self.bump.alloc(canonical)))
            }
            V::FunctionBound(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let parameters = self.bump.alloc_slice_copy(v.parameters);
                let canonical = FunctionBoundNameT { template: v.template, template_args, parameters, _must_intern: MustIntern(()) };
                let key = FunctionBoundNameValT { template: v.template, template_args, parameters };
                (V::FunctionBound(key), T::FunctionBound(self.bump.alloc(canonical)))
            }
            V::PredictedFunction(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let parameters = self.bump.alloc_slice_copy(v.parameters);
                let canonical = PredictedFunctionNameT { template: v.template, template_args, parameters, _must_intern: MustIntern(()) };
                let key = PredictedFunctionNameValT { template: v.template, template_args, parameters };
                (V::PredictedFunction(key), T::PredictedFunction(self.bump.alloc(canonical)))
            }
            V::LambdaCallFunctionTemplate(v) => {
                let param_types = self.bump.alloc_slice_copy(v.param_types);
                let canonical = LambdaCallFunctionTemplateNameT { code_location: v.code_location, param_types, _must_intern: MustIntern(()) };
                let key = LambdaCallFunctionTemplateNameValT { code_location: v.code_location, param_types };
                (V::LambdaCallFunctionTemplate(key), T::LambdaCallFunctionTemplate(self.bump.alloc(canonical)))
            }
            V::LambdaCallFunction(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let parameters = self.bump.alloc_slice_copy(v.parameters);
                let canonical = LambdaCallFunctionNameT { template: v.template, template_args, parameters, _must_intern: MustIntern(()) };
                let key = LambdaCallFunctionNameValT { template: v.template, template_args, parameters };
                (V::LambdaCallFunction(key), T::LambdaCallFunction(self.bump.alloc(canonical)))
            }
            V::Struct(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let canonical = StructNameT { template: v.template, template_args, _must_intern: MustIntern(()) };
                let key = StructNameValT { template: v.template, template_args };
                (V::Struct(key), T::Struct(self.bump.alloc(canonical)))
            }
            V::Interface(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let canonical = InterfaceNameT { template: v.template, template_args, _must_intern: MustIntern(()) };
                let key = InterfaceNameValT { template: v.template, template_args };
                (V::Interface(key), T::Interface(self.bump.alloc(canonical)))
            }
            V::AnonymousSubstructImpl(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let canonical = AnonymousSubstructImplNameT { template: v.template, template_args, sub_citizen: v.sub_citizen, _must_intern: MustIntern(()) };
                let key = AnonymousSubstructImplNameValT { template: v.template, template_args, sub_citizen: v.sub_citizen };
                (V::AnonymousSubstructImpl(key), T::AnonymousSubstructImpl(self.bump.alloc(canonical)))
            }
            V::AnonymousSubstructConstructor(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let parameters = self.bump.alloc_slice_copy(v.parameters);
                let canonical = AnonymousSubstructConstructorNameT { template: v.template, template_args, parameters, _must_intern: MustIntern(()) };
                let key = AnonymousSubstructConstructorNameValT { template: v.template, template_args, parameters };
                (V::AnonymousSubstructConstructor(key), T::AnonymousSubstructConstructor(self.bump.alloc(canonical)))
            }
            V::AnonymousSubstruct(v) => {
                let template_args = self.bump.alloc_slice_copy(v.template_args);
                let canonical = AnonymousSubstructNameT { template: v.template, template_args, _must_intern: MustIntern(()) };
                let key = AnonymousSubstructNameValT { template: v.template, template_args };
                (V::AnonymousSubstruct(key), T::AnonymousSubstruct(self.bump.alloc(canonical)))
            }
            // 57 simple variants: payload is Copy, alloc + wrap directly.
            V::ExportTemplate(p) => (V::ExportTemplate(p), T::ExportTemplate(self.bump.alloc(p))),
            V::Export(p) => (V::Export(p), T::Export(self.bump.alloc(p))),
            V::ImplTemplate(p) => (V::ImplTemplate(p), T::ImplTemplate(self.bump.alloc(p))),
            V::ImplBoundTemplate(p) => (V::ImplBoundTemplate(p), T::ImplBoundTemplate(self.bump.alloc(p))),
            V::Let(p) => (V::Let(p), T::Let(self.bump.alloc(p))),
            V::ExportAs(p) => (V::ExportAs(p), T::ExportAs(self.bump.alloc(p))),
            V::RawArray(p) => (V::RawArray(p), T::RawArray(self.bump.alloc(p))),
            V::ReachablePrototype(p) => (V::ReachablePrototype(p), T::ReachablePrototype(self.bump.alloc(p))),
            V::StaticSizedArrayTemplate(p) => (V::StaticSizedArrayTemplate(p), T::StaticSizedArrayTemplate(self.bump.alloc(p))),
            V::StaticSizedArray(p) => (V::StaticSizedArray(p), T::StaticSizedArray(self.bump.alloc(p))),
            V::RuntimeSizedArrayTemplate(p) => (V::RuntimeSizedArrayTemplate(p), T::RuntimeSizedArrayTemplate(self.bump.alloc(p))),
            V::RuntimeSizedArray(p) => (V::RuntimeSizedArray(p), T::RuntimeSizedArray(self.bump.alloc(p))),
            V::KindPlaceholderTemplate(p) => (V::KindPlaceholderTemplate(p), T::KindPlaceholderTemplate(self.bump.alloc(p))),
            V::KindPlaceholder(p) => (V::KindPlaceholder(p), T::KindPlaceholder(self.bump.alloc(p))),
            V::NonKindNonRegionPlaceholder(p) => (V::NonKindNonRegionPlaceholder(p), T::NonKindNonRegionPlaceholder(self.bump.alloc(p))),
            V::OverrideDispatcherTemplate(p) => (V::OverrideDispatcherTemplate(p), T::OverrideDispatcherTemplate(self.bump.alloc(p))),
            V::TypingPassBlockResultVar(p) => (V::TypingPassBlockResultVar(p), T::TypingPassBlockResultVar(self.bump.alloc(p))),
            V::TypingPassFunctionResultVar(p) => (V::TypingPassFunctionResultVar(p), T::TypingPassFunctionResultVar(self.bump.alloc(p))),
            V::TypingPassTemporaryVar(p) => (V::TypingPassTemporaryVar(p), T::TypingPassTemporaryVar(self.bump.alloc(p))),
            V::TypingPassPatternMember(p) => (V::TypingPassPatternMember(p), T::TypingPassPatternMember(self.bump.alloc(p))),
            V::TypingIgnoredParam(p) => (V::TypingIgnoredParam(p), T::TypingIgnoredParam(self.bump.alloc(p))),
            V::TypingPassPatternDestructuree(p) => (V::TypingPassPatternDestructuree(p), T::TypingPassPatternDestructuree(self.bump.alloc(p))),
            V::UnnamedLocal(p) => (V::UnnamedLocal(p), T::UnnamedLocal(self.bump.alloc(p))),
            V::ClosureParam(p) => (V::ClosureParam(p), T::ClosureParam(self.bump.alloc(p))),
            V::ConstructingMember(p) => (V::ConstructingMember(p), T::ConstructingMember(self.bump.alloc(p))),
            V::WhileCondResult(p) => (V::WhileCondResult(p), T::WhileCondResult(self.bump.alloc(p))),
            V::Iterable(p) => (V::Iterable(p), T::Iterable(self.bump.alloc(p))),
            V::Iterator(p) => (V::Iterator(p), T::Iterator(self.bump.alloc(p))),
            V::IterationOption(p) => (V::IterationOption(p), T::IterationOption(self.bump.alloc(p))),
            V::MagicParam(p) => (V::MagicParam(p), T::MagicParam(self.bump.alloc(p))),
            V::CodeVar(p) => (V::CodeVar(p), T::CodeVar(self.bump.alloc(p))),
            V::AnonymousSubstructMember(p) => (V::AnonymousSubstructMember(p), T::AnonymousSubstructMember(self.bump.alloc(p))),
            V::Primitive(p) => (V::Primitive(p), T::Primitive(self.bump.alloc(p))),
            V::PackageTopLevel(p) => (V::PackageTopLevel(p), T::PackageTopLevel(self.bump.alloc(p))),
            V::Project(p) => (V::Project(p), T::Project(self.bump.alloc(p))),
            V::Package(p) => (V::Package(p), T::Package(self.bump.alloc(p))),
            V::Rune(p) => (V::Rune(p), T::Rune(self.bump.alloc(p))),
            V::BuildingFunctionNameWithClosureds(p) => (V::BuildingFunctionNameWithClosureds(p), T::BuildingFunctionNameWithClosureds(self.bump.alloc(p))),
            V::ExternTemplate(p) => (V::ExternTemplate(p), T::ExternTemplate(self.bump.alloc(p))),
            V::Extern(p) => (V::Extern(p), T::Extern(self.bump.alloc(p))),
            V::ForwarderFunction(p) => (V::ForwarderFunction(p), T::ForwarderFunction(self.bump.alloc(p))),
            V::FunctionBoundTemplate(p) => (V::FunctionBoundTemplate(p), T::FunctionBoundTemplate(self.bump.alloc(p))),
            V::PredictedFunctionTemplate(p) => (V::PredictedFunctionTemplate(p), T::PredictedFunctionTemplate(self.bump.alloc(p))),
            V::FunctionTemplate(p) => (V::FunctionTemplate(p), T::FunctionTemplate(self.bump.alloc(p))),
            V::ForwarderFunctionTemplate(p) => (V::ForwarderFunctionTemplate(p), T::ForwarderFunctionTemplate(self.bump.alloc(p))),
            V::ConstructorTemplate(p) => (V::ConstructorTemplate(p), T::ConstructorTemplate(self.bump.alloc(p))),
            V::Self_(p) => (V::Self_(p), T::Self_(self.bump.alloc(p))),
            V::Arbitrary(p) => (V::Arbitrary(p), T::Arbitrary(self.bump.alloc(p))),
            V::LambdaCitizenTemplate(p) => (V::LambdaCitizenTemplate(p), T::LambdaCitizenTemplate(self.bump.alloc(p))),
            V::LambdaCitizen(p) => (V::LambdaCitizen(p), T::LambdaCitizen(self.bump.alloc(p))),
            V::StructTemplate(p) => (V::StructTemplate(p), T::StructTemplate(self.bump.alloc(p))),
            V::InterfaceTemplate(p) => (V::InterfaceTemplate(p), T::InterfaceTemplate(self.bump.alloc(p))),
            V::AnonymousSubstructImplTemplate(p) => (V::AnonymousSubstructImplTemplate(p), T::AnonymousSubstructImplTemplate(self.bump.alloc(p))),
            V::AnonymousSubstructTemplate(p) => (V::AnonymousSubstructTemplate(p), T::AnonymousSubstructTemplate(self.bump.alloc(p))),
            V::AnonymousSubstructConstructorTemplate(p) => (V::AnonymousSubstructConstructorTemplate(p), T::AnonymousSubstructConstructorTemplate(self.bump.alloc(p))),
            V::ResolvingEnv(p) => (V::ResolvingEnv(p), T::ResolvingEnv(self.bump.alloc(p))),
            V::CallEnv(p) => (V::CallEnv(p), T::CallEnv(self.bump.alloc(p))),
        }
    }

    // =========================================================================
    // Family 2-4: Id / Prototype / Signature (singletons, no dispatch needed)
    // =========================================================================

    pub fn intern_id<'tmp>(&self, val: IdValT<'s, 't, 'tmp>) -> &'t IdT<'s, 't> {
        {
            let inner = self.inner.borrow();
            let query = IdValQuery(&val);
            if let Some(existing) = inner.id_val_to_ref.get(&query) {
                return *existing;
            }
        }
        let init_steps = self.bump.alloc_slice_copy(val.init_steps);
        let canonical: &'t IdT<'s, 't> = self.bump.alloc(IdT {
            package_coord: val.package_coord,
            init_steps,
            local_name: val.local_name,
            _must_intern: MustIntern(()),
        });
        let stored_key = IdValT {
            package_coord: val.package_coord,
            init_steps,
            local_name: val.local_name,
        };
        let mut inner = self.inner.borrow_mut();
        inner.id_val_to_ref.insert(stored_key, canonical);
        canonical
    }

    pub fn intern_prototype<'tmp>(&self, val: PrototypeValT<'s, 't, 'tmp>) -> &'t PrototypeT<'s, 't> {
        {
            let inner = self.inner.borrow();
            let query = PrototypeValQuery(&val);
            if let Some(existing) = inner.prototype_val_to_ref.get(&query) {
                return *existing;
            }
        }
        let id_ref = self.intern_id(val.id);
        let canonical: &'t PrototypeT<'s, 't> = self.bump.alloc(PrototypeT {
            id: *id_ref,
            return_type: val.return_type,
        });
        let stored_key = PrototypeValT {
            id: IdValT {
                package_coord: id_ref.package_coord,
                init_steps: id_ref.init_steps,
                local_name: id_ref.local_name,
            },
            return_type: val.return_type,
        };
        let mut inner = self.inner.borrow_mut();
        inner.prototype_val_to_ref.insert(stored_key, canonical);
        canonical
    }

    pub fn intern_signature<'tmp>(&self, val: SignatureValT<'s, 't, 'tmp>) -> &'t SignatureT<'s, 't> {
        {
            let inner = self.inner.borrow();
            let query = SignatureValQuery(&val);
            if let Some(existing) = inner.signature_val_to_ref.get(&query) {
                return *existing;
            }
        }
        let id_ref = self.intern_id(val.id);
        let canonical: &'t SignatureT<'s, 't> = self.bump.alloc(SignatureT { id: *id_ref });
        let stored_key = SignatureValT {
            id: IdValT {
                package_coord: id_ref.package_coord,
                init_steps: id_ref.init_steps,
                local_name: id_ref.local_name,
            },
        };
        let mut inner = self.inner.borrow_mut();
        inner.signature_val_to_ref.insert(stored_key, canonical);
        canonical
    }

    // =========================================================================
    // Family 5: Kind-payload interning (all 6 variants simple)
    // =========================================================================

    pub fn intern_kind_payload(
        &self,
        val: InternedKindPayloadValT<'s, 't>,
    ) -> InternedKindPayloadT<'s, 't> {
        {
            let inner = self.inner.borrow();
            if let Some(existing) = inner.kind_payload_val_to_ref.get(&val) {
                return *existing;
            }
        }
        use InternedKindPayloadT as T;
        use InternedKindPayloadValT as V;
        let canonical = match val {
            V::StructTT(v) => {
                let c = StructTT { id: v.id, _must_intern: MustIntern(()) };
                T::StructTT(self.bump.alloc(c))
            }
            V::InterfaceTT(v) => {
                let c = InterfaceTT { id: v.id, _must_intern: MustIntern(()) };
                T::InterfaceTT(self.bump.alloc(c))
            }
            V::StaticSizedArrayTT(v) => {
                let c = StaticSizedArrayTT { name: v.name, _must_intern: MustIntern(()) };
                T::StaticSizedArrayTT(self.bump.alloc(c))
            }
            V::RuntimeSizedArrayTT(v) => {
                let c = RuntimeSizedArrayTT { name: v.name, _must_intern: MustIntern(()) };
                T::RuntimeSizedArrayTT(self.bump.alloc(c))
            }
            V::KindPlaceholder(p) => T::KindPlaceholder(self.bump.alloc(p)),
            V::OverloadSet(v) => {
                let c = OverloadSetT { env: v.env, name: v.name, _must_intern: MustIntern(()) };
                T::OverloadSet(self.bump.alloc(c))
            }
        };
        let mut inner = self.inner.borrow_mut();
        inner.kind_payload_val_to_ref.insert(val, canonical);
        canonical
    }

    // =========================================================================
    // ~75 per-concrete wrappers (dispatch into family methods, unwrap).
    // =========================================================================

    // --- 15 transient name wrappers ---
    impl_intern_name_wrapper_transient!(intern_impl_name, Impl, ImplNameValT, ImplNameT);
    impl_intern_name_wrapper_transient!(intern_impl_bound_name, ImplBound, ImplBoundNameValT, ImplBoundNameT);
    impl_intern_name_wrapper_transient!(intern_override_dispatcher_name, OverrideDispatcher, OverrideDispatcherNameValT, OverrideDispatcherNameT);
    impl_intern_name_wrapper_transient!(intern_override_dispatcher_case_name, OverrideDispatcherCase, OverrideDispatcherCaseNameValT, OverrideDispatcherCaseNameT);
    impl_intern_name_wrapper_transient!(intern_extern_function_name, ExternFunction, ExternFunctionNameValT, ExternFunctionNameT);
    impl_intern_name_wrapper_transient!(intern_function_name, Function, FunctionNameValT, FunctionNameT);
    impl_intern_name_wrapper_transient!(intern_function_bound_name, FunctionBound, FunctionBoundNameValT, FunctionBoundNameT);
    impl_intern_name_wrapper_transient!(intern_predicted_function_name, PredictedFunction, PredictedFunctionNameValT, PredictedFunctionNameT);
    impl_intern_name_wrapper_transient!(intern_lambda_call_function_template_name, LambdaCallFunctionTemplate, LambdaCallFunctionTemplateNameValT, LambdaCallFunctionTemplateNameT);
    impl_intern_name_wrapper_transient!(intern_lambda_call_function_name, LambdaCallFunction, LambdaCallFunctionNameValT, LambdaCallFunctionNameT);
    impl_intern_name_wrapper_transient!(intern_struct_name, Struct, StructNameValT, StructNameT);
    impl_intern_name_wrapper_transient!(intern_interface_name, Interface, InterfaceNameValT, InterfaceNameT);
    impl_intern_name_wrapper_transient!(intern_anonymous_substruct_impl_name, AnonymousSubstructImpl, AnonymousSubstructImplNameValT, AnonymousSubstructImplNameT);
    impl_intern_name_wrapper_transient!(intern_anonymous_substruct_constructor_name, AnonymousSubstructConstructor, AnonymousSubstructConstructorNameValT, AnonymousSubstructConstructorNameT);
    impl_intern_name_wrapper_transient!(intern_anonymous_substruct_name, AnonymousSubstruct, AnonymousSubstructNameValT, AnonymousSubstructNameT);

    // --- 57 simple name wrappers ---
    impl_intern_name_wrapper_simple_s_only!(intern_export_template_name, ExportTemplate, ExportTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_export_name, Export, ExportNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_impl_template_name, ImplTemplate, ImplTemplateNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_impl_bound_template_name, ImplBoundTemplate, ImplBoundTemplateNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_let_name, Let, LetNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_export_as_name, ExportAs, ExportAsNameT);
    impl_intern_name_wrapper_simple!(intern_raw_array_name, RawArray, RawArrayNameT);
    impl_intern_name_wrapper_simple_none!(intern_reachable_prototype_name, ReachablePrototype, ReachablePrototypeNameT);
    impl_intern_name_wrapper_simple_none!(intern_static_sized_array_template_name, StaticSizedArrayTemplate, StaticSizedArrayTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_static_sized_array_name, StaticSizedArray, StaticSizedArrayNameT);
    impl_intern_name_wrapper_simple_none!(intern_runtime_sized_array_template_name, RuntimeSizedArrayTemplate, RuntimeSizedArrayTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_runtime_sized_array_name, RuntimeSizedArray, RuntimeSizedArrayNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_kind_placeholder_template_name, KindPlaceholderTemplate, KindPlaceholderTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_kind_placeholder_name, KindPlaceholder, KindPlaceholderNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_non_kind_non_region_placeholder_name, NonKindNonRegionPlaceholder, NonKindNonRegionPlaceholderNameT);
    impl_intern_name_wrapper_simple!(intern_override_dispatcher_template_name, OverrideDispatcherTemplate, OverrideDispatcherTemplateNameT);
    impl_intern_name_wrapper_simple_t_only!(intern_typing_pass_block_result_var_name, TypingPassBlockResultVar, TypingPassBlockResultVarNameT);
    impl_intern_name_wrapper_simple_none!(intern_typing_pass_function_result_var_name, TypingPassFunctionResultVar, TypingPassFunctionResultVarNameT);
    impl_intern_name_wrapper_simple_t_only!(intern_typing_pass_temporary_var_name, TypingPassTemporaryVar, TypingPassTemporaryVarNameT);
    impl_intern_name_wrapper_simple_t_only!(intern_typing_pass_pattern_member_name, TypingPassPatternMember, TypingPassPatternMemberNameT);
    impl_intern_name_wrapper_simple_none!(intern_typing_ignored_param_name, TypingIgnoredParam, TypingIgnoredParamNameT);
    impl_intern_name_wrapper_simple_t_only!(intern_typing_pass_pattern_destructuree_name, TypingPassPatternDestructuree, TypingPassPatternDestructureeNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_unnamed_local_name, UnnamedLocal, UnnamedLocalNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_closure_param_name, ClosureParam, ClosureParamNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_constructing_member_name, ConstructingMember, ConstructingMemberNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_while_cond_result_name, WhileCondResult, WhileCondResultNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_iterable_name, Iterable, IterableNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_iterator_name, Iterator, IteratorNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_iteration_option_name, IterationOption, IterationOptionNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_magic_param_name, MagicParam, MagicParamNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_code_var_name, CodeVar, CodeVarNameT);
    impl_intern_name_wrapper_simple_none!(intern_anonymous_substruct_member_name, AnonymousSubstructMember, AnonymousSubstructMemberNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_primitive_name, Primitive, PrimitiveNameT);
    impl_intern_name_wrapper_simple_none!(intern_package_top_level_name, PackageTopLevel, PackageTopLevelNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_project_name, Project, ProjectNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_package_name, Package, PackageNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_rune_name, Rune, RuneNameT);
    impl_intern_name_wrapper_simple!(intern_building_function_name_with_closureds, BuildingFunctionNameWithClosureds, BuildingFunctionNameWithClosuredsT);
    impl_intern_name_wrapper_simple_s_only!(intern_extern_template_name, ExternTemplate, ExternTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_extern_name, Extern, ExternNameT);
    impl_intern_name_wrapper_simple!(intern_forwarder_function_name, ForwarderFunction, ForwarderFunctionNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_function_bound_template_name, FunctionBoundTemplate, FunctionBoundTemplateNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_predicted_function_template_name, PredictedFunctionTemplate, PredictedFunctionTemplateNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_function_template_name, FunctionTemplate, FunctionTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_forwarder_function_template_name, ForwarderFunctionTemplate, ForwarderFunctionTemplateNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_constructor_template_name, ConstructorTemplate, ConstructorTemplateNameT);
    impl_intern_name_wrapper_simple_none!(intern_self_name, Self_, SelfNameT);
    impl_intern_name_wrapper_simple_none!(intern_arbitrary_name, Arbitrary, ArbitraryNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_lambda_citizen_template_name, LambdaCitizenTemplate, LambdaCitizenTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_lambda_citizen_name, LambdaCitizen, LambdaCitizenNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_struct_template_name, StructTemplate, StructTemplateNameT);
    impl_intern_name_wrapper_simple_s_only!(intern_interface_template_name, InterfaceTemplate, InterfaceTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_anonymous_substruct_impl_template_name, AnonymousSubstructImplTemplate, AnonymousSubstructImplTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_anonymous_substruct_template_name, AnonymousSubstructTemplate, AnonymousSubstructTemplateNameT);
    impl_intern_name_wrapper_simple!(intern_anonymous_substruct_constructor_template_name, AnonymousSubstructConstructorTemplate, AnonymousSubstructConstructorTemplateNameT);
    impl_intern_name_wrapper_simple_none!(intern_resolving_env_name, ResolvingEnv, ResolvingEnvNameT);
    impl_intern_name_wrapper_simple_none!(intern_call_env_name, CallEnv, CallEnvNameT);

    // --- 6 Kind-payload wrappers ---
    // 5 sealed types take their `*ValT` mirror; the macro builds the canonical
    // (with `_must_intern: MustIntern(())`) inside `intern_kind_payload`'s match.
    // KindPlaceholderT is Value-type per @WVSBIZ — its "Val" is the canonical itself.
    impl_intern_kind_wrapper!(intern_struct_tt, StructTT, StructTTValT, StructTT);
    impl_intern_kind_wrapper!(intern_interface_tt, InterfaceTT, InterfaceTTValT, InterfaceTT);
    impl_intern_kind_wrapper!(intern_static_sized_array_tt, StaticSizedArrayTT, StaticSizedArrayTTValT, StaticSizedArrayTT);
    impl_intern_kind_wrapper!(intern_runtime_sized_array_tt, RuntimeSizedArrayTT, RuntimeSizedArrayTTValT, RuntimeSizedArrayTT);
    impl_intern_kind_wrapper!(intern_kind_placeholder, KindPlaceholder, KindPlaceholderT, KindPlaceholderT);
    impl_intern_kind_wrapper!(intern_overload_set, OverloadSet, OverloadSetTValT, OverloadSetT);

    // (Templata payload wrappers removed — types are TFITCX Value-type per
    // Scala parity and constructed directly via `bump.alloc(FooTemplataT { ... })`.)
}

// KindT and ITemplataT are inline-owned (not arena-interned), so they have no
// Val companions and no intern methods. See the "reuse struct as Val" comment
// in types/types.rs for the per-concrete-payload story.
