#![allow(non_snake_case)]
#![allow(unused_imports)]

use rustc_hash::FxHashSet;
use std::time::{Duration, SystemTime};
use std::thread::sleep;
use std::io::stdout;
extern crate generational_arena;
use generational_arena::Arena;

use crossterm::{ExecutableCommand, cursor::MoveTo};

use crate::model;
use crate::make_level;
use crate::unit;
use crate::attack;
use crate::items;
use crate::wander;
use crate::chase;
use crate::seek;

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

pub fn moveCursorTo(x: u16, y: u16) {
  match stdout().execute(MoveTo(x, y)) {
    Ok(_) => {}
    Err(_) => { panic!("wat"); }
  }
}

pub fn paintCell(
    game: &model::Game,
    loc: &model::Location,
    locsPlayerCanSee: &FxHashSet<model::Location>) {

  moveCursorTo(loc.x as u16, loc.y as u16);

  let background =
    if locsPlayerCanSee.contains(&loc) {
      "\x1b[40m"
    } else {
      "\x1b[0m"
    };


  if let Some(unit_index) = game.getCurrentLevel().unit_by_location.get(&loc) {
    let unit = game.units.get(*unit_index).expect("no unit");
    match unit.display_class.as_str() {
      "goblin" => print!("{}{}g\x1b[0m", background, "\x1b[1;31m"),
      "chronomancer" => print!("{}{}@\x1b[0m", background, "\x1b[36m"),
      _ => panic!("unrecognized unit display class"),
    }
    return;
  }
  if let Some(tile) = game.getCurrentLevel().terrain.tiles.get(&loc) {
    match tile.display_class.as_str() {
      "dirt" => print!("{}{}.\x1b[0m", background, "\x1b[33m"),
      "grass" => print!("{}{}.\x1b[0m", background, "\x1b[32m"),
      "wall" => print!("{}{}#\x1b[0m", background, "\x1b[37m"),
      _ => panic!("unrecognized tile display class"),
    }
    return;
  }
  print!("{} \x1b[0m", background);
}

pub fn benchmark_rl() {
  // let now = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).expect("wat").as_secs();
  let now = 1596340300;
  println!("Using seed: {}", now);
  let mut rand = model::LCGRand { seed: now as u32 };

  let mut game =
    model::Game::new(
      Arena::new(), Vec::new(), None);

  let num_levels = 2;
  for _ in 0..num_levels {
    let max_width: i32 = 80;
    let max_height: i32 = 30;
    let levelIndex = game.levels.len();
    game.levels.push(
        make_level::make_level(max_width, max_height, &mut rand));

    // for _ in 0..0 {
    // for _ in 0..1 {
    let numWalkableLocations =
        game.levels[levelIndex].getWalkableLocations().len();
    for _ in 0..(numWalkableLocations / 10) {
      let new_unit_loc =
        game.levels[levelIndex]
          .find_random_walkable_unoccuped_location(&mut rand);

      let new_unit_index =
          game.addUnitToLevel(
              levelIndex,
              new_unit_loc,
              10,
              10,
              unit::Allegiance::Evil,
              "goblin".to_string());
      let new_unit = game.units.get_mut(new_unit_index).expect("wat");
      new_unit.components.insert(Box::new(wander::WanderUnitCapability::new()));
      new_unit.components.insert(Box::new(attack::AttackUnitCapability::new()));
      new_unit.components.insert(Box::new(chase::ChaseUnitCapability::new()));
      new_unit.components.insert(Box::new(items::GoblinClaws::new()));
    }
  }


  let playerLoc =
      game.levels[0].find_random_walkable_unoccuped_location(&mut rand);
  let player_index =
      game.addUnitToLevel(
          0,
          playerLoc,
          1000000,
          1000000,
          unit::Allegiance::Good,
          "chronomancer".to_string());
  game.playerIndex = Some(player_index);
  {
    let player = game.units.get_mut(player_index).expect("wat");
    player.components.insert(Box::new(wander::WanderUnitCapability::new()));
    player.components.insert(Box::new(attack::AttackUnitCapability::new()));
    player.components.insert(Box::new(chase::ChaseUnitCapability::new()));
    player.components.insert(Box::new(seek::SeekUnitCapability::new()));
    player.components.insert(Box::new(items::IncendiumShortSword::new()));
  }

  let mut dirty_tiles = Vec::new();
  for x in 0..game.getCurrentLevel().max_width {
    dirty_tiles.push(Vec::new());
    for _ in 0..game.getCurrentLevel().max_height {
      // All tiles start as dirty
      dirty_tiles[x as usize].push(true);
    }
  }

  for _ in 0..game.getCurrentLevel().max_height {
    println!("");
  }

  loop {
    let units: Vec<generational_arena::Index> =
        game.getCurrentLevel().unit_by_location.values().map(|&x| x).collect();
    for &unit_index in units.iter() {
      if game.units.contains(unit_index) {
        unit::Unit::act(unit_index, &mut rand, &mut game);
      }
    }

    let playerOldLevelIndex = game.getPlayer().levelIndex;
    if game.levels[playerOldLevelIndex].unit_by_location.keys().len() == 1 {
      {
        let level = game.getCurrentLevel();
        for loc in level.terrain.tiles.keys() {
          dirty_tiles[loc.x as usize][loc.y as usize] = true;
        }
      }

      let oldPlayerLoc = game.getPlayer().loc;
      game.getCurrentLevelMut().unit_by_location.remove(&oldPlayerLoc);
      let playerMut = &mut game.units[player_index];
      playerMut.levelIndex = playerMut.levelIndex + 1;

      if (playerMut.levelIndex >= game.levels.len()) {
        return;
      }

      {
        let level = game.getCurrentLevel();
        for loc in level.terrain.tiles.keys() {
          dirty_tiles[loc.x as usize][loc.y as usize] = true;
        }
      }

      let newPlayerLoc =
          game.levels[game.units[player_index].levelIndex]
          .find_random_walkable_unoccuped_location(&mut rand);
      game.units[player_index].loc = newPlayerLoc;
      game.getCurrentLevelMut().unit_by_location.insert(
          newPlayerLoc, player_index);
    }

    let locsPlayerCanSee =
        match game.units.get(game.playerIndex.expect("wat")).map(|p| p.loc) {
          None => { FxHashSet::default() }
          Some(playerLoc) => {
            game.getCurrentLevel().getLocationsWithinSight(
                playerLoc, true, unit::DEFAULT_SIGHT_RANGE)
          }
        };

    let level = game.getCurrentLevel();

    // Mark the current locations of all units as dirty, so we re-paint
    // them below.
    for unit_loc in level.unit_by_location.keys() {
      dirty_tiles[unit_loc.x as usize][unit_loc.y as usize] = true;
    }

    // let display_width = 200;
    // let display_height = 200;
    // let player = &units.get(player_index).expect("no player");
    // for y in cmp::max(0, player.loc.y - display_height)..cmp::min(level.max_height, player.loc.y + display_height) {
    //   for x in cmp::max(0, player.loc.x - display_width)..cmp::min(level.max_width, player.loc.x + display_width) {
    for y in 0..level.max_height {
      for x in 0..level.max_width {
        let loc = model::Location::new(x, y);
        if dirty_tiles[x as usize][y as usize] {
          paintCell(&game, &loc, &locsPlayerCanSee);
          dirty_tiles[x as usize][y as usize] = false;
        }
      }
    }
    moveCursorTo(0, level.max_height as u16);

    if let Some(player) = game.units.get(game.playerIndex.expect("wat")) {
      println!("Level {}   HP: {} / {}", player.levelIndex, player.hp, player.max_hp);
    } else {
      println!("Dead!                            ");
    }

    // Mark the current locations of all units as dirty, in case the unit
    // moves off of that location. This will make sure we re-paint these
    // spots next turn.
    for unit_loc in level.unit_by_location.keys() {
      dirty_tiles[unit_loc.x as usize][unit_loc.y as usize] = true;
    }
    for loc in locsPlayerCanSee {
      dirty_tiles[loc.x as usize][loc.y as usize] = true;
    }

    sleep(Duration::new(0, 200));
  }
}
