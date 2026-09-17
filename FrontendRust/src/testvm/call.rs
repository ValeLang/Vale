use std::cell::Cell;
use std::collections::HashMap;
use std::marker::PhantomData;
use crate::final_ast::types::{KindHT, CoordH};
use crate::testvm::values::{
    AllocationIdV, CallIdV, ExpressionIdV, IObjectReferrerV,
    ReferenceV, RegisterV, VariableAddressV, VariableV,
};

/// Temporary state
pub struct CallV<'v, 'h, 's> {
  pub call_id: CallIdV<'v, 'h, 's>,
  pub in_args: &'v [ReferenceV<'v, 'h, 's>],
  pub args: HashMap<i32, Option<ReferenceV<'v, 'h, 's>>>,
  pub locals: HashMap<VariableAddressV<'v, 'h, 's>, VariableV<'v, 'h, 's>>,
}

impl<'v, 'h, 's> CallV<'v, 'h, 's> {
  pub fn add_local(&mut self, var_addr: VariableAddressV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>, tyype: CoordH<'s, 'h>) {
    assert_eq!(var_addr.call_id, self.call_id);
    let locals = &mut self.locals;
    assert!(!locals.contains_key(&var_addr));
    assert!(!locals.iter().any(|(addr, _)| addr.local.id.number == var_addr.local.id.number));
    locals.insert(var_addr, VariableV {
      id: var_addr,
      reference,
      expected_type: tyype,
    });
  }

  pub fn remove_local(&mut self, var_addr: VariableAddressV<'v, 'h, 's>) {
    assert_eq!(var_addr.call_id, self.call_id);
    let locals = &mut self.locals;
    assert!(locals.contains_key(&var_addr));
    locals.remove(&var_addr);
  }

  pub fn get_local(&self, addr: VariableAddressV<'v, 'h, 's>) -> VariableV<'v, 'h, 's> {
    let locals = &self.locals;
    let result = locals.get(&addr).expect("get_local: not found").clone();
    result
  }

  pub fn mutate_local(&mut self, var_addr: VariableAddressV<'v, 'h, 's>, reference: ReferenceV<'v, 'h, 's>, _expected_type: CoordH<'s, 'h>) {
    self.locals.get_mut(&var_addr).expect("mutate_local: not found").reference = reference;
  }

  pub fn take_argument(&mut self, index: i32) -> ReferenceV<'v, 'h, 's> {
    assert!((index as usize) < self.args.len());
    match self.args.get(&index).copied() {
      Some(Some(r#ref)) => {
        self.args.insert(index, None);
        r#ref
      }
      Some(None) => panic!("Already took from argument {}", index),
      None => panic!("take_argument: missing argument key {} (assert should have caught this)", index),
    }
  }

  pub fn prepare_to_die(&mut self) {
    let locals = &self.locals;
    assert!(locals.is_empty());
    let args = &self.args;
    let undead_args: Vec<_> = args.iter().filter_map(|(i, v)| v.map(|val| (*i, val))).collect();
    if !undead_args.is_empty() {
        panic!("Undead arguments:\n{:?}", undead_args);
    }
  }
}

