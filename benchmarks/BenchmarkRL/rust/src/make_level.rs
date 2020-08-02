#![allow(dead_code)]
#![allow(non_snake_case)]

use std::iter::FromIterator;


use rustc_hash::FxHashMap;
use rustc_hash::FxHashSet;


extern crate generational_arena;
use std::cmp;

use crate::model;


// same
fn getAdjacentLocations(
  width: i32,
  height: i32,
  center: model::Location,
  considerCornersAdjacent: bool)
-> Vec<model::Location> {
  let mut result = Vec::new();
  for adjacent in model::getPatternAdjacentLocations(center, considerCornersAdjacent) {
    if adjacent.x >= 0 && adjacent.y >= 0 && adjacent.x < width && adjacent.y < height {
      result.push(adjacent)
    }
  }
  return result;
}

// same
fn getAdjacentWalkableLocations(
  walkabilities: &Vec<Vec<bool>>,
  center: model::Location,
  considerCornersAdjacent: bool)
-> Vec<model::Location> {
  let mut result = Vec::new();
  let adjacents = 
      getAdjacentLocations(
          walkabilities.len() as i32,
          walkabilities[0].len() as i32,
          center,
          considerCornersAdjacent);
  for neighbor in adjacents {
    if walkabilities[neighbor.x as usize][neighbor.y as usize] {
      result.push(neighbor);
    }
  }
  return result;
}


/*
pub fn make_level<'world, 'gen>(
    rand: &!LCGRand,
    'gen Void) 'gen {
*/

pub fn make_level(
  max_width: i32,
  max_height: i32,
  mut rand: &mut model::LCGRand)
-> model::Level {
  let mut level =
//    'world Level(
      model::Level {
          max_width: max_width as i32,
          max_height: max_height as i32,
          terrain: model::Terrain { tiles: FxHashMap::default() },
          unit_by_location: FxHashMap::default() };

  let mut walkabilities = Vec::with_capacity(max_width as usize);
  for x in 0..max_width {
    walkabilities.push(Vec::with_capacity(max_height as usize));
    for y in 0..max_height {
      let half_max_width = max_width / 2;
      let half_max_height = max_height / 2;
      let inside_circle =
          (x - half_max_width) * (x - half_max_width) * (half_max_height * half_max_height)
          + (y - half_max_height) * (y - half_max_height) * (half_max_width * half_max_width)
          < (half_max_width * half_max_width) * (half_max_height * half_max_height);
      let walkable = rand.next() % 2 == 0;
      walkabilities[x as usize].push(inside_circle && walkable);
    }
  }

  smoothLevel(max_width, max_height, &mut walkabilities);
  smoothLevel(max_width, max_height, &mut walkabilities);

  connectAllRooms(&mut rand, &mut walkabilities, false);

  for x in 0..max_width {
    for y in 0..max_height {
      let loc = model::Location::new(x as i32, y as i32 );
      let walkable = walkabilities[x as usize][y as usize];
      let on_edge = x == 0 || y == 0 || x == max_width - 1 || y == max_height - 1;
      if walkable && !on_edge {
        let display_class = if rand.next() % 2 == 0 { "dirt" } else { "grass" };
        level.terrain.tiles.insert(
            loc, model::Tile{ walkable: true, display_class: display_class.to_string() });
      } else {
        let mut next_to_walkable = false;
        for neighbor_x in cmp::max(0, x - 1)..cmp::min(max_width - 1, x + 1 + 1) {
          for neighbor_y in cmp::max(0, y - 1)..cmp::min(max_height - 1, y + 1 + 1) {
            let neighbor_walkable = walkabilities[neighbor_x as usize][neighbor_y as usize];
            if neighbor_walkable {
              next_to_walkable = true;
            }
          }
        }
        if next_to_walkable {
          level.terrain.tiles.insert(loc, model::Tile{ walkable: false, display_class: "wall".to_string() });
        }
      }
    }
  }

  return level;
}

fn smoothLevel(
    max_width: i32,
    max_height: i32,
    walkabilities: &mut Vec<Vec<bool>>) {
  let mut new_walkabilities = Vec::with_capacity(max_width as usize);
  for x in 0..max_width {
    new_walkabilities.push(Vec::with_capacity(max_height as usize));
    for _y in 0..max_height {
      new_walkabilities[x as usize].push(false);
    }
  }

  for x in 0..max_width {
    for y in 0..max_height {
      let mut num_walkable_neighbors = 0;

      for neighbor_x in cmp::max(0, x - 1)..cmp::min(max_width - 1, x + 1 + 1) {
        for neighbor_y in cmp::max(0, y - 1)..cmp::min(max_height - 1, y + 1 + 1) {
          if walkabilities[neighbor_x as usize][neighbor_y as usize] {
            num_walkable_neighbors = num_walkable_neighbors + 1;
          }
        }
      }

      new_walkabilities[x as usize][y as usize] = num_walkable_neighbors >= 5;
    }
  }

  // swap method here?
  *walkabilities = new_walkabilities;
}

fn connectAllRooms(
    mut rand: &mut model::LCGRand,
    mut walkabilities: &mut Vec<Vec<bool>>,
    considerCornersAdjacent: bool) {
  let mut rooms = identifyRooms(&mut walkabilities, considerCornersAdjacent);
  connectRooms(&mut rand, &mut rooms);
  for i in 0..rooms.len() {
    let newRoom = &rooms[i];
    for loc in newRoom {
      walkabilities[loc.x as usize][loc.y as usize] = true;
    }
  }
}

// fn void addTile(SSContext context, Terrain terrain, model::Location location, int elevation) {
//   let tile =
//     terrain.root.EffectTerrainTileCreate(
//           NullITerrainTileEvent.Null,
//           elevation,
//           ITerrainTileComponentMutBunch.New(terrain.root));
//   terrain.tiles.insert(location, tile);
// }


pub fn identifyRooms(
    walkabilities: &mut Vec<Vec<bool>>,
    considerCornersAdjacent: bool)
-> Vec<FxHashSet<model::Location>> {
  let mut roomIndexByLocation = FxHashMap::default();//<model::Location, int>();
  let mut rooms = Vec::new();//<FxHashSet<model::Location>>();

  for x in 0..walkabilities.len() {
    for y in 0..walkabilities[x].len() {
      if walkabilities[x][y] {
        let sparkLocation = model::Location::new(x as i32, y as i32);
        if roomIndexByLocation.contains_key(&sparkLocation) {
          continue;
        }
        let connectedLocations =
            findAllConnectedLocations(
                &walkabilities, considerCornersAdjacent, sparkLocation);
        let newRoomIndex = rooms.len();
        rooms.push(connectedLocations.clone());
        for connectedLocation in connectedLocations {
          assert!(!roomIndexByLocation.contains_key(&connectedLocation));
          roomIndexByLocation.insert(connectedLocation, newRoomIndex);
        }
      }
    }
  }
  return rooms;
}

pub fn findAllConnectedLocations(
  walkabilities: &Vec<Vec<bool>>,
  considerCornersAdjacent: bool,
  startLocation: model::Location)
-> FxHashSet<model::Location> {
  let mut connectedWithUnexploredNeighbors = FxHashSet::default();//new FxHashSet<model::Location>();
  let mut connectedWithExploredNeighbors = FxHashSet::<model::Location>::default();//new FxHashSet<model::Location>();

  connectedWithUnexploredNeighbors.insert(startLocation);

  while connectedWithUnexploredNeighbors.len() > 0 {
    let current = connectedWithUnexploredNeighbors.iter().nth(0).expect("wat").clone();
    assert!(!connectedWithExploredNeighbors.contains(&current));

    connectedWithUnexploredNeighbors.remove(&current);
    connectedWithExploredNeighbors.insert(current);

    for neighbor in getAdjacentWalkableLocations(walkabilities, current, considerCornersAdjacent) {
      if connectedWithExploredNeighbors.contains(&neighbor) {
        continue;
      }
      if connectedWithUnexploredNeighbors.contains(&neighbor) {
        continue;
      }
      connectedWithUnexploredNeighbors.insert(neighbor);
    }
  }

  return connectedWithExploredNeighbors;
}

fn getVecRandomNth<'a, T>(rand: &mut model::LCGRand, vec: &'a Vec<T>) -> Option<&'a T> {
  return vec.iter().nth((rand.next() as usize) % vec.len());
}
fn getFxHashSetRandomNth<'a, T>(rand: &mut model::LCGRand, set: &'a FxHashSet<T>) -> Option<&'a T> {
  return set.iter().nth((rand.next() as usize) % set.len());
}

pub fn connectRooms(
  mut rand: &mut model::LCGRand,
  rooms: &mut Vec<FxHashSet<model::Location>>) {
  // This function will be adding the corridors to roomByNumber.

  let mut roomIndexByLocation = FxHashMap::<model::Location, usize>::default();

  for roomIndex in 0..rooms.len() {
    let room = &rooms[roomIndex];
    for roomFloorLoc in room {
      roomIndexByLocation.insert(*roomFloorLoc, roomIndex);
    }
  }

  let mut regions = FxHashSet::default();

  let mut regionByRoomIndex = FxHashMap::default();
  let mut roomIndicesByRegion = FxHashMap::default();//new SortedDictionary<String, SortedSet<int>>();

  for roomIndex in 0..rooms.len() {
    // let room = rooms[roomIndex];
    let region = roomIndex;
    regionByRoomIndex.insert(roomIndex, region);
    let mut roomIndicesInRegion = FxHashSet::default();
    roomIndicesInRegion.insert(roomIndex);
    roomIndicesByRegion.insert(region, roomIndicesInRegion);
    regions.insert(region);
    //Logger.Info("Made region " + region);
  }

  loop {
    let distinctRegions = FxHashSet::<usize>::from_iter(regionByRoomIndex.values().cloned());
    //Logger.Info(distinctRegions.Count + " distinct regions!");
    if distinctRegions.len() < 2 {
      break;
    }
    let mut twoRegionsIter = distinctRegions.iter();// SetUtils.GetFirstN(distinctRegions, 2);
    let regionA = *twoRegionsIter.next().expect("wat");// twoRegions[0];
    let regionB = *twoRegionsIter.next().expect("wat");
    //Logger.Info("Will aim to connect regions " + regionA + " and " + regionB);

    let regionARoomIndex = *getFxHashSetRandomNth(&mut rand, &roomIndicesByRegion[&regionA]).expect("wat");
    let regionARoom = &rooms[regionARoomIndex];
    let regionALocation = *getFxHashSetRandomNth(&mut rand, &regionARoom).expect("wat");

    let regionBRoomIndex = *getFxHashSetRandomNth(&mut rand, &roomIndicesByRegion[&regionB]).expect("wat");
    let regionBRoom = &rooms[regionBRoomIndex];
    let regionBLocation = *getFxHashSetRandomNth(&mut rand, &regionBRoom).expect("wat");

    // Now lets drive from regionALocation to regionBLocation, and see what happens on the
    // way there.
    let mut path = Vec::new();
    let mut currentLocation = regionALocation;
    while currentLocation != regionBLocation {
      if currentLocation.x != regionBLocation.x {
        currentLocation.x += (regionBLocation.x - currentLocation.x).signum();
      } else if currentLocation.y != regionBLocation.y {
        currentLocation.y += (regionBLocation.y - currentLocation.y).signum();
      } else {
        panic!("wat")
      }
      if !roomIndexByLocation.contains_key(&currentLocation) {
        // It means we're in open space, keep going.
        path.push(currentLocation);
      } else {
        let currentRoomIndex = roomIndexByLocation[&currentLocation];
        let currentRegion = regionByRoomIndex[&currentRoomIndex];
        if currentRegion == regionA {
          // Keep going, but restart the path here.
          path = Vec::new();
        } else if currentRegion != regionA {
          // currentRegionNumber is probably regionBNumber, but isn't necessarily... we could
          // have just come across a random other region.
          // Either way, we hit something, so we stop now.
          break;
        }
      }
    }

    let combinedRegion = regions.len();
    regions.insert(combinedRegion);

    let newRoomIndex = rooms.len();
    rooms.push(FxHashSet::from_iter(path.iter().cloned()));
    for pathLocation in &path {
      roomIndexByLocation.insert(*pathLocation, newRoomIndex);
    }
    regionByRoomIndex.insert(newRoomIndex, combinedRegion);
    // We'll fill in regionNumberByRoomIndex and roomIndicesByRegionNumber shortly.

    // So, now we have a path that we know connects some regions. However, it might be
    // accidentally connecting more than two! It could have grazed past another region without
    // us realizing it.
    // So now, figure out all the regions that this path touches.

    let pathAdjacentLocations = model::getPatternLocationsAdjacentToAny(&FxHashSet::from_iter(path.iter().cloned()), true, false);
    let mut pathAdjacentRegions = FxHashSet::default();
    for pathAdjacentLocation in pathAdjacentLocations {
      if roomIndexByLocation.contains_key(&pathAdjacentLocation) {
        let roomIndex = roomIndexByLocation[&pathAdjacentLocation];
        let region = regionByRoomIndex[&roomIndex];
        pathAdjacentRegions.insert(region);
      }
    }

    let mut roomIndicesInCombinedRegion = FxHashSet::default();
    roomIndicesInCombinedRegion.insert(newRoomIndex);
    for pathAdjacentRegion in pathAdjacentRegions {
      if pathAdjacentRegion == combinedRegion {
        // The new room is already part of this region
        continue;
      }
      for pathAdjacentRoomIndex in &roomIndicesByRegion[&pathAdjacentRegion] {
        //Logger.Info("Overwriting " + pathAdjacentRoomIndex + "'s region to " + combinedRegion);
        regionByRoomIndex.insert(*pathAdjacentRoomIndex, combinedRegion);
        roomIndicesInCombinedRegion.insert(*pathAdjacentRoomIndex);
      }
      roomIndicesByRegion.remove(&pathAdjacentRegion);
    }
    roomIndicesByRegion.insert(combinedRegion, roomIndicesInCombinedRegion.clone());

    // String roomNums = "";
    // foreach (let pathAdjacentRoomIndex in roomIndicesInCombinedRegion) {
    //   if roomNums != "" {
    //     roomNums = roomNums + ", ";
    //   }
    //   roomNums = roomNums + pathAdjacentRoomIndex;
    // }
    //Logger.Info("Region " + combinedRegion + " now has room numbers: " + roomNums);
    roomIndicesByRegion.insert(combinedRegion, roomIndicesInCombinedRegion);
  }
}
