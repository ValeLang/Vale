use rustc_hash::FxHashMap;
use rustc_hash::FxHashSet;

use crate::location::*;
use crate::tile::*;
use crate::astar::*;
use crate::game::*;
use crate::unit::*;

#[derive(new)]
pub struct Level {
    pub max_width: i32,
    pub max_height: i32,
    pub tiles: FxHashMap<Location, Tile>,

    // Tiles that want to act every turn.
    // The value is the number of registrations for that, because it might have
    // been registered for multiple reasons, and we want to keep it registered
    // until that number of reasons goes down to zero.
    pub acting_tile_locations: FxHashMap<Location, u32>,

    // An index of what units are at what locations, so a unit can easily
    // see whats around it.
    pub unit_by_location: FxHashMap<Location, generational_arena::Index>,
}

impl Level {
    pub fn find_random_walkable_unoccuped_location(self: &Level, rand: &mut LCGRand) -> Location {
        let walkable_locations = self.get_walkable_locations();
        let loc_index = rand.next() % (walkable_locations.len() as u32);
        return walkable_locations[loc_index as usize];
    }
    pub fn get_adjacent_locations(
        &self,
        center: Location,
        consider_corners_adjacent: bool,
    ) -> Vec<Location> {
        let mut result = Vec::new();
        for adjacent in get_pattern_adjacent_locations(center, consider_corners_adjacent) {
            if adjacent.x >= 0
                && adjacent.y >= 0
                && adjacent.x < self.max_width
                && adjacent.y < self.max_height
            {
                result.push(adjacent)
            }
        }
        return result;
    }

    // If `consider_units` is true, and a unit is on that location, this function
    // will return false, because it's considering units in determining walkability,
    // and that unit is in the way.
    // If `consider_units` is false, it ignores any unit that's there.
    pub fn loc_is_walkable(&self, loc: Location, consider_units: bool) -> bool {
        if !self.tiles[&loc].walkable {
            return false;
        }
        if consider_units {
            if self.unit_by_location.contains_key(&loc) {
                return false;
            }
        }
        return true;
    }

    pub fn get_adjacent_walkable_locations(
        &self,
        center: Location,
        consider_units: bool,
        consider_corners_adjacent: bool,
    ) -> Vec<Location> {
        let mut result = Vec::new();
        let adjacents = self.get_adjacent_locations(center, consider_corners_adjacent);
        for neighbor in adjacents {
            if self.loc_is_walkable(neighbor, consider_units) {
                result.push(neighbor);
            }
        }
        return result;
    }

    pub fn find_path(
        &self,
        from_loc: Location,
        to_loc: Location,
        max_distance: i32,
        consider_corners_adjacent: bool,
    ) -> Option<(Vec<Location>, i32)> {
        return a_star(
            from_loc,
            to_loc,
            max_distance,
            &|a, b| a.diagonal_manhattan_distance_100(b),
            &|loc| self.get_adjacent_walkable_locations(loc, false, consider_corners_adjacent),
        );
    }

    pub fn can_see(&self, from_loc: Location, to_loc: Location, sight_range: i32) -> bool {
        let straight_distance = from_loc.diagonal_manhattan_distance_100(to_loc);
        let maximum_distance = std::cmp::min(straight_distance, sight_range);

        // Here, were making it so a straight line's distance is the maximum distance we're
        // willing to search for a path. This should give us a straight line.
        // Well, "straight" isn't exactly right. We're looking for any line where we go
        // as straight-as-possible towards the enemy.
        // These are all valid "straight-as-possible" lines:
        //
        //      ..........--g..  ............g..  .....-------g..
        //      ........./.....  .........../...  ..../..........
        //      ......---......  ........../....  .../...........
        //      ...../.........  ........./.....  ../............
        //      .@---..........  .@------.......  .@.............
        //
        // We find that "as straight as possible" line using A* and a diagonal manhattan
        // distance, and it lets us see around corners in interesting ways, like so:
        //
        //           .........           ###   ....
        //         .............         ####  ......
        //       ................        ##### .......
        //       ..............g.        ######........
        //      ............#            ..............
        //      ............#            .......#
        //      .........@..#            .......##
        //      #############            ...@...###
        //
        // See Location::diagonal_manhattan_distance_100 for more on this weird diagonal
        // line stuff.
        //
        match self.find_path(from_loc, to_loc, maximum_distance, true) {
            None => return false,
            Some((_, path_length)) => {
                assert!(path_length <= maximum_distance); // curiosity assert
                return true;
            }
        }
    }

    // This gets all the locations within sight with a breadth-first search.
    // It follows the same logic as the above `can_see` function.
    pub fn get_locations_within_sight(
        &self,
        start_loc: Location,
        include_self: bool,
        sight_range: i32,
    ) -> FxHashSet<Location> {
        // This is the result set, which we'll eventually return.
        let mut visible_locs: FxHashSet<Location> = FxHashSet::default();
        // This is the set of locations we have yet to explore. This is our "to do"
        // list.
        // The keys are the locations we want to consider next, and the value is
        // how far we walked to get to there so far.
        let mut walked_distance_by_loc_to_consider: FxHashMap<Location, i32> = FxHashMap::default();

        // Adds the start location to our "to do" list.
        walked_distance_by_loc_to_consider.insert(start_loc, 0);

        while walked_distance_by_loc_to_consider.len() > 0 {
            // Pick an arbitrary location from the to do list to look at.
            let (&this_loc, &walked_distance_from_start_to_this) =
                walked_distance_by_loc_to_consider
                    .iter()
                    .next()
                    .expect("wat");
            walked_distance_by_loc_to_consider.remove(&this_loc);
            visible_locs.insert(this_loc);

            // It's not walkable, so it's a wall, so dont check its adjacents.
            if !self.loc_is_walkable(this_loc, false) {
                continue;
            }

            for adjacent_loc in self.get_adjacent_locations(this_loc, true) {
                // This is the ideal distance, if we were to go straight to the location.
                let direct_distance_from_start_to_adjacent =
                    start_loc.diagonal_manhattan_distance_100(adjacent_loc);
                if direct_distance_from_start_to_adjacent > sight_range {
                    // This is outside sight range even if there was a clear line of sight,
                    // skip it.
                    continue;
                }

                let distance_from_this_to_adjacent =
                    this_loc.diagonal_manhattan_distance_100(adjacent_loc);
                let walked_distance_from_start_to_adjacent =
                    walked_distance_from_start_to_this + distance_from_this_to_adjacent;

                // If these arent equal, then we went out of our way somehow, which means
                // we didnt go very straight, so skip it.
                if walked_distance_from_start_to_adjacent != direct_distance_from_start_to_adjacent
                {
                    continue;
                }

                // If we've already visible_locs it, or we already plan to explore it,
                // then don't add it.
                if visible_locs.contains(&adjacent_loc)
                    || walked_distance_by_loc_to_consider.contains_key(&adjacent_loc)
                {
                    continue;
                }

                // We dont check walkability here because we want to see the walls...
                // we just don't want to see past them, so we dont consider their
                // adjacents. That check is above.

                walked_distance_by_loc_to_consider
                    .insert(adjacent_loc, walked_distance_from_start_to_adjacent);
            }
        }

        if !include_self {
            visible_locs.remove(&start_loc);
        }

        return visible_locs;
    }

    pub fn forget_unit(&mut self, unit_index: generational_arena::Index, loc: Location) {
        assert!(self.unit_by_location.contains_key(&loc));
        assert!(self.unit_by_location[&loc] == unit_index);
        self.unit_by_location.remove(&loc);
    }

    pub fn get_walkable_locations(&self) -> Vec<Location> {
        let mut result = Vec::new();
        for (&loc, tile) in &self.tiles {
            if tile.walkable {
                result.push(loc);
            }
        }
        return result;
    }

    pub fn move_unit(&mut self, unit: &mut Unit, new_loc: Location) {
        assert!(!self.unit_by_location.contains_key(&new_loc));
        self.unit_by_location.remove(&unit.loc);
        unit.loc = new_loc;
        self.unit_by_location.insert(unit.loc, unit.get_index());
    }

    // Increment the registration count for the given tile.
    pub fn register_acting_tile(&mut self, loc: Location) {
        let previous_num_registrations =
            self.acting_tile_locations.get(&loc).map(|&x| x).or_else(|| Some(0)).expect("");
        let new_num_registrations = previous_num_registrations + 1;
        self.acting_tile_locations.insert(loc, new_num_registrations);
    }

    // Decrement the registration count for the given tile, and if it hits zero,
    // dont consider it an acting tile anymore.
    pub fn unregister_acting_tile(&mut self, loc: Location) {
        let previous_num_registrations =
            self.acting_tile_locations.get(&loc).expect("Expected registration!");
        let new_num_registrations = previous_num_registrations - 1;
        if new_num_registrations == 0 {
            self.acting_tile_locations.remove(&loc);
        } else {
            self.acting_tile_locations.insert(loc, new_num_registrations);
        }
    }
}
