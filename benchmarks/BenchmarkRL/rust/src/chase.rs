#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(unused_variables)]

use crate::model;

use crate::unit::*;

#[derive(new)]
pub struct ChaseUnitDesire {
  strength: i32,
  targetUnitIndex: generational_arena::Index,
  nextStepLoc: model::Location,
  futurePathToTarget: Vec<model::Location>,
}
impl IUnitDesire for ChaseUnitDesire {
  fn getStrength(&self) -> i32 { return self.strength; }
  fn enact(
      self: Box<Self>,
      selfUnitIndex: generational_arena::Index,
      game: &mut model::Game) {

    let ChaseUnitDesire{ strength, targetUnitIndex, nextStepLoc, futurePathToTarget } = *self;

    let selfUnit = game.units.get_mut(selfUnitIndex).expect("wat");
    let chaseCapability = selfUnit.getFirstComponentMut::<ChaseUnitCapability>()
        .expect("Expected a ChaseCapability that this ChaseUnitDesire came from");

    if futurePathToTarget.len() > 0 {
      chaseCapability.currentTarget =
          Some(CurrentTarget::new(
              targetUnitIndex, futurePathToTarget));
    } else {
      chaseCapability.currentTarget = None;
    }

    let levelIndex = selfUnit.levelIndex;
    let level = &mut game.levels[levelIndex];
    level.moveUnit(selfUnit, nextStepLoc);
  }
}

#[derive(new)]
pub struct CurrentTarget {
  pub targetUnitIndex: generational_arena::Index,
  pub pathToTarget: Vec<model::Location>,
}

pub struct ChaseUnitCapability {
  currentTarget: Option<CurrentTarget>,
}
impl ChaseUnitCapability {
  pub fn new() -> ChaseUnitCapability {
    return ChaseUnitCapability { currentTarget: None };
  }
}
impl IUnitComponent for ChaseUnitCapability {
  fn asCapability(&self) -> Option<&dyn IUnitCapability> {
    return Some(self);
  }
  fn asCapabilityMut(&mut self) -> Option<&mut dyn IUnitCapability> {
    return Some(self);
  }
}
impl IUnitCapability for ChaseUnitCapability {
  fn getDesire(
    &self,
    _rand: &mut model::LCGRand,
    selfUnit: &Unit,
    game: &model::Game)
  -> Box<dyn IUnitDesire> {
    fn makeDesireFromPath(
        game: &model::Game,
        selfUnit: &Unit,
        strength: i32,
        targetUnitIndex: generational_arena::Index,
        mut newPath: Vec<model::Location>) -> Box<dyn IUnitDesire> {

      assert!(newPath.len() > 0);
      assert!(newPath[0] != selfUnit.loc);
      assert!(newPath[newPath.len() - 1] != selfUnit.loc);

      let nextStep = newPath[0];
      newPath.remove(0);

      assert!(game.getCurrentLevel().locIsWalkable(nextStep, true));

      return Box::new(
        ChaseUnitDesire::new(
          desire::ALMOST_NEED, // Gotta protect your territory, man
          targetUnitIndex,
          nextStep,
          newPath
        ));
    }

    // If we're targeting a unit and have a path, make sure we're
    // next to the first step on the path.
    match &self.currentTarget {
      Some(currentTarget) => {
        if !selfUnit.loc.nextTo(currentTarget.pathToTarget[0], true, false) {
          panic!("Unit at {} not next to next step in path at {}", selfUnit.loc, currentTarget.pathToTarget[0]);
        }
      }
      None => {} // proceed
    }
    
    // If the enemy we were previously targeting is still alive and
    // still within sight range, chase them!
    // If not (such as them going around a corner), follow
    // the last known path to them, maybe we'll see them again.
    match &self.currentTarget {
      Some(currentTarget) => {
        // Ignore targetUnit if they're dead
        if let Some(targetUnit) = game.units.get(currentTarget.targetUnitIndex) {
          // targetUnit is alive!

          if game.getCurrentLevel().canSee(selfUnit.loc, targetUnit.loc, DEFAULT_SIGHT_RANGE) {
            // We can see the enemy right now, chase them!
            // Recalculate a new path to the unit!

            // Enemies are lazy, they'll only chase you if you're 2x their sight range away.
            let maxTravelDistance = DEFAULT_SIGHT_RANGE * 2;

            match game.getCurrentLevel().findPath(selfUnit.loc, targetUnit.loc, maxTravelDistance, true) {
              Some((newPath, _)) => {
                if game.getCurrentLevel().locIsWalkable(newPath[0], true) {
                  return makeDesireFromPath(
                      game,
                      selfUnit,
                      desire::ALMOST_NEED, // Gotta protect your territory, man
                      currentTarget.targetUnitIndex,
                      newPath);
                } else {
                  // We can't follow the path to the last position the enemy was at.
                  // Continue on, maybe we'll find a new enemy.
                }
              }
              None => {
                // Cant find an acceptable path to the unit, even though we can see them.
                // This could happen if it's too far, or the level was separated by a river,
                // for example.
                // Continue on, maybe we'll find a new enemy.
              }
            }
          } else {
            // We can't see the enemy right now. Follow the stored path to them!

            if game.getCurrentLevel().locIsWalkable(currentTarget.pathToTarget[0], true) {
              return makeDesireFromPath(
                  game,
                  selfUnit,
                  desire::REALLY_WANT, // If they're out of sight, it's slightly less important
                  currentTarget.targetUnitIndex,
                  currentTarget.pathToTarget.clone());
            } else {
              // We can't follow the path to the last position the enemy was at.
              // Continue on, maybe we'll find a new enemy.
            }
          }
        } else {
          // Target unit is dead. Continue on, maybe we'll find a new enemy.
        }
      }
      None => {} // proceed
    }

    // There's no target, or we couldn't follow them, or something,
    // so lets look around and see if we can find one.
    match selfUnit.getNearestEnemyInSight(&game, DEFAULT_SIGHT_RANGE) {
      Some(nearestEnemyUnit) => {
        // Calculate a path to the unit.

        // Enemies are lazy, they'll only chase you if you're 2x their sight range away.
        let maxTravelDistance = DEFAULT_SIGHT_RANGE * 2;

        match game.getCurrentLevel().findPath(selfUnit.loc, nearestEnemyUnit.loc, maxTravelDistance, true) {
          Some((newPath, _)) => {
            if game.getCurrentLevel().locIsWalkable(newPath[0], true) {
              // println!("bork D");
              return makeDesireFromPath(
                  game,
                  selfUnit,
                  desire::ALMOST_NEED, // Gotta protect your territory, man
                  nearestEnemyUnit.getIndex(),
                  newPath);
            } else { 
              // We have a path, but we can't take the first step.
            }
          }
          None => {
            // Cant find a path to the unit, even though we can see them. This could
            // happen if a level was separated by a river, for example.
          }
        }
      }
      None => {} // proceed
    }

    // No enemy in sight, and no path. Welp, guess it's time to just relax!
    return Box::new(DoNothingUnitDesire::new());
  }

  fn preAct(
      &mut self,
      desire: &dyn IUnitDesire) {
    if let Some(w) = desire.downcast_ref::<ChaseUnitDesire>() {
      // Do nothing, the desire will take care of it
    } else {
      // Some other capability's desire is about to be enacted!
      // Our chase is interrupted, throw away our state.
      self.currentTarget = None;
    }
  }
}


