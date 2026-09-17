use std::collections::HashSet;
use indexmap::IndexSet;
use crate::higher_typing::ast::FunctionA;
use crate::scout_arena::ScoutArena;
use crate::postparsing::expressions::IExpressionSE;
use crate::postparsing::names::IImpreciseNameS;
use crate::typing::ast::ast::LocationInFunctionEnvironmentT;
use crate::typing::env::environment::{
  GlobalEnvironmentT, IEnvironmentT, IInDenizenEnvironmentT, ILookupContext, TemplatasStoreBuilder, TemplatasStoreT,
};
use crate::typing::env::i_env_entry::IEnvEntryT;
use crate::typing::names::names::{IdT, INameT, IVarNameT};
use crate::typing::templata::templata::{FunctionTemplataT, ITemplataT};
use crate::typing::types::types::{CoordT, RegionT, StructTT, VariabilityT};
use crate::typing::typing_interner::TypingInterner;
use std::hash::Hash;
use std::hash::Hasher;
use std::ptr::eq;

/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct BuildingFunctionEnvironmentWithClosuredsT<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IEnvironmentT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas: &'t TemplatasStoreT<'s, 't>,
  pub function: &'s FunctionA<'s>,
  pub variables: &'t [IVariableT<'s, 't>],
  pub is_root_compiling_denizen: bool,
}

impl<'s, 't> BuildingFunctionEnvironmentWithClosuredsT<'s, 't> where 's: 't {
  pub fn templata(&'t self) -> FunctionTemplataT<'s, 't> {
    FunctionTemplataT { outer_env: self.parent_env, function: self.function }
  }
  
}
impl<'s, 't> Hash for BuildingFunctionEnvironmentWithClosuredsT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, state: &mut H) { self.id.hash(state); }
  
}

impl<'s, 't> PartialEq for BuildingFunctionEnvironmentWithClosuredsT<'s, 't> where 's: 't {
  fn eq(&self, other: &Self) -> bool { self.id == other.id }
  
}
impl<'s, 't> Eq for BuildingFunctionEnvironmentWithClosuredsT<'s, 't> where 's: 't {}

impl<'s, 't> BuildingFunctionEnvironmentWithClosuredsT<'s, 't> where 's: 't {
  pub fn root_compiling_denizen_env(&'t self) -> IInDenizenEnvironmentT<'s, 't> {
    panic!("Unimplemented: root_compiling_denizen_env");
  }
  
}
impl<'s, 't> BuildingFunctionEnvironmentWithClosuredsT<'s, 't> where 's: 't {
  pub fn lookup_with_name_inner(
    &'t self,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_with_name_inner");
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &'t self,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    lookup_with_imprecise_name_inner(
      IEnvironmentT::BuildingWithClosureds(self), &self.templatas, self.parent_env, name, lookup_filter, get_only_nearest, interner)
  }
  
}

/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IEnvironmentT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub template_args: &'t [ITemplataT<'s, 't>],
  pub templatas: &'t TemplatasStoreT<'s, 't>,
  pub function: &'s FunctionA<'s>,
  pub variables: &'t [IVariableT<'s, 't>],
  pub is_root_compiling_denizen: bool,
  pub default_region: RegionT,
}

impl<'s, 't> Hash for BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, state: &mut H) { self.id.hash(state); }
  
}

impl<'s, 't> PartialEq for BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> where 's: 't {
  fn eq(&self, other: &Self) -> bool { self.id == other.id }
  
}
impl<'s, 't> Eq for BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> where 's: 't {}

impl<'s, 't> BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> where 's: 't {
  pub fn root_compiling_denizen_env(&'t self) -> IInDenizenEnvironmentT<'s, 't> {
    panic!("Unimplemented: root_compiling_denizen_env");
  }
  
}
impl<'s, 't> BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> where 's: 't {
  pub fn lookup_with_name_inner(
    &'t self,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_with_name_inner");
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &'t self,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    // EnvironmentHelper.lookupWithImpreciseNameInner(this, templatas, parentEnv, name, lookupFilter, getOnlyNearest)
    lookup_with_imprecise_name_inner(
      IEnvironmentT::BuildingWithClosuredsAndTemplateArgs(self), &self.templatas, self.parent_env, name, lookup_filter, get_only_nearest, interner)
  }
  
}

/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct NodeEnvironmentT<'s, 't>
where 's: 't,
{
  pub parent_function_env: &'t FunctionEnvironmentT<'s, 't>,
  pub parent_node_env: Option<&'t NodeEnvironmentT<'s, 't>>,
  pub node: &'s IExpressionSE<'s>,
  pub life: LocationInFunctionEnvironmentT<'t>,
  pub templatas: &'t TemplatasStoreT<'s, 't>,
  pub declared_locals: &'t [IVariableT<'s, 't>],
  pub unstackified_locals: &'t [IVarNameT<'s, 't>],
  pub restackified_locals: &'t [IVarNameT<'s, 't>],
  pub default_region: RegionT,
}

// Scala hashes `id.hashCode ^ life.hashCode` and compares `(id, life)`. The id
// delegates to parent_function_env.id.
impl<'s, 't> Hash for NodeEnvironmentT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, state: &mut H) {
    self.parent_function_env.id.hash(state);
    self.life.hash(state);
  }
  
}

impl<'s, 't> PartialEq for NodeEnvironmentT<'s, 't> where 's: 't {
  fn eq(&self, other: &Self) -> bool {
    self.parent_function_env.id == other.parent_function_env.id
      && self.life == other.life
  }
  
}
impl<'s, 't> Eq for NodeEnvironmentT<'s, 't> where 's: 't {}

impl<'s, 't> NodeEnvironmentT<'s, 't> where 's: 't {
  pub fn root_compiling_denizen_env(&'t self) -> IInDenizenEnvironmentT<'s, 't> {
    panic!("Unimplemented: root_compiling_denizen_env");
  }
  
}
impl<'s, 't> NodeEnvironmentT<'s, 't> where 's: 't {
  pub fn id(&self) -> IdT<'s, 't> {
    self.parent_function_env.id
  }
  
  pub fn function(&self) -> &'s FunctionA<'s> {
    panic!("Unimplemented: function");
  }
  
  pub fn lookup_with_name_inner(
    &'t self,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let parent: IEnvironmentT<'s, 't> = match self.parent_node_env {
        Some(p) => IEnvironmentT::Node(p),
        None => IEnvironmentT::Function(self.parent_function_env),
    };
    lookup_with_name_inner(
        IEnvironmentT::Node(self), &self.templatas, parent, name, lookup_filter, get_only_nearest, interner)
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &'t self,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let parent: IEnvironmentT<'s, 't> = match self.parent_node_env {
        Some(p) => IEnvironmentT::Node(p),
        None => IEnvironmentT::Function(self.parent_function_env),
    };
    lookup_with_imprecise_name_inner(
        IEnvironmentT::Node(self), &self.templatas, parent, name, lookup_filter, get_only_nearest, interner)
  }
  
  pub fn global_env(&self) -> &'t GlobalEnvironmentT<'s, 't> {
    self.parent_function_env.global_env
  }
  
  pub fn parent_env(&self) -> IInDenizenEnvironmentT<'s, 't> {
    panic!("Unimplemented: parent_env");
  }
  
  pub fn get_variable(&self, name: IVarNameT<'s, 't>) -> Option<IVariableT<'s, 't>> {
    match self.declared_locals.iter().find(|v| v.name() == name) {
      Some(v) => Some(*v),
      None => {
        match self.parent_node_env {
          Some(p) => p.get_variable(name),
          None => {
            self.parent_function_env.closured_locals.iter().find(|v| v.name() == name).copied()
          }
        }
      }
    }
  }
  
  pub fn get_all_locals(&self) -> Vec<ILocalVariableT<'s, 't>> {
    panic!("Unimplemented: get_all_locals");
  }
  
  pub fn get_all_unstackified_locals(&self) -> Vec<IVarNameT<'s, 't>> {
    self.unstackified_locals.to_vec()
  }
  
  pub fn add_variables(&self, new_vars: &[IVariableT<'s, 't>]) -> &'t NodeEnvironmentT<'s, 't> {
    panic!("Unimplemented: add_variables");
  }
  
  pub fn add_variable(&self, new_var: IVariableT<'s, 't>) -> &'t NodeEnvironmentT<'s, 't> {
    panic!("Unimplemented: add_variable");
  }
  
  pub fn get_all_restackified_locals(&self) -> Vec<IVarNameT<'s, 't>> {
    panic!("Unimplemented: get_all_restackified_locals");
  }
  
  pub fn mark_local_unstackified(&self, new_unstackified: IVarNameT<'s, 't>) -> &'t NodeEnvironmentT<'s, 't> {
    panic!("Unimplemented: mark_local_unstackified");
  }
  
  pub fn mark_local_restackified(&self, new_restackified: IVarNameT<'s, 't>) -> &'t NodeEnvironmentT<'s, 't> {
    panic!("Unimplemented: mark_local_restackified");
  }
  
  pub fn get_effects_since(
    &self,
    earlier_node_env: &NodeEnvironmentT<'s, 't>,
  ) -> (IndexSet<IVarNameT<'s, 't>>, IndexSet<IVarNameT<'s, 't>>) {
    assert!(eq(self.parent_function_env, earlier_node_env.parent_function_env));
    let earlier_node_env_declared_locals: HashSet<IVarNameT<'s, 't>> =
        earlier_node_env.declared_locals.iter().map(|v| v.name()).collect();
    let earlier_node_env_unstackified: HashSet<IVarNameT<'s, 't>> =
        earlier_node_env.unstackified_locals.iter().copied().collect();
    let earlier_node_env_live_locals: HashSet<IVarNameT<'s, 't>> =
        earlier_node_env_declared_locals.difference(&earlier_node_env_unstackified).copied().collect();
    let live_locals_introduced_since_earlier: HashSet<IVarNameT<'s, 't>> =
        self.declared_locals.iter().map(|v| v.name()).filter(|x| !earlier_node_env_live_locals.contains(x)).collect();
    let unstackified_ancestor_locals: IndexSet<IVarNameT<'s, 't>> =
        self.unstackified_locals.iter().copied().filter(|x| !live_locals_introduced_since_earlier.contains(x)).collect();
    let restackified_ancestor_locals: IndexSet<IVarNameT<'s, 't>> =
        self.restackified_locals.iter().copied().filter(|x| !live_locals_introduced_since_earlier.contains(x)).collect();
    (unstackified_ancestor_locals, restackified_ancestor_locals)
  }
  
  pub fn get_live_variables_introduced_since(
    &self,
    since_nenv: &NodeEnvironmentT<'s, 't>,
  ) -> Vec<ILocalVariableT<'s, 't>> {
    let locals_as_of_then: Vec<ILocalVariableT<'s, 't>> =
        since_nenv.declared_locals.iter().filter_map(|v| match v {
            IVariableT::ReferenceLocal(r) => Some(ILocalVariableT::Reference(*r)),
            IVariableT::AddressibleLocal(a) => Some(ILocalVariableT::Addressible(*a)),
            _ => None,
        }).collect();
    let locals_as_of_now: Vec<ILocalVariableT<'s, 't>> =
        self.declared_locals.iter().filter_map(|v| match v {
            IVariableT::ReferenceLocal(r) => Some(ILocalVariableT::Reference(*r)),
            IVariableT::AddressibleLocal(a) => Some(ILocalVariableT::Addressible(*a)),
            _ => None,
        }).collect();

    assert!(locals_as_of_now.starts_with(&locals_as_of_then));
    let locals_declared_since_then = &locals_as_of_now[locals_as_of_then.len()..];
    assert!(locals_declared_since_then.len() == locals_as_of_now.len() - locals_as_of_then.len());

    locals_declared_since_then.iter()
        .filter(|x| !self.unstackified_locals.contains(&x.name()))
        .copied()
        .collect()
  }
  
  pub fn make_child(
    &'t self,
    interner: &TypingInterner<'s, 't>,
    node: &'s IExpressionSE<'s>,
    maybe_new_default_region: Option<RegionT>,
  ) -> &'t NodeEnvironmentT<'s, 't> {
    let empty_templatas = TemplatasStoreBuilder::new(&self.parent_function_env.id).build_in(interner);
    interner.alloc(NodeEnvironmentT {
      parent_function_env: self.parent_function_env,
      parent_node_env: Some(self),
      node,
      life: self.life.clone(),
      templatas: empty_templatas,
      declared_locals: self.declared_locals, // See WTHPFE.
      unstackified_locals: self.unstackified_locals, // See WTHPFE
      restackified_locals: self.restackified_locals,
      default_region: maybe_new_default_region.unwrap_or(self.default_region), // See WTHPFE.
    })
  }
  
  pub fn add_entry(
    &self,
    interner: &TypingInterner<'s, 't>,
    name: INameT<'s, 't>,
    entry: IEnvEntryT<'s, 't>,
  ) -> &'t NodeEnvironmentT<'s, 't> {
    panic!("Unimplemented: add_entry");
  }
  
  pub fn add_entries(
    &self,
    interner: &TypingInterner<'s, 't>,
    scout_arena: &ScoutArena<'s>,
    new_entries: &[(INameT<'s, 't>, IEnvEntryT<'s, 't>)],
  ) -> &'t NodeEnvironmentT<'s, 't> {
    interner.alloc(NodeEnvironmentT {
      parent_function_env: self.parent_function_env,
      parent_node_env: self.parent_node_env,
      node: self.node,
      life: self.life,
      templatas: interner.alloc(self.templatas.add_entries(interner, scout_arena, new_entries.to_vec())),
      declared_locals: self.declared_locals,
      unstackified_locals: self.unstackified_locals,
      restackified_locals: self.restackified_locals,
      default_region: self.default_region,
    })
  }
  
  pub fn nearest_block_env(&'t self) -> Option<(&'t NodeEnvironmentT<'s, 't>, &'s IExpressionSE<'s>)> {
    match self.node {
        IExpressionSE::Block(_) => Some((self, self.node)),
        _ => self.parent_node_env.and_then(|p| p.nearest_block_env()),
    }
  }

  pub fn nearest_loop_env(&'t self) -> Option<(&'t NodeEnvironmentT<'s, 't>, &'s IExpressionSE<'s>)> {
    match self.node {
        IExpressionSE::While(_) => Some((self, self.node)),
        IExpressionSE::Map(_) => Some((self, self.node)),
        _ => self.parent_node_env.and_then(|p| p.nearest_loop_env()),
    }
  }
  
}

/// Temporary state (see @TFITCX)
//
// Mirrors Scala's `NodeEnvironmentBox`. Why a Box instead of `&mut NodeEnvironmentT`?
// Two reasons, both rooted in arena allocation:
//
// 1. `NodeEnvironmentT` is arena-allocated and accessed via `&'t NodeEnvironmentT`.
//    The interner hands out shared borrows; per @TFITCX/@IEOIBZ, arena-allocated
//    identity-bearing types are treated as immutable. There's no `&mut` to obtain.
//
// 2. Its list fields (`declared_locals`, `unstackified_locals`, `restackified_locals`)
//    are arena slices `&'t [...]`, not `Vec`. Slices aren't growable in place — even
//    with `&mut` you couldn't `push`; you'd have to re-arena-allocate the whole slice.
//
// The Box owns `Vec`s instead, mutates via `&mut self` without touching the arena,
// then `build_in`/`snapshot` re-allocates those `Vec`s into arena slices to produce
// the immutable `&'t NodeEnvironmentT`. Scala can sidestep all this with a literal
// `var nodeEnvironment: NodeEnvironmentT` because GC makes every reference
// mutable-by-default; Rust + arena can't, so the Box exists to bridge the gap.
pub struct NodeEnvironmentBox<'s, 't>
where 's: 't,
{
  pub parent_function_env: &'t FunctionEnvironmentT<'s, 't>,
  pub parent_node_env: Option<&'t NodeEnvironmentT<'s, 't>>,
  pub node: &'s IExpressionSE<'s>,
  pub life: LocationInFunctionEnvironmentT<'t>,
  pub templatas_builder: TemplatasStoreBuilder<'s, 't>,
  pub declared_locals: Vec<IVariableT<'s, 't>>,
  pub unstackified_locals: Vec<IVarNameT<'s, 't>>,
  pub restackified_locals: Vec<IVarNameT<'s, 't>>,
  pub default_region: RegionT,
}

// (Realizes Scala's case-class 1-arg apply `NodeEnvironmentBox(nodeEnvironment)`.
//  Rust adaptation (SPDMX-B): Box stores fields out-of-arena (Vec instead of &'t [..])
//  per design v3 §3.3, so wrapping a `&'t NodeEnvironmentT` requires copying slice
//  fields into owned Vecs. The inverse of `snapshot`.)
impl<'s, 't> NodeEnvironmentBox<'s, 't> where 's: 't {
  pub fn new(node_env: &'t NodeEnvironmentT<'s, 't>) -> Self {
    NodeEnvironmentBox {
      parent_function_env: node_env.parent_function_env,
      parent_node_env: node_env.parent_node_env,
      node: node_env.node,
      life: node_env.life.clone(),
      templatas_builder: TemplatasStoreBuilder::from_store(&node_env.templatas),
      declared_locals: node_env.declared_locals.to_vec(),
      unstackified_locals: node_env.unstackified_locals.to_vec(),
      restackified_locals: node_env.restackified_locals.to_vec(),
      default_region: node_env.default_region,
    }
  }

// (No Rust impl — Box deliberately doesn't impl PartialEq, mirroring Scala's vcurious panic-on-call. Misuse fails at compile time, which is strictly stronger than Scala's runtime vfail.)

// (No Rust impl — Box deliberately doesn't impl Hash, mirroring Scala's "shouldn't hash, is mutable" vfail.)

  pub fn snapshot(
    &self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t NodeEnvironmentT<'s, 't> {
    let templatas = self.templatas_builder.snapshot(interner);
    let declared_locals = interner.alloc_slice_from_vec(self.declared_locals.clone());
    let unstackified_locals = interner.alloc_slice_from_vec(self.unstackified_locals.clone());
    let restackified_locals = interner.alloc_slice_from_vec(self.restackified_locals.clone());
    interner.alloc(NodeEnvironmentT {
      parent_function_env: self.parent_function_env,
      parent_node_env: self.parent_node_env,
      node: self.node,
      life: self.life.clone(),
      templatas,
      declared_locals,
      unstackified_locals,
      restackified_locals,
      default_region: self.default_region,
    })
  }

  pub fn default_region(&self) -> RegionT {
    self.default_region
  }

  pub fn id(&self) -> IdT<'s, 't> {
    self.parent_function_env.id
  }

  pub fn node(&self) -> &'s IExpressionSE<'s> {
    panic!("Unimplemented: node");
  }

  pub fn maybe_return_type(&self) -> Option<CoordT<'s, 't>> {
    self.parent_function_env.maybe_return_type
  }

  pub fn global_env(&self) -> &'t GlobalEnvironmentT<'s, 't> {
    panic!("Unimplemented: global_env");
  }

  pub fn declared_locals(&self) -> &[IVariableT<'s, 't>] {
    &self.declared_locals
  }

  pub fn unstackifieds(&self) -> &[IVarNameT<'s, 't>] {
    &self.unstackified_locals
  }

  pub fn function(&self) -> &'s FunctionA<'s> {
    panic!("Unimplemented: function");
  }

  pub fn function_environment(&self) -> &'t FunctionEnvironmentT<'s, 't> {
    self.parent_function_env
  }

  pub fn add_variable(&mut self, new_var: IVariableT<'s, 't>) {
    self.declared_locals.push(new_var);
  }

  pub fn mark_local_unstackified(&mut self, new_unstackified: IVarNameT<'s, 't>) {
    // Verbatim port of NodeEnvironmentT.markLocalUnstackified (FunctionEnvironmentT.scala:269-300):
    assert!(self.get_all_locals().iter().any(|l| l.name() == new_unstackified));
    assert!(!self.unstackified_locals.contains(&new_unstackified));

    if self.restackified_locals.contains(&new_unstackified) {
      // It was a restackified local, so don't mark it as unstackified, just undo the
      // restackification.
      // Even if the local belongs to a parent env, we still mark it unstackified here, see UCRTVPE.
      self.restackified_locals.retain(|x| *x != new_unstackified);
    } else {
      // Even if the local belongs to a parent env, we still mark it unstackified here, see UCRTVPE.
      self.unstackified_locals.push(new_unstackified);
    }
  }

  pub fn mark_local_restackified(&mut self, new_restackified: IVarNameT<'s, 't>) {
    // Verbatim port of NodeEnvironmentT.markLocalRestackified (FunctionEnvironmentT.scala:303-329):
    assert!(self.get_all_locals().iter().any(|l| l.name() == new_restackified));
    assert!(!self.restackified_locals.contains(&new_restackified));
    if self.unstackified_locals.contains(&new_restackified) {
      // It was an unstackified local, so don't mark it as restackified, just undo the
      // unstackification.
      // Even if the local belongs to a parent env, we still mark it restackified here, see UCRTVPE.
      self.unstackified_locals.retain(|x| *x != new_restackified);
    } else {
      // Even if the local belongs to a parent env, we still mark it restackified here, see UCRTVPE.
      self.restackified_locals.push(new_restackified);
    }
  }

  // AFTERM: remove the needless snapshot — transcribe the inner's `def getVariable`
  // body directly off the Box's fields (declared_locals / parent_node_env /
  // parent_function_env.closured_locals), drop the interner parameter, and update
  // call sites. See `get_all_locals` / `get_all_unstackified_locals` below for
  // the precedent pattern in this file.
  pub fn get_variable(&self, name: IVarNameT<'s, 't>, interner: &TypingInterner<'s, 't>) -> Option<IVariableT<'s, 't>> {
    self.snapshot(interner).get_variable(name)
  }

  pub fn get_all_locals(&self) -> Vec<ILocalVariableT<'s, 't>> {
    self.declared_locals.iter().filter_map(|v| match v {
      IVariableT::AddressibleLocal(a) => Some(ILocalVariableT::Addressible(*a)),
      IVariableT::ReferenceLocal(r) => Some(ILocalVariableT::Reference(*r)),
      IVariableT::AddressibleClosure(_) | IVariableT::ReferenceClosure(_) => None,
    }).collect()
  }

  pub fn get_all_unstackified_locals(&self) -> Vec<IVarNameT<'s, 't>> {
    self.unstackified_locals.clone()
  }

  pub fn lookup_nearest_with_imprecise_name(
    &self,
    name_s: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Option<ITemplataT<'s, 't>> {
    let node_env = self.snapshot(interner);
    IEnvironmentT::Node(node_env).lookup_nearest_with_imprecise_name(name_s, lookup_filter.clone(), interner)
  }

  pub fn lookup_nearest_with_name(
    &self,
    _name_s: INameT<'s, 't>,
    _lookup_filter: &HashSet<ILookupContext>,
  ) -> Option<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_nearest_with_name");
  }

  pub fn lookup_all_with_imprecise_name(
    &self,
    name_s: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    let node_env = self.snapshot(interner);
    IEnvironmentT::Node(node_env).lookup_all_with_imprecise_name(name_s, lookup_filter.clone(), interner)
  }

  pub fn lookup_all_with_name(
    &self,
    _name_s: INameT<'s, 't>,
    _lookup_filter: &HashSet<ILookupContext>,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_all_with_name");
  }

  pub fn lookup_with_imprecise_name_inner(
    &self,
    _name_s: IImpreciseNameS<'s>,
    _lookup_filter: &HashSet<ILookupContext>,
    _get_only_nearest: bool,
    _interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_with_imprecise_name_inner");
  }

  pub fn lookup_with_name_inner(
    &self,
    _name_s: INameT<'s, 't>,
    _lookup_filter: &HashSet<ILookupContext>,
    _get_only_nearest: bool,
  ) -> Vec<ITemplataT<'s, 't>> {
    panic!("Unimplemented: lookup_with_name_inner");
  }

  pub fn make_child(
    &self,
    interner: &TypingInterner<'s, 't>,
    node: &'s IExpressionSE<'s>,
    maybe_new_default_region: Option<RegionT>,
  ) -> &'t NodeEnvironmentT<'s, 't> {
    self.snapshot(interner).make_child(interner, node, maybe_new_default_region)
  }

  pub fn add_entry(
    &mut self,
    _interner: &TypingInterner<'s, 't>,
    _name: INameT<'s, 't>,
    _entry: IEnvEntryT<'s, 't>,
  ) {
    panic!("Unimplemented: add_entry");
  }

  pub fn add_entries(
    &mut self,
    scout_arena: &ScoutArena<'s>,
    _interner: &TypingInterner<'s, 't>,
    new_entries: &[(INameT<'s, 't>, IEnvEntryT<'s, 't>)],
  ) {
    self.templatas_builder.add_entries(scout_arena, new_entries.to_vec());
  }

  pub fn nearest_block_env(
    &self,
    interner: &TypingInterner<'s, 't>,
  ) -> Option<(&'t NodeEnvironmentT<'s, 't>, &'s IExpressionSE<'s>)> {
    let snap = self.snapshot(interner);
    snap.nearest_block_env()
  }

  pub fn nearest_loop_env(
    &self,
    interner: &TypingInterner<'s, 't>,
  ) -> Option<(&'t NodeEnvironmentT<'s, 't>, &'s IExpressionSE<'s>)> {
    let snap = self.snapshot(interner);
    snap.nearest_loop_env()
  }

}
/// Arena-allocated (see @TFITCX)
#[derive(Debug)]
pub struct FunctionEnvironmentT<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas: &'t TemplatasStoreT<'s, 't>,
  pub function: &'s FunctionA<'s>,
  pub maybe_return_type: Option<CoordT<'s, 't>>,
  pub closured_locals: &'t [IVariableT<'s, 't>],
  pub is_root_compiling_denizen: bool,
  pub default_region: RegionT,
}

impl<'s, 't> Hash for FunctionEnvironmentT<'s, 't> where 's: 't {
  fn hash<H: Hasher>(&self, state: &mut H) { self.id.hash(state); }
  
}

impl<'s, 't> PartialEq for FunctionEnvironmentT<'s, 't> where 's: 't {
  fn eq(&self, other: &Self) -> bool { self.id == other.id }
  
}
impl<'s, 't> Eq for FunctionEnvironmentT<'s, 't> where 's: 't {}

impl<'s, 't> FunctionEnvironmentT<'s, 't> where 's: 't {
  pub fn root_compiling_denizen_env(&'t self) -> IInDenizenEnvironmentT<'s, 't> {
    if self.is_root_compiling_denizen {
        IInDenizenEnvironmentT::Function(self)
    } else {
        match self.parent_env {
            IEnvironmentT::Package(_) => panic!("vwat: root_compiling_denizen_env parent is Package"),
            _ => {
                match IInDenizenEnvironmentT::try_from(self.parent_env) {
                    Ok(parent_in_denizen_env) => parent_in_denizen_env.root_compiling_denizen_env(),
                    Err(_) => panic!("vwat: root_compiling_denizen_env parent is not IInDenizenEnvironmentT"),
                }
            }
        }
    }
  }
  
}
impl<'s, 't> FunctionEnvironmentT<'s, 't> where 's: 't {
  pub fn templata(&'t self) -> FunctionTemplataT<'s, 't> {
    FunctionTemplataT { outer_env: self.parent_env, function: self.function }
  }
  
  pub fn add_entry(
    &self,
    interner: &TypingInterner<'s, 't>,
    name: INameT<'s, 't>,
    entry: IEnvEntryT<'s, 't>,
  ) -> &'t FunctionEnvironmentT<'s, 't> {
    panic!("Unimplemented: add_entry");
  }
  
  pub fn add_entries(
    &self,
    interner: &TypingInterner<'s, 't>,
    new_entries: &[(INameT<'s, 't>, IEnvEntryT<'s, 't>)],
  ) -> &'t FunctionEnvironmentT<'s, 't> {
    panic!("Unimplemented: add_entries");
  }
  
  pub fn lookup_with_name_inner(
    &'t self,
    name: INameT<'s, 't>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    lookup_with_name_inner(
      IEnvironmentT::Function(self), self.templatas, self.parent_env, name, lookup_filter, get_only_nearest, interner)
  }
  
  pub fn lookup_with_imprecise_name_inner(
    &'t self,
    name: IImpreciseNameS<'s>,
    lookup_filter: &HashSet<ILookupContext>,
    get_only_nearest: bool,
    interner: &TypingInterner<'s, 't>,
  ) -> Vec<ITemplataT<'s, 't>> {
    lookup_with_imprecise_name_inner(
      IEnvironmentT::Function(self), self.templatas, self.parent_env, name, lookup_filter, get_only_nearest, interner)
  }
  
  pub fn make_child_node_environment(
    &'t self,
    node: &'s IExpressionSE<'s>,
    life: LocationInFunctionEnvironmentT<'t>,
  ) -> NodeEnvironmentBox<'s, 't> {
    // See WTHPFE, if this is a lambda, we let our blocks start with
    // locals from the parent function.
    let (declared_locals, unstackified_locals, restackified_locals) =
      match &self.parent_env {
        IEnvironmentT::Node(_node_env) => {
          panic!("implement: make_child_node_environment — NodeEnvironmentT parent");
        }
        _ => (Vec::new(), Vec::new(), Vec::new()),
      };
    NodeEnvironmentBox {
      parent_function_env: self,
      parent_node_env: None,
      node,
      life,
      templatas_builder: TemplatasStoreBuilder::new(&self.id),
      declared_locals,
      unstackified_locals,
      restackified_locals,
      default_region: self.default_region,
    }
  }
  
  pub fn get_closured_declared_locals(&self) -> Vec<IVariableT<'s, 't>> {
    panic!("Unimplemented: get_closured_declared_locals");
  }
  
}

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT / IDenizenEnvironmentBoxT
//  Scala mutable wrappers are subsumed by the builder-freeze pattern in Rust.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

// (Deleted in Rust per typing-pass-design-v3.md §3.3 — FunctionEnvironmentBoxT subsumed by builder-freeze pattern.)

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum IVariableT<'s, 't>
where 's: 't,
{
  AddressibleLocal(AddressibleLocalVariableT<'s, 't>),
  ReferenceLocal(ReferenceLocalVariableT<'s, 't>),
  AddressibleClosure(AddressibleClosureVariableT<'s, 't>),
  ReferenceClosure(ReferenceClosureVariableT<'s, 't>),
}

impl<'s, 't> IVariableT<'s, 't> where 's: 't {
  pub fn name(&self) -> IVarNameT<'s, 't> {
    match self {
      IVariableT::AddressibleLocal(v) => v.name,
      IVariableT::ReferenceLocal(v) => v.name,
      IVariableT::AddressibleClosure(v) => v.name,
      IVariableT::ReferenceClosure(v) => v.name,
    }
  }
  
  pub fn variability(&self) -> VariabilityT {
    panic!("Unimplemented: variability");
  }
  
  pub fn coord(&self) -> CoordT<'s, 't> {
    panic!("Unimplemented: coord");
  }
  
}
/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub enum ILocalVariableT<'s, 't>
where 's: 't,
{
  Addressible(AddressibleLocalVariableT<'s, 't>),
  Reference(ReferenceLocalVariableT<'s, 't>),
}

impl<'s, 't> ILocalVariableT<'s, 't> where 's: 't {
  pub fn name(&self) -> IVarNameT<'s, 't> {
    match self {
      ILocalVariableT::Addressible(a) => a.name,
      ILocalVariableT::Reference(r) => r.name,
    }
  }
  
  pub fn coord(&self) -> CoordT<'s, 't> {
    match self {
      ILocalVariableT::Addressible(a) => a.coord,
      ILocalVariableT::Reference(r) => r.coord,
    }
  }
  
  pub fn variability(&self) -> VariabilityT {
    match self {
      ILocalVariableT::Addressible(a) => a.variability,
      ILocalVariableT::Reference(r) => r.variability,
    }
  }
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AddressibleLocalVariableT<'s, 't>
where 's: 't,
{
  pub name: IVarNameT<'s, 't>,
  pub variability: VariabilityT,
  pub coord: CoordT<'s, 't>,
}

// (Realized by `#[derive(Hash)]` on AddressibleLocalVariableT above.)

// (Realized by `#[derive(PartialEq, Eq)]` on AddressibleLocalVariableT above.)

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ReferenceLocalVariableT<'s, 't>
where 's: 't,
{
  pub name: IVarNameT<'s, 't>,
  pub variability: VariabilityT,
  pub coord: CoordT<'s, 't>,
}

// (Realized by `#[derive(Hash)]` on ReferenceLocalVariableT above.)

// (Realized by `#[derive(PartialEq, Eq)]` on ReferenceLocalVariableT above.)

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct AddressibleClosureVariableT<'s, 't>
where 's: 't,
{
  pub name: IVarNameT<'s, 't>,
  pub closured_vars_struct_type: &'t StructTT<'s, 't>,
  pub variability: VariabilityT,
  pub coord: CoordT<'s, 't>,
}

/// Value-type (see @TFITCX)
#[derive(Copy, Clone, PartialEq, Eq, Hash, Debug)]
pub struct ReferenceClosureVariableT<'s, 't>
where 's: 't,
{
  pub name: IVarNameT<'s, 't>,
  pub closured_vars_struct_type: &'t StructTT<'s, 't>,
  pub variability: VariabilityT,
  pub coord: CoordT<'s, 't>,
}

// (Realized by `#[derive(Hash)]` on ReferenceClosureVariableT above.)

// (Realized by `#[derive(PartialEq, Eq)]` on ReferenceClosureVariableT above.)

impl<'s, 't> From<AddressibleLocalVariableT<'s, 't>> for ILocalVariableT<'s, 't> {
  fn from(v: AddressibleLocalVariableT<'s, 't>) -> Self { ILocalVariableT::Addressible(v) }
  
}
impl<'s, 't> From<ReferenceLocalVariableT<'s, 't>> for ILocalVariableT<'s, 't> {
  fn from(v: ReferenceLocalVariableT<'s, 't>) -> Self { ILocalVariableT::Reference(v) }
  
}

impl<'s, 't> From<AddressibleLocalVariableT<'s, 't>> for IVariableT<'s, 't> {
  fn from(v: AddressibleLocalVariableT<'s, 't>) -> Self { IVariableT::AddressibleLocal(v) }
  
}
impl<'s, 't> From<ReferenceLocalVariableT<'s, 't>> for IVariableT<'s, 't> {
  fn from(v: ReferenceLocalVariableT<'s, 't>) -> Self { IVariableT::ReferenceLocal(v) }
  
}
impl<'s, 't> From<AddressibleClosureVariableT<'s, 't>> for IVariableT<'s, 't> {
  fn from(v: AddressibleClosureVariableT<'s, 't>) -> Self { IVariableT::AddressibleClosure(v) }
  
}
impl<'s, 't> From<ReferenceClosureVariableT<'s, 't>> for IVariableT<'s, 't> {
  fn from(v: ReferenceClosureVariableT<'s, 't>) -> Self { IVariableT::ReferenceClosure(v) }
  
}

impl<'s, 't> From<ILocalVariableT<'s, 't>> for IVariableT<'s, 't> {
  fn from(v: ILocalVariableT<'s, 't>) -> Self {
    match v {
      ILocalVariableT::Addressible(a) => IVariableT::AddressibleLocal(a),
      ILocalVariableT::Reference(r) => IVariableT::ReferenceLocal(r),
    }
  }
  
}

impl<'s, 't> TryFrom<IVariableT<'s, 't>> for ILocalVariableT<'s, 't> {
  type Error = IVariableT<'s, 't>;
  fn try_from(v: IVariableT<'s, 't>) -> Result<Self, Self::Error> {
    match v {
      IVariableT::AddressibleLocal(a) => Ok(ILocalVariableT::Addressible(a)),
      IVariableT::ReferenceLocal(r) => Ok(ILocalVariableT::Reference(r)),
      other => Err(other),
    }
  }
  
}

impl<'s, 't> TryFrom<IVariableT<'s, 't>> for AddressibleLocalVariableT<'s, 't> {
  type Error = IVariableT<'s, 't>;
  fn try_from(v: IVariableT<'s, 't>) -> Result<Self, Self::Error> {
    match v { IVariableT::AddressibleLocal(a) => Ok(a), other => Err(other) }
  }
  
}
impl<'s, 't> TryFrom<IVariableT<'s, 't>> for ReferenceLocalVariableT<'s, 't> {
  type Error = IVariableT<'s, 't>;
  fn try_from(v: IVariableT<'s, 't>) -> Result<Self, Self::Error> {
    match v { IVariableT::ReferenceLocal(r) => Ok(r), other => Err(other) }
  }
  
}
impl<'s, 't> TryFrom<IVariableT<'s, 't>> for AddressibleClosureVariableT<'s, 't> {
  type Error = IVariableT<'s, 't>;
  fn try_from(v: IVariableT<'s, 't>) -> Result<Self, Self::Error> {
    match v { IVariableT::AddressibleClosure(a) => Ok(a), other => Err(other) }
  }
  
}
impl<'s, 't> TryFrom<IVariableT<'s, 't>> for ReferenceClosureVariableT<'s, 't> {
  type Error = IVariableT<'s, 't>;
  fn try_from(v: IVariableT<'s, 't>) -> Result<Self, Self::Error> {
    match v { IVariableT::ReferenceClosure(r) => Ok(r), other => Err(other) }
  }
  
}

impl<'s, 't> TryFrom<ILocalVariableT<'s, 't>> for AddressibleLocalVariableT<'s, 't> {
  type Error = ILocalVariableT<'s, 't>;
  fn try_from(v: ILocalVariableT<'s, 't>) -> Result<Self, Self::Error> {
    match v { ILocalVariableT::Addressible(a) => Ok(a), other => Err(other) }
  }
  
}
impl<'s, 't> TryFrom<ILocalVariableT<'s, 't>> for ReferenceLocalVariableT<'s, 't> {
  type Error = ILocalVariableT<'s, 't>;
  fn try_from(v: ILocalVariableT<'s, 't>) -> Result<Self, Self::Error> {
    match v { ILocalVariableT::Reference(r) => Ok(r), other => Err(other) }
  }
  
}

pub fn lookup_with_name_inner<'s, 't>(
  requesting_env: IEnvironmentT<'s, 't>,
  templatas: &TemplatasStoreT<'s, 't>,
  parent: IEnvironmentT<'s, 't>,
  name: INameT<'s, 't>,
  lookup_filter: &HashSet<ILookupContext>,
  get_only_nearest: bool,
  interner: &TypingInterner<'s, 't>,
) -> Vec<ITemplataT<'s, 't>>
where 's: 't,
{
  let result: Vec<ITemplataT<'s, 't>> = templatas.lookup_with_name_inner(requesting_env, name, lookup_filter, interner).into_iter().collect();
  if !result.is_empty() && get_only_nearest {
    result
  } else {
    let mut combined = result;
    combined.extend(parent.lookup_with_name_inner(name, lookup_filter.clone(), get_only_nearest, interner));
    combined
  }
}

pub fn lookup_with_imprecise_name_inner<'s, 't>(
  requesting_env: IEnvironmentT<'s, 't>,
  templatas: &TemplatasStoreT<'s, 't>,
  parent: IEnvironmentT<'s, 't>,
  name: IImpreciseNameS<'s>,
  lookup_filter: &HashSet<ILookupContext>,
  get_only_nearest: bool,
  interner: &TypingInterner<'s, 't>,
) -> Vec<ITemplataT<'s, 't>>
where 's: 't,
{
  let result = templatas.lookup_with_imprecise_name_inner(requesting_env, name, lookup_filter, interner);
  if !result.is_empty() && get_only_nearest {
    result
  } else {
    let mut combined = result;
    combined.extend(parent.lookup_with_imprecise_name_inner(name, lookup_filter.clone(), get_only_nearest, interner));
    combined
  }
}

// Builders — see environment.rs for the Package/Citizen/Export/Extern/General
// builders; these 4 finish out the set for the function-env family.

/// Temporary state (see @TFITCX)
pub struct BuildingFunctionEnvironmentWithClosuredsBuilder<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IEnvironmentT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas_builder: TemplatasStoreBuilder<'s, 't>,
  pub function: &'s FunctionA<'s>,
  pub variables: Vec<IVariableT<'s, 't>>,
  pub is_root_compiling_denizen: bool,
}

impl<'s, 't> BuildingFunctionEnvironmentWithClosuredsBuilder<'s, 't>
where 's: 't,
{
  pub fn build_in(
    self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t BuildingFunctionEnvironmentWithClosuredsT<'s, 't> {
    let templatas = self.templatas_builder.build_in(interner);
    let variables = interner.alloc_slice_from_vec(self.variables);
    interner.alloc(BuildingFunctionEnvironmentWithClosuredsT {
      global_env: self.global_env,
      parent_env: self.parent_env,
      id: self.id,
      templatas,
      function: self.function,
      variables,
      is_root_compiling_denizen: self.is_root_compiling_denizen,
    })
  }
  
}

/// Temporary state (see @TFITCX)
pub struct BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsBuilder<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IEnvironmentT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub template_args: Vec<ITemplataT<'s, 't>>,
  pub templatas_builder: TemplatasStoreBuilder<'s, 't>,
  pub function: &'s FunctionA<'s>,
  pub variables: Vec<IVariableT<'s, 't>>,
  pub is_root_compiling_denizen: bool,
  pub default_region: RegionT,
}

impl<'s, 't> BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsBuilder<'s, 't>
where 's: 't,
{
  pub fn build_in(
    self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT<'s, 't> {
    let templatas = self.templatas_builder.build_in(interner);
    let template_args = interner.alloc_slice_from_vec(self.template_args);
    let variables = interner.alloc_slice_from_vec(self.variables);
    interner.alloc(BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT {
      global_env: self.global_env,
      parent_env: self.parent_env,
      id: self.id,
      template_args,
      templatas,
      function: self.function,
      variables,
      is_root_compiling_denizen: self.is_root_compiling_denizen,
      default_region: self.default_region,
    })
  }
  
}

/// Temporary state (see @TFITCX)
pub struct FunctionEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub global_env: &'t GlobalEnvironmentT<'s, 't>,
  pub parent_env: IEnvironmentT<'s, 't>,
  pub template_id: IdT<'s, 't>,
  pub id: IdT<'s, 't>,
  pub templatas_builder: TemplatasStoreBuilder<'s, 't>,
  pub function: &'s FunctionA<'s>,
  pub maybe_return_type: Option<CoordT<'s, 't>>,
  pub closured_locals: Vec<IVariableT<'s, 't>>,
  pub is_root_compiling_denizen: bool,
  pub default_region: RegionT,
}

impl<'s, 't> FunctionEnvironmentBuilder<'s, 't>
where 's: 't,
{
  pub fn snapshot(
    &self,
    interner: &TypingInterner<'s, 't>,
  ) -> &'t FunctionEnvironmentT<'s, 't> {
    let templatas = self.templatas_builder.snapshot(interner);
    let closured_locals = interner.alloc_slice_from_vec(self.closured_locals.clone());
    interner.alloc(FunctionEnvironmentT {
      global_env: self.global_env,
      parent_env: self.parent_env,
      template_id: self.template_id,
      id: self.id,
      templatas,
      function: self.function,
      maybe_return_type: self.maybe_return_type,
      closured_locals,
      is_root_compiling_denizen: self.is_root_compiling_denizen,
      default_region: self.default_region,
    })
  }
  
}

// (NodeEnvironmentBox struct + impls were moved up adjacent to the Scala `case class
//  NodeEnvironmentBox` block — see ~line 822 above. The previous "deleted in Rust per
//  design v3 §3.3" stance was reversed when we re-recognized the Box semantics.)