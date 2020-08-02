#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(unused_variables)]

use crate::model;

use crate::unit::*;



#[derive(new)]
pub struct AttackUnitDesire {
  strength: i32,
  targetUnitIndex: generational_arena::Index,
}
impl IUnitDesire for AttackUnitDesire {
  fn getStrength(&self) -> i32 { return self.strength; }
  fn enact(
      self: Box<Self>,
      selfUnitIndex: generational_arena::Index,
      game: &mut model::Game) {

    let mut attackModifiers = Vec::with_capacity(game.units.get(selfUnitIndex).expect("wat").components.len());
    for (_, c) in &game.units.get(selfUnitIndex).expect("wat").components {
      attackModifiers.push(c.modifyOutgoingAttack(game, self.targetUnitIndex));
    }
    // 100 means over 100, like we're doing fixed point numbers.
    let mut damage100 = 0;
    for am in &attackModifiers {
      damage100 += am.initialDamage100;
    }
    for am in &attackModifiers {
      damage100 = damage100 * am.damageMultiply100 / 100;
    }
    for am in &attackModifiers {
      damage100 += am.damageAdd100;
    }
    let actualDamage = damage100 / 100;

    let died =
      {
        let enemyUnit = game.units.get_mut(self.targetUnitIndex).expect("wat");
        let wasAlive = enemyUnit.hp > 0;
        enemyUnit.hp = enemyUnit.hp - actualDamage;
        let isAlive = enemyUnit.hp > 0;
        wasAlive && !isAlive
      };

    if died {
      let mut initialComponentsIndices =
          Vec::with_capacity(game.units.get(self.targetUnitIndex).expect("wat").components.len());
      {
        let enemyUnit = game.units.get(self.targetUnitIndex).expect("wat");
        for (componentIndex, _) in &enemyUnit.components {
          initialComponentsIndices.push(componentIndex);
        }
      }
      for componentIndex in initialComponentsIndices {
        let maybeGameMutator: Option<Box<dyn Fn(&mut model::Game)>> =
          {
            let enemyUnit = game.units.get(self.targetUnitIndex).expect("wat");
            match enemyUnit.components.get(componentIndex) {
              None => { None } // The component was removed by some other component.
              Some(c) => Some(c.onUnitDeath(game, self.targetUnitIndex, selfUnitIndex))
            }
          };
        if let Some(gameMutator) = maybeGameMutator {
          gameMutator(game);
        }
      }
      game.removeUnit(self.targetUnitIndex);
    }
  }
}

#[derive(new)]
pub struct AttackUnitCapability { }
impl IUnitComponent for AttackUnitCapability {
  fn asCapability(&self) -> Option<&dyn IUnitCapability> {
    return Some(self);
  }
  fn asCapabilityMut(&mut self) -> Option<&mut dyn IUnitCapability> {
    return Some(self);
  }
}
impl IUnitCapability for AttackUnitCapability {
  fn getDesire(
    &self,
    rand: &mut model::LCGRand,
    selfUnit: &Unit,
    game: &model::Game)
  -> Box<dyn IUnitDesire> {

    // If there's an enemy right next to us, attack them!
    match selfUnit.getNearestEnemyInSight(&game, 150) {
      Some(enemyUnit) => {
        // println!("bork A");
        // TODO replace this with an attack
        return Box::new(AttackUnitDesire::new(desire::NEED, enemyUnit.getIndex()));
      }
      None => {} // proceed
    }

    // No enemy next to us. Maybe the ChaseUnitCapability will tell us to
    // go chase them. As for us, we dont really want anything.
    return Box::new(DoNothingUnitDesire::new());
  }
}


