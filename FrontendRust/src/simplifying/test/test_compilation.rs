use bumpalo::Bump;
use crate::compile_options::GlobalOptions;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_compilation::{HammerCompilation, HammerCompilationOptions};
use crate::simplifying::hammer_interner::HammerInterner;
use crate::utils::code_hierarchy::{IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use std::sync::Arc;
use crate::typing::typing_interner::TypingInterner;

pub fn test<'s, 'h, 'ctx, 't, 'i, 'p>(
  interner: &'ctx HammerInterner<'s, 'h>,
  typing_interner: &'ctx TypingInterner<'s, 't>,
  scout_arena: &'ctx ScoutArena<'s>,
  keywords: &'ctx Keywords<'s>,
  parser_keywords: &'ctx Keywords<'p>,
  parse_arena: &'ctx ParseArena<'p>,
  resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
  instantiating_bump: &'i Bump,
) -> HammerCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where 's: 'h, 's: 't, 's: 'i, 'p: 'ctx,
{
  let builtin = PackageCoordinate::builtin(parse_arena, parser_keywords);
  let test_tld = parse_arena.intern_package_coordinate(parse_arena.intern_str("test"), &[]);
  let global_options = GlobalOptions {
    sanity_check: true,
    use_overload_index: true,
    use_optimized_solver: true,
    verbose_errors: true,
    debug_output: true,
  };
  HammerCompilation::new(
    scout_arena,
    interner,
    typing_interner,
    keywords,
    parser_keywords,
    parse_arena,
    vec![builtin, test_tld],
    resolver,
    HammerCompilationOptions {
      debug_out: Arc::new(|x: &str| println!("{}", x)),
      global_options,
    },
    instantiating_bump)
}

