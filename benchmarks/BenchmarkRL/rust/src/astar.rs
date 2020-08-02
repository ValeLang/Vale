#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(unused_imports)]

use std::collections::BinaryHeap;
use std::cmp::Ordering;
use std::cmp::Reverse;
use rustc_hash::FxHashMap;
use rustc_hash::FxHashSet;

use crate::model;



// struct LowerFScoreComparer {//: IComparer<Location> {
//   fGAndCameFromByLocation: SortedDictionary<Location, GFAndCameFrom>,

//   public LowerFScoreComparer(SortedDictionary<Location, GFAndCameFrom> fGAndCameFromByLocation) {
//     this.fGAndCameFromByLocation = fGAndCameFromByLocation;
//   }
//   public int Compare(Location a, Location b) {
//     float aF = float.PositiveInfinity;
//     if fGAndCameFromByLocation.ContainsKey(a) {
//       aF = fGAndCameFromByLocation[a].fScore;
//     }
//     float bF = float.PositiveInfinity;
//     if fGAndCameFromByLocation.ContainsKey(b) {
//       bF = fGAndCameFromByLocation[b].fScore;
//     }
//     if aF != bF {
//       return Math.Sign(aF - bF);
//     }
//     return a.CompareTo(b);
//   }
// }

// Stands for Location, G, F, and Came from
#[derive(new, Clone, Copy, Eq, PartialEq, Debug)]
struct LGFC {
  pub location: model::Location,
  pub gScore: i32,
  pub fScore: i32,
  pub cameFrom: model::Location,
}
impl Ord for LGFC {
  fn cmp(&self, other: &Self) -> Ordering {
    self.fScore.cmp(&other.fScore).reverse()
  }
}
impl PartialOrd for LGFC {
  fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
    Some(self.fScore.cmp(&other.fScore).reverse())
  }
}

//   This is used to prioritize which ones to explore first.
//   Numbers that are higher are explored with more interest.
//   For example, ApatheticPatternExplorerPrioritizer treats all locations
//   equally and therefore just spreads out in a breadth-first search (which
//   is almost a circle but not exactly).
//   To get an exact circle, you'd use LessDistanceFromPrioritizer which
//   returns the negative distance from the start. It's negative because
//   higher numbers are explored first.
//   To make a square, make one that returns the difference between the X
//   distance and Y distance from the start.

pub fn aStar(
  startLocation: model::Location,
  targetLocation: model::Location,
  maxDistance: i32,
  getDistance: &dyn Fn(model::Location, model::Location) -> i32,
  getAdjacentLocations: &dyn Fn(model::Location) -> Vec<model::Location>)
-> Option<(Vec<model::Location>, i32)> {
  assert!(startLocation != targetLocation);
  // println!("                 Starting aStar from {} to {}, (dist {}) max dist {}", startLocation, targetLocation, getDistance(startLocation, targetLocation), maxDistance);
  if getDistance(startLocation, targetLocation) > maxDistance {
    // println!("                 Target is too far, give up! {} > {}", getDistance(startLocation, targetLocation), maxDistance);
    return None;
  }

  if getAdjacentLocations(startLocation).contains(&targetLocation) {
    let distance = getDistance(startLocation, targetLocation);
    if distance <= maxDistance {
      // println!("                 It's 1 step away, returning path!");
      return Some((vec![startLocation, targetLocation], distance));
    } else {
      // println!("                 It's 1 step away but still too far, returning None!");
      return None;
    }
  }

  // The set of nodes already evaluated
  let mut closedLocations = FxHashSet::default();
  // For each node:
  // - cameFrom: which node it can most efficiently be reached from.
  //   If a node can be reached from many nodes, cameFrom will eventually contain the
  //   most efficient previous step.
  // - g: the cost of getting from the start node to that node.
  // - f: the total cost of getting from the start node to the goal
  //   by passing by that node. That value is partly known, partly heuristic.
  let mut lcfgByLocation = FxHashMap::default();//<Location, GFAndCameFrom>();
  // The set of currently discovered nodes that are not evaluated yet.
  // Initially, only the start node is known.
  let mut openLocationsLowestFFirst = BinaryHeap::new();
  let mut openLocations = FxHashSet::default();

  let startLGFC =
      LGFC::new(
          startLocation,
          getDistance(startLocation, targetLocation),
          0,
          startLocation.clone());
  lcfgByLocation.insert(startLocation, startLGFC);
  openLocationsLowestFFirst.push(startLGFC);
  openLocations.insert(startLocation);

  let mut iterations = 0;
  while openLocationsLowestFFirst.len() > 0 {
    iterations = iterations + 1;
    //UnityEngine.Debug.Log("remaining in open: " + openLocationsLowestFFirst.Count);
    let thisLGFC = openLocationsLowestFFirst.pop().expect("wat");
    let thisLocation = thisLGFC.location;
    // println!("                 Size {}, considering {}", openLocationsLowestFFirst.len(), thisLocation);

    if thisLGFC != lcfgByLocation[&thisLocation] {
      // We put things into openLocationsLowestFFirst whenever we discover them,
      // and also whenever we discover a better f-score for them.
      // When we insert better ones, the old ones are still in the heap somewhere,
      // out of date.
      // Here, we're checking if the thing we just removed (thisLGFC)
      // is actually up to date. If so, discard it and wait for the
      // up to date one to come in.
      continue;
    }

    if thisLocation == targetLocation {
      // In this block we're looping backwards from the target to the start,
      // so "previous" means closer to the target, and "next" means closer
      // to the start.
      let mut path = Vec::new();
      let mut distance = 0;
      let mut currentLocation = targetLocation;
      while lcfgByLocation[&currentLocation].cameFrom != currentLocation {
        path.push(currentLocation);
        let prevLocation = currentLocation;
        currentLocation = lcfgByLocation[&currentLocation].cameFrom;
        distance += getDistance(currentLocation, prevLocation);
      }
      path.reverse();
      assert!(path[0] != startLocation);
      assert!(path[path.len() - 1] == targetLocation);
      // println!("                 Found a path! Next step is {}", path[0]);
      return Some((path, distance));
    }

    //if (!removed) {
    //  throw new Exception("wtf");
    //}
    closedLocations.insert(thisLocation);

    let neighbors = getAdjacentLocations(thisLocation);
    for neighborLocation in neighbors {
      let tentativeGScore =
          lcfgByLocation[&thisLocation].gScore +
          getDistance(thisLocation, neighborLocation);
      let tentativeFScore =
          tentativeGScore + getDistance(neighborLocation, targetLocation);

      if tentativeFScore > maxDistance {
        // println!("                 Neighbor {} too far: {}!", neighborLocation, tentativeFScore);
        continue;
      }
      if closedLocations.contains(&neighborLocation) {
        // println!("                 Neighbor {} already closed!", neighborLocation);
        continue;
      }

      if openLocations.contains(&neighborLocation) &&
          tentativeGScore >= lcfgByLocation[&neighborLocation].gScore {
        // println!("                 Neighbor {} already had a better score", neighborLocation);
        continue;
      }

      // Inserts, and replaces anything already there.
      // This could effectively change a node's f-score.
      lcfgByLocation.insert(
        neighborLocation,
        LGFC::new(
          neighborLocation,
          tentativeGScore,
          tentativeFScore,
          thisLocation));
      openLocationsLowestFFirst.push(lcfgByLocation[&neighborLocation]);
      openLocations.insert(neighborLocation);
      // println!("                 Neighbor {} updated/inserted, tentative f {} and g {}!", neighborLocation, tentativeFScore, tentativeGScore);
    }
  }
  // There was no path.
  // println!("                 No path, after {} iterations!", iterations);
  return None;
}
