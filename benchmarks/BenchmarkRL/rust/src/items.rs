use generational_arena;

use crate::game::*;
use crate::unit::*;

#[derive(new)]
pub struct IncendiumShortSword {}
impl IUnitComponent for IncendiumShortSword {
    fn modify_outgoing_attack(
        &self,
        _game: &Game,
        _self_unit: generational_arena::Index,
    ) -> AttackModifier {
        return AttackModifier::new(700, 100, 200);
    }
}

#[derive(new)]
pub struct GoblinClaws {}
impl IUnitComponent for GoblinClaws {
    fn modify_outgoing_attack(
        &self,
        _game: &Game,
        _self_unit: generational_arena::Index,
    ) -> AttackModifier {
        return AttackModifier::new(150, 100, 0);
    }
}
