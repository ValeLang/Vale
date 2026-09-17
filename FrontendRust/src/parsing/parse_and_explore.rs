use crate::compile_options::GlobalOptions;
use crate::lexing::ast::{IDenizenL, ImportL, RangeL};
use crate::lexing::errors::FailedParse;
use crate::lexing::lex_and_explore;
use crate::parsing::ast::IDenizenP;
use crate::parsing::Parser;
use crate::utils::code_hierarchy::{FileCoordinate, IPackageResolver, PackageCoordinate};
use crate::Keywords;
// V: can we put the Keywords struct into the arena? so it doesnt have to be a separate thing...
// VA: Yes, with one fix: Keywords.tuple_human_name is Vec<StrI<'a>> (heap-allocated, AASSNCMCX
// VA: violation). Change it to &'a [StrI<'a>] (arena slice), then arena-allocate via
// VA: parse_arena.bump.alloc(Keywords::new_for_parse(...)). The result is &'p Keywords<'p> which
// VA: coerces to &'ctx Keywords<'p> at all existing call sites — no signature changes needed.
// VA: All other ~110 fields are StrI<'a> (Copy), so no other blockers.
use std::collections::HashMap;
use crate::parse_arena::ParseArena;

pub fn parse_and_explore<'p, 'ctx, D, F, R, HandleParsedDenizen, FileHandler>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  _opts: GlobalOptions,
  parser: &Parser<'p, 'ctx>,
  packages: Vec<&'p PackageCoordinate<'p>>,
  resolver: &R,
  mut handle_parsed_denizen: HandleParsedDenizen,
  mut file_handler: FileHandler,
) -> Result<Vec<F>, FailedParse<'p>>
where
  'p: 'ctx,
  R: IPackageResolver<'p, HashMap<String, String>>,
  HandleParsedDenizen: FnMut(&'p FileCoordinate<'p>, &str, &[ImportL<'p>], IDenizenP<'p>) -> D,
  FileHandler: FnMut(&'p FileCoordinate<'p>, &str, &[RangeL], Vec<D>) -> F,
{
  lex_and_explore::lex_and_explore(
    parse_arena,
    keywords,
    packages,
    resolver,
    |file_coord: &'p FileCoordinate<'p>,
     code: &str,
     imports: &[ImportL<'p>],
     denizen_l: &IDenizenL<'p>|
     -> D {
      let denizen_p: IDenizenP<'p> = match denizen_l {
        IDenizenL::TopLevelImport(import) => {
          IDenizenP::TopLevelImport(
            parser
              .parse_import(import.clone())
              .expect("parse_import failed - error handling not yet fully implemented"),
          )
        }
        IDenizenL::TopLevelFunction(function_l) => {
          IDenizenP::TopLevelFunction(
            parser
              .parse_function(function_l.clone(), false)
              .expect("parse_function failed - error handling not yet fully implemented"),
          )
        }
        IDenizenL::TopLevelStruct(struct_l) => {
          IDenizenP::TopLevelStruct(
            parser
              .parse_struct(struct_l.clone())
              .expect("parse_struct failed - error handling not yet fully implemented"),
          )
        }
        IDenizenL::TopLevelInterface(interface_l) => {
          IDenizenP::TopLevelInterface(
            parser
              .parse_interface(interface_l.clone())
              .expect("parse_interface failed - error handling not yet fully implemented"),
          )
        }
        IDenizenL::TopLevelImpl(impl_l) => {
          IDenizenP::TopLevelImpl(
            parser
              .parse_impl(impl_l.clone())
              .expect("parse_impl failed - error handling not yet fully implemented"),
          )
        }
        IDenizenL::TopLevelExportAs(export) => {
          IDenizenP::TopLevelExportAs(
            parser
              .parse_export_as(export.clone())
              .expect("parse_export_as failed - error handling not yet fully implemented"),
          )
        }
      };
      handle_parsed_denizen(file_coord, code, imports, denizen_p)
    },
    |file_coord: &'p FileCoordinate<'p>,
     code: &str,
     comment_ranges: &[RangeL],
     denizens: Vec<D>|
     -> F {
      file_handler(file_coord, code, comment_ranges, denizens)
    },
  )
}

