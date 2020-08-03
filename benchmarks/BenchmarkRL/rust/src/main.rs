#![deny(
    nonstandard_style,
    warnings,
    rust_2018_idioms,
    unused,
    future_incompatible,
    clippy::all,
    clippy::restriction,
    clippy::pedantic,
    clippy::nursery,
    clippy::cargo
)]
#![allow(clippy::integer_arithmetic)]
#![allow(clippy::missing_inline_in_public_items)]
#![allow(clippy::multiple_crate_versions)]
#![allow(clippy::implicit_return)]

use atoi::atoi;
use std::env;
use std::time::SystemTime;

#[macro_use]
extern crate derive_new;

#[macro_use]
extern crate downcast_rs;

mod astar;
mod attack;
mod benchmark_rl;
mod chase;
mod items;
mod make_level;
mod game;
mod screen;
mod seek;
mod unit;
mod wander;
mod explodey;
mod location;
mod tile;
mod level;
mod fire;

fn get_int_arg(args: &Vec<String>, param_str: &str, default: i32) -> i32 {
    match args.iter().position(|x| x == param_str) {
        None => {
            return default;
        }
        Some(pos) => {
            let int_index = pos + 1;
            if int_index >= args.len() {
                panic!(
                    "Must have a number after {}. Use --help for help.",
                    param_str
                );
            }
            let width_str = &args[int_index];
            match atoi(width_str.as_bytes()) {
                None => panic!(
                    "Must have a number after {}.  Use --help for help.",
                    param_str
                ),
                Some(w) => return w,
            }
        }
    };
}

fn main() {
    let args: Vec<String> = env::args().collect();

    match args.iter().position(|x| x == "--help") {
        None => {}
        Some(_) => {
            println!(
                "
--width N       Sets level width.
--height N      Sets level height.
--num_levels N  Sets number of levels until game end.
--seed N        Uses given seed for level generation. If absent, random.
--display N     0 to not display, 1 to display.
--turn_delay N  Sleeps for N ms between each turn.
"
            );
            return;
        }
    }

    let level_width = get_int_arg(&args, "--width", 80);
    let level_height = get_int_arg(&args, "--height", 22);
    let num_levels = get_int_arg(&args, "--num_levels", 2);
    let seed = get_int_arg(
        &args,
        "--seed",
        SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .expect("No system time!")
            .as_secs() as i32,
    );
    let display = get_int_arg(&args, "--display", 1) != 0;
    let turn_delay = get_int_arg(&args, "--turn_delay", 100);

    benchmark_rl::benchmark_rl(
        seed,
        level_width,
        level_height,
        num_levels,
        display,
        turn_delay,
    );
}
