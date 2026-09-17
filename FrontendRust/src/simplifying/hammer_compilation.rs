// Coordinates the Hammer (simplifying) pass

use bumpalo::Bump;
use crate::compile_options::GlobalOptions;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::simplifying::hammer::Hammer;
use crate::instantiating::instantiated_compilation::{InstantiatedCompilation, InstantiatorCompilationOptions};
use crate::utils::code_hierarchy::{IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use std::sync::Arc;
use crate::final_ast::ast::ProgramH;
use crate::instantiating::ast::hinputs::HinputsI;
use crate::lexing::ast::RangeL;
use crate::lexing::errors::FailedParse;
use crate::parsing::ast::FileP;
use crate::postparsing::ast::ProgramS;
use crate::postparsing::post_parser::ICompileErrorS;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::hinputs_t::HinputsT;
use crate::typing::typing_interner::TypingInterner;
use crate::utils::code_hierarchy::FileCoordinateMap;

//
// Drops PartialEq/Eq/Hash because `debug_out: Arc<dyn Fn(...)>` and
// `global_options: GlobalOptions` don't impl them. Scala uses vcurious.
pub struct HammerCompilationOptions {
  pub debug_out: Arc<dyn Fn(&str) + Send + Sync>,
  pub global_options: GlobalOptions,
}
// (Realized by `impl Hash for HammerCompilationOptions` or `#[derive(Hash)]`.)

// (Realized by `impl PartialEq for HammerCompilationOptions` or `#[derive(PartialEq)]`.)

// Mirrors Scala's `object HammerCompilationOptions { def apply() }` zero-arg
// constructor (the case-class default-arg apply pattern). Defaults match
impl HammerCompilationOptions {
  pub fn new() -> Self {
    HammerCompilationOptions {
      debug_out: Arc::new(|x: &str| println!("##: {}", x)),
      global_options: GlobalOptions {
        sanity_check: false,
        use_overload_index: false,
        use_optimized_solver: true,
        verbose_errors: false,
        debug_output: false,
      },
    }
  }
}

pub struct HammerCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where 's: 'h, 's: 'i,
{
  pub interner: &'ctx HammerInterner<'s, 'h>,
  pub keywords: &'ctx Keywords<'s>,
  pub scout_arena: &'ctx ScoutArena<'s>,
  pub packages_to_build: Vec<&'ctx PackageCoordinate<'p>>,
  pub package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
  pub options: HammerCompilationOptions,
  pub instantiated_compilation: InstantiatedCompilation<'s, 'ctx, 't, 'i, 'p>,
  pub hamuts_cache: Option<&'h ProgramH<'s, 'h>>,
  // Scala has `var vonHammerCache: Option[VonHammer]`. Dropped: per
  // typing-pass precedent the VonHammer compiler class was collapsed
  // onto `Hammer` (no separate VonHammer state), so there is nothing
  // to cache.
}

impl<'s, 'h, 'ctx, 't, 'i, 'p> HammerCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where 's: 'h, 's: 't, 's: 'i, 'p: 'ctx,
{
  // Rust adaptation (SPDMX Exception B): `typing_interner` borrowed from test wrapper, threaded down (mirrors Scala `val interner` flowing through the pipeline). `typing_bump` is no longer needed here because TypingInterner is constructed at the test site.
  pub fn new(
    scout_arena: &'ctx ScoutArena<'s>,
    interner: &'ctx HammerInterner<'s, 'h>,
    typing_interner: &'ctx TypingInterner<'s, 't>,
    keywords: &'ctx Keywords<'s>,
    parser_keywords: &'ctx Keywords<'p>,
    parse_arena: &'ctx ParseArena<'p>,
    packages_to_build: Vec<&'p PackageCoordinate<'p>>,
    package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
    options: HammerCompilationOptions,
    instantiating_bump: &'i Bump,
  ) -> Self {
    let instantiated_compilation =
      InstantiatedCompilation::new(
        typing_interner,
        scout_arena,
        keywords,
        parser_keywords,
        parse_arena,
        packages_to_build.clone(),
        package_to_contents_resolver,
        options.global_options.clone(),
        InstantiatorCompilationOptions {
          debug_out: options.debug_out.clone(),
        },
        instantiating_bump);
    HammerCompilation {
      interner,
      keywords,
      scout_arena,
      packages_to_build,
      package_to_contents_resolver,
      options,
      instantiated_compilation,
      hamuts_cache: None,
    }
  }
}

impl<'s, 'h, 'ctx, 't, 'i, 'p> HammerCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where 's: 'h, 's: 'i, 'i: 'h,
{
  pub fn get_code_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
    self.instantiated_compilation.get_code_map()
  }

  pub fn get_parseds(&mut self) -> Result<FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)>, FailedParse<'p>> {
    self.instantiated_compilation.get_parseds()
  }

  pub fn get_vpst_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
    self.instantiated_compilation.get_vpst_map()
  }

  pub fn get_scoutput(&mut self) -> Result<&FileCoordinateMap<'s, ProgramS<'s>>, ICompileErrorS<'s>> {
    self.instantiated_compilation.get_scoutput()
  }

  pub fn get_astrouts(&mut self) -> Result<&crate::utils::code_hierarchy::PackageCoordinateMap<'s, crate::higher_typing::ast::ProgramA<'s>>, crate::higher_typing::astronomer_error_reporter::ICompileErrorA<'s>> {
    self.instantiated_compilation.get_astrouts()
  }

  pub fn get_compiler_outputs(&mut self) -> Result<&HinputsT<'s, 't>, ICompileErrorT<'s, 't>> {
    self.instantiated_compilation.get_compiler_outputs()
  }

  pub fn get_monouts(&mut self) -> &HinputsI<'s, 'i> {
    self.instantiated_compilation.get_monouts()
  }

  pub fn expect_compiler_outputs(&mut self) -> &HinputsT<'s, 't> {
    self.instantiated_compilation.expect_compiler_outputs()
  }

  pub fn get_hamuts(&mut self) -> &'h ProgramH<'s, 'h> {
    match self.hamuts_cache {
      Some(hamuts) => hamuts,
      None => {
        self.instantiated_compilation.get_monouts();
        let hinputs = self.instantiated_compilation.cached_monouts();
        let hammer = Hammer { interner: self.interner, keywords: self.keywords, scout_arena: self.scout_arena, instantiating_interner: &self.instantiated_compilation.instantiating_interner };
        let hamuts = hammer.translate(hinputs);
        self.hamuts_cache = Some(hamuts);
        hamuts
      }
    }
  }
}

