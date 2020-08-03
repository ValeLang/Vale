use crate::game::*;
use crate::unit::*;
use crate::location::*;

// A unit capability that wants to chase units it can see.
pub struct ChaseUnitCapability {
    // We remember which unit we're chasing (and the path to them) even between
    // turns.
    // When the enemy goes out of sight (such as around a corner), we keep following
    // the path to them, in hopes that we'll see them when they get there.
    current_target: Option<CurrentTarget>,
}
#[derive(new)]
pub struct CurrentTarget {
    pub target_unit_index: generational_arena::Index,
    pub path_to_target: Vec<Location>,
}
impl ChaseUnitCapability {
    pub fn new() -> ChaseUnitCapability {
        return ChaseUnitCapability {
            current_target: None,
        };
    }
}
impl IUnitComponent for ChaseUnitCapability {
    fn as_capability(&self) -> Option<&dyn IUnitCapability> {
        return Some(self);
    }
    fn as_capability_mut(&mut self) -> Option<&mut dyn IUnitCapability> {
        return Some(self);
    }
}
impl IUnitCapability for ChaseUnitCapability {
    fn get_desire(
        &self,
        _rand: &mut LCGRand,
        self_unit: &Unit,
        game: &Game,
    ) -> Box<dyn IUnitDesire> {
        // A helper function to make a ChaseUnitDesire from a path we found
        // towards an enemy.
        fn make_desire_from_path(
            game: &Game,
            self_unit: &Unit,
            _strength: i32,
            target_unit_index: generational_arena::Index,
            mut new_path: Vec<Location>,
        ) -> Box<dyn IUnitDesire> {
            assert!(new_path.len() > 0);
            assert!(new_path[0] != self_unit.loc);
            assert!(new_path[new_path.len() - 1] != self_unit.loc);

            let next_step = new_path[0];
            new_path.remove(0);

            assert!(game.get_current_level().loc_is_walkable(next_step, true));

            return Box::new(ChaseUnitDesire::new(
                desire::ALMOST_NEED, // Gotta protect your territory, man
                target_unit_index,
                next_step,
                new_path,
            ));
        }

        // If we're targeting a unit and have a path, make sure we're
        // next to the first step on the path.
        match &self.current_target {
            Some(current_target) => {
                if !self_unit
                    .loc
                    .next_to(current_target.path_to_target[0], true, false)
                {
                    panic!(
                        "Unit at {} not next to next step in path at {}",
                        self_unit.loc, current_target.path_to_target[0]
                    );
                }
            }
            None => {} // proceed
        }

        // If the enemy we were previously targeting is still alive and
        // still within sight range, chase them!
        // If not (such as them going around a corner), follow
        // the last known path to them, maybe we'll see them again.
        match &self.current_target {
            Some(current_target) => {
                // Ignore target_unit if they're dead
                if let Some(target_unit) = game.units.get(current_target.target_unit_index) {
                    // target_unit is alive!

                    if game.get_current_level().can_see(
                        self_unit.loc,
                        target_unit.loc,
                        DEFAULT_SIGHT_RANGE_100,
                    ) {
                        // We can see the enemy right now, chase them!
                        // Recalculate a new path to the unit!

                        // Enemies are lazy, they'll only chase you if you're 2x their sight range away.
                        let max_travel_distance = DEFAULT_SIGHT_RANGE_100 * 2;

                        match game.get_current_level().find_path(
                            self_unit.loc,
                            target_unit.loc,
                            max_travel_distance,
                            true,
                        ) {
                            Some((new_path, _)) => {
                                if game.get_current_level().loc_is_walkable(new_path[0], true) {
                                    return make_desire_from_path(
                                        game,
                                        self_unit,
                                        desire::ALMOST_NEED, // Gotta protect your territory, man
                                        current_target.target_unit_index,
                                        new_path,
                                    );
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

                        if game
                            .get_current_level()
                            .loc_is_walkable(current_target.path_to_target[0], true)
                        {
                            return make_desire_from_path(
                                game,
                                self_unit,
                                desire::REALLY_WANT, // If they're out of sight, it's slightly less important
                                current_target.target_unit_index,
                                current_target.path_to_target.clone(),
                            );
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
        match self_unit.get_nearest_enemy_in_sight(&game, DEFAULT_SIGHT_RANGE_100) {
            Some(nearest_enemy_unit) => {
                // Calculate a path to the unit.

                // Enemies are lazy, they'll only chase you if you're 2x their sight range away.
                let max_travel_distance = DEFAULT_SIGHT_RANGE_100 * 2;

                match game.get_current_level().find_path(
                    self_unit.loc,
                    nearest_enemy_unit.loc,
                    max_travel_distance,
                    true,
                ) {
                    Some((new_path, _)) => {
                        if game.get_current_level().loc_is_walkable(new_path[0], true) {
                            // println!("bork D");
                            return make_desire_from_path(
                                game,
                                self_unit,
                                desire::ALMOST_NEED, // Gotta protect your territory, man
                                nearest_enemy_unit.get_index(),
                                new_path,
                            );
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

    // Called when an desire is about to be enacted by this unit.
    // If a non-chase desire is being enacted, this gives us the opportunity
    // to throw away our state which was tracking how we'd get to our target
    // enemy.
    fn pre_act(&mut self, desire: &dyn IUnitDesire) {
        if let Some(_) = desire.downcast_ref::<ChaseUnitDesire>() {
            // Do nothing, the desire will take care of it
        } else {
            // Some other capability's desire is about to be enacted!
            // Our chase is interrupted, throw away our state.
            self.current_target = None;
        }
    }
}

// The desire produced by the chase capability above on a given turn.
#[derive(new)]
pub struct ChaseUnitDesire {
    // How much we want to chase them this turn.
    strength: i32,

    // The unit to chase.
    target_unit_index: generational_arena::Index,

    // First step towards them.
    next_step_loc: Location,

    // Where we should go in the future if we lose sight of them.
    future_path_to_target: Vec<Location>,
}
impl IUnitDesire for ChaseUnitDesire {
    fn get_strength(&self) -> i32 {
        return self.strength;
    }
    fn enact(self: Box<Self>, _rand: &mut LCGRand, self_unit_index: generational_arena::Index, game: &mut Game) {
        let ChaseUnitDesire {
            strength: _,
            target_unit_index,
            next_step_loc,
            future_path_to_target,
        } = *self;

        let self_unit = &mut game.units[self_unit_index];
        let chase_capability = self_unit
            .get_first_component_mut::<ChaseUnitCapability>()
            .expect("Expected a ChaseCapability that this ChaseUnitDesire came from");

        if future_path_to_target.len() > 0 {
            chase_capability.current_target =
                Some(CurrentTarget::new(target_unit_index, future_path_to_target));
        } else {
            chase_capability.current_target = None;
        }

        let level_index = self_unit.level_index;
        let level = &mut game.levels[level_index];
        level.move_unit(self_unit, next_step_loc);
    }
}
