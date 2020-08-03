use downcast_rs::Downcast;

use generational_arena;
use generational_arena::Arena;

use crate::game::*;
use crate::location::*;

pub trait ITileComponent: Downcast {
    // Called on a component every turn. It must be registered with the level
    // as an acting tile for it to actually be called though.
    // Returns a lambda with which it can modify the game.
    fn on_turn(
        &self,
        _rand: &mut LCGRand,
        _game: &Game,
        _self_tile_loc: Location,
        _self_tile_component_index: generational_arena::Index,
    ) -> Box<dyn Fn(&mut LCGRand, &mut Game)> {
        return Box::new(|_rand, _game| {});
    }
}
// We don't use an enum for tile components for the same reason as IUnitComponent.
impl_downcast!(ITileComponent);


#[derive(new)]
pub struct Tile {
    pub walkable: bool,

    // A string that the UI can recognize so it knows what to display. This should
    // ONLY be read by the UI, and not by any special logic. Any special logic
    // should be specified in components on the tile (which we don't have yet).
    pub display_class: String,

    pub components: Arena<Box<dyn ITileComponent>>,
}
impl Tile {
    // Gets the component of the given class.
    pub fn get_first_component<T: ITileComponent>(&self) -> Option<&T> {
        for (_, c_boxed) in &self.components {
            if let Some(w) = c_boxed.downcast_ref::<T>() {
                return Some(w);
            }
        }
        return None;
    }
    // // Gets the component of the given class.
    // pub fn get_first_component_mut<T: ITileComponent>(&mut self) -> Option<&mut T> {
    //     for (_, c_boxed) in &mut self.components {
    //         if let Some(w) = c_boxed.downcast_mut::<T>() {
    //             return Some(w);
    //         }
    //     }
    //     return None;
    // }
}