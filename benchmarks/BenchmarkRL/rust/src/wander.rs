use crate::game::*;
use crate::unit::*;
use crate::location::*;

// A capability for a unit to randomly wander in some direction.
#[derive(new)]
pub struct WanderUnitCapability {}
impl IUnitComponent for WanderUnitCapability {
    fn as_capability(&self) -> Option<&dyn IUnitCapability> {
        return Some(self);
    }
    fn as_capability_mut(&mut self) -> Option<&mut dyn IUnitCapability> {
        return Some(self);
    }
}
impl IUnitCapability for WanderUnitCapability {
    fn get_desire(
        &self,
        rand: &mut LCGRand,
        self_unit: &Unit,
        game: &Game,
    ) -> Box<dyn IUnitDesire> {
        let level = game.get_current_level();
        let adjacent_locations = level.get_adjacent_locations(self_unit.loc, true);
        let walkable_adjacent_locations: Vec<Location> = adjacent_locations
            .into_iter()
            .filter(|&loc| level.loc_is_walkable(loc, true))
            .collect();
        if walkable_adjacent_locations.len() == 0 {
            return Box::new(DoNothingUnitDesire {});
        } else {
            return Box::new(MoveUnitDesire::new(
                desire::MEH,
                walkable_adjacent_locations
                    [(rand.next() % (walkable_adjacent_locations.len() as u32)) as usize],
            ));
        }
    }
}
