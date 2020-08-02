#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(unused_imports)]

use rustc_hash::FxHashMap;
use rustc_hash::FxHashSet;
// use std::collections::BinaryHeap;

extern crate generational_arena;
use generational_arena::Arena;

use crate::astar;

use std::num::Wrapping;
use std::fmt;
use std::iter::FromIterator;

use crate::unit;

#[derive(new, Copy, Clone, Hash, Eq, PartialEq, Debug)]
pub struct Location {
  pub x: i32,
  pub y: i32,
}
impl Location {
  pub fn distSquared(&self, other: Location) -> i32 {
    return (self.x - other.x) * (self.x - other.x) +
        (self.y - other.y) * (self.y - other.y);
  }
  pub fn nextTo(&self, other: Location, considerCornersAdjacent: bool, includeSelf: bool) -> bool {
    let distSquared = self.distSquared(other);
    let minSquaredDistance = if includeSelf { 0 } else { 1 };
    let maxSquaredDistance = if considerCornersAdjacent { 2 } else { 1 };
    return distSquared >= minSquaredDistance &&
        distSquared <= maxSquaredDistance;
  }
  // The 100 means times 100.
  pub fn diagonalManhattanDistance100(&self, other: Location) -> i32 {
    let x_dist = (self.x - other.x).abs();
    let y_dist = (self.y - other.y).abs();
    let diagonal_dist = std::cmp::min(x_dist, y_dist); // 100 sqrt 2
    let remaining_x_dist = x_dist - diagonal_dist;
    let remaining_y_dist = y_dist - diagonal_dist;
    return diagonal_dist * 144 + remaining_x_dist * 100 + remaining_y_dist * 100;
  }
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

#[derive(new)]
pub struct Tile {
  pub walkable: bool,
  pub display_class: String,
}

#[derive(new)]
pub struct Terrain {
  pub tiles: FxHashMap<Location, Tile>,
}

#[derive(new)]
pub struct Level {
  pub max_width: i32,
  pub max_height: i32,
  pub terrain: Terrain,
  pub unit_by_location: FxHashMap<Location, generational_arena::Index>,
}

impl Level {
  pub fn find_random_walkable_unoccuped_location(
      self: &Level, rand: &mut LCGRand) -> Location {
    let walkableLocations = self.getWalkableLocations();
    let loc_index = rand.next() % (walkableLocations.len() as u32);
    return walkableLocations[loc_index as usize];
  }
  pub fn getAdjacentLocations(
    &self,
    center: Location,
    considerCornersAdjacent: bool)
  -> Vec<Location> {
    let mut result = Vec::new();
    for adjacent in getPatternAdjacentLocations(center, considerCornersAdjacent) {
      if adjacent.x >= 0 && adjacent.y >= 0 && adjacent.x < self.max_width && adjacent.y < self.max_height {
        result.push(adjacent)
      }
    }
    return result;
  }

  pub fn locIsWalkable(&self, loc: Location, considerUnits: bool) -> bool {
    if !self.terrain.tiles.get(&loc).expect("wat").walkable {
      return false;
    }
    if considerUnits {
      if self.unit_by_location.contains_key(&loc) {
        return false;
      }
    }
    return true;
  }

  pub fn getAdjacentWalkableLocations(
    &self,
    center: Location,
    considerUnits: bool,
    considerCornersAdjacent: bool)
  -> Vec<Location> {
    let mut result = Vec::new();
    let adjacents = 
        self.getAdjacentLocations(
            center,
            considerCornersAdjacent);
    for neighbor in adjacents {
      if self.locIsWalkable(neighbor, considerUnits) {
        result.push(neighbor);
      }
    }
    return result;
  }

  pub fn findPath(
      &self,
      fromLoc: Location,
      toLoc: Location,
      maxDistance: i32,
      considerCornersAdjacent: bool)
  -> Option<(Vec<Location>, i32)> {
    return astar::aStar(
        fromLoc,
        toLoc,
        maxDistance,
        &|a, b| a.diagonalManhattanDistance100(b),
        &|loc| self.getAdjacentWalkableLocations(loc, false, considerCornersAdjacent));
  }

  pub fn canSee(
      &self,
      fromLoc: Location,
      toLoc: Location,
      sightRange: i32)
  -> bool {
    match self.findPath(fromLoc, toLoc, sightRange, true) {
      None => { return false }
      Some((_, pathLength)) => {
        assert!(pathLength <= sightRange); // curiosity
        return true;
      }
    }
  }

  pub fn forgetUnit(&mut self, unitIndex: generational_arena::Index, loc: Location) {
    assert!(self.unit_by_location.contains_key(&loc));
    assert!(self.unit_by_location[&loc] == unitIndex);
    self.unit_by_location.remove(&loc);
  }

  pub fn getLocationsWithinSight(
    &self,
    startLoc: Location,
    includeSelf: bool,
    sightRange: i32)
  -> FxHashSet<Location> {
    let mut visibleLocs: FxHashSet<Location> =
        FxHashSet::default();
    let mut walkedDistanceByLocToConsider: FxHashMap<Location, i32> =
        FxHashMap::default();

    walkedDistanceByLocToConsider.insert(startLoc, 0);

    while walkedDistanceByLocToConsider.len() > 0 {
      let (&thisLoc, &walkedDistanceFromStartToThis) = walkedDistanceByLocToConsider.iter().next().expect("wat");
      walkedDistanceByLocToConsider.remove(&thisLoc);
      visibleLocs.insert(thisLoc);

      // It's not walkable, so it's a wall, so dont check its adjacents.
      if !self.locIsWalkable(thisLoc, false) {
        continue;
      }

      for adjacentLoc in self.getAdjacentLocations(thisLoc, true) {
        // This is the ideal distance, if we were to go straight to the location.
        let directDistanceFromStartToAdjacent = startLoc.diagonalManhattanDistance100(adjacentLoc);
        if directDistanceFromStartToAdjacent > sightRange {
          // This is outside sight range even if there was a clear line of sight,
          // skip it.
          continue;
        }

        let distanceFromThisToAdjacent =
            thisLoc.diagonalManhattanDistance100(adjacentLoc);
        let walkedDistanceWalkedFromStartToAdjacent =
            walkedDistanceFromStartToThis + distanceFromThisToAdjacent;

        // If these arent equal, then we went out of our way somehow, which means
        // we didnt go very straight, so skip it.
        if walkedDistanceWalkedFromStartToAdjacent != directDistanceFromStartToAdjacent {
          continue;
        }

        // If we've already visibleLocs it, or we already plan to explore it,
        // then don't add it.
        if visibleLocs.contains(&adjacentLoc) || walkedDistanceByLocToConsider.contains_key(&adjacentLoc) {
          continue;
        }

        // We dont check walkability here because we want to see the walls...
        // we just don't want to see past them, so we dont consider their
        // adjacents. That check is above.

        // println!("Found location in sight range! {} far away {}", adjacentLoc, walkedDistanceWalkedFromStartToAdjacent);
        walkedDistanceByLocToConsider.insert(
            adjacentLoc, walkedDistanceWalkedFromStartToAdjacent);
      }
    }

    if !includeSelf {
      visibleLocs.remove(&startLoc);
    }

    // println!("Done canSee'ing, can see {} locs within {}", visibleLocs.len(), sightRange);

    // return Vec::<Location>::from_iter(visibleLocs.iter().cloned());
    return visibleLocs;
  }

  pub fn getWalkableLocations(&self) -> Vec<Location> {
    let mut result = Vec::new();
    for (&loc, tile) in &self.terrain.tiles {
      if tile.walkable {
        result.push(loc);
      }
    }
    return result;
  }

  pub fn moveUnit(&mut self, unit: &mut unit::Unit, newLoc: Location) {
    assert!(!self.unit_by_location.contains_key(&newLoc));
    self.unit_by_location.remove(&unit.loc);
    unit.loc = newLoc;
    self.unit_by_location.insert(unit.loc, unit.getIndex());
  }
}

#[derive(new)]
pub struct Game {
  pub units: Arena<unit::Unit>,
  pub levels: Vec<Level>,
  // pub currentLevelIndex: Option<generational_arena::Index>,
  pub playerIndex: Option<generational_arena::Index>,
}

impl Game {
  pub fn getCurrentLevel(&self) -> &Level {
    return &self.levels[self.getPlayer().levelIndex];
  }
  pub fn getCurrentLevelMut(&mut self) -> &mut Level {
    let levelIndex = self.getPlayer().levelIndex;
    return &mut self.levels[levelIndex];
  }
  pub fn getPlayer(&self) -> &unit::Unit {
    return self.units.get(self.playerIndex.expect("wat")).expect("wat");
  }
  pub fn getPlayerMut(&mut self) -> &mut unit::Unit {
    return self.units.get_mut(self.playerIndex.expect("wat")).expect("wat");
  }

  pub fn removeUnit(&mut self, unitIndex: generational_arena::Index) {
    let loc = self.units.get(unitIndex).expect("wat").loc;
    self.getCurrentLevelMut().forgetUnit(unitIndex, loc);
    self.units.remove(unitIndex);
  }

  pub fn addUnitToLevel(
    &mut self,
    levelIndex: usize,
    loc: Location,
    hp: i32,
    maxHp: i32,
    allegiance: unit::Allegiance,
    display_class: String)
  -> generational_arena::Index {
    let unit_index =
      self.units.insert(
        unit::Unit::new(
          None,
          hp,
          maxHp,
          levelIndex,
          loc,
          allegiance,
          display_class,
          Arena::new(),
        ));
    self.levels.get_mut(levelIndex).expect("wat").unit_by_location.insert(loc, unit_index);
    let unit = self.units.get_mut(unit_index).expect("no unit");
    unit.index = Some(unit_index);
    return unit_index;
  }
}


// same
pub fn getPatternAdjacentLocations(
  center: Location,
  considerCornersAdjacent: bool)
-> Vec<Location> {
  let mut result = Vec::new();
  result.push(Location::new(center.x - 1, center.y));
  result.push(Location::new(center.x, center.y + 1));
  result.push(Location::new(center.x, center.y - 1));
  result.push(Location::new(center.x + 1, center.y));
  if considerCornersAdjacent {
    result.push(Location::new(center.x - 1, center.y - 1));
    result.push(Location::new(center.x - 1, center.y + 1));
    result.push(Location::new(center.x + 1, center.y - 1));
    result.push(Location::new(center.x + 1, center.y + 1));
  }
  return result;
}

// same
pub fn getPatternLocationsAdjacentToAny(
  sourceLocs: &FxHashSet<Location>,
  includeSourceLocs: bool,
  considerCornersAdjacent: bool)
-> FxHashSet<Location> {
  let mut result = FxHashSet::default();
  for &originalLocation in sourceLocs {
    let mut adjacents =
        getPatternAdjacentLocations(originalLocation, considerCornersAdjacent);
    if includeSourceLocs {
      adjacents.push(originalLocation.clone());
    }
    for adjacentLocation in adjacents {
      if !includeSourceLocs && sourceLocs.contains(&adjacentLocation) {
        continue;
      }
      result.insert(adjacentLocation);
    }
  }
  return result;
}
