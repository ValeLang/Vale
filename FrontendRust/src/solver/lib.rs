pub mod optimized_solver_state;
pub mod simple_solver_state;
pub mod solver;
pub mod solver_error_humanizer;

pub use simple_solver_state::SimpleSolverState;
pub use solver::*;

pub mod test {
    pub mod solver_tests;
    pub mod test_rule_solver;
    pub mod test_rules;

    pub use test_rules::{TestRule, IRule};
}
