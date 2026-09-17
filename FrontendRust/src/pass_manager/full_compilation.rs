// Coordinates the full compilation pipeline

use bumpalo::Bump;
use crate::compile_options::GlobalOptions;
use crate::scout_arena::ScoutArena;
use crate::keywords::Keywords;
use crate::lexing::ast::RangeL;
use crate::lexing::errors::FailedParse;
use crate::parsing::ast::FileP;
use crate::utils::code_hierarchy::FileCoordinateMap;
use crate::utils::code_hierarchy::{IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use std::sync::Arc;
use crate::parse_arena::ParseArena;
use crate::simplifying::hammer_compilation::{HammerCompilation, HammerCompilationOptions};
use crate::simplifying::hammer_interner::HammerInterner;
use crate::typing::typing_interner::TypingInterner;
use crate::final_ast::ast::ProgramH;
use crate::instantiating::ast::hinputs::HinputsI;
use crate::postparsing::ast::ProgramS;
use crate::postparsing::post_parser::ICompileErrorS;
use crate::typing::compiler_error_reporter::ICompileErrorT;
use crate::typing::hinputs_t::HinputsT;

pub struct FullCompilationOptions {
  pub global_options: GlobalOptions,
  pub debug_out: Arc<dyn Fn(&str) + Send + Sync>,
}

pub struct FullCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where
  's: 'h,
  's: 't,
  's: 'i,
  'p: 'ctx,
{
  pub hammer_compilation: HammerCompilation<'s, 'h, 'ctx, 't, 'i, 'p>,
}

impl<'s, 'h, 'ctx, 't, 'i, 'p> FullCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where
  's: 'h,
  's: 't,
  's: 'i,
  'p: 'ctx,
{
  pub fn new(
    scout_arena: &'ctx ScoutArena<'s>,
    interner: &'ctx HammerInterner<'s, 'h>,
    typing_interner: &'ctx TypingInterner<'s, 't>,
    keywords: &'ctx Keywords<'s>,
    parser_keywords: &'ctx Keywords<'p>,
    // VV: crate::
    parse_arena: &'ctx ParseArena<'p>,
    packages_to_build: Vec<&'p PackageCoordinate<'p>>,
    package_to_contents_resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
    options: FullCompilationOptions,
    instantiating_bump: &'i Bump,
  ) -> Self {
    let hammer_options = HammerCompilationOptions {
      debug_out: options.debug_out,
      global_options: options.global_options,
    };
    let hammer_compilation = HammerCompilation::new(
      scout_arena,
      interner,
      typing_interner,
      keywords,
      parser_keywords,
      parse_arena,
      packages_to_build,
      package_to_contents_resolver,
      hammer_options,
      instantiating_bump,
    );
    FullCompilation { hammer_compilation }
  }

}

impl<'s, 'h, 'ctx, 't, 'i, 'p> FullCompilation<'s, 'h, 'ctx, 't, 'i, 'p>
where 's: 'h, 's: 't, 's: 'i, 'p: 'ctx, 'i: 'h,
{
  pub fn get_code_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
    self.hammer_compilation.get_code_map()
  }

  pub fn get_parseds(&mut self) -> Result<FileCoordinateMap<'p, (FileP<'p>, Vec<RangeL>)>, FailedParse<'p>> {
    self.hammer_compilation.get_parseds()
  }

  pub fn get_vpst_map(&mut self) -> Result<FileCoordinateMap<'p, String>, FailedParse<'p>> {
    self.hammer_compilation.get_vpst_map()
  }

  pub fn get_scoutput(&mut self) -> Result<&FileCoordinateMap<'s, ProgramS<'s>>, ICompileErrorS<'s>> {
    self.hammer_compilation.get_scoutput()
  }

  pub fn get_astrouts(&mut self) -> Result<&crate::utils::code_hierarchy::PackageCoordinateMap<'s, crate::higher_typing::ast::ProgramA<'s>>, crate::higher_typing::astronomer_error_reporter::ICompileErrorA<'s>> {
    self.hammer_compilation.get_astrouts()
  }

  pub fn get_compiler_outputs(&mut self) -> Result<&HinputsT<'s, 't>, ICompileErrorT<'s, 't>> {
    self.hammer_compilation.get_compiler_outputs()
  }

  pub fn expect_compiler_outputs(&mut self) -> &HinputsT<'s, 't> {
    self.hammer_compilation.expect_compiler_outputs()
  }

  pub fn get_hamuts(&mut self) -> &'h ProgramH<'s, 'h> {
    self.hammer_compilation.get_hamuts()
  }

  pub fn get_monouts(&mut self) -> &HinputsI<'s, 'i> {
    self.hammer_compilation.get_monouts()
  }
}

