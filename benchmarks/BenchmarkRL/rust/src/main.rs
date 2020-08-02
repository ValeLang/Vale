#![allow(non_snake_case)]
#![allow(unused_imports)]

use std::env;
use atoi::atoi;
use std::time::{Duration, SystemTime};

#[macro_use]
extern crate derive_new;

#[macro_use]
extern crate downcast_rs;

mod model;
mod make_level;
mod unit;
mod items;
mod astar;
mod wander;
mod chase;
mod attack;
mod seek;
mod benchmark_rl;
mod screen;

fn getIntArg(args: &Vec<String>, paramStr: &str, default: i32) -> i32 {
  match args.iter().position(|x| x == paramStr) {
    None => { return default; }
    Some(pos) => {
      let intIndex = pos + 1;
      if intIndex >= args.len() {
        panic!("Must have a number after {}. Use --help for help.", paramStr);
      }
      let widthStr = &args[intIndex];
      match atoi(widthStr.as_bytes()) {
        None => panic!("Must have a number after {}.  Use --help for help.", paramStr),
        Some(w) => return w
      }
    }
  };
}

fn main() {
  let args: Vec<String> = env::args().collect();
  
  match args.iter().position(|x| x == "--help") {
    None => {}
    Some(_) => {
      println!("
--width N       Sets level width.
--height N      Sets level height.
--numLevels N   Sets number of levels until game end.
--seed N        Uses given seed for level generation. If absent, random.
--display N     0 to not display, 1 to display.
--turnDelay N   Sleeps for N ms between each turn.
");
      return;
    }
  }

  let levelWidth = getIntArg(&args, "--width", 80);
  let levelHeight = getIntArg(&args, "--height", 22);
  let numLevels = getIntArg(&args, "--numLevels", 2);
  let seed = getIntArg(&args, "--seed", SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).expect("wat").as_secs() as i32);
  let display = getIntArg(&args, "--display", 1) != 0;
  let turnDelay = getIntArg(&args, "--turnDelay", 100);

  benchmark_rl::benchmark_rl(seed, levelWidth, levelHeight, numLevels, display, turnDelay);
}
