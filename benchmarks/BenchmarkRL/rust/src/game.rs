use rustc_hash::FxHashSet;

use generational_arena;
use generational_arena::Arena;

use crate::unit::*;
use crate::location::*;
use crate::level::*;
use crate::tile::*;

use std::num::Wrapping;

// From https://stackoverflow.com/a/3062783
pub struct LCGRand {
    pub seed: u32,
}
impl LCGRand {
    pub fn next(&mut self) -> u32 {
        let a = 1103515245;
        let c = 12345;
        let m = 0x7FFFFFFF;
        self.seed = ((Wrapping(a) * Wrapping(self.seed) + Wrapping(c)) % Wrapping(m)).0;
        return self.seed;
    }
}


// Used to modify the game. Functions that immutably borrow the world can return
// one of these which will then be called with a mutable reference to the game.
pub type GameMutator = Box<dyn Fn(&mut LCGRand, &mut Game)>;

// Makes a GameMutator that does nothing.
pub fn do_nothing_game_mutator() -> GameMutator {
    return Box::new(|_rand, _game| {});
}



#[derive(new)]
pub struct Game {
    pub units: Arena<Unit>,
    pub levels: Vec<Level>,
    pub player_index: Option<generational_arena::Index>,
}

impl Game {
    pub fn get_current_level(&self) -> &Level {
        return &self.levels[self.get_player().level_index];
    }
    pub fn get_current_level_mut(&mut self) -> &mut Level {
        let level_index = self.get_player().level_index;
        return &mut self.levels[level_index];
    }
    pub fn get_player_index(&self) -> generational_arena::Index {
        return self.player_index.expect("No player yet!");
    }
    pub fn get_player(&self) -> &Unit {
        return &self.units[self.get_player_index()];
    }

    pub fn add_unit_to_level(
        &mut self,
        level_index: usize,
        loc: Location,
        hp: i32,
        max_hp: i32,
        allegiance: Allegiance,
        display_class: String,
    ) -> generational_arena::Index {
        let unit_index = self.units.insert(Unit::new(
            None,
            hp,
            max_hp,
            level_index,
            loc,
            allegiance,
            display_class,
            Arena::new(),
        ));
        self.levels[level_index]
            .unit_by_location
            .insert(loc, unit_index);
        let unit = &mut self.units[unit_index];
        unit.index = Some(unit_index);
        return unit_index;
    }

    pub fn damage_unit(
            &mut self,
            rand: &mut LCGRand,
            unit_index: generational_arena::Index,
            damage: i32) {
        let enemy_unit = &mut self.units[unit_index];
        let was_alive = enemy_unit.hp > 0;
        enemy_unit.hp = enemy_unit.hp - damage;
        let is_alive = enemy_unit.hp > 0;
        let died = was_alive && !is_alive;

        if died {
            self.kill_unit(rand, unit_index);
        }
    }

    pub fn kill_unit(
            &mut self,
            rand: &mut LCGRand,
            unit_index: generational_arena::Index) {
        let enemy_unit = &self.units[unit_index];
        let mut initial_components_indices =
            Vec::with_capacity(enemy_unit.components.len());
        for (component_index, _) in &enemy_unit.components {
            initial_components_indices.push(component_index);
        }
        for component_index in initial_components_indices {
            let maybe_game_mutator: Option<Box<dyn Fn(&mut LCGRand, &mut Game)>> = {
                let enemy_unit = &self.units[unit_index];
                match enemy_unit.components.get(component_index) {
                    None => None, // The component was removed by some other component.
                    Some(c) => {
                        Some(c.on_unit_death(rand, self, unit_index, component_index, unit_index))
                    }
                }
            };
            if let Some(game_mutator) = maybe_game_mutator {
                game_mutator(rand, self);
            }
        }
        self.remove_unit(unit_index);
    }

    pub fn remove_unit(&mut self, unit_index: generational_arena::Index) {
        let loc = self.units[unit_index].loc;
        self.get_current_level_mut().forget_unit(unit_index, loc);
        self.units.remove(unit_index);
    }

    pub fn get_tile_component_mut<T: ITileComponent>(
            &mut self,
            tile_loc: Location,
            tile_component_index: generational_arena::Index)
    -> &mut T {
        let tile = self.get_current_level_mut().tiles.get_mut(&tile_loc).expect("");
        let icomponent = tile.components.get_mut(tile_component_index).expect("wat");
        return &mut *icomponent.downcast_mut::<T>().expect("");
    }
}

// Get all the locations adjacent to `center`.
pub fn get_pattern_adjacent_locations(
    center: Location,
    consider_corners_adjacent: bool,
) -> Vec<Location> {
    let mut result = Vec::new();
    result.push(Location::new(center.x - 1, center.y));
    result.push(Location::new(center.x, center.y + 1));
    result.push(Location::new(center.x, center.y - 1));
    result.push(Location::new(center.x + 1, center.y));
    if consider_corners_adjacent {
        result.push(Location::new(center.x - 1, center.y - 1));
        result.push(Location::new(center.x - 1, center.y + 1));
        result.push(Location::new(center.x + 1, center.y - 1));
        result.push(Location::new(center.x + 1, center.y + 1));
    }
    return result;
}

// Get all the locations adjacent to any of the ones in `source_locs`.
pub fn get_pattern_locations_adjacent_to_any(
    source_locs: &FxHashSet<Location>,
    include_source_locs: bool,
    consider_corners_adjacent: bool,
) -> FxHashSet<Location> {
    let mut result = FxHashSet::default();
    for &original_location in source_locs {
        let mut adjacents =
            get_pattern_adjacent_locations(original_location, consider_corners_adjacent);
        if include_source_locs {
            adjacents.push(original_location.clone());
        }
        for adjacent_location in adjacents {
            if !include_source_locs && source_locs.contains(&adjacent_location) {
                continue;
            }
            result.insert(adjacent_location);
        }
    }
    return result;
}
