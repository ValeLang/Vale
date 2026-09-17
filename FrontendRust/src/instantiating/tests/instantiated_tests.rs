use crate::builtins::builtins::get_code_map;
use crate::compile_options::GlobalOptions;
use crate::instantiating::instantiated_compilation::InstantiatedCompilation;
use crate::instantiating::instantiated_compilation::InstantiatorCompilationOptions;
use crate::tests::tests::get_package_to_resource_resolver;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::code_hierarchy::test_from_vec;
use std::collections::HashMap;
use std::marker::PhantomData;
use std::sync::Arc;
use bumpalo::Bump;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::typing::typing_interner::TypingInterner;
use crate::utils::code_hierarchy::IPackageResolver;

pub fn test<'s, 'ctx, 't, 'i, 'p>(
    compilation_bump: &'ctx bumpalo::Bump,
    typing_interner: &'ctx TypingInterner<'s, 't>,
    scout_arena: &'ctx ScoutArena<'s>,
    keywords: &'ctx Keywords<'s>,
    parser_keywords: &'ctx Keywords<'p>,
    parse_arena: &'ctx ParseArena<'p>,
    instantiating_bump: &'i bumpalo::Bump,
    code: &str,
) -> InstantiatedCompilation<'s, 'ctx, 't, 'i, 'p>
where 's: 't, 's: 'i, 'p: 'ctx,
{
    let packages_to_build: Vec<&'p PackageCoordinate<'p>> =
        vec![PackageCoordinate::test_tld(parse_arena, parser_keywords)];
    let base_code_map = get_code_map(parse_arena, parser_keywords);
    let resolver_concrete = base_code_map
        .or(test_from_vec(parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>> =
        compilation_bump.alloc(resolver_concrete);
    let global_options = GlobalOptions {
        sanity_check: true,
        use_overload_index: true,
        use_optimized_solver: true,
        verbose_errors: true,
        debug_output: true,
    };
    let instantiator_options = InstantiatorCompilationOptions {
        debug_out: Arc::new(|x: &str| println!("{}", x)),
    };
    InstantiatedCompilation::new(
        typing_interner,
        scout_arena,
        keywords,
        parser_keywords,
        parse_arena,
        packages_to_build,
        resolver,
        global_options,
        instantiator_options,
        instantiating_bump,
    )
}

/// Temporary state
#[derive(PartialEq, Eq, Hash)]
pub struct InstantiatedTests<'s, 't> {
  pub _marker: PhantomData<(&'s (), &'t ())>,
}

#[test]
fn test_templates() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let instantiating_bump = Bump::new();
    let compilation_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let typing_interner = TypingInterner::new(&typing_bump);
    let code = r"
func drop(x int) { }
func bork<T>(a T) void where func drop(T)void {
  // implicitly calls drop
}
exported func main() {
  bork(3);
}
";
    let mut compile = test(&compilation_bump, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &instantiating_bump, code);
    compile.get_monouts();
}
#[test]
fn nested_anonymous_substruct_captures_outer() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let instantiating_bump = Bump::new();
    let compilation_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let typing_interner = TypingInterner::new(&typing_bump);
    let code = r"
interface IF<R Ref, P Ref> {
  func __call(virtual this &IF<R, P>, p P) R;
}
exported func main() int {
  inner = IF<bool, int>((it) => { true });
  outer = IF<bool, int>((it) => { inner(it) });
  return 0;
}
";
    let mut compile = test(&compilation_bump, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &instantiating_bump, code);
    compile.get_monouts();
}
