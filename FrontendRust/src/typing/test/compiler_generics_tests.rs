use bumpalo::Bump;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::utils::code_hierarchy::{self, IPackageResolver};
use super::compiler_test_compilation::compiler_test_compilation;
use crate::typing::typing_interner::TypingInterner;
use crate::builtins::builtins::get_embedded_modulized_code_map;
use crate::tests::tests::get_package_to_resource_resolver;

pub struct CompilerGenericsTests;
impl CompilerGenericsTests {}

fn read_code_from_resource(resource_filename: &str) -> String {
    panic!("Unimplemented: read_code_from_resource");
}

#[test]
fn upcasting_with_generic_bounds() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = concat!(
        "\n",
        "import v.builtins.panic.*;\n",
        "import v.builtins.drop.*;\n",
        "\n",
        "#!DeriveInterfaceDrop\n",
        "sealed interface XOpt<T Ref> where func drop(T)void {\n",
        "  func harvest(virtual opt XOpt<T>) &T;\n",
        "}\n",
        "\n",
        "#!DeriveStructDrop\n",
        "struct XNone<T Ref> where func drop(T)void  { }\n",
        "\n",
        "impl<T> XOpt<T> for XNone<T>;\n",
        "\n",
        "func harvest<T>(opt XNone<T>) &T {\n",
        "  __vbi_panic();\n",
        "}\n",
        "\n",
        "exported func main() int {\n",
        "  m XOpt<int> = XNone<int>();\n",
        "  return (m).harvest();\n",
        "}\n",
        "\n",
    );
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(
        &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver,
    );
    let _coutputs = compile.expect_compiler_outputs();
}

