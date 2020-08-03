use generational_arena;
use generational_arena::Arena;
use rustc_hash::FxHashSet;
use std::thread::sleep;
use std::iter::FromIterator;
use std::time::Duration;

use crate::attack::*;
use crate::chase::*;
use crate::items::*;
use crate::make_level::*;
use crate::game::*;
use crate::screen::*;
use crate::seek::*;
use crate::fire::*;
use crate::unit::*;
use crate::wander::*;
use crate::explodey::*;
use crate::location::*;

// Returns whether we should probably re-display it next turn.
// Will be false if its something static like terrain, or true
// if it's something that moves around like a unit.
pub fn set_screen_cell(
    screen: &mut Screen,
    game: &Game,
    player_visible_locs: &FxHashSet<Location>,
    loc: Location,
) {
    let mut foreground_color = ScreenColor::LIGHTGRAY;
    let mut background_color = ScreenColor::BLACK;
    let mut character = " ";

    if let Some(tile) = game.get_current_level().tiles.get(&loc) {
        match tile.display_class.as_str() {
            "dirt" => {
                character = ".";
                foreground_color = ScreenColor::ORANGE;
            }
            "grass" => {
                character = ".";
                foreground_color = ScreenColor::GREEN;
            }
            "wall" => {
                character = "#";
                foreground_color = ScreenColor::LIGHTGRAY;
            }
            _ => panic!("unrecognized tile display class"),
        }
    }

    if let Some(tile) = game.get_current_level().tiles.get(&loc) {
        if let Some(fire) = tile.get_first_component::<FireTileComponent>() {
            character = "^";
            if fire.num_turns_remaining % 2 == 0 {
                foreground_color = ScreenColor::RED;
            } else {
                foreground_color = ScreenColor::ORANGE;
            }
        }
    }

    if let Some(&unit_index) = game.get_current_level().unit_by_location.get(&loc) {
        let unit = &game.units[unit_index];
        match unit.display_class.as_str() {
            "goblin" => {
                character = "g";
                foreground_color = ScreenColor::GREEN;
            }
            "chronomancer" => {
                character = "@";
                foreground_color = ScreenColor::TURQUOISE;
            }
            _ => panic!("unrecognized unit display class"),
        }
    }

    if player_visible_locs.contains(&loc) {
        background_color = ScreenColor::DARKGRAY;
    }

    screen.set_cell(
        loc.x as usize,
        loc.y as usize,
        background_color,
        foreground_color,
        character.to_string(),
    );
}

// Moves the player to the next level.
// Returns true to continue with the game, false to exit the game.
pub fn descend_to_next_level(mut rand: &mut LCGRand, game: &mut Game) -> bool {
    let player_index = game.get_player_index();
    let old_player_loc = game.get_player().loc;
    // Remove the player from the old level's unit-by-location index.
    game.get_current_level_mut()
        .unit_by_location
        .remove(&old_player_loc);

    let player_mut = &mut game.units[player_index];

    // Figure out the new level index.
    let player_new_level_index = player_mut.level_index + 1;
    // If we're descending past the last level, end the game.
    if player_new_level_index >= game.levels.len() {
        // End the game.
        return false;
    }

    // Move the player to the new level.
    player_mut.level_index = player_new_level_index;
    // Update the player's location so he's not, for example, embedded in the
    // middle of a wall, stuck helpless for all eternity.
    let new_player_loc =
        game.levels[player_new_level_index].find_random_walkable_unoccuped_location(&mut rand);
    game.units[player_index].loc = new_player_loc;

    // Add the player to the new level's unit-by-location index.
    game.get_current_level_mut()
        .unit_by_location
        .insert(new_player_loc, player_index);

    // Continue with the game.
    return true;
}

pub fn setup(
    mut rand: &mut LCGRand,
    max_width: i32,
    max_height: i32,
    num_levels: i32,
) -> Game {
    let mut game = Game::new(Arena::new(), Vec::new(), None);

    for _ in 0..num_levels {
        let level_index = game.levels.len();
        game.levels.push(make_level(max_width, max_height, &mut rand));

        // Add one goblin for every 10 walkable spaces in the level.
        let num_walkable_locations = game.levels[level_index].get_walkable_locations().len();
        for _ in 0..(num_walkable_locations / 10) {
            let new_unit_loc =
                game.levels[level_index].find_random_walkable_unoccuped_location(&mut rand);

            let new_unit_index = game.add_unit_to_level(
                level_index,
                new_unit_loc,
                10,
                10,
                Allegiance::Evil,
                "goblin".to_string(),
            );
            let new_unit = &mut game.units[new_unit_index];
            new_unit.components.insert(Box::new(WanderUnitCapability::new()));
            new_unit.components.insert(Box::new(AttackUnitCapability::new()));
            new_unit.components.insert(Box::new(ChaseUnitCapability::new()));
            new_unit.components.insert(Box::new(GoblinClaws::new()));
            if rand.next() % 10 == 0 {
              new_unit.components.insert(Box::new(ExplodeyUnitComponent::new()));
            }
        }
    }

    let player_loc = game.levels[0].find_random_walkable_unoccuped_location(&mut rand);
    let player_index = game.add_unit_to_level(
        0,
        player_loc,
        1000000,
        1000000,
        Allegiance::Good,
        "chronomancer".to_string(),
    );
    game.player_index = Some(player_index);

    let player = &mut game.units[player_index];
    player.components.insert(Box::new(WanderUnitCapability::new()));
    player.components.insert(Box::new(AttackUnitCapability::new()));
    player.components.insert(Box::new(ChaseUnitCapability::new()));
    player.components.insert(Box::new(SeekUnitCapability::new()));
    player.components.insert(Box::new(IncendiumShortSword::new()));

    return game;
}

// Advance the game by 1 turn for all units.
pub fn turn(mut rand: &mut LCGRand, mut game: &mut Game) {
    // First, let all the tiles act.
    let acting_tile_locs: Vec<Location> = {
        Vec::from_iter(game.get_current_level().acting_tile_locations.keys().map(|&x| x).clone())
    };
    for acting_tile_loc in acting_tile_locs {
        // Get a list of components to iterate over, so that tile components'
        // adding and removing dont modify the units array while we're iterating.
        let component_indices: Vec<generational_arena::Index> = game
            .get_current_level()
            .tiles[&acting_tile_loc]
            .components
            .iter()
            .map(|(x, _)| x)
            .collect();
        for component_index in component_indices {
            let game_mutator =
                game.get_current_level().tiles.get(&acting_tile_loc).expect("").components[component_index]
                .on_turn(
                    rand,
                    game,
                    acting_tile_loc,
                    component_index);
            game_mutator(rand, game);
        }
    }

    // Get a list of weak pointers to iterate over, so that units' death
    // and spawning dont modify the units array while we're iterating.
    let units: Vec<generational_arena::Index> = game
        .get_current_level()
        .unit_by_location
        .values()
        .map(|&x| x)
        .collect();
    // Now iterate over them, only considering ones that are still alive.
    for &unit_index in units.iter() {
        if game.units.contains(unit_index) {
            Unit::act(unit_index, &mut rand, &mut game);
        }
    }
}

pub fn display(seed: i32, maybe_screen: &mut Option<Screen>, game: &Game) {
    let maybe_player_loc = game.units.get(game.get_player_index()).map(|p| p.loc);
    let player_visible_locs =
        match maybe_player_loc {
            None => FxHashSet::default(),
            Some(player_loc) => {
                game.get_current_level().get_locations_within_sight(
                    player_loc, true, DEFAULT_SIGHT_RANGE_100)
            }
        };

    if let Some(mut screen) = maybe_screen.as_mut() {
        for x in 0..game.get_current_level().max_width {
            for y in 0..game.get_current_level().max_height {
                let loc = Location::new(x, y);
                set_screen_cell(&mut screen, &game, &player_visible_locs, loc);
            }
        }
        if let Some(player) = game.units.get(game.get_player_index()) {
            screen.set_status_line(format!("Seed {}   Level {}   HP: {} / {}\nTo benchmark: --seed 1337 --width 40 --height 30 --num_levels 5 --turn_delay 0 --display 0", seed, player.level_index, player.hp, player.max_hp));
        } else {
            screen.set_status_line("Dead!                                      ".to_string());
        }
        screen.paint_screen();
    }
}

pub fn benchmark_rl(
    seed: i32,
    level_width: i32,
    level_height: i32,
    num_levels: i32,
    should_display: bool,
    turn_delay: i32,
) {
    let mut rand = LCGRand { seed: seed as u32 };
    let mut game = setup(&mut rand, level_width, level_height, num_levels);

    let mut maybe_screen = if should_display {
        Some(Screen::new(
            game.get_current_level().max_width as usize,
            game.get_current_level().max_height as usize,
        ))
    } else {
        None
    };

    loop {
        turn(&mut rand, &mut game);

        let player_leve_index = game.get_player().level_index;
        let num_units = game.levels[player_leve_index].unit_by_location.keys().len();
        if num_units == 1 {
            let keep_running = descend_to_next_level(&mut rand, &mut game);
            if !keep_running {
                return;
            }
        }

        display(seed, &mut maybe_screen, &game);

        if turn_delay > 0 {
            sleep(Duration::new(0, turn_delay as u32));
        }
    }
}
