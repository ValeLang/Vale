
use std::fmt;

#[derive(new, Copy, Clone, Hash, Eq, PartialEq, Debug)]
pub struct Location {
    pub x: i32,
    pub y: i32,
}
impl Location {
    pub fn dist_squared(&self, other: Location) -> i32 {
        return (self.x - other.x) * (self.x - other.x) + (self.y - other.y) * (self.y - other.y);
    }
    pub fn next_to(
        &self,
        other: Location,
        consider_corners_adjacent: bool,
        include_self: bool,
    ) -> bool {
        let dist_squared = self.dist_squared(other);
        let min_squared_distance = if include_self { 0 } else { 1 };
        let max_squared_distance = if consider_corners_adjacent { 2 } else { 1 };
        return dist_squared >= min_squared_distance && dist_squared <= max_squared_distance;
    }

    // This is different than the normal manhattan distance.
    // Normal manhattan distance will give you the difference in x plus the difference
    // in y.
    // This allows us to go diagonal as well.
    //
    // Normal manhattan distance     Diagonal manhattan distance
    //
    //     ..................            ..................
    //     ...............g..            ...............g..
    //     ...............|..            ............../...
    //     ...............|..            ............./....
    //     ...............|..            ............/.....
    //     .@--------------..            .@----------......
    //     ..................            ..................
    //
    // The 100 means times 100, for better precision.
    //
    pub fn diagonal_manhattan_distance_100(&self, other: Location) -> i32 {
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
