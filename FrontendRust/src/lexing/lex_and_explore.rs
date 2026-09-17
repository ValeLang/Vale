use crate::interner::StrI;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::lexing::ast::{IDenizenL, ImportL, RangeL};
use crate::lexing::errors::FailedParse;
use crate::lexing::lexer::Lexer;
use crate::lexing::lexing_iterator::LexingIterator;
use crate::utils::code_hierarchy::{
  FileCoordinate, FileCoordinateMap, IPackageResolver, PackageCoordinate,
};
use std::collections::{HashMap, HashSet};
use std::sync::Arc;

/// Helper function that collects all denizens and files
/// TODO: Fix closure lifetime issues - collect pattern causes borrow checker to reject.
/// Workaround: Implement without using lex_and_explore's callback, or change handler to take owned data.
#[allow(dead_code)]
pub fn lex_and_explore_and_collect<'p, R>(
  _parse_arena: &ParseArena<'p>,
  _keywords: &Keywords<'p>,
  _packages: Vec<&'p PackageCoordinate<'p>>,
  _resolver: &R,
) -> Result<
  (
    Vec<(Arc<FileCoordinate<'p>>, String, Vec<ImportL<'p>>, IDenizenL<'p>)>,
    Vec<(Arc<FileCoordinate<'p>>, String, Vec<RangeL>, Vec<IDenizenL<'p>>)>,
  ),
  FailedParse<'p>,
>
where
  R: IPackageResolver<'p, HashMap<String, String>>,
{
  panic!("lex_and_explore_and_collect: closure lifetime fix needed")
  // Already tracked in docs/migration/todo.md line 47.
}

/// Main generic lexing function with import-driven package discovery
pub fn lex_and_explore<'p, 'ctx, D, F, R>(
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  packages: Vec<&'p PackageCoordinate<'p>>,
  resolver: &R,
  mut denizen_handler: impl FnMut(&'p FileCoordinate<'p>, &str, &[ImportL<'p>], &IDenizenL<'p>) -> D,
  mut file_handler: impl FnMut(&'p FileCoordinate<'p>, &str, &[RangeL], Vec<D>) -> F,
) -> Result<Vec<F>, FailedParse<'p>>
where
  'p: 'ctx,
  R: IPackageResolver<'p, HashMap<String, String>>,
{
  let mut unexplored_packages: HashSet<&'p PackageCoordinate<'p>> =
    packages.into_iter().collect();
  let mut started_packages: HashSet<PackageCoordinate<'p>> = HashSet::new();
  let mut already_found_file_to_code = FileCoordinateMap::<String>::new();

  let mut files_acc = Vec::new();

  while !unexplored_packages.is_empty() {
    let needed_package_coord = unexplored_packages.iter().next().cloned().unwrap();
    unexplored_packages.remove(&needed_package_coord);
    started_packages.insert(needed_package_coord.clone());

    let filepaths_and_contents: Vec<(&'p FileCoordinate<'p>, String)> = match resolver.resolve(&needed_package_coord) {
      None => {
        panic!("Couldn't find: {:?}", needed_package_coord);
      }
      Some(filepath_to_code) => {
        let mut result = Vec::new();
        for (filepath, code) in filepath_to_code {
          let file_coord = parse_arena.intern_file_coordinate(needed_package_coord, &filepath);
          result.push((file_coord, code));
        }
        result
      }
    };

    let filepaths_map: HashMap<&'p FileCoordinate<'p>, String> = filepaths_and_contents
      .iter()
    .map(|(fc, code)| (*fc, code.clone()))
      .collect();
    already_found_file_to_code.put_package(needed_package_coord, filepaths_map);

    for (file_coord, code) in filepaths_and_contents {
      let mut result_acc = Vec::new();

      let mut iter = LexingIterator::new(&code);
      let lexer = Lexer::<'p, 'ctx>::new(parse_arena, keywords);
      // Store (module, packages) as owned strings to avoid lexer borrow conflict.
      let mut packages_to_explore: Vec<(String, Vec<String>)> = Vec::new();

      iter.consume_comments_and_whitespace();

      let mut maybe_imports_accum: Option<Vec<ImportL>> = Some(Vec::new());
      let mut maybe_imports: Option<Vec<ImportL>> = None;

      // Imports must come first, so that we can ship these denizens off with all
      // their relevant imports.
      // Defer intern_package_coordinate until after the lex loop to avoid borrow conflicts.
      while !iter.at_end() {
        let denizen = match lexer.lex_denizen(&mut iter) {
          Err(e) => {
            return Err(FailedParse {
              code: code.clone(),
              file_coord: (*file_coord).clone(),
              error: e,
            })
          }
          Ok(x) => x,
        };
        iter.consume_comments_and_whitespace();

        match &denizen {
          IDenizenL::TopLevelImport(im) => {
            match &mut maybe_imports_accum {
              None => panic!("Imports must come before everything else"),
              Some(imports_accum) => imports_accum.push(im.clone()),
            }

            packages_to_explore.push((
              im.module_name.str.to_string(),
              im.package_steps.iter().map(|x| x.str.to_string()).collect(),
            ));

            let denizen_result = denizen_handler(file_coord, &code, &[], &denizen);
            result_acc.push(denizen_result);
          }
          _ => {
            match maybe_imports_accum.take() {
              None => {}
              Some(imports_accum) => {
                for imp in &imports_accum {
                  packages_to_explore.push((
                    imp.module_name.str.to_string(),
                    imp.package_steps.iter().map(|x| x.str.to_string()).collect(),
                  ));
                }
                maybe_imports = Some(imports_accum);
              }
            }

            let imports = maybe_imports
              .as_ref()
              .expect("maybe_imports should be Some");
            let denizen_result = denizen_handler(file_coord, &code, imports, &denizen);
            result_acc.push(denizen_result);
          }
        }
      }

      // Add discovered packages to unexplored (after lex loop to avoid borrow conflicts).
      for (module_str, package_strs) in packages_to_explore {
        let package_steps: Vec<StrI<'p>> =
          package_strs.iter().map(|s| parse_arena.intern_str(s)).collect();
        let coord = parse_arena.intern_package_coordinate(parse_arena.intern_str(&module_str), &package_steps);
        if !started_packages.contains(&*coord) {
          unexplored_packages.insert(&*coord);
        }
      }

      let comments_ranges = iter.comments.clone();
      let file = file_handler(file_coord, &code, &comments_ranges, result_acc);
      files_acc.push(file);
    }
  }

  Ok(files_acc)
}

