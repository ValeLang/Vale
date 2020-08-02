#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(unused_variables)]

use crate::model;
use crate::unit;
use crate::unit::*;


#[derive(new)]
pub struct WanderUnitCapability { }
impl IUnitComponent for WanderUnitCapability {
  fn asCapability(&self) -> Option<&dyn IUnitCapability> {
    return Some(self);
  }
  fn asCapabilityMut(&mut self) -> Option<&mut dyn IUnitCapability> {
    return Some(self);
  }
}
impl IUnitCapability for WanderUnitCapability {
  fn getDesire(
    &self,
    rand: &mut model::LCGRand,
    selfUnit: &Unit,
    game: &model::Game)
  -> Box<dyn IUnitDesire> {
    let level = game.getCurrentLevel();
    let mut adjacentLocations = level.getAdjacentLocations(selfUnit.loc, true);
    adjacentLocations =
        adjacentLocations
        .into_iter()
        .filter(|&loc| level.locIsWalkable(loc, true))
        .collect();
    if adjacentLocations.len() == 0 {
      return Box::new(DoNothingUnitDesire{});
    } else {
      return Box::new(
        unit::MoveUnitDesire::new(
          desire::MEH,
          adjacentLocations[(rand.next() % (adjacentLocations.len() as u32)) as usize]
        ));
    }
  }
}

