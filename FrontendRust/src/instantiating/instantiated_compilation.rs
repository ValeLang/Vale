// Coordinates the Instantiating pass

use bumpalo::Bump;
use crate::scout_arena::ScoutArena;
use crate::keywords::Keywords;
use crate::lexing::ast::RangeL;
use crate::lexing::errors::FailedParse;
use crate::parsing::ast::FileP;
// Simplifying pass unlinked during instantiating bring-up (Slabs 16a–16j).
// use crate::simplifying::HammerCompilationOptions;
use crate::typing::TypingPassCompilation;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::code_hierarchy::{IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use std::sync::Arc;
use crate::parse_arena::ParseArena;
use crate::instantiating::instantiating_interner::InstantiatingInterner;
use crate::instantiating::ast::hinputs::HinputsI;
use crate::instantiating::instantiator;
use crate::compile_options::GlobalOptions;
use crate::postparsing::ast::ProgramS;
use crate::postparsing::post_parser::ICompileErrorS;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::hinputs_t::HinputsT;
use crate::typing::typing_interner::TypingInterner;
use std::any::Any;

pub struct InstantiatorCompilationOptions {
  pub debug_out: Arc<dyn Fn(&str) + Send + Sync>,
}

pub struct InstantiatedCompilation<'s, 'ctx, 't, 'i, 'p>
where 's: 't, 's: 'i,
{
  pub typing_pass_compilation: TypingPassCompilation<'s, 'ctx, 't, 'p>,
  // Retained from `new` to feed `Instantiator::translate` in `get_monouts`
  // (Scala's `val keywords` + `options` class fields).
  keywords: &'ctx Keywords<'s>,
  global_options: GlobalOptions,
  // The instantiating arena's interner, built from the externally-owned 'i Bump
  // passed to `new` — mirrors TypingPassCompilation's `typing_interner` (built
  // from `typing_bump: &'t Bump`).
  pub instantiating_interner: InstantiatingInterner<'s, 'i>,
  monouts_cache: Option<HinputsI<'s, 'i>>,
}

impl<'s, 'ctx, 't, 'i, 'p> InstantiatedCompilation<'s, 'ctx, 't, 'i, 'p>
where
    's: 't,
    's: 'i,
    'p: 'ctx,
{
  // Rust adaptation (SPDMX Exception B): `typing_interner` borrowed from test wrapper, threaded down to TypingPassCompilation (mirrors Scala `val interner` flowing from RunCompilation through the pipeline).
  pub fn new(
    typing_interner: &'ctx TypingInterner<'s, 't>,
    scout_arena: &'ctx ScoutArena<'s>,
    keywords: &'ctx Keywords<'s>,
    parser_keywords: &'ctx Keywords<'p>,
    parse_arena: &'ctx ParseArena<'p>,
    packages_to_build: Vec<&'p PackageCoordinate<'p>>,
    package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
    global_options: GlobalOptions,
    options: InstantiatorCompilationOptions,
    instantiating_bump: &'i Bump,
  ) -> Self {
    let typing_options = InstantiatorCompilationOptions {
      debug_out: options.debug_out.clone(),
    };

    let typing_pass_compilation = TypingPassCompilation::new(
      typing_interner,
      scout_arena,
      keywords,
      parser_keywords,
      parse_arena,
      packages_to_build,
      package_to_contents_resolver,
      global_options.clone(),
      typing_options,
    );

    let instantiating_interner = InstantiatingInterner::new(instantiating_bump);

    InstantiatedCompilation {
      typing_pass_compilation,
      keywords,
      global_options,
      instantiating_interner,
      monouts_cache: None,
    }
  }
}

impl<'s, 'ctx, 't, 'i, 'p> InstantiatedCompilation<'s, 'ctx, 't, 'i, 'p>
where
    's: 't,
    's: 'i,
    'p: 'ctx,
{
  pub fn get_code_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
    self.typing_pass_compilation.get_code_map()
  }

  pub fn get_parseds(&mut self) -> Result<FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)>, FailedParse<'p>> {
    self.typing_pass_compilation.get_parseds()
  }

  pub fn get_vpst_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
    self.typing_pass_compilation.get_vpst_map()
  }

  pub fn get_scoutput(&mut self) -> Result<&FileCoordinateMap<'s, ProgramS<'s>>, ICompileErrorS<'s>> {
    self.typing_pass_compilation.get_scoutput()
  }

  pub fn get_astrouts(&mut self) -> Result<&crate::utils::code_hierarchy::PackageCoordinateMap<'s, crate::higher_typing::ast::ProgramA<'s>>, crate::higher_typing::astronomer_error_reporter::ICompileErrorA<'s>> {
    self.typing_pass_compilation.get_astrouts()
  }

  pub fn get_compiler_outputs(&mut self) -> Result<&HinputsT<'s, 't>, ICompileErrorT<'s, 't>> {
    self.typing_pass_compilation.get_compiler_outputs()
  }

  pub fn expect_compiler_outputs(&mut self) -> &HinputsT<'s, 't> {
    self.typing_pass_compilation.expect_compiler_outputs()
  }

  // Returns HinputsI<'s, 'i>, where 'i is the InstantiatedCompilation's own
  // instantiating-arena lifetime (the `instantiating_interner` field), mirroring
  // how TypingPassCompilation::get_compiler_outputs returns HinputsT<'s, 't>.
  pub fn get_monouts(&mut self) -> &HinputsI<'s, 'i> {
    if self.monouts_cache.is_some() {
      return self.monouts_cache.as_ref().unwrap();
    }
    // Populate the typing-pass output cache (the `&mut` borrow ends here), so the two reads below —
    // the typing_interner and the cached outputs, both fields of typing_pass_compilation — can coexist.
    self.typing_pass_compilation.expect_compiler_outputs();
    let monouts =
      instantiator::translate(
        &self.global_options, &self.instantiating_interner, &self.typing_pass_compilation.typing_interner, self.keywords, self.typing_pass_compilation.cached_compiler_outputs());
    self.monouts_cache = Some(monouts);
    self.monouts_cache.as_ref().unwrap()
  }

  pub fn cached_monouts(&self) -> &HinputsI<'s, 'i> {
    self.monouts_cache.as_ref().expect("monouts not computed")
  }
  
}