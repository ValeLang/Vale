use crate::typing::compilation::TypingPassCompilation;
use crate::typing::compiler_error_humanizer::humanize;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::utils::source_code_utils::{
    humanize_pos_code_map, line_containing, line_range_containing, lines_between,
};

pub fn humanize_compile_error<'s, 'ctx, 't, 'p>(
    compile: &mut TypingPassCompilation<'s, 'ctx, 't, 'p>,
    err: ICompileErrorT<'s, 't>,
) -> String
where
    's: 't,
{
    let code_map = compile.get_code_map().expect("getCodeMap failed");
    let scout_arena = compile.scout_arena_for_tests();
    let typing_interner = compile.typing_interner;
    humanize(
        scout_arena,
        typing_interner,
        false,
        &|x| humanize_pos_code_map(&code_map, &x),
        &|a, b| lines_between(&code_map, &a, &b),
        &|x| line_range_containing(&code_map, &x),
        &|x| line_containing(&code_map, &x),
        err,
    )
}

pub fn assert_humanized_eq(actual: &str, expected: &str) {
    if actual != expected {
        eprintln!("\n--- captured humanized output (paste verbatim between r#\" and \"# in the test; preserve trailing blank lines) ---");
        eprint!("{}", actual);
        eprintln!("[END]");
        panic!("humanized output mismatch (see captured output above)");
    }
}
