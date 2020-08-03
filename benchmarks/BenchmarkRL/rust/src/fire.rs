
use crate::location::*;
use crate::game::*;
use crate::tile::*;

// A tile component that damages any unit that's standing on it.
#[derive(new)]
pub struct FireTileComponent {
    pub num_turns_remaining: u32,
}
impl ITileComponent for FireTileComponent {
    fn on_turn(
        &self,
        _rand: &mut LCGRand,
        game: &Game,
        self_tile_loc: Location,
        self_tile_component_index: generational_arena::Index,
    ) -> Box<dyn Fn(&mut LCGRand, &mut Game)> {

        let maybe_unit_index =
            game.get_current_level().unit_by_location.get(&self_tile_loc).map(|&x| x);

        if self.num_turns_remaining == 0 {
            // Remove the fire tile component, and unregister the tile as an acting tile.

            // We can't modify game from in here because we're immutably borrowing it,
            // so we return a lambda that will do the modification for us.
            return Box::new(move |_rand, inner_game| {
                let tile = inner_game.get_current_level_mut().tiles.get_mut(&self_tile_loc).expect("");
                tile.components.remove(self_tile_component_index);
                // register/unregister use an int under the hood, so this won't unregister
                // if there are other FireTileComponents on this space.
                inner_game.get_current_level_mut().unregister_acting_tile(self_tile_loc);
            });
        }

        // Now we decrement the num_turns_remaining, and damage any unit standing on us.

        // We can't modify game from in here because we're immutably borrowing it,
        // so we return a lambda that will do the modification for us.
        return Box::new(move |rand, inner_game| {
            // Let's reduce our num_turns_remaining by 1, and damage whatever unit is on here.
            let tile = inner_game.get_current_level_mut().tiles.get_mut(&self_tile_loc).expect("");
            let icomponent = tile.components.get_mut(self_tile_component_index).expect("wat");
            let component = &mut *icomponent.downcast_mut::<FireTileComponent>().expect("");
            component.num_turns_remaining = component.num_turns_remaining - 1;

            if let Some(unit_index) = maybe_unit_index {
                inner_game.damage_unit(rand, unit_index, 3);
            }
        });
    }
}
