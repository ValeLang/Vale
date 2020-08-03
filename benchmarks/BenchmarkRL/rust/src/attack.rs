use crate::game::*;
use crate::unit::*;

// A unit capability which wants to attack anything that the unit is next to.
#[derive(new)]
pub struct AttackUnitCapability {}
impl IUnitComponent for AttackUnitCapability {
    fn as_capability(&self) -> Option<&dyn IUnitCapability> {
        return Some(self);
    }
    fn as_capability_mut(&mut self) -> Option<&mut dyn IUnitCapability> {
        return Some(self);
    }
}
impl IUnitCapability for AttackUnitCapability {
    fn get_desire(
        &self,
        _rand: &mut LCGRand,
        self_unit: &Unit,
        game: &Game,
    ) -> Box<dyn IUnitDesire> {
        // If there's an enemy right next to us, attack them!
        match self_unit.get_nearest_enemy_in_sight(&game, 150) {
            Some(enemy_unit) => {
                return Box::new(AttackUnitDesire::new(desire::NEED, enemy_unit.get_index()));
            }
            None => {} // proceed
        }

        // No enemy next to us. If we have a ChaseUnitCapability, maybe it will tell us to
        // go chase one. As for us, we dont really want anything.
        return Box::new(DoNothingUnitDesire::new());
    }
}

// A desire to attack a unit.
#[derive(new)]
pub struct AttackUnitDesire {
    strength: i32,
    target_unit_index: generational_arena::Index,
}
impl IUnitDesire for AttackUnitDesire {
    fn get_strength(&self) -> i32 {
        return self.strength;
    }
    fn enact(self: Box<Self>, rand: &mut LCGRand, self_unit_index: generational_arena::Index, game: &mut Game) {
        let mut attack_modifiers = Vec::with_capacity(game.units[self_unit_index].components.len());
        for (_, c) in &game.units[self_unit_index].components {
            attack_modifiers.push(c.modify_outgoing_attack(game, self.target_unit_index));
        }
        // 100 means over 100, like we're doing fixed point numbers.
        // See AttackModifier for what this is all trying to do.
        let mut damage_100 = 0;
        for am in &attack_modifiers {
            damage_100 += am.initial_damage_100;
        }
        for am in &attack_modifiers {
            damage_100 = damage_100 * am.damage_multiply_100 / 100;
        }
        for am in &attack_modifiers {
            damage_100 += am.damage_add_100;
        }
        let actual_damage = damage_100 / 100;

        game.damage_unit(rand, self.target_unit_index, actual_damage);
    }
}
