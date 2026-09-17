
// ScoutArena: arena + interning maps for the postparsing (scout) pass.
// Has string/coord interning (like ParseArena) plus name/rune/imprecise-name interning.

use crate::interner::{InternedSlice, StrI};
use crate::postparsing::names::{
  IImpreciseNameS, IImpreciseNameValS, INameS, INameValS, IRuneS, IRuneValS,
  IFunctionDeclarationNameS, IFunctionDeclarationNameValS, IVarNameS, IVarNameValS,
  ImplicitRegionRuneS, ImplicitCoercionOwnershipRuneS, ImplicitCoercionKindRuneS,
  RuneNameS, TopLevelStructDeclarationNameS, TopLevelInterfaceDeclarationNameS,
  ImplicitCoercionTemplateRuneS, AnonymousSubstructMethodInheritedRuneS,
  DispatcherRuneFromImplS, CaseRuneFromImplS,
  LambdaStructImpreciseNameS,
  AnonymousSubstructTemplateImpreciseNameS,
  AnonymousSubstructConstructorTemplateImpreciseNameS, ImplImpreciseNameS,
  ImplSubCitizenImpreciseNameS, ImplSuperInterfaceImpreciseNameS,
  ImplicitRuneValS, PureBlockRegionRuneValS, CallRegionRuneValS,
  CallPureMergeRegionRuneValS, LetImplicitRuneValS, MagicParamRuneValS,
  LocalDefaultRegionRuneValS,
  ImplicitRuneS, PureBlockRegionRuneS, CallRegionRuneS,
  CallPureMergeRegionRuneS, LetImplicitRuneS, MagicParamRuneS,
  LocalDefaultRegionRuneS,
};
use crate::postparsing::ast::LocationInDenizenVal;
use crate::utils::code_hierarchy::{FileCoordinate, PackageCoordinate};
use bumpalo::Bump;
use std::cell::RefCell;
use std::collections::HashMap;
use crate::postparsing::names::IImpreciseNameValS::*;
use crate::postparsing::names::{ForwarderFunctionDeclarationNameS, ForwarderFunctionDeclarationNameValS};
use IRuneValS::*;
use crate::utils::arena_index_map::ArenaIndexMap;
use crate::postparsing::names::AnonymousSubstructImplDeclarationNameS;
use crate::postparsing::names::AnonymousSubstructTemplateNameS;
use crate::postparsing::names::RuneValQuery;
use std::hash::Hash;
use std::hash::Hasher;
use std::ptr::eq;

#[derive(Clone)]
struct FileCoordLookupKey<'s> {
  package_coord: &'s PackageCoordinate<'s>,
  filepath: String,
}

impl<'s> PartialEq for FileCoordLookupKey<'s> {
  fn eq(&self, other: &Self) -> bool {
    eq(self.package_coord, other.package_coord) && self.filepath == other.filepath
  }
}
impl<'s> Eq for FileCoordLookupKey<'s> {}

impl<'s> Hash for FileCoordLookupKey<'s> {
  fn hash<H: Hasher>(&self, state: &mut H) {
    (self.package_coord as *const PackageCoordinate<'_>).hash(state);
    self.filepath.hash(state);
  }
}

pub struct ScoutArena<'s> {
  bump: &'s Bump,
  inner: RefCell<ScoutArenaInner<'s>>,
}

struct ScoutArenaInner<'s> {
  string_to_interned: HashMap<String, &'s str>,
  package_coord_to_ref: HashMap<PackageCoordinate<'s>, &'s PackageCoordinate<'s>>,
  file_coord_to_ref: HashMap<FileCoordLookupKey<'s>, &'s FileCoordinate<'s>>,
  imprecise_name_val_to_ref: HashMap<IImpreciseNameValS<'s>, IImpreciseNameS<'s>>,
  name_val_to_ref: HashMap<INameValS<'s>, INameS<'s>>,
  // Per @DSAUIMZ, uses hashbrown for heterogeneous lookup (IRuneValS<'s, 'tmp> against IRuneValS<'s, 's> keys).
  rune_val_to_ref: hashbrown::HashMap<IRuneValS<'s, 's>, IRuneS<'s>>,
}

impl<'s> ScoutArena<'s> {
  pub fn new(bump: &'s Bump) -> Self {
    ScoutArena {
      bump,
      inner: RefCell::new(ScoutArenaInner {
        string_to_interned: HashMap::with_capacity(256),
        package_coord_to_ref: HashMap::new(),
        file_coord_to_ref: HashMap::new(),
        imprecise_name_val_to_ref: HashMap::new(),
        name_val_to_ref: HashMap::new(),
        rune_val_to_ref: hashbrown::HashMap::new(),
      }),
    }
  }

  pub fn alloc<T>(&self, val: T) -> &'s mut T {
    self.bump.alloc(val)
  }

  pub fn alloc_slice_copy<T: Copy>(&self, src: &[T]) -> &'s [T] {
    self.bump.alloc_slice_copy(src)
  }

  /// Allocate a slice from a Vec into the arena.
  pub fn alloc_slice_from_vec<T>(&self, vec: Vec<T>) -> &'s [T] {
    self.bump.alloc_slice_fill_iter(vec.into_iter())
  }

  /// Create an empty ArenaIndexMap allocated in this arena.
  pub fn alloc_index_map<K: Hash + Eq + Clone, V>(&self) -> ArenaIndexMap<'s, K, V> {
    ArenaIndexMap::new_in(self.bump)
  }

  /// Create an ArenaIndexMap from an iterator, allocated in this arena.
  pub fn alloc_index_map_from_iter<K: Hash + Eq + Clone, V, I: IntoIterator<Item = (K, V)>>(&self, iter: I) -> ArenaIndexMap<'s, K, V> {
    ArenaIndexMap::from_iter_in(iter, self.bump)
  }

  // --- String interning ---

  pub fn intern_str(&self, s: &str) -> StrI<'s> {
    let mut inner = self.inner.borrow_mut();
    if let Some(&existing) = inner.string_to_interned.get(s) {
      return StrI(existing);
    }
    let arena_str = self.bump.alloc_str(s);
    inner.string_to_interned.insert(s.to_string(), arena_str);
    StrI(arena_str)
  }

  // --- Package/File coordinate interning ---

  pub fn intern_package_coordinate(
    &self,
    module: StrI<'s>,
    packages: &[StrI<'s>],
  ) -> &'s PackageCoordinate<'s> {
    let mut inner = self.inner.borrow_mut();
    let lookup_coord = PackageCoordinate {
      module,
      packages: InternedSlice::new(packages),
    };
    if let Some(existing) = inner.package_coord_to_ref.get(&lookup_coord) {
      return *existing;
    }
    let arena_packages = self.bump.alloc_slice_copy(packages);
    let coord = PackageCoordinate {
      module,
      packages: InternedSlice::new(arena_packages),
    };
    let new_ref: &'s PackageCoordinate<'s> = self.bump.alloc(coord.clone());
    inner.package_coord_to_ref.insert(coord, new_ref);
    new_ref
  }

  pub fn intern_file_coordinate(
    &self,
    package_coord: &'s PackageCoordinate<'s>,
    filepath: &str,
  ) -> &'s FileCoordinate<'s> {
    let mut inner = self.inner.borrow_mut();
    let lookup_key = FileCoordLookupKey {
      package_coord,
      filepath: filepath.to_string(),
    };
    if let Some(existing) = inner.file_coord_to_ref.get(&lookup_key) {
      return *existing;
    }
    let arena_filepath = self.bump.alloc_str(filepath);
    let coord = FileCoordinate {
      package_coord,
      filepath: StrI(arena_filepath),
    };
    let new_ref: &'s FileCoordinate<'s> = self.bump.alloc(coord.clone());
    let insert_key = FileCoordLookupKey {
      package_coord,
      filepath: filepath.to_string(),
    };
    inner.file_coord_to_ref.insert(insert_key, new_ref);
    new_ref
  }

  // --- Imprecise name interning ---

  pub fn intern_imprecise_name(&self, val: IImpreciseNameValS<'s>) -> IImpreciseNameS<'s> {
    {
      let inner = self.inner.borrow();
      if let Some(existing) = inner.imprecise_name_val_to_ref.get(&val) {
        return existing.clone();
      }
    }
    let canonical: IImpreciseNameS<'s> = self.alloc_imprecise_name_canonical(val.clone());
    let mut inner = self.inner.borrow_mut();
    inner.imprecise_name_val_to_ref.insert(val, canonical.clone());
    canonical
  }

  fn alloc_imprecise_name_canonical(&self, val: IImpreciseNameValS<'s>) -> IImpreciseNameS<'s> {
    match val {
      CodeName(p) => IImpreciseNameS::CodeName(self.bump.alloc(p)),
      IterableName(p) => IImpreciseNameS::IterableName(self.bump.alloc(p)),
      IteratorName(p) => IImpreciseNameS::IteratorName(self.bump.alloc(p)),
      IterationOptionName(p) => IImpreciseNameS::IterationOptionName(self.bump.alloc(p)),
      LambdaImpreciseName(p) => IImpreciseNameS::LambdaImpreciseName(self.bump.alloc(p)),
      PlaceholderImpreciseName(p) => IImpreciseNameS::PlaceholderImpreciseName(self.bump.alloc(p)),
      LambdaStructImpreciseName(v) => {
        let payload = LambdaStructImpreciseNameS { lambda_name: v.lambda_name };
        IImpreciseNameS::LambdaStructImpreciseName(self.bump.alloc(payload))
      }
      ClosureParamImpreciseName(p) => IImpreciseNameS::ClosureParamImpreciseName(self.bump.alloc(p)),
      PrototypeName(p) => IImpreciseNameS::PrototypeName(self.bump.alloc(p)),
      AnonymousSubstructTemplateImpreciseName(v) => {
        let payload = AnonymousSubstructTemplateImpreciseNameS { interface_imprecise_name: v.interface_imprecise_name };
        IImpreciseNameS::AnonymousSubstructTemplateImpreciseName(self.bump.alloc(payload))
      }
      AnonymousSubstructConstructorTemplateImpreciseName(v) => {
        let payload = AnonymousSubstructConstructorTemplateImpreciseNameS { interface_imprecise_name: v.interface_imprecise_name };
        IImpreciseNameS::AnonymousSubstructConstructorTemplateImpreciseName(self.bump.alloc(payload))
      }
      ImplImpreciseName(v) => {
        let payload = ImplImpreciseNameS { sub_citizen_imprecise_name: v.sub_citizen_imprecise_name, super_interface_imprecise_name: v.super_interface_imprecise_name };
        IImpreciseNameS::ImplImpreciseName(self.bump.alloc(payload))
      }
      ImplSubCitizenImpreciseName(v) => {
        let payload = ImplSubCitizenImpreciseNameS { sub_citizen_imprecise_name: v.sub_citizen_imprecise_name };
        IImpreciseNameS::ImplSubCitizenImpreciseName(self.bump.alloc(payload))
      }
      ImplSuperInterfaceImpreciseName(v) => {
        let payload = ImplSuperInterfaceImpreciseNameS { super_interface_imprecise_name: v.super_interface_imprecise_name };
        IImpreciseNameS::ImplSuperInterfaceImpreciseName(self.bump.alloc(payload))
      }
      SelfName(p) => IImpreciseNameS::SelfName(self.bump.alloc(p)),
      RuneName(v) => {
        let payload = RuneNameS { rune: v.rune };
        IImpreciseNameS::RuneName(self.bump.alloc(payload))
      }
      ArbitraryName(p) => IImpreciseNameS::ArbitraryName(self.bump.alloc(p)),
    }
  }

  // --- Name interning ---

  pub fn intern_struct_declaration_name(&self, val: TopLevelStructDeclarationNameS<'s>) -> &'s TopLevelStructDeclarationNameS<'s> {
    match self.intern_name(INameValS::TopLevelStructDeclaration(val)) {
      INameS::TopLevelStructDeclaration(r) => r,
      _ => unreachable!(),
    }
  }

  pub fn intern_interface_declaration_name(&self, val: TopLevelInterfaceDeclarationNameS<'s>) -> &'s TopLevelInterfaceDeclarationNameS<'s> {
    match self.intern_name(INameValS::TopLevelInterfaceDeclaration(val)) {
      INameS::TopLevelInterfaceDeclaration(r) => r,
      _ => unreachable!(),
    }
  }

  pub fn intern_name(&self, val: INameValS<'s>) -> INameS<'s> {
    {
      let inner = self.inner.borrow();
      if let Some(existing) = inner.name_val_to_ref.get(&val) {
        return existing.clone();
      }
    }
    let canonical = self.alloc_name_canonical(val.clone());
    let mut inner = self.inner.borrow_mut();
    inner.name_val_to_ref.insert(val, canonical.clone());
    canonical
  }

  fn alloc_name_canonical(&self, val: INameValS<'s>) -> INameS<'s> {
    use crate::postparsing::names::{
      AnonymousSubstructImplDeclarationNameValS, AnonymousSubstructTemplateNameValS,
    };
    match val {
      INameValS::FunctionDeclaration(v) => {
        let inner = self.alloc_function_declaration_name_canonical(v);
        INameS::FunctionDeclaration(self.bump.alloc(inner))
      }
      INameValS::ImplDeclaration(p) => INameS::ImplDeclaration(self.bump.alloc(p)),
      INameValS::AnonymousSubstructImplDeclaration(AnonymousSubstructImplDeclarationNameValS { interface }) => {
        let payload = AnonymousSubstructImplDeclarationNameS { interface: interface.clone() };
        INameS::AnonymousSubstructImplDeclaration(self.bump.alloc(payload))
      }
      INameValS::ExportAsName(p) => INameS::ExportAsName(self.bump.alloc(p)),
      INameValS::LetName(p) => INameS::LetName(self.bump.alloc(p)),
      INameValS::TopLevelStructDeclaration(p) => INameS::TopLevelStructDeclaration(self.bump.alloc(p)),
      INameValS::TopLevelInterfaceDeclaration(p) => INameS::TopLevelInterfaceDeclaration(self.bump.alloc(p)),
      INameValS::LambdaStructDeclaration(p) => INameS::LambdaStructDeclaration(self.bump.alloc(p)),
      INameValS::AnonymousSubstructTemplateName(AnonymousSubstructTemplateNameValS { interface_name }) => {
        let payload = AnonymousSubstructTemplateNameS { interface_name: interface_name.clone() };
        INameS::AnonymousSubstructTemplateName(self.bump.alloc(payload))
      }
      INameValS::RuneName(v) => {
        let payload = RuneNameS { rune: v.rune };
        INameS::RuneName(self.bump.alloc(payload))
      }
      INameValS::RuntimeSizedArrayDeclarationName(p) => INameS::RuntimeSizedArrayDeclarationName(self.bump.alloc(p)),
      INameValS::StaticSizedArrayDeclarationName(p) => INameS::StaticSizedArrayDeclarationName(self.bump.alloc(p)),
      INameValS::GlobalFunctionFamilyName(p) => INameS::GlobalFunctionFamilyName(self.bump.alloc(p)),
      INameValS::ArbitraryName(p) => INameS::ArbitraryName(self.bump.alloc(p)),
      INameValS::VarName(v) => {
        let inner = self.alloc_var_name_canonical(v);
        INameS::VarName(self.bump.alloc(inner))
      }
    }
  }

  fn alloc_function_declaration_name_canonical(&self, val: IFunctionDeclarationNameValS<'s>) -> IFunctionDeclarationNameS<'s> {
    match val {
      IFunctionDeclarationNameValS::FunctionName(p) => IFunctionDeclarationNameS::FunctionName(p),
      IFunctionDeclarationNameValS::LambdaDeclarationName(p) => IFunctionDeclarationNameS::LambdaDeclarationName(p),
      IFunctionDeclarationNameValS::ForwarderFunctionDeclarationName(ForwarderFunctionDeclarationNameValS { inner, index }) => {
        let payload = ForwarderFunctionDeclarationNameS { inner: inner.clone(), index };
        IFunctionDeclarationNameS::ForwarderFunctionDeclarationName(self.bump.alloc(payload))
      }
      IFunctionDeclarationNameValS::ConstructorName(p) => IFunctionDeclarationNameS::ConstructorName(self.bump.alloc(p)),
      IFunctionDeclarationNameValS::ImmConcreteDestructorName(p) => IFunctionDeclarationNameS::ImmConcreteDestructorName(self.bump.alloc(p)),
      IFunctionDeclarationNameValS::ImmInterfaceDestructorName(p) => IFunctionDeclarationNameS::ImmInterfaceDestructorName(self.bump.alloc(p)),
    }
  }

  fn alloc_var_name_canonical(&self, val: IVarNameValS<'s>) -> IVarNameS<'s> {
    match val {
      IVarNameValS::CodeVarName(n) => IVarNameS::CodeVarName(n),
      IVarNameValS::ConstructingMemberName(n) => IVarNameS::ConstructingMemberName(n),
      IVarNameValS::ClosureParamName(p) => IVarNameS::ClosureParamName(self.bump.alloc(p)),
      IVarNameValS::MagicParamName(p) => IVarNameS::MagicParamName(p),
      IVarNameValS::IterableName(p) => IVarNameS::IterableName(p),
      IVarNameValS::IteratorName(p) => IVarNameS::IteratorName(p),
      IVarNameValS::IterationOptionName(p) => IVarNameS::IterationOptionName(p),
      IVarNameValS::WhileCondResultName(p) => IVarNameS::WhileCondResultName(p),
      IVarNameValS::SelfName => IVarNameS::SelfName,
      IVarNameValS::AnonymousSubstructMemberName(i) => IVarNameS::AnonymousSubstructMemberName(i),
    }
  }

  // --- Rune interning ---

  // Per @DSAUIMZ, slices are arena-allocated here on miss, not by the caller.
  pub fn intern_rune<'tmp>(&self, val: IRuneValS<'s, 'tmp>) -> IRuneS<'s> {
    {
      let inner = self.inner.borrow();
      let query = RuneValQuery(&val);
      if let Some(existing) = inner.rune_val_to_ref.get(&query) {
        return existing.clone();  // HIT — zero allocation
      }
    }
    // MISS — promote val (arena-alloc slices) and build canonical
    let (promoted_key, canonical) = self.alloc_rune_canonical(val);
    let mut inner = self.inner.borrow_mut();
    inner.rune_val_to_ref.insert(promoted_key, canonical.clone());
    canonical
  }

  /// Promotes a Val (which may borrow temporaries via 'tmp) into an arena-allocated
  /// canonical IRuneS and a stored key IRuneValS<'s, 's>.
  /// Per @DSAUIMZ, this is where lid slices get arena-allocated — only on intern miss.
  fn alloc_rune_canonical<'tmp>(&self, val: IRuneValS<'s, 'tmp>) -> (IRuneValS<'s, 's>, IRuneS<'s>) {
    match val {
      // ── 7 lid variants: promote LocationInDenizenVal → LocationInDenizen ──
      ImplicitRune(v) => {
        let lid = v.lid().promote_in(self.bump);
        let canonical = IRuneS::ImplicitRune(self.bump.alloc(ImplicitRuneS { lid }));
        (IRuneValS::ImplicitRune(ImplicitRuneValS::new(LocationInDenizenVal::from_canonical(&lid))), canonical)
      }
      PureBlockRegionRune(v) => {
        let lid = v.lid().promote_in(self.bump);
        let canonical = IRuneS::PureBlockRegionRune(self.bump.alloc(PureBlockRegionRuneS { lid }));
        (IRuneValS::PureBlockRegionRune(PureBlockRegionRuneValS::new(LocationInDenizenVal::from_canonical(&lid))), canonical)
      }
      CallRegionRune(v) => {
        let lid = v.lid().promote_in(self.bump);
        let canonical = IRuneS::CallRegionRune(self.bump.alloc(CallRegionRuneS { lid }));
        (IRuneValS::CallRegionRune(CallRegionRuneValS::new(LocationInDenizenVal::from_canonical(&lid))), canonical)
      }
      CallPureMergeRegionRune(v) => {
        let lid = v.lid().promote_in(self.bump);
        let canonical = IRuneS::CallPureMergeRegionRune(self.bump.alloc(CallPureMergeRegionRuneS { lid }));
        (IRuneValS::CallPureMergeRegionRune(CallPureMergeRegionRuneValS::new(LocationInDenizenVal::from_canonical(&lid))), canonical)
      }
      LetImplicitRune(v) => {
        let lid = v.lid().promote_in(self.bump);
        let canonical = IRuneS::LetImplicitRune(self.bump.alloc(LetImplicitRuneS { lid }));
        (IRuneValS::LetImplicitRune(LetImplicitRuneValS::new(LocationInDenizenVal::from_canonical(&lid))), canonical)
      }
      MagicParamRune(v) => {
        let lid = v.lid().promote_in(self.bump);
        let canonical = IRuneS::MagicParamRune(self.bump.alloc(MagicParamRuneS { lid }));
        (IRuneValS::MagicParamRune(MagicParamRuneValS::new(LocationInDenizenVal::from_canonical(&lid))), canonical)
      }
      LocalDefaultRegionRune(v) => {
        let lid = v.lid().promote_in(self.bump);
        let canonical = IRuneS::LocalDefaultRegionRune(self.bump.alloc(LocalDefaultRegionRuneS { lid }));
        (IRuneValS::LocalDefaultRegionRune(LocalDefaultRegionRuneValS::new(LocationInDenizenVal::from_canonical(&lid))), canonical)
      }
      // ── Shallow Val variants (already have separate Val structs) ──
      // Clone v for the stored key before moving fields into the canonical payload.
      // These inner fields are all small Copy-ish types (IRuneS is a tagged pointer).
      ImplicitRegionRune(v) => {
        let key = v.clone();
        let payload = ImplicitRegionRuneS { original_rune: v.original_rune };
        let canonical = IRuneS::ImplicitRegionRune(self.bump.alloc(payload));
        (IRuneValS::ImplicitRegionRune(key), canonical)
      }
      ImplicitCoercionOwnershipRune(v) => {
        let key = v.clone();
        let payload = ImplicitCoercionOwnershipRuneS { range: v.range, original_coord_rune: v.original_coord_rune };
        let canonical = IRuneS::ImplicitCoercionOwnershipRune(self.bump.alloc(payload));
        (IRuneValS::ImplicitCoercionOwnershipRune(key), canonical)
      }
      ImplicitCoercionKindRune(v) => {
        let key = v.clone();
        let payload = ImplicitCoercionKindRuneS { range: v.range, original_coord_rune: v.original_coord_rune };
        let canonical = IRuneS::ImplicitCoercionKindRune(self.bump.alloc(payload));
        (IRuneValS::ImplicitCoercionKindRune(key), canonical)
      }
      ImplicitCoercionTemplateRune(v) => {
        let key = v.clone();
        let payload = ImplicitCoercionTemplateRuneS { range: v.range, original_kind_rune: v.original_kind_rune };
        let canonical = IRuneS::ImplicitCoercionTemplateRune(self.bump.alloc(payload));
        (IRuneValS::ImplicitCoercionTemplateRune(key), canonical)
      }
      AnonymousSubstructMethodInheritedRune(v) => {
        let key = v.clone();
        let payload = AnonymousSubstructMethodInheritedRuneS { interface: v.interface, method: v.method, inner: v.inner };
        let canonical = IRuneS::AnonymousSubstructMethodInheritedRune(self.bump.alloc(payload));
        (IRuneValS::AnonymousSubstructMethodInheritedRune(key), canonical)
      }
      DispatcherRuneFromImpl(v) => {
        let key = v.clone();
        let payload = DispatcherRuneFromImplS { inner_rune: v.inner_rune };
        let canonical = IRuneS::DispatcherRuneFromImpl(self.bump.alloc(payload));
        (IRuneValS::DispatcherRuneFromImpl(key), canonical)
      }
      CaseRuneFromImpl(v) => {
        let key = v.clone();
        let payload = CaseRuneFromImplS { inner_rune: v.inner_rune };
        let canonical = IRuneS::CaseRuneFromImpl(self.bump.alloc(payload));
        (IRuneValS::CaseRuneFromImpl(key), canonical)
      }
      // ── Simple Val variants (same struct in both enums) ──
      CodeRune(p) => { let c = IRuneS::CodeRune(self.bump.alloc(p.clone())); (IRuneValS::CodeRune(p), c) }
      ImplDropCoordRune(p) => { let c = IRuneS::ImplDropCoordRune(self.bump.alloc(p.clone())); (IRuneValS::ImplDropCoordRune(p), c) }
      ImplDropVoidRune(p) => { let c = IRuneS::ImplDropVoidRune(self.bump.alloc(p.clone())); (IRuneValS::ImplDropVoidRune(p), c) }
      ReachablePrototypeRune(p) => { let c = IRuneS::ReachablePrototypeRune(self.bump.alloc(p.clone())); (IRuneValS::ReachablePrototypeRune(p), c) }
      FreeOverrideStructTemplateRune(p) => { let c = IRuneS::FreeOverrideStructTemplateRune(self.bump.alloc(p.clone())); (IRuneValS::FreeOverrideStructTemplateRune(p), c) }
      FreeOverrideStructRune(p) => { let c = IRuneS::FreeOverrideStructRune(self.bump.alloc(p.clone())); (IRuneValS::FreeOverrideStructRune(p), c) }
      FreeOverrideInterfaceRune(p) => { let c = IRuneS::FreeOverrideInterfaceRune(self.bump.alloc(p.clone())); (IRuneValS::FreeOverrideInterfaceRune(p), c) }
      MemberRune(p) => { let c = IRuneS::MemberRune(self.bump.alloc(p.clone())); (IRuneValS::MemberRune(p), c) }
      DenizenDefaultRegionRune(p) => { let c = IRuneS::DenizenDefaultRegionRune(self.bump.alloc(p.clone())); (IRuneValS::DenizenDefaultRegionRune(p), c) }
      ExportDefaultRegionRune(p) => { let c = IRuneS::ExportDefaultRegionRune(self.bump.alloc(p.clone())); (IRuneValS::ExportDefaultRegionRune(p), c) }
      ExternDefaultRegionRune(p) => { let c = IRuneS::ExternDefaultRegionRune(self.bump.alloc(p.clone())); (IRuneValS::ExternDefaultRegionRune(p), c) }
      ArraySizeImplicitRune(p) => { let c = IRuneS::ArraySizeImplicitRune(self.bump.alloc(p.clone())); (IRuneValS::ArraySizeImplicitRune(p), c) }
      ArrayMutabilityImplicitRune(p) => { let c = IRuneS::ArrayMutabilityImplicitRune(self.bump.alloc(p.clone())); (IRuneValS::ArrayMutabilityImplicitRune(p), c) }
      ArrayVariabilityImplicitRune(p) => { let c = IRuneS::ArrayVariabilityImplicitRune(self.bump.alloc(p.clone())); (IRuneValS::ArrayVariabilityImplicitRune(p), c) }
      ReturnRune(p) => { let c = IRuneS::ReturnRune(self.bump.alloc(p.clone())); (IRuneValS::ReturnRune(p), c) }
      StructNameRune(p) => { let c = IRuneS::StructNameRune(self.bump.alloc(p.clone())); (IRuneValS::StructNameRune(p), c) }
      InterfaceNameRune(p) => { let c = IRuneS::InterfaceNameRune(self.bump.alloc(p.clone())); (IRuneValS::InterfaceNameRune(p), c) }
      SelfRune(p) => { let c = IRuneS::SelfRune(self.bump.alloc(p.clone())); (IRuneValS::SelfRune(p), c) }
      SelfOwnershipRune(p) => { let c = IRuneS::SelfOwnershipRune(self.bump.alloc(p.clone())); (IRuneValS::SelfOwnershipRune(p), c) }
      SelfKindRune(p) => { let c = IRuneS::SelfKindRune(self.bump.alloc(p.clone())); (IRuneValS::SelfKindRune(p), c) }
      SelfKindTemplateRune(p) => { let c = IRuneS::SelfKindTemplateRune(self.bump.alloc(p.clone())); (IRuneValS::SelfKindTemplateRune(p), c) }
      SelfCoordRune(p) => { let c = IRuneS::SelfCoordRune(self.bump.alloc(p.clone())); (IRuneValS::SelfCoordRune(p), c) }
      MacroVoidKindRune(p) => { let c = IRuneS::MacroVoidKindRune(self.bump.alloc(p.clone())); (IRuneValS::MacroVoidKindRune(p), c) }
      MacroVoidCoordRune(p) => { let c = IRuneS::MacroVoidCoordRune(self.bump.alloc(p.clone())); (IRuneValS::MacroVoidCoordRune(p), c) }
      MacroSelfKindRune(p) => { let c = IRuneS::MacroSelfKindRune(self.bump.alloc(p.clone())); (IRuneValS::MacroSelfKindRune(p), c) }
      MacroSelfKindTemplateRune(p) => { let c = IRuneS::MacroSelfKindTemplateRune(self.bump.alloc(p.clone())); (IRuneValS::MacroSelfKindTemplateRune(p), c) }
      MacroSelfCoordRune(p) => { let c = IRuneS::MacroSelfCoordRune(self.bump.alloc(p.clone())); (IRuneValS::MacroSelfCoordRune(p), c) }
      ArgumentRune(p) => { let c = IRuneS::ArgumentRune(self.bump.alloc(p.clone())); (IRuneValS::ArgumentRune(p), c) }
      PatternInputRune(p) => { let c = IRuneS::PatternInputRune(self.bump.alloc(p.clone())); (IRuneValS::PatternInputRune(p), c) }
      ExplicitTemplateArgRune(p) => { let c = IRuneS::ExplicitTemplateArgRune(self.bump.alloc(p.clone())); (IRuneValS::ExplicitTemplateArgRune(p), c) }
      AnonymousSubstructParentInterfaceTemplateRune(p) => { let c = IRuneS::AnonymousSubstructParentInterfaceTemplateRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructParentInterfaceTemplateRune(p), c) }
      AnonymousSubstructParentInterfaceKindRune(p) => { let c = IRuneS::AnonymousSubstructParentInterfaceKindRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructParentInterfaceKindRune(p), c) }
      AnonymousSubstructParentInterfaceCoordRune(p) => { let c = IRuneS::AnonymousSubstructParentInterfaceCoordRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructParentInterfaceCoordRune(p), c) }
      AnonymousSubstructTemplateRune(p) => { let c = IRuneS::AnonymousSubstructTemplateRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructTemplateRune(p), c) }
      AnonymousSubstructKindRune(p) => { let c = IRuneS::AnonymousSubstructKindRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructKindRune(p), c) }
      AnonymousSubstructCoordRune(p) => { let c = IRuneS::AnonymousSubstructCoordRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructCoordRune(p), c) }
      AnonymousSubstructVoidKindRune(p) => { let c = IRuneS::AnonymousSubstructVoidKindRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructVoidKindRune(p), c) }
      AnonymousSubstructVoidCoordRune(p) => { let c = IRuneS::AnonymousSubstructVoidCoordRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructVoidCoordRune(p), c) }
      AnonymousSubstructMemberRune(p) => { let c = IRuneS::AnonymousSubstructMemberRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructMemberRune(p), c) }
      AnonymousSubstructMethodSelfBorrowCoordRune(p) => { let c = IRuneS::AnonymousSubstructMethodSelfBorrowCoordRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructMethodSelfBorrowCoordRune(p), c) }
      AnonymousSubstructMethodSelfOwnCoordRune(p) => { let c = IRuneS::AnonymousSubstructMethodSelfOwnCoordRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructMethodSelfOwnCoordRune(p), c) }
      AnonymousSubstructDropBoundPrototypeRune(p) => { let c = IRuneS::AnonymousSubstructDropBoundPrototypeRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructDropBoundPrototypeRune(p), c) }
      AnonymousSubstructDropBoundParamsListRune(p) => { let c = IRuneS::AnonymousSubstructDropBoundParamsListRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructDropBoundParamsListRune(p), c) }
      AnonymousSubstructFunctionBoundPrototypeRune(p) => { let c = IRuneS::AnonymousSubstructFunctionBoundPrototypeRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructFunctionBoundPrototypeRune(p), c) }
      AnonymousSubstructFunctionBoundParamsListRune(p) => { let c = IRuneS::AnonymousSubstructFunctionBoundParamsListRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructFunctionBoundParamsListRune(p), c) }
      AnonymousSubstructFunctionInterfaceTemplateRune(p) => { let c = IRuneS::AnonymousSubstructFunctionInterfaceTemplateRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructFunctionInterfaceTemplateRune(p), c) }
      AnonymousSubstructFunctionInterfaceKindRune(p) => { let c = IRuneS::AnonymousSubstructFunctionInterfaceKindRune(self.bump.alloc(p.clone())); (IRuneValS::AnonymousSubstructFunctionInterfaceKindRune(p), c) }
      FunctorPrototypeRuneName(p) => { let c = IRuneS::FunctorPrototypeRuneName(self.bump.alloc(p.clone())); (IRuneValS::FunctorPrototypeRuneName(p), c) }
      FunctorParamRuneName(p) => { let c = IRuneS::FunctorParamRuneName(self.bump.alloc(p.clone())); (IRuneValS::FunctorParamRuneName(p), c) }
      FunctorReturnRuneName(p) => { let c = IRuneS::FunctorReturnRuneName(self.bump.alloc(p.clone())); (IRuneValS::FunctorReturnRuneName(p), c) }
    }
  }
}
