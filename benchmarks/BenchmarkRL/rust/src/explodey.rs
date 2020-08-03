use crate::game::*;
use crate::unit::*;
use crate::fire::*;

const BLAST_RANGE: i32 = 400;

// A unit component that makes a unit explode on death and leave
// a bunch of flames on the ground.
#[derive(new)]
pub struct ExplodeyUnitComponent { }

impl IUnitComponent for ExplodeyUnitComponent {
    fn on_unit_death(
        &self,
        _rand: &mut LCGRand,
        game: &Game,
        self_unit_index: generational_arena::Index,
        _self_unit_capability_index: generational_arena::Index,
        _attacker: generational_arena::Index,
    ) -> Box<dyn Fn(&mut LCGRand, &mut Game)> {
        // Look for all the enemies around us.

        let level = game.get_current_level();
        let self_unit = &game.units[self_unit_index];
        // The visible area is a good approximation of where a blast
        // would go.
        let blasted_locs = level.get_locations_within_sight(self_unit.loc, false, BLAST_RANGE);
        let mut walkable_blasted_locs = Vec::new();
        for blasted_loc in blasted_locs {
            if level.loc_is_walkable(blasted_loc, false) {
                walkable_blasted_locs.push(blasted_loc);
            }
        }
        let center_loc = self_unit.loc;

        // We can't modify the game from in here because we borrowed the game
        // immutably.
        // So, we return a lambda that will modify the game for us.
        return Box::new(move |rand, game| {
            for &blasted_loc in &walkable_blasted_locs {
                // Lets make it burn longer if it was close.
                let distance_from_center_100 = center_loc.diagonal_manhattan_distance_100(blasted_loc);
                // 0 means on the edge, 100 means at center.
                let closeness_to_center_100 = 100 - distance_from_center_100 * 100 / BLAST_RANGE;
                let num_turns_to_burn = closeness_to_center_100 as u32 / 10 + rand.next() % 10;

                let level = game.get_current_level_mut();
                level.tiles.get_mut(&blasted_loc).expect("").components.insert(
                    Box::new(FireTileComponent::new(num_turns_to_burn)));
                level.register_acting_tile(blasted_loc);
            }
        });
    }
}
