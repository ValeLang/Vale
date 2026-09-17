// Coordinates the Typing pass

use bumpalo::Bump;
use crate::compile_options::GlobalOptions;
use crate::higher_typing::HigherTypingCompilation;
use crate::instantiating::InstantiatorCompilationOptions;
use crate::scout_arena::ScoutArena;
use crate::keywords::Keywords;
use crate::lexing::ast::RangeL;
use crate::lexing::errors::FailedParse;
use crate::parsing::ast::FileP;
use crate::typing::compiler::Compiler;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::hinputs_t::HinputsT;
use crate::typing::typing_interner::TypingInterner;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::code_hierarchy::{IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use std::sync::Arc;
use crate::parse_arena::ParseArena;
use crate::postparsing::ast::ProgramS;
use crate::postparsing::post_parser::ICompileErrorS;
use std::marker::PhantomData;

/// Miscellaneous (see @TFITCX)
pub struct TypingPassOptions {
  pub global_options: GlobalOptions,
  pub debug_out: Arc<dyn Fn(&str) + Send + Sync>,
  pub tree_shaking_enabled: bool,
}

/// Miscellaneous (see @TFITCX)
pub struct TypingPassCompilation<'s, 'ctx, 't, 'p>
where 's: 't,
{
  higher_typing_compilation: HigherTypingCompilation<'s, 'ctx, 'p>,
  hinputs_cache: Option<HinputsT<'s, 't>>,
  scout_arena: &'ctx ScoutArena<'s>,
  keywords: &'ctx Keywords<'s>,
  options: TypingPassOptions,
  pub typing_interner: &'ctx TypingInterner<'s, 't>,
}

impl<'s, 'ctx, 't, 'p> TypingPassCompilation<'s, 'ctx, 't, 'p>
where 's: 't,
{
  // Rust adaptation (SPDMX Exception B): `typing_interner` is borrowed from the test wrapper (mirrors Scala's `val interner: Interner` constructor field on RunCompilation). The interner used to be constructed internally from `typing_bump` here; now it lives at the test wrapper and is threaded in. `typing_bump` itself is dropped as a constructor param.
  pub fn new(
    typing_interner: &'ctx TypingInterner<'s, 't>,
    scout_arena: &'ctx ScoutArena<'s>,
    keywords: &'ctx Keywords<'s>,
    parser_keywords: &'ctx Keywords<'p>,
    parse_arena: &'ctx ParseArena<'p>,
    packages_to_build: Vec<&'p PackageCoordinate<'p>>,
    package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
    global_options: GlobalOptions,
    instantiator_options: InstantiatorCompilationOptions,
  ) -> Self {
    let typing_options = TypingPassOptions {
      global_options,
      debug_out: instantiator_options.debug_out.clone(),
      tree_shaking_enabled: true,
    };

    let higher_typing_compilation = HigherTypingCompilation::new(
      scout_arena,
      keywords,
      parser_keywords,
      parse_arena,
      packages_to_build,
      package_to_contents_resolver,
      typing_options.global_options.clone(),
    );

    TypingPassCompilation {
      higher_typing_compilation,
      hinputs_cache: None,
      scout_arena,
      keywords,
      options: typing_options,
      typing_interner,
    }
  }
  
pub fn get_code_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
  self.higher_typing_compilation.get_code_map()
}

pub fn scout_arena_for_tests(&self) -> &'ctx ScoutArena<'s> {
  self.scout_arena
}

pub fn get_parseds(&mut self) -> Result<FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)>, FailedParse<'p>> {
  self.higher_typing_compilation.get_parseds()
}

pub fn get_vpst_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
  self.higher_typing_compilation.get_vpst_map()
}

pub fn get_scoutput(&mut self) -> Result<&FileCoordinateMap<'s, ProgramS<'s>>, ICompileErrorS<'s>> {
  self.higher_typing_compilation.get_scoutput()
}

pub fn get_astrouts(&mut self) -> Result<&crate::utils::code_hierarchy::PackageCoordinateMap<'s, crate::higher_typing::ast::ProgramA<'s>>, crate::higher_typing::astronomer_error_reporter::ICompileErrorA<'s>> {
  self.higher_typing_compilation.get_astrouts()
}

pub fn get_compiler_outputs(&mut self) -> Result<&HinputsT<'s, 't>, ICompileErrorT<'s, 't>> {
  if self.hinputs_cache.is_some() {
    return Ok(self.hinputs_cache.as_ref().unwrap());
  }
  let code_map = self.get_code_map().expect("getCodeMap failed");
  let astrouts = self.higher_typing_compilation.expect_astrouts();
  let compiler = Compiler::new(self.scout_arena, &self.typing_interner, self.keywords, &self.options);
  match compiler.evaluate(&code_map, astrouts) {
    Err(e) => Err(e),
    Ok(hinputs) => {
      self.hinputs_cache = Some(hinputs);
      Ok(self.hinputs_cache.as_ref().unwrap())
    }
  }
}
  
pub fn expect_compiler_outputs(&mut self) -> &HinputsT<'s, 't> {

  match self.get_compiler_outputs() {

    Err(_err) => panic!("Not yet implemented: CompilerErrorHumanizer.humanize"),

    Ok(x) => x,
  }
}
  
  pub fn cached_compiler_outputs(&self) -> &HinputsT<'s, 't> {
    self.hinputs_cache.as_ref().expect("compiler outputs not computed")
  }
  
}
