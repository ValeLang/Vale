
extern crate generational_arena;
use generational_arena::Arena;
use std::cmp;


use crate::model;
use crate::make_level;

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

pub fn benchmark_rl() {
  let mut rand = model::LCGRand { seed: 1337 };

  let mut units = Arena::new();

  let num_levels = 100;
  let mut levels = Vec::new();

  for _ in 0..num_levels {
    levels.push(make_level::make_level(&mut rand, &mut units));
  }

  let level_index = 0;
  let level = &mut levels[level_index];
  let player_index =
    units.insert(model::Unit {
      hp: 100,
      max_hp: 100,
      loc: level.find_random_walkable_unoccuped_location(&mut rand),
      display_class: "chronomancer".to_string(),
      components: Vec::new(),
    });
  level.unit_by_location.insert(units.get(player_index).expect("no player").loc, player_index);

  let player = &units.get(player_index).expect("no player");

  // change this to loop over the keys in the hash map



  // let display_width = 200;
  // let display_height = 200;
  // for y in cmp::max(0, player.loc.y - display_height)..cmp::min(level.max_height, player.loc.y + display_height) {
  //   for x in cmp::max(0, player.loc.x - display_width)..cmp::min(level.max_width, player.loc.x + display_width) {
  //     let loc = model::Location{ x: x, y: y};
  //     if let Some(unit_index) = level.unit_by_location.get(&loc) {
  //       let unit = units.get(*unit_index).expect("no unit");
  //       match unit.display_class.as_str() {
  //         "goblin" => print!("\x1b[40m\x1b[0;1;31mg\x1b[0m"),
  //         "chronomancer" => print!("\x1b[40m\x1b[0;36m@\x1b[0m"),
  //         _ => panic!("unrecognized unit display class"),
  //       }
  //       continue;
  //     }
  //     if let Some(tile) = level.terrain.tiles.get(&loc) {
  //       match tile.display_class.as_str() {
  //         "dirt" => print!("\x1b[40m\x1b[0;33m.\x1b[0m"),
  //         "grass" => print!("\x1b[40m\x1b[0;32m.\x1b[0m"),
  //         "wall" => print!("\x1b[40m\x1b[0;37m#\x1b[0m"),
  //         _ => panic!("unrecognized tile display class"),
  //       }
  //       continue;
  //     }
  //     print!("\x1b[40m\x1b[0;35m \x1b[0m");
  //   }
  //   println!();
  // }
}
