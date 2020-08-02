#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(unused_variables)]

use crate::model;

use crate::unit;
use crate::unit::*;

#[derive(new)]
pub struct SeekUnitCapability { }

impl IUnitComponent for SeekUnitCapability {
  fn asCapability(&self) -> Option<&dyn IUnitCapability> {
    return Some(self);
  }
  fn asCapabilityMut(&mut self) -> Option<&mut dyn IUnitCapability> {
    return Some(self);
  }
}
impl IUnitCapability for SeekUnitCapability {
  fn getDesire(
    &self,
    _rand: &mut model::LCGRand,
    selfUnit: &Unit,
    game: &model::Game)
  -> Box<dyn IUnitDesire> {

    // Pick a random enemy and head towards them.
    let mut maybeNearestEnemy: Option<&Unit> = None;
    for (&loc, &unitIndex) in &game.getCurrentLevel().unit_by_location {
      if unitIndex == selfUnit.getIndex() {
        continue;
      }
      let unit = game.units.get(unitIndex).expect("wat");
      if unit.allegiance == selfUnit.allegiance {
        continue;
      }
      if let Some(nearestEnemy) = maybeNearestEnemy {
        if selfUnit.loc.diagonalManhattanDistance100(nearestEnemy.loc) < selfUnit.loc.diagonalManhattanDistance100(loc) {
          continue;
        }
      }
      maybeNearestEnemy = Some(unit);
    }

    if let Some(enemy) = maybeNearestEnemy {
      if let Some((newPath, _)) = game.getCurrentLevel().findPath(selfUnit.loc, enemy.loc, i32::MAX, true) {
        if game.getCurrentLevel().locIsWalkable(newPath[0], true) {
          return Box::new(unit::MoveUnitDesire::new(desire::WANT, newPath[0]));
        }
        return Box::new(DoNothingUnitDesire::new());
      } else {
        panic!("No path!");
      }
    } else {
      // No enemies left, just relax!
      return Box::new(DoNothingUnitDesire::new());
    }
  }
}


