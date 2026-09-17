use crate::utils::code_hierarchy::IPackageResolver;
use crate::builtins::builtins::get_code_map;
use crate::builtins::builtins::get_modulized_code_map;
use crate::compile_options::GlobalOptions;
use crate::final_ast::ast::ProgramH;
use crate::instantiating::ast::hinputs::HinputsI;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::lexing::ast::RangeL;
use crate::lexing::errors::FailedParse;
use crate::parse_arena::ParseArena;
use crate::parsing::ast::FileP;
use crate::postparsing::ast::ProgramS;
use crate::postparsing::post_parser::ICompileErrorS;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_compilation::HammerCompilation;
use crate::simplifying::hammer_compilation::HammerCompilationOptions;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::tests::tests::get_package_to_resource_resolver;
use crate::testvm::heap::HeapV;
use crate::testvm::values::PrimitiveKindV;
use crate::testvm::values::ReferenceV;
use crate::testvm::vivem::VmRuntimeErrorV;
use crate::testvm::vivem::empty_stdin;
use crate::testvm::vivem::execute_with_heap;
use crate::testvm::vivem::execute_with_primitive_args;
use crate::testvm::vivem::regular_stdout;
use crate::testvm::vivem::stdin_from_list;
use crate::testvm::vivem::stdout_collector;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::hinputs_t::HinputsT;
use crate::typing::typing_interner::TypingInterner;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::code_hierarchy::test_from_vec;
use crate::von::ast::IVonData;
use std::collections::HashMap;
use std::io::stdout;

pub fn test<'s, 'h, 'ctx, 't, 'i, 'p>(
    compilation_bump: &'ctx bumpalo::Bump,
    interner: &'ctx HammerInterner<'s, 'h>,
    typing_interner: &'ctx TypingInterner<'s, 't>,
    scout_arena: &'ctx ScoutArena<'s>,
    keywords: &'ctx Keywords<'s>,
    parser_keywords: &'ctx Keywords<'p>,
    parse_arena: &'ctx ParseArena<'p>,
    instantiating_bump: &'i bumpalo::Bump,
    code: &str,
) -> RunCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where 's: 'h, 's: 't, 's: 'i, 'p: 'ctx,
{
    let packages_to_build: Vec<&'p PackageCoordinate<'p>> =
        vec![
            PackageCoordinate::builtin(parse_arena, parser_keywords),
            PackageCoordinate::test_tld(parse_arena, parser_keywords),
        ];
    let base_code_map = get_code_map(parse_arena, parser_keywords);
    let resolver_concrete = base_code_map
        .or(test_from_vec(parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>> =
        compilation_bump.alloc(resolver_concrete);
    let options = HammerCompilationOptions {
        global_options: GlobalOptions {
            sanity_check: true,
            use_overload_index: true,
            use_optimized_solver: true,
            verbose_errors: true,
            debug_output: true,
        },
        ..HammerCompilationOptions::new()
    };
    RunCompilation {
        interner: typing_interner,
        hammer_compilation: HammerCompilation::new(
            scout_arena, interner, typing_interner, keywords, parser_keywords, parse_arena,
            packages_to_build, resolver, options, instantiating_bump,
        ),
    }
}

pub fn test_no_builtins<'s, 'h, 'ctx, 't, 'i, 'p>(
    compilation_bump: &'ctx bumpalo::Bump,
    interner: &'ctx HammerInterner<'s, 'h>,
    typing_interner: &'ctx TypingInterner<'s, 't>,
    scout_arena: &'ctx ScoutArena<'s>,
    keywords: &'ctx Keywords<'s>,
    parser_keywords: &'ctx Keywords<'p>,
    parse_arena: &'ctx ParseArena<'p>,
    instantiating_bump: &'i bumpalo::Bump,
    code: &str,
) -> RunCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where 's: 'h, 's: 't, 's: 'i, 'p: 'ctx,
{
    let packages_to_build: Vec<&'p PackageCoordinate<'p>> =
        vec![
            PackageCoordinate::test_tld(parse_arena, parser_keywords),
        ];
    let base_code_map =
        get_modulized_code_map(parse_arena, parser_keywords)
            .expect("Builtins code map failed to load");
    let resolver_concrete = base_code_map
        .or(test_from_vec(parse_arena, vec![code.to_string()]))
        .or(get_package_to_resource_resolver());
    let resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>> =
        compilation_bump.alloc(resolver_concrete);
    let options = HammerCompilationOptions {
        global_options: GlobalOptions {
            sanity_check: true,
            use_overload_index: true,
            use_optimized_solver: true,
            verbose_errors: true,
            debug_output: true,
        },
        ..HammerCompilationOptions::new()
    };
    RunCompilation {
        interner: typing_interner,
        hammer_compilation: HammerCompilation::new(
            scout_arena, interner, typing_interner, keywords, parser_keywords, parse_arena,
            packages_to_build, resolver, options, instantiating_bump,
        ),
    }
}

pub struct RunCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where 's: 'h, 's: 't, 's: 'i, 'p: 'ctx,
{
    pub interner: &'ctx TypingInterner<'s, 't>,
    pub hammer_compilation: HammerCompilation<'s, 'h, 'ctx, 't, 'i, 'p>,
}

impl<'s, 'h, 'ctx, 't, 'i, 'p> RunCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where 's: 'h, 's: 't, 's: 'i, 'p: 'ctx, 'ctx: 'h, 'p: 'h, 'i: 'h,
{
  pub fn get_code_map(&self) { panic!("Unimplemented: get_code_map"); }
  
  pub fn get_parseds(&mut self) -> Result<FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)>, FailedParse<'p>> {
    self.hammer_compilation.get_parseds()
  }
  
  pub fn get_vpst_map(&self) { panic!("Unimplemented: get_vpst_map"); }
  
  pub fn get_scoutput(&mut self) -> Result<&FileCoordinateMap<'s, ProgramS<'s>>, ICompileErrorS<'s>> {
      self.hammer_compilation.get_scoutput()
  }
  
  pub fn get_astrouts(&self) { panic!("Unimplemented: get_astrouts"); }
  
  pub fn get_compiler_outputs(&mut self) -> Result<&HinputsT<'s, 't>, ICompileErrorT<'s, 't>> {
      self.hammer_compilation.get_compiler_outputs()
  }
  
  pub fn expect_compiler_outputs(&mut self) -> &HinputsT<'s, 't> {
    self.hammer_compilation.expect_compiler_outputs()
  }
  
  pub fn get_monouts(&mut self) -> &HinputsI<'s, 'i> {
    self.hammer_compilation.get_monouts()
  }
  
  pub fn get_hamuts(&mut self) -> &'h ProgramH<'s, 'h> {
      // Scala called `vonHammer.vonifyProgram(hamuts)` here as a discarded
      // side effect (parity-checking the von tree). VonHammer was retired
      // alongside `pass_manager::build`'s JSON output path; the sanity
      // check went with it.
      self.hammer_compilation.get_hamuts()
  }
  
  // The following seven methods drive the reference backend (Vivem/HeapV/ReferenceV/
  // PrimitiveKindV). They're sliced as panic stubs because their bodies all delegate
  // into Vivem, which is itself entirely panic-stubbed (180 fns across the testvm module).
  //
  // Rust adaptation note: Scala has overloads `evalForKind ×3` and `run ×2` that share
  // the same name but differ by arg shape. Rust requires distinct fn names, so each
  // variant gets a suffix describing its arg shape, e.g. `_heap_args` / `_primitive_args`
  // / `_primitive_args_with_stdin`. evalForStdout and evalForKindAndStdout don't overload,
  // so they keep their Scala names verbatim (in snake_case).

  pub fn eval_for_kind_heap_args<'v>(&self, _heap: HeapV<'v, 'h, 's>, _args: Vec<ReferenceV<'v, 'h, 's>>) -> IVonData { panic!("Unimplemented: eval_for_kind_heap_args"); }
  
  pub fn run_heap_args<'v>(&mut self, mut heap: HeapV<'v, 'h, 's>, args: Vec<ReferenceV<'v, 'h, 's>>) -> Result<(), VmRuntimeErrorV<'s>> {
      let interner = self.hammer_compilation.interner;
      let scout_arena = self.hammer_compilation.scout_arena;
      let hamuts = self.get_hamuts();
      let input_argument_references: &'v [ReferenceV<'v, 'h, 's>] = heap.vivem_bump.alloc_slice_copy(&args);
      execute_with_heap(
          hamuts, interner, scout_arena, &mut heap, input_argument_references, &empty_stdin, &regular_stdout,
      ).map(|_| ())
  }
  
  pub fn run_primitive_args<'v>(&mut self, args: Vec<PrimitiveKindV<'v, 'h, 's>>) -> Result<(), VmRuntimeErrorV<'s>> {
      let interner = self.hammer_compilation.interner;
      let scout_arena = self.hammer_compilation.scout_arena;
      let hamuts = self.get_hamuts();
      let mut vivem_dout = stdout();
      let vivem_bump = bumpalo::Bump::new();
      execute_with_primitive_args(
          hamuts, interner, scout_arena, &args, &mut vivem_dout, &vivem_bump, &empty_stdin, &regular_stdout,
      ).map(|_| ())
  }
  
  pub fn eval_for_kind_primitive_args<'v>(&mut self, args: Vec<PrimitiveKindV<'v, 'h, 's>>) -> Result<IVonData, VmRuntimeErrorV<'s>> {
      let interner = self.hammer_compilation.interner;
      let scout_arena = self.hammer_compilation.scout_arena;
      let hamuts = self.get_hamuts();
      let mut vivem_dout = stdout();
      let vivem_bump = bumpalo::Bump::new();
      execute_with_primitive_args(
          hamuts, interner, scout_arena, &args, &mut vivem_dout, &vivem_bump, &empty_stdin, &regular_stdout,
      )
  }
  
  pub fn eval_for_kind_primitive_args_with_stdin<'v>(&mut self, args: Vec<PrimitiveKindV<'v, 'h, 's>>, stdin: Vec<String>) -> Result<IVonData, VmRuntimeErrorV<'s>> {
      let scout_arena = self.hammer_compilation.scout_arena;
      let interned_stdin: Vec<StrI<'s>> = stdin.iter().map(|s| scout_arena.intern_str(s)).collect();
      let interner = self.hammer_compilation.interner;
      let hamuts = self.get_hamuts();
      let mut vivem_dout = stdout();
      let vivem_bump = bumpalo::Bump::new();
      let stdin_fn = stdin_from_list(&interned_stdin);
      execute_with_primitive_args(
          hamuts, interner, scout_arena, &args, &mut vivem_dout, &vivem_bump, &*stdin_fn, &regular_stdout,
      )
  }
  
  pub fn eval_for_stdout<'v>(&mut self, args: Vec<PrimitiveKindV<'v, 'h, 's>>) -> Result<String, VmRuntimeErrorV<'s>> {
      let (stdoutput_string_builder, stdout_func) = stdout_collector::<'s>();
      let interner = self.hammer_compilation.interner;
      let scout_arena = self.hammer_compilation.scout_arena;
      let hamuts = self.get_hamuts();
      let mut vivem_dout = stdout();
      let vivem_bump = bumpalo::Bump::new();
      execute_with_primitive_args(
          hamuts, interner, scout_arena, &args, &mut vivem_dout, &vivem_bump, &empty_stdin, &*stdout_func,
      )?;
      let result = stdoutput_string_builder.borrow().clone();
      Ok(result)
  }
  
  pub fn eval_for_kind_and_stdout<'v>(&mut self, args: Vec<PrimitiveKindV<'v, 'h, 's>>) -> Result<(IVonData, String), VmRuntimeErrorV<'s>> {
      let (stdoutput_string_builder, stdout_func) = stdout_collector::<'s>();
      let interner = self.hammer_compilation.interner;
      let scout_arena = self.hammer_compilation.scout_arena;
      let hamuts = self.get_hamuts();
      let mut vivem_dout = stdout();
      let vivem_bump = bumpalo::Bump::new();
      let kind = execute_with_primitive_args(
          hamuts, interner, scout_arena, &args, &mut vivem_dout, &vivem_bump, &empty_stdin, &*stdout_func,
      )?;
      let result = stdoutput_string_builder.borrow().clone();
      Ok((kind, result))
  }
  
}
