
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

fn main() {
  benchmark_rl::benchmark_rl();
}
