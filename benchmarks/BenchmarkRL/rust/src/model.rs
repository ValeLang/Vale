use std::collections::HashMap;

extern crate generational_arena;
use generational_arena::Arena;

use std::num::Wrapping;
use std::fmt;

#[derive(Copy, Clone, Hash, Eq, PartialEq, Debug)]
pub struct Location {
  pub x: i32,
  pub y: i32,
}
impl std::fmt::Display for Location {
  fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
    write!(f, "({}, {})", self.x, self.y)
  }
}


// From https://stackoverflow.com/a/3062783
pub struct LCGRand {
  pub seed: u32
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

pub struct Tile {
  pub walkable: bool,
  pub display_class: String,
}

pub struct Terrain {
  pub tiles: HashMap<Location, Tile>,
}

pub struct Level {
  pub max_width: i32,
  pub max_height: i32,
  pub terrain: Terrain,
  pub unit_by_location: HashMap<Location, generational_arena::Index>,
}

impl Level {
  pub fn find_random_walkable_unoccuped_location(
      self: &Level, rand: &mut LCGRand) -> Location {
    loop {
      // return Location{ x: 4, y: 4 };
      let loc_index = rand.next() % (self.terrain.tiles.keys().len() as u32);
      let loc = *self.terrain.tiles.keys().nth(loc_index as usize).expect("wat");
      match self.terrain.tiles.get(&loc) {
        None => continue,
        Some(tile) => {
          if !tile.walkable {
            continue;
          }
          if self.unit_by_location.contains_key(&loc) {
            continue;
          }
        }
      }
      return loc;
    }
  }
}

pub struct Game {
  pub units: Arena<Unit>,
}


pub trait IUnitComponent {

}

pub struct Unit {
  pub hp: i32,
  pub max_hp: i32,
  pub loc: Location,
  pub display_class: String,
  pub components: Vec<Box<dyn IUnitComponent>>,
}
