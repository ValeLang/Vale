#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(unused_variables)]

use downcast_rs::Downcast;
use generational_arena::Arena;

use crate::model;

pub mod desire {
  pub const MAX: i32 = 1000;
  pub const MIN: i32 = 0;

  pub const EXISTENTIAL_NEED: i32 = 1000;
  pub const REALLY_NEED: i32 = 900;
  pub const NEED: i32 = 800;
  pub const ALMOST_NEED: i32 = 700;
  pub const REALLY_WANT: i32 = 600;
  pub const WANT: i32 = 500;
  pub const KINDA_WANT: i32 = 400;
  pub const INTERESTED: i32 = 300;
  pub const MILDLY_INTERESTED: i32 = 200;
  pub const MEH: i32 = 100;
  pub const NONE: i32 = 0;
}

pub const DEFAULT_SIGHT_RANGE: i32 = 800;




pub trait IUnitComponent : Downcast {
  fn modifyOutgoingAttack(
    &self,
    _game: &model::Game,
    _selfUnit: generational_arena::Index)
  -> AttackModifier {
    return AttackModifier::default();
  }

  fn modifyIncomingAttack(
    &self,
    _game: &model::Game,
    _selfUnit: generational_arena::Index)
  -> AttackModifier {
    return AttackModifier::default();
  }

  fn onUnitAttacked(
    &self,
    _game: &model::Game,
    _selfUnit: generational_arena::Index,
    _attacker: generational_arena::Index)
  -> Box<dyn Fn(&mut model::Game)> {
    return Box::new(|game| {});
  }

  fn onUnitDeath(
    &self,
    _game: &model::Game,
    _selfUnit: generational_arena::Index,
    _attacker: generational_arena::Index)
  -> Box<dyn Fn(&mut model::Game)> {
    return Box::new(|game| {});
  }

  // Named uuse because `use` is a keyword
  fn uuse(
    &self,
    _game: &model::Game,
    _selfUnit: generational_arena::Index)
  -> Box<dyn Fn(&mut model::Game)> {
    return Box::new(|game| {});
  }

  fn asCapability(&self) -> Option<&dyn IUnitCapability> {
    return None;
  }
  fn asCapabilityMut(&mut self) -> Option<&mut dyn IUnitCapability> {
    return None;
  }
}
impl_downcast!(IUnitComponent);


#[derive(new)]
pub struct MoveUnitDesire {
  strength: i32,
  destination: model::Location,
}
impl IUnitDesire for MoveUnitDesire {
  fn getStrength(&self) -> i32 { return self.strength; }
  fn enact(
      self: Box<Self>,
      selfUnit: generational_arena::Index,
      game: &mut model::Game) {
    // println!("Moving unit from {} to {}",
    //   game.units.get_mut(selfUnit).expect("wat").loc,
    //   self.destination);
    let oldLoc = game.units.get(selfUnit).expect("wat").loc;
    let levelIndex = game.getPlayer().levelIndex;
    let level = &mut game.levels[levelIndex];
    level.moveUnit(
        game.units.get_mut(selfUnit).expect("wat"), self.destination);
  }
}


pub trait IUnitDesire : Downcast {
  fn getStrength(&self) -> i32;
  fn enact(self: Box<Self>, selfUnit: generational_arena::Index, game: &mut model::Game);
}
impl_downcast!(IUnitDesire);

#[derive(new)]
pub struct DoNothingUnitDesire { }

impl IUnitDesire for DoNothingUnitDesire {
  fn getStrength(&self) -> i32 { return 0; }
  fn enact(
    self: Box<Self>,
    _selfUnit: generational_arena::Index,
    _game: &mut model::Game) { }
}


pub trait IUnitCapability {
  fn getDesire(
    &self,
    rand: &mut model::LCGRand,
    selfUnit: &Unit,
    game: &model::Game)
  -> Box<dyn IUnitDesire>;

  fn preAct(
    &mut self,
    desire: &dyn IUnitDesire) {}
}


  // fn enact(self: Box<Self>, game: &mut model::Game);


#[derive(new)]
pub struct AttackModifier {
  // This is how much we contribute to the initial attack amount.
  // Weapons will usually contribute to this; a claw might be 1.2,
  // a shortsword would be 1.3, broadsword would be 1.4.
  // 100 means over 100, so we can have attack values like 1.25,
  // which would be represented here as 125.
  pub initialDamage100: i32,

  // 100 means over 100. 150 would mean 1.5x damage, 70 would mean 70% damage.
  // Certain abilities might do this, like if we double damage for 10 turns it
  // would show up here.
  // Try to keep this low, we dont want things as high as 2x, prefer things
  // like 110 to represent +10%.
  pub damageMultiply100: i32,

  // Extra final additions go here. For example we might have a ring
  // of +3 damage, that would show up here as 300.
  pub damageAdd100: i32,
}
impl AttackModifier {
  pub fn default() -> AttackModifier {
    return AttackModifier::new(0, 100, 0);
  }
}




// we'll have the AI happen with a readonly region, after all, thats how someone's brain
// works. they look and they think and they guess.

// the actual effects should happen with their own allocator, but not a readonly region.
// things happen, things go boom, lots of mutability in the


// pub struct ExplodesOnDeath {
//   radius: i32,
// }
// impl IUnitComponent for ExplodesOnDeath {
//   fn OnEvent(&mut self, &mut Level, event: UnitEvent)
// }

// pub struct BlastRod {
//   radius: i32,
// }
// impl IUnitComponent for BlastRod {
//   fn OnEvent(&mut self, &mut Level, event: UnitEvent)
// }

#[derive(PartialEq)]
pub enum Allegiance {
  Good,
  Evil,
}

#[derive(new)]
pub struct Unit {
  pub index: Option<generational_arena::Index>,
  pub hp: i32,
  pub max_hp: i32,
  pub levelIndex: usize,
  pub loc: model::Location,
  pub allegiance: Allegiance,
  pub display_class: String,
  pub components: Arena<Box<dyn IUnitComponent>>,
}
impl Unit {
  pub fn getIndex(&self) -> generational_arena::Index {
    return self.index.expect("wat");
  }

  pub fn getFirstComponent<T: IUnitComponent>(&self) -> Option<&T> {
    for (_, c_boxed) in &self.components {
      // let c: &dyn IUnitComponent = c_boxed;
      if let Some(w) = c_boxed.downcast_ref::<T>() {
        return Some(w);
      }
    }
    return None;
  }

  pub fn getFirstComponentMut<T: IUnitComponent>(&mut self) -> Option<&mut T> {
    for (_, c_boxed) in &mut self.components {
      if let Some(w) = c_boxed.downcast_mut::<T>() {
        return Some(w);
      }
    }
    return None;
  }

  pub fn act(
      selfIndex: generational_arena::Index,
      rand: &mut model::LCGRand,
      mut game: &mut model::Game) {
    let selfUnit = game.units.get(selfIndex).expect("wat");
    let mut greatestDesire: Box<dyn IUnitDesire> =
        Box::new(DoNothingUnitDesire::new());
    for (_, component) in &selfUnit.components {
      if let Some(capability) = component.asCapability() {
        let desire = capability.getDesire(rand, selfUnit, &game);
        if desire.getStrength() > greatestDesire.getStrength() {
          greatestDesire = desire;
        }
      }
    }

    let selfUnitMut = game.units.get_mut(selfIndex).expect("wat");
    for (_, component) in &mut selfUnitMut.components {
      if let Some(capability) = component.asCapabilityMut() {
        capability.preAct(&*greatestDesire);
      }
    }

    greatestDesire.enact(selfIndex, &mut game);
  }

  pub fn getNearestEnemyInSight<'a>(
      &self,
      game: &'a model::Game,
      range: i32)
  -> Option<&'a Unit> {
    let locationsWithinSight =
        game.getCurrentLevel().getLocationsWithinSight(
          self.loc, false, range);
        
    let mut maybeNearestEnemy: Option<&Unit> = None;
    for locationWithinSight in locationsWithinSight {
      match game.getCurrentLevel().unit_by_location.get(&locationWithinSight) {
        None => {}
        Some(&otherUnitIndex) => {
          assert!(otherUnitIndex != self.getIndex());
          let otherUnit = game.units.get(otherUnitIndex).expect("wat");
          if self.allegiance != otherUnit.allegiance {
            let isNearestEnemy =
              match maybeNearestEnemy {
                None => { true }
                Some(nearestEnemy) => {
                  self.loc.distSquared(nearestEnemy.loc) <
                      self.loc.distSquared(otherUnit.loc)
                }
              };
            if isNearestEnemy {
              maybeNearestEnemy = Some(otherUnit);
            }
          }
        }
      }
    }

    return maybeNearestEnemy;
  }
}
