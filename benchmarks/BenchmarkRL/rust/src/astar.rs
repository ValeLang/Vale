use std::cmp::Ordering;
use std::collections::BinaryHeap;

use rustc_hash::FxHashMap;
use rustc_hash::FxHashSet;

use crate::location::*;

// Stands for Location, G, F, and Came from, it's the basic unit of data
// used in A* pathfinding. We shuffle these around in interesting ways
// and sort them by their f_score to figure out what the best path is.
#[derive(new, Clone, Copy, Eq, PartialEq, Debug)]
struct LGFC {
    // The location.
    pub location: Location,

    // The distance we've already walked to get to this location, when
    // coming from `came_from`.
    pub g_score: i32,

    // The distance we've already walked to get to this location, when
    // coming from `came_from`, PLUS the estimated distance to the goal.
    pub f_score: i32,

    // The adjacent location that we came from, that gave us such a nice
    // f_score.
    pub came_from: Location,
}
impl Ord for LGFC {
    fn cmp(&self, other: &Self) -> Ordering {
        self.f_score.cmp(&other.f_score).reverse()
    }
}
impl PartialOrd for LGFC {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.f_score.cmp(&other.f_score).reverse())
    }
}

// Find a path from start_location to target_location.
// Won't consider any locations further than `max_distance`.
// Returns None if we couldn't find a path, or a tuple containing
// the path and the total distance traveled on it.
pub fn a_star(
    start_location: Location,
    target_location: Location,
    max_distance: i32,
    get_distance: &dyn Fn(Location, Location) -> i32,
    get_adjacent_locations: &dyn Fn(Location) -> Vec<Location>,
) -> Option<(Vec<Location>, i32)> {
    assert!(start_location != target_location);
    if get_distance(start_location, target_location) > max_distance {
        return None;
    }

    if get_adjacent_locations(start_location).contains(&target_location) {
        let distance = get_distance(start_location, target_location);
        if distance <= max_distance {
            return Some((vec![start_location, target_location], distance));
        } else {
            return None;
        }
    }

    // The set of nodes already evaluated
    let mut closed_locations = FxHashSet::default();

    // For each node:
    // - came_from: which node it can most efficiently be reached from.
    //   If a node can be reached from many nodes, came_from will eventually contain the
    //   most efficient previous step.
    // - g: the cost of getting from the start node to that node.
    // - f: the total cost of getting from the start node to the goal
    //   by passing by that node. That value is partly known, partly heuristic.
    let mut lcfg_by_location = FxHashMap::default();

    // The set of currently discovered nodes that are not evaluated yet.
    // Initially, only the start node is known.
    let mut open_locations_lowest_f_first = BinaryHeap::new();
    let mut open_locations = FxHashSet::default();
    // The above two collections must always have the same lcfg's.

    let start_lgfc =
        LGFC::new(
            start_location,
            get_distance(start_location, target_location),
            0,
            start_location.clone());
    lcfg_by_location.insert(start_location, start_lgfc);
    open_locations_lowest_f_first.push(start_lgfc);
    open_locations.insert(start_location);

    while open_locations_lowest_f_first.len() > 0 {
        let this_lgfc = open_locations_lowest_f_first.pop().expect("");
        let this_location = this_lgfc.location;

        if this_lgfc != lcfg_by_location[&this_location] {
            // We put things into open_locations_lowest_f_first whenever we discover them,
            // and also whenever we discover a better f-score for them.
            // When we insert better ones, the old ones are still in the heap somewhere,
            // out of date.
            // Here, we're checking if the thing we just removed (this_lgfc)
            // is actually up to date. If so, discard it and wait for the
            // up to date one to come in.
            continue;
        }

        if this_location == target_location {
            // In this block we're looping backwards from the target to the start,
            // so "previous" means closer to the target, and "next" means closer
            // to the start.
            let mut path = Vec::new();
            let mut distance = 0;
            let mut current_location = target_location;
            while lcfg_by_location[&current_location].came_from != current_location {
                path.push(current_location);
                let prev_location = current_location;
                current_location = lcfg_by_location[&current_location].came_from;
                distance += get_distance(current_location, prev_location);
            }
            path.reverse();
            assert!(path[0] != start_location);
            assert!(path[path.len() - 1] == target_location);
            return Some((path, distance));
        }

        closed_locations.insert(this_location);

        let neighbors = get_adjacent_locations(this_location);
        for neighbor_loc in neighbors {
            let tentative_g_score =
                lcfg_by_location[&this_location].g_score
                + get_distance(this_location, neighbor_loc);
            let tentative_f_score =
                tentative_g_score + get_distance(neighbor_loc, target_location);

            if tentative_f_score > max_distance {
                continue;
            }
            if closed_locations.contains(&neighbor_loc) {
                continue;
            }

            if open_locations.contains(&neighbor_loc)
                && tentative_g_score >= lcfg_by_location[&neighbor_loc].g_score
            {
                continue;
            }

            // Inserts, and replaces anything already there.
            // This could effectively change a node's f-score.
            lcfg_by_location.insert(
                neighbor_loc,
                LGFC::new(
                    neighbor_loc,
                    tentative_g_score,
                    tentative_f_score,
                    this_location,
                ),
            );
            open_locations_lowest_f_first.push(lcfg_by_location[&neighbor_loc]);
            open_locations.insert(neighbor_loc);
        }
    }
    // There was no path.
    return None;
}
