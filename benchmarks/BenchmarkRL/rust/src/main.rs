use std::collections::HashMap;

extern crate generational_arena;
use generational_arena::Arena;
use std::cmp;

use std::num::Wrapping;


#[derive(Copy, Clone, Hash, Eq, PartialEq, Debug)]
struct Location {
  x: i32,
  y: i32,
}


// From https://stackoverflow.com/a/3062783
struct LCGRand {
  seed: u32
}
impl LCGRand {
  fn next(&mut self) -> u32 {
    let a = 1103515245;
    let c = 12345;
    let m = 0x7FFFFFFF;
    self.seed = ((Wrapping(a) * Wrapping(self.seed) + Wrapping(c)) % Wrapping(m)).0;
    return self.seed;
  }
}

struct Tile {
  walkable: bool,
  display_class: String,
}

struct Terrain {
  tiles: HashMap<Location, Tile>,
}

struct Level {
  max_width: i32,
  max_height: i32,
  terrain: Terrain,
  unit_by_location: HashMap<Location, generational_arena::Index>,
}

impl Level {
  fn find_random_walkable_unoccuped_location(
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

struct Game {
  units: Arena<Unit>,
}


trait IUnitComponent {

}

struct Unit {
  hp: i32,
  max_hp: i32,
  loc: Location,
  display_class: String,
  components: Vec<Box<dyn IUnitComponent>>,
}

// struct LocationHasher { }
// fn __call(this: &LocationHasher, loc: Location) {
//   hash = 0;
//   mut hash = 41 * hash + loc.groupX;
//   mut hash = 41 * hash + loc.groupY;
//   mut hash = 41 * hash + loc.indexInGroup;
//   = hash;
// }

// struct LocationEquator { }
// fn __call(this: &LocationEquator, a: Location, b: Location) {
//   (a.groupX == b.groupX) and (a.groupY == b.groupY) and (a.indexInGroup == b.indexInGroup)
// }

// struct Terrain {
//   pattern: Pattern,
//   elevationStepHeight: Float,
//   tiles: HashMap<Location, Tile, LocationHasher, LocationEquator>,
// }

fn make_level(mut rand: &mut LCGRand, units: &mut Arena<Unit>) -> Level {

  let max_width: i32 = 20;
  let max_height: i32 = 20;

  let mut level =
      Level {
          max_width: max_width as i32,
          max_height: max_height as i32,
          terrain: Terrain { tiles: HashMap::new() },
          unit_by_location: HashMap::new() };

  let mut walkabilities = Vec::with_capacity(max_width as usize);
  for x in 0..max_width {
    walkabilities.push(Vec::with_capacity(max_height as usize));
    for y in 0..max_height {
      let inside_circle =
          (x - max_width/2) * (x - max_width/2) + (y - max_height/2) * (y - max_height/2) < 50 * 50;
      let walkable = rand.next() % 2 == 0;
      walkabilities[x as usize].push(inside_circle && walkable);
    }
  }

  // do cellular automata


  for x in 0..max_width {
    for y in 0..max_height {
      let loc = Location{ x: x as i32, y: y as i32 };
      let walkable = walkabilities[x as usize][y as usize];
      let on_edge = x == 0 || y == 0 || x == max_width - 1 || y == max_height - 1;
      if walkable && !on_edge {
        let display_class = if rand.next() % 2 == 0 { "dirt" } else { "grass" };
        level.terrain.tiles.insert(
            loc, Tile{ walkable: true, display_class: display_class.to_string() });
      } else {
        let mut next_to_walkable = false;
        for neighbor_x in cmp::max(0, x - 1)..cmp::min(max_width - 1, x + 1) {
          for neighbor_y in cmp::max(0, y - 1)..cmp::min(max_height - 1, y + 1) {
            let neighbor_walkable = walkabilities[neighbor_x as usize][neighbor_y as usize];
            if neighbor_walkable {
              next_to_walkable = true;
            }
          }
        }
        if next_to_walkable {
          level.terrain.tiles.insert(loc, Tile{ walkable: false, display_class: "wall".to_string() });
        }
      }
    }
  }

  for _ in 0..(level.terrain.tiles.keys().len() / 10) {
    let new_unit_location = level.find_random_walkable_unoccuped_location(&mut rand);
    let unit =
      units.insert(Unit {
        hp: 10,
        max_hp: 10,
        loc: new_unit_location,
        display_class: "goblin".to_string(),
        components: Vec::new() });
    level.unit_by_location.insert(new_unit_location, unit);
  }

  return level;
}

fn main() {
  let mut rand = LCGRand { seed: 1337 };

  let mut units = Arena::new();

  let num_levels = 1;
  let mut levels = Vec::new();

  for _ in 0..num_levels {
    levels.push(make_level(&mut rand, &mut units));
  }

  let mut level_index = 0;
  let mut level = &mut levels[level_index];
  let player_index =
    units.insert(Unit {
      hp: 100,
      max_hp: 100,
      loc: level.find_random_walkable_unoccuped_location(&mut rand),
      display_class: "chronomancer".to_string(),
      components: Vec::new(),
    });
  level.unit_by_location.insert(units.get(player_index).expect("no player").loc, player_index);

  let player = &units.get(player_index).expect("no player");

  // change this to loop over the keys in the hash map

  println!("player is at {}, {}", player.loc.x, player.loc.y);

  for y in cmp::max(0, player.loc.y - 30)..cmp::min(level.max_height, player.loc.y + 30) {
    for x in cmp::max(0, player.loc.x - 30)..cmp::min(level.max_width, player.loc.x + 30) {
      let loc = Location{ x: x, y: y};
      if let Some(unit_index) = level.unit_by_location.get(&loc) {
        let unit = units.get(*unit_index).expect("no unit");
        match unit.display_class.as_str() {
          "goblin" => print!("\x1b[0;31mg\x1b[0m"),
          "chronomancer" => print!("\x1b[1;36m@\x1b[0m"),
          _ => panic!("unrecognized unit display class"),
        }
        continue;
      }
      if let Some(tile) = level.terrain.tiles.get(&loc) {
        match tile.display_class.as_str() {
          "dirt" => print!("\x1b[0;33m.\x1b[0m"),
          "grass" => print!("\x1b[0;32m.\x1b[0m"),
          "wall" => print!("\x1b[1;30m#\x1b[0m"),
          _ => panic!("unrecognized tile display class"),
        }
      }
      print!(" ");
    }
    println!();
  }

  println!("Hello, world!");
}
