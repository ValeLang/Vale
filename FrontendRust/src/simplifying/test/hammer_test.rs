
use bumpalo::Bump;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::simplifying::test::test_compilation::test;
use crate::final_ast::test::traverse::NodeRefH;
use crate::final_ast::instructions::StackifyH;
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};
use crate::builtins::builtins::get_code_map;
use crate::tests::tests::get_package_to_resource_resolver;
use std::collections::HashMap;
use crate::collect_where_hnode;
use crate::typing::typing_interner::TypingInterner;
pub struct HammerTest {
}

// (impl block suppressed per simplifying-pass policy — test fns emitted at module scope)

#[test]
fn local_ids_unique() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let instantiating_bump = Bump::new();
    let hammer_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    // Rust adaptation (SPDMX Exception B): TypingInterner construction hoisted to the test site (Scala's `val interner = new Interner()` inside `HammerTestCompilation.test` becomes a caller-owned interner so Rust can borrow it `&'ctx` through the pipeline).
    let typing_interner = TypingInterner::new(&typing_bump);
    let code = "
exported func main() {
  a = 6;
  if (true) {
    b = 7;
    c = 8;
  } else {
    while (false) {
      d = 9;
    }
    e = 10;
  }
  f = 11;
}
";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let mut compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
    let hamuts = compile.get_hamuts();
    let paackage = hamuts.lookup_package(*PackageCoordinate::test_tld(&parse_arena, &parser_keywords));
    let main = paackage.lookup_function("main");

    assert!(paackage.export_name_to_function.iter().any(|(_, proto)| *proto == main.prototype));

    let stackifies: Vec<&StackifyH> = collect_where_hnode!(NodeRefH::Function(main), NodeRefH::StackifyH(s) => Some(s));
    let mut local_ids = stackifies.iter().map(|s| s.local.id.number).collect::<Vec<_>>();
    local_ids.sort();
    let mut distinct = local_ids.clone();
    distinct.dedup();
    assert_eq!(local_ids, distinct);
    assert!(local_ids.len() >= 6);
}

// NOVEL CODE: no Scala counterpart. Minimal end-to-end driving test for hammer
// body migration — the simplest possible program (return an int; no control flow,
// no generics), modeled structurally on `local_ids_unique` above. Running it drives
// the hammer pipeline from the first `panic!()` outward (currently
// `HammerTestCompilation::test`). As the harness + entry points get real bodies,
// grow this to mirror local_ids_unique's shape: get_hamuts() →
// lookup_package(TEST_TLD) → lookup_function("main") → assert the export exists.
#[test]
fn returns_int() {
    
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let instantiating_bump = Bump::new();
    let hammer_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    // Rust adaptation (SPDMX Exception B): TypingInterner construction hoisted to the test site (Scala's `val interner = new Interner()` inside `HammerTestCompilation.test` becomes a caller-owned interner so Rust can borrow it `&'ctx` through the pipeline).
    let typing_interner = TypingInterner::new(&typing_bump);
    let code = "exported func main() int { return 7; }\n";
    let resolver = get_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let _compile = test(
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver, &instantiating_bump,
    );
}
