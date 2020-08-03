use crate::game::*;
use crate::unit::*;

#[derive(new)]
pub struct SeekUnitCapability {}

impl IUnitComponent for SeekUnitCapability {
    fn as_capability(&self) -> Option<&dyn IUnitCapability> {
        return Some(self);
    }
    fn as_capability_mut(&mut self) -> Option<&mut dyn IUnitCapability> {
        return Some(self);
    }
}
impl IUnitCapability for SeekUnitCapability {
    fn get_desire(
        &self,
        _rand: &mut LCGRand,
        self_unit: &Unit,
        game: &Game,
    ) -> Box<dyn IUnitDesire> {
        // Pick a random enemy and head towards them.
        let mut maybe_nearest_enemy: Option<&Unit> = None;
        for (&this_loc, &unit_index) in &game.get_current_level().unit_by_location {
            if unit_index == self_unit.get_index() {
                continue;
            }
            let unit = &game.units[unit_index];
            if unit.allegiance == self_unit.allegiance {
                continue;
            }
            if let Some(nearest_enemy) = maybe_nearest_enemy {
                let dist_from_self_to_nearest_enemy =
                    self_unit.loc.diagonal_manhattan_distance_100(nearest_enemy.loc);
                let dist_from_self_to_this_loc =
                    self_unit.loc.diagonal_manhattan_distance_100(this_loc);
                if dist_from_self_to_nearest_enemy < dist_from_self_to_this_loc {
                    continue;
                }
            }
            maybe_nearest_enemy = Some(unit);
        }

        if let Some(enemy) = maybe_nearest_enemy {
            let maybe_path =
                game.get_current_level()
                    .find_path(self_unit.loc, enemy.loc, i32::MAX, true);
            if let Some((new_path, _)) = maybe_path {
                if game.get_current_level().loc_is_walkable(new_path[0], true) {
                    return Box::new(MoveUnitDesire::new(desire::WANT, new_path[0]));
                }
                return Box::new(DoNothingUnitDesire::new());
            } else {
                panic!("No path!");
            }
        } else {
            // No enemies left, just relax!
            return Box::new(DoNothingUnitDesire::new());
        }
    }
}
