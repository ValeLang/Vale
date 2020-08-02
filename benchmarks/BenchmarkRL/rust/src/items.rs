#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(unused_imports)]

use rustc_hash::FxHashMap;
use rustc_hash::FxHashSet;
// use std::collections::BinaryHeap;

extern crate generational_arena;
use generational_arena::Arena;

use crate::astar;

use std::num::Wrapping;
use std::fmt;
use std::iter::FromIterator;

use crate::unit;
use crate::model;

#[derive(new)]
pub struct IncendiumShortSword { }
impl unit::IUnitComponent for IncendiumShortSword {
  fn modifyOutgoingAttack(
    &self,
    _game: &model::Game,
    _selfUnit: generational_arena::Index)
  -> unit::AttackModifier {
    return unit::AttackModifier::new(700, 100, 200);
  }
}


#[derive(new)]
pub struct GoblinClaws { }
impl unit::IUnitComponent for GoblinClaws {
  fn modifyOutgoingAttack(
    &self,
    _game: &model::Game,
    _selfUnit: generational_arena::Index)
  -> unit::AttackModifier {
    return unit::AttackModifier::new(150, 100, 0);
  }
}
