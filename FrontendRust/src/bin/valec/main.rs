mod build;
mod midas;
mod frontend;

use std::env;
use std::path::PathBuf;
use std::process;

use clap::{Parser, Subcommand};

#[derive(Parser)]
#[command(
    name = "valec",
    version,
    about = "The Vale compiler.",
    long_about = None,
)]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    /// Compile Vale source files into an executable.
    Build(build::BuildArgs),
    /// Print version information.
    Version,
}

fn main() {
    let cli = Cli::parse();

    // Resolve the compiler's install dir from the actual binary location.
    // Falls back to argv[0] if current_exe() somehow fails (it shouldn't).
    let compiler_dir: PathBuf = env::current_exe()
        .and_then(|p| p.canonicalize())
        .ok()
        .and_then(|p| p.parent().map(|d| d.to_path_buf()))
        .unwrap_or_else(|| {
            eprintln!("Could not determine compiler install directory.");
            process::exit(1);
        });

    match cli.command {
        Command::Build(args) => {
            build::build_stuff(&compiler_dir, args);
        }
        Command::Version => {
            println!("valec {}", env!("CARGO_PKG_VERSION"));
        }
    }
}
