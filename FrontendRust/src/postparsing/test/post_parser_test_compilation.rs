use std::collections::HashMap;
use crate::Keywords;
use crate::compile_options::GlobalOptions;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::postparsing::ScoutCompilation;
use crate::utils::code_hierarchy::{IPackageResolver, PackageCoordinate};

pub fn test<'s, 'ctx, 'p>(
  scout_arena: &'ctx ScoutArena<'s>,
  keywords: &'ctx Keywords<'s>,
  parser_keywords: &'ctx Keywords<'p>,
  parse_arena: &'ctx ParseArena<'p>,
  package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
  code: &str,
) -> ScoutCompilation<'s, 'ctx, 'p>
where 'p: 's,
{
  // `code` is unused in the body — in the Rust port, the code string is already baked into
  // `package_to_contents_resolver` (the caller builds the resolver via `code_hierarchy::test_from_vec`
  // before passing it in). Scala's `FileCoordinateMap.test(interner, Vector(code))` performs that
  // role inside `test`, but Rust hoists it to the caller per SPDMX exception B (lifetime adaptation).
  let _ = code;
  let global_options = GlobalOptions {
    sanity_check: true,
    use_overload_index: true,
    use_optimized_solver: true,
    verbose_errors: false,
    debug_output: false,
  };
  let packages_to_build = vec![PackageCoordinate::test_tld(parse_arena, parser_keywords)];
  ScoutCompilation::new(
    scout_arena,
    keywords,
    parser_keywords,
    parse_arena,
    packages_to_build,
    package_to_contents_resolver,
    global_options,
  )
}

