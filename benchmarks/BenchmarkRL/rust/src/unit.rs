use downcast_rs::Downcast;
use generational_arena::Arena;

use crate::game::*;
use crate::location::*;

// The 100 means it's multiplied by 100 (for more precision)
pub const DEFAULT_SIGHT_RANGE_100: i32 = 800;

// An aspect of a unit, which can be attached or detached from a unit
// at any time. Some examples:
// - A sword could be a unit component, which modifies outgoing damage.
// - A seek ability could be a unit component, which partakes in the
//   unit's AI to seek and find out any units on the level.
pub trait IUnitComponent: Downcast {
    // Called on a component when its unit is attacking another unit.
    // Used for weapons, damage modifiers, etc.
    fn modify_outgoing_attack(
        &self,
        _game: &Game,
        _self_unit: generational_arena::Index,
    ) -> AttackModifier {
        return AttackModifier::default();
    }

    // Called on a component whenever its unit dies.
    // Returns a lambda with which it can modify the game.
    fn on_unit_death(
        &self,
        _rand: &mut LCGRand,
        _game: &Game,
        _self_unit_index: generational_arena::Index,
        _self_unit_capability_index: generational_arena::Index,
        _attacker: generational_arena::Index,
    ) -> GameMutator {
        return do_nothing_game_mutator();
    }

    // If this component is a capability, return a reference to itself.
    // Otherwise, None.
    fn as_capability(&self) -> Option<&dyn IUnitCapability> {
        return None;
    }
    // If this component is a capability, return a reference to itself (mut).
    // Otherwise, None.
    fn as_capability_mut(&mut self) -> Option<&mut dyn IUnitCapability> {
        return None;
    }

    // TODO: Add armor!
    // fn modify_incoming_attack(
    //     &self,
    //     _game: &Game,
    //     _self_unit_index: generational_arena::Index,
    // ) -> AttackModifier {
    //     return AttackModifier::default();
    // }

    // TODO: Make an enemy that spreads its damage across any allies nearby
    // fn on_unit_attacked(
    //     &self,
    //     _game: &Game,
    //     _self_unit_index: generational_arena::Index,
    //     _attacker: generational_arena::Index,
    // ) -> Box<dyn Fn(&mut Game)> {
    //     return Box::new(|_game| {});
    // }

    // TODO: Add an item that you can use to cast a fireball around you
    // // Named uuse because `use` is a keyword
    // fn uuse(
    //     &self,
    //     _game: &Game,
    //     _self_unit: generational_arena::Index,
    // ) -> Box<dyn Fn(&mut Game)> {
    //     return Box::new(|_game| {});
    // }
}
// We don't use an enum for our components because:
// - It's better architecture to have all of the code for a capability in one place,
//   rather than a central list and central dispatching functions (low coupling,
//   high cohesion).
// - There are one day going to be hundreds of them!
impl_downcast!(IUnitComponent);

// A capability is a special kind of component that factors into the unit's AI.
// Each unit has various capabilities (wander, chase, seek, attack) and they each
// are considered every turn.
// Each capability will calculate and produce a IUnitDesire, which describes what
// exactly the unit should do, and a "strength" int representing how much it wants
// to do it.
// We keep the strongest one. We then call pre_act on all the capabilities to
// inform them what's about to happen, and then we call that desire's enact()
// method so it can effect itself.
pub trait IUnitCapability {
    // Produces a desire describing what the unit wants to do and how much it wants
    // to do it.
    fn get_desire(&self, rand: &mut LCGRand, self_unit: &Unit, game: &Game)
        -> Box<dyn IUnitDesire>;

    // Called before any desire is enacted. Usually used to clean up a capability's
    // internal state.
    fn pre_act(&mut self, _desire: &dyn IUnitDesire) {}
}

// Describes what the unit wants to do and how much it wants to do it.
// Generated each turn by each of a unit's IUnitCapability, and it will be compared
// against other generated desires from other IUnitCapabilitys.
pub trait IUnitDesire: Downcast {
    // Gets how strong
    fn get_strength(&self) -> i32;
    fn enact(self: Box<Self>, rand: &mut LCGRand, self_unit: generational_arena::Index, game: &mut Game);
}
// We don't use an enum for our IUnitDesire for the same reason as IUnitComponent.
impl_downcast!(IUnitDesire);

// Ints describing how much a unit wants to do something.
pub mod desire {
    // pub const MAX: i32 = 1000;
    // pub const MIN: i32 = 0;

    // pub const EXISTENTIAL_NEED: i32 = 1000;
    // pub const REALLY_NEED: i32 = 900;
    pub const NEED: i32 = 800;
    pub const ALMOST_NEED: i32 = 700;
    pub const REALLY_WANT: i32 = 600;
    pub const WANT: i32 = 500;
    // pub const KINDA_WANT: i32 = 400;
    // pub const INTERESTED: i32 = 300;
    // pub const MILDLY_INTERESTED: i32 = 200;
    pub const MEH: i32 = 100;
    // pub const NONE: i32 = 0;
}

// A basic desire to do nothing.
#[derive(new)]
pub struct DoNothingUnitDesire {}
impl IUnitDesire for DoNothingUnitDesire {
    fn get_strength(&self) -> i32 {
        return 0;
    }
    fn enact(
        self: Box<Self>,
        _rand: &mut LCGRand,
        _self_unit: generational_arena::Index,
        _game: &mut Game) {}
}

// A basic desire to move to somewhere.
#[derive(new)]
pub struct MoveUnitDesire {
    // Describes how much the unit wants to move here.
    // Could be low (if it was just meandering) or high (if it was attacking or fleeing).
    strength: i32,

    // Where the unit wants to step to.
    destination: Location,
}
impl IUnitDesire for MoveUnitDesire {
    fn get_strength(&self) -> i32 {
        return self.strength;
    }
    fn enact(
            self: Box<Self>,
            _rand: &mut LCGRand,
            self_unit_index: generational_arena::Index,
            game: &mut Game) {
        let level_index = game.get_player().level_index;
        let level = &mut game.levels[level_index];
        level.move_unit(&mut game.units[self_unit_index], self.destination);
    }
}

// A struct that partakes in the equation to calculate a unit's damage.
#[derive(new)]
pub struct AttackModifier {
    // This is how much we contribute to the initial attack amount.
    // Weapons will usually contribute to this; a claw might be 1.2,
    // a shortsword would be 1.3, broadsword would be 1.4.
    // 100 means over 100, so we can have attack values like 1.25,
    // which would be represented here as 125.
    pub initial_damage_100: i32,

    // 100 means over 100. 150 would mean 1.5x damage, 70 would mean 70% damage.
    // Certain abilities might do this, like if we double damage for 10 turns it
    // would show up here.
    // Try to keep this low, we dont want things as high as 2x, prefer things
    // like 110 to represent +10%.
    pub damage_multiply_100: i32,

    // Extra final additions go here. For example we might have a ring
    // of +3 damage, that would show up here as 300.
    pub damage_add_100: i32,
}
impl AttackModifier {
    pub fn default() -> AttackModifier {
        return AttackModifier::new(0, 100, 0);
    }
}

// This is how units know who to attack.
// When we add pets, they'll also have the Good allegiance.
#[derive(PartialEq)]
pub enum Allegiance {
    Good,
    Evil,
}

// Anything that has consciousness or takes action in the world (person, pet, golems, etc)
#[derive(new)]
pub struct Unit {
    pub index: Option<generational_arena::Index>,
    pub hp: i32,
    pub max_hp: i32,
    pub level_index: usize,
    pub loc: Location,
    pub allegiance: Allegiance,
    pub display_class: String,
    pub components: Arena<Box<dyn IUnitComponent>>,
}
impl Unit {
    // Gets the index of this unit in the containing level.
    pub fn get_index(&self) -> generational_arena::Index {
        return self.index.expect("wat");
    }

    // Gets the component of the given class.
    pub fn get_first_component_mut<T: IUnitComponent>(&mut self) -> Option<&mut T> {
        for (_, c_boxed) in &mut self.components {
            if let Some(w) = c_boxed.downcast_mut::<T>() {
                return Some(w);
            }
        }
        return None;
    }

    // Takes a turn. Will do some AI to figure out what it wants to do, then do it.
    pub fn act(self_index: generational_arena::Index, rand: &mut LCGRand, mut game: &mut Game) {
        // First, loop over all the capabilities this unit has, and produce a desire for each,
        // keeping only the strongest one.
        let self_unit = &game.units[self_index];
        let mut greatest_desire: Box<dyn IUnitDesire> = Box::new(DoNothingUnitDesire::new());
        for (_, component) in &self_unit.components {
            if let Some(capability) = component.as_capability() {
                let desire = capability.get_desire(rand, self_unit, &game);
                if desire.get_strength() > greatest_desire.get_strength() {
                    greatest_desire = desire;
                }
            }
        }

        // Now, inform all of the capabilities of what's about to happen, so they can clean
        // up any state for in-progress things they had going (like an enemy they were chasing)
        let self_unit_mut = &mut game.units[self_index];
        for (_, component) in &mut self_unit_mut.components {
            if let Some(capability) = component.as_capability_mut() {
                capability.pre_act(&*greatest_desire);
            }
        }

        // Now, let the desire implement itself onto the world and make things happen.
        greatest_desire.enact(rand, self_index, &mut game);
    }

    pub fn get_nearest_enemy_in_sight<'a>(&self, game: &'a Game, range: i32) -> Option<&'a Unit> {
        let locations_within_sight = game
            .get_current_level()
            .get_locations_within_sight(self.loc, false, range);

        let mut maybe_nearest_enemy: Option<&Unit> = None;
        for location_within_sight in locations_within_sight {
            let level = game.get_current_level();
            match level.unit_by_location.get(&location_within_sight) {
                None => {}
                Some(&other_unit_index) => {
                    assert!(other_unit_index != self.get_index());
                    let other_unit = &game.units[other_unit_index];
                    if self.allegiance != other_unit.allegiance {
                        let is_nearest_enemy = match maybe_nearest_enemy {
                            None => true,
                            Some(nearest_enemy) => {
                                self.loc.dist_squared(nearest_enemy.loc)
                                    < self.loc.dist_squared(other_unit.loc)
                            }
                        };
                        if is_nearest_enemy {
                            maybe_nearest_enemy = Some(other_unit);
                        }
                    }
                }
            }
        }

        return maybe_nearest_enemy;
    }
}
