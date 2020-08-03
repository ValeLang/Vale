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
        let blasted_locs =
            level.get_locations_within_sight(self_unit.loc, false, BLAST_RANGE);

        // We can't modify the game from in here because we borrowed the game
        // immutably.
        // So, we return a lambda that will modify the game for us.
        return Box::new(move |rand, game| {
            for &blasted_loc in &blasted_locs {
                let level = game.get_current_level_mut();
                let num_turns_to_burn = rand.next() % 30;
                level.tiles.get_mut(&blasted_loc).expect("").components.insert(
                    Box::new(FireTileComponent::new(num_turns_to_burn)));
                level.register_acting_tile(blasted_loc);
            }
        });
    }
}
