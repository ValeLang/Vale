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
use crate::screen;
use crate::screen::*;
use crate::items;
use crate::wander;
use crate::chase;
use crate::seek;

// Returns whether we should probably re-display it next turn.
// Will be false if its something static like terrain, or true
// if it's something that moves around like a unit.
pub fn setScreenCell(
    screen: &mut Screen,
    game: &model::Game,
    playerVisibleLocs: &FxHashSet<model::Location>,
    loc: model::Location) {
  let mut foregroundColor = ScreenColor::LIGHTGRAY;
  let mut backgroundColor = ScreenColor::BLACK;
  let mut character = " ";

  if let Some(tile) = game.getCurrentLevel().terrain.tiles.get(&loc) {
    match tile.display_class.as_str() {
      "dirt" => { character = "."; foregroundColor = ScreenColor::ORANGE; },
      "grass" => { character = "."; foregroundColor = ScreenColor::GREEN; },
      "wall" => { character = "#"; foregroundColor = ScreenColor::LIGHTGRAY; },
      _ => panic!("unrecognized tile display class"),
    }
  }

  if let Some(unit_index) = game.getCurrentLevel().unit_by_location.get(&loc) {
    let unit = game.units.get(*unit_index).expect("no unit");
    match unit.display_class.as_str() {
      "goblin" => { character = "g"; foregroundColor = ScreenColor::RED; },
      "chronomancer" => { character = "@"; foregroundColor = ScreenColor::TURQUOISE; },
      _ => panic!("unrecognized unit display class"),
    }
  }

  if playerVisibleLocs.contains(&loc) {
    backgroundColor = ScreenColor::DARKGRAY;
  }

  screen.setCell(
      loc.x as usize,
      loc.y as usize,
      backgroundColor,
      foregroundColor,
      character.to_string());
}

pub fn ascend(mut rand: &mut model::LCGRand, game: &mut model::Game) -> bool {
  let player_index = game.playerIndex.expect("wat");
  let oldPlayerLoc = game.getPlayer().loc;
  game.getCurrentLevelMut().unit_by_location.remove(&oldPlayerLoc);
  let playerMut = &mut game.units[player_index];
  let playerNewLevelIndex = playerMut.levelIndex + 1;

  if playerNewLevelIndex >= game.levels.len() {
    return false;
  }

  playerMut.levelIndex = playerNewLevelIndex;

  let newPlayerLoc = game.levels[playerNewLevelIndex]
      .find_random_walkable_unoccuped_location(&mut rand);
  game.units[player_index].loc = newPlayerLoc;
  game.getCurrentLevelMut().unit_by_location.insert(
      newPlayerLoc, player_index);

  return true;
}

pub fn setup(
  mut rand: &mut model::LCGRand,
  max_width: i32,
  max_height: i32,
  num_levels: i32)
-> model::Game {
  let mut game =
    model::Game::new(
      Arena::new(), Vec::new(), None);

  for _ in 0..num_levels {
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

  return game;
}

pub fn turn(mut rand: &mut model::LCGRand, mut game: &mut model::Game) {
  let units: Vec<generational_arena::Index> =
      game.getCurrentLevel().unit_by_location.values().map(|&x| x).collect();
  for &unit_index in units.iter() {
    if game.units.contains(unit_index) {
      unit::Unit::act(unit_index, &mut rand, &mut game);
    }
  }
}

pub fn display(seed: i32, maybeScreen: &mut Option<screen::Screen>, game: &model::Game) {
  let playerVisibleLocs = 
      match game.units.get(game.playerIndex.expect("wat")).map(|p| p.loc) {
        None => { FxHashSet::default() }
        Some(playerLoc) => {
          game.getCurrentLevel().getLocationsWithinSight(
              playerLoc, true, unit::DEFAULT_SIGHT_RANGE)
        }
      };

  if let Some(mut screen) = maybeScreen.as_mut() {
    for x in 0..game.getCurrentLevel().max_width {
      for y in 0..game.getCurrentLevel().max_height {
        let loc = model::Location::new(x, y);
        setScreenCell(&mut screen, &game, &playerVisibleLocs, loc);
      }
    }
    if let Some(player) = game.units.get(game.playerIndex.expect("wat")) {
      screen.setStatusLine(format!("Seed {}   Level {}   HP: {} / {}\nTo benchmark: --seed 1337 --width 40 --height 30 --numLevels 5 --turnDelay 0 --display 0", seed, player.levelIndex, player.hp, player.max_hp));
    } else {
      screen.setStatusLine("Dead!                                  ".to_string());
    }
    screen.paintScreen();
  }
}

pub fn benchmark_rl(
    seed: i32,
    levelWidth: i32,
    levelHeight: i32,
    numLevels: i32,
    shouldDisplay: bool,
    turnDelay: i32) {
  let mut rand = model::LCGRand { seed: seed as u32 };
  let mut game = setup(&mut rand, levelWidth, levelHeight, numLevels);

  let mut maybeScreen =
      if shouldDisplay {
        Some(
            Screen::new(
                game.getCurrentLevel().max_width as usize,
                game.getCurrentLevel().max_height as usize))
      } else {
        None
      };

  loop {
    turn(&mut rand, &mut game);

    let playerOldLevelIndex = game.getPlayer().levelIndex;
    if game.levels[playerOldLevelIndex].unit_by_location.keys().len() == 1 {
      let keepRunning = ascend(&mut rand, &mut game);
      if !keepRunning {
        return;
      }
    }

    display(seed, &mut maybeScreen, &game);

    if turnDelay > 0 {
      sleep(Duration::new(0, turnDelay as u32));
    }
  }
}
