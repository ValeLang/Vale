use bumpalo::Bump;
use crate::parse_arena::ParseArena;
use crate::keywords::Keywords;
use crate::parsing::tests::parser_test_compilation;
use std::fs;
use std::path::PathBuf;
use crate::utils::code_hierarchy::{FileCoordinateMap, IPackageResolver, PackageCoordinate};
use std::collections::HashMap;

fn load_expected(path: &str) -> String {
  let full_path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
    .join("src/tests")
    .join(path);
  fs::read_to_string(&full_path)
    .unwrap_or_else(|e| panic!("Failed to load sample '{}': {} ({:?})", path, e, full_path))
}

fn parse<'p, 'ctx>(
  path: &str,
  parse_arena: &'ctx ParseArena<'p>,
  keywords: &'ctx Keywords<'p>,
  resolver: &'ctx dyn IPackageResolver<'p, HashMap<String, String>>,
  test_package_coord: &'p PackageCoordinate<'p>,
)
where
  'p: 'ctx,
{
  let mut compilation = parser_test_compilation::test(parse_arena, keywords, resolver, test_package_coord);
  compilation
    .get_parseds()
    .unwrap_or_else(|e| panic!("Failed to parse sample '{}': {:?}", path, e));
}

struct ParserTestResolver<'p> {
  code_map: FileCoordinateMap<'p, String>,
}
impl<'p> IPackageResolver<'p, HashMap<String, String>> for ParserTestResolver<'p> {
  fn resolve(&self, package_coord: &'p PackageCoordinate<'p>) -> Option<HashMap<String, String>> {
    // For testing the parser, we dont want it to fetch things with import statements.
    Some(
      self
        .code_map
        .resolve(package_coord)
        .unwrap_or_else(|| HashMap::from([("".to_string(), "".to_string())])),
    )
  }
}

macro_rules! parse_sample_test {
  ($name:ident, $path:literal) => {
    #[test]
    fn $name() {
      let parse_bump = Bump::new();
      let parse_arena = ParseArena::new(&parse_bump);
      let keywords = Keywords::new_for_parse(&parse_arena);

      let test_module = parse_arena.intern_str("test");
      let test_package_coord = parse_arena.intern_package_coordinate(test_module, &[]);

      let code: &[String] = &[load_expected($path)];

      let mut code_map = FileCoordinateMap::new();
      for (index, contents) in code.iter().enumerate() {
        let filepath = if code.len() == 1 {
          "test.vale".to_string()
        } else {
          format!("{}.vale", index)
        };
        let file_coord = parse_arena.intern_file_coordinate(test_package_coord, &filepath);
        code_map.put(&file_coord, contents.clone());
      }

      let resolver = ParserTestResolver { code_map };

      parse($path, &parse_arena, &keywords, &resolver, &test_package_coord);
    }
  };
}

parse_sample_test!(parse_sample_000, "optutils/optutils.vale");
parse_sample_test!(parse_sample_001, "printutils/printutils.vale");
parse_sample_test!(parse_sample_002, "ioutils/ioutils.vale");
parse_sample_test!(parse_sample_003, "array/indices/indices.vale");
parse_sample_test!(parse_sample_004, "array/iter/iter.vale");
parse_sample_test!(parse_sample_005, "array/each/each.vale");
parse_sample_test!(parse_sample_006, "array/drop_into/drop_into.vale");
parse_sample_test!(parse_sample_007, "array/make/make.vale");
parse_sample_test!(parse_sample_008, "array/has/has.vale");
parse_sample_test!(parse_sample_009, "listprintutils/listprintutils.vale");
parse_sample_test!(parse_sample_010, "logic/logic.vale");
parse_sample_test!(parse_sample_011, "math/math.vale");
parse_sample_test!(parse_sample_012, "hashmap/hashmap.vale");
parse_sample_test!(parse_sample_013, "intrange/intrange.vale");
parse_sample_test!(parse_sample_014, "programs/lambdas/doubleclosure.vale");
parse_sample_test!(parse_sample_015, "programs/lambdas/mutate.vale");
parse_sample_test!(parse_sample_016, "programs/lambdas/lambda.vale");
parse_sample_test!(parse_sample_017, "programs/lambdas/lambdamut.vale");
parse_sample_test!(parse_sample_018, "programs/comparei64.vale");
parse_sample_test!(parse_sample_019, "programs/unreachablemoot.vale");
parse_sample_test!(parse_sample_020, "programs/constraintRef.vale");
parse_sample_test!(parse_sample_021, "programs/add64ret.vale");
parse_sample_test!(parse_sample_022, "programs/concatstrfloat.vale");
parse_sample_test!(parse_sample_023, "programs/strings/smallstr.vale");
parse_sample_test!(parse_sample_024, "programs/strings/complex/main.vale");
parse_sample_test!(parse_sample_025, "programs/strings/stradd.vale");
parse_sample_test!(parse_sample_026, "programs/strings/strprint.vale");
parse_sample_test!(parse_sample_027, "programs/strings/strneq.vale");
parse_sample_test!(parse_sample_028, "programs/strings/i64tostr.vale");
parse_sample_test!(parse_sample_029, "programs/strings/inttostr.vale");
parse_sample_test!(parse_sample_030, "programs/strings/strlen.vale");
parse_sample_test!(parse_sample_031, "programs/externs/structimmparamextern/test.vale");
parse_sample_test!(parse_sample_032, "programs/externs/structimmparamexport/test.vale");
parse_sample_test!(parse_sample_033, "programs/externs/strreturnexport/test.vale");
parse_sample_test!(parse_sample_034, "programs/externs/voidreturnextern/test.vale");
parse_sample_test!(parse_sample_035, "programs/externs/voidreturnexport/test.vale");
parse_sample_test!(parse_sample_036, "programs/externs/structmutreturnexport/test.vale");
parse_sample_test!(parse_sample_037, "programs/externs/structmutparamexport/test.vale");
parse_sample_test!(parse_sample_038, "programs/externs/structimmparamdeepextern/test.vale");
parse_sample_test!(parse_sample_039, "programs/externs/ssaimmparamdeepextern/test.vale");
parse_sample_test!(parse_sample_040, "programs/externs/rsaimmparamdeepexport/test.vale");
parse_sample_test!(parse_sample_041, "programs/externs/tupleparamextern/test.vale");
parse_sample_test!(parse_sample_042, "programs/externs/ssamutreturnexport/test.vale");
parse_sample_test!(parse_sample_043, "programs/externs/structimmparamdeepexport/test.vale");
parse_sample_test!(parse_sample_044, "programs/externs/ssaimmparamdeepexport/test.vale");
parse_sample_test!(parse_sample_045, "programs/externs/rsaimmparamdeepextern/test.vale");
parse_sample_test!(parse_sample_046, "programs/externs/rsaimmparamextern/test.vale");
parse_sample_test!(parse_sample_047, "programs/externs/rsaimmreturnexport/test.vale");
parse_sample_test!(parse_sample_048, "programs/externs/export.vale");
parse_sample_test!(parse_sample_049, "programs/externs/rsaimmparamexport/test.vale");
parse_sample_test!(parse_sample_050, "programs/externs/rsaimmreturnextern/test.vale");
parse_sample_test!(parse_sample_051, "programs/externs/interfaceimmreturnexport/test.vale");
parse_sample_test!(parse_sample_052, "programs/externs/interfacemutparamexport/test.vale");
parse_sample_test!(parse_sample_053, "programs/externs/interfaceimmreturnextern/test.vale");
parse_sample_test!(parse_sample_054, "programs/externs/strlenextern/test.vale");
parse_sample_test!(parse_sample_055, "programs/externs/ssamutparamexport/test.vale");
parse_sample_test!(parse_sample_056, "programs/externs/rsamutreturnexport/test.vale");
parse_sample_test!(parse_sample_057, "programs/externs/ssaimmreturnextern/test.vale");
parse_sample_test!(parse_sample_058, "programs/externs/interfaceimmparamdeepextern/test.vale");
parse_sample_test!(parse_sample_059, "programs/externs/ssaimmreturnexport/test.vale");
parse_sample_test!(parse_sample_060, "programs/externs/rsamutparamexport/test.vale");
parse_sample_test!(parse_sample_061, "programs/externs/interfaceimmparamdeepexport/test.vale");
parse_sample_test!(parse_sample_062, "programs/externs/extern.vale");
parse_sample_test!(parse_sample_063, "programs/externs/interfaceimmparamextern/test.vale");
parse_sample_test!(parse_sample_064, "programs/externs/tupleretextern/test.vale");
parse_sample_test!(parse_sample_065, "programs/externs/interfaceimmparamexport/test.vale");
parse_sample_test!(parse_sample_066, "programs/externs/structmutparamdeepexport/test.vale");
parse_sample_test!(parse_sample_067, "programs/externs/ssaimmparamextern/test.vale");
parse_sample_test!(parse_sample_068, "programs/externs/interfacemutreturnexport/test.vale");
parse_sample_test!(parse_sample_069, "programs/externs/ssaimmparamexport/test.vale");
parse_sample_test!(parse_sample_070, "programs/weaks/callWeakSelfMethodAfterDrop.vale");
parse_sample_test!(parse_sample_071, "programs/weaks/callWeakSelfMethodWhileLive.vale");
parse_sample_test!(parse_sample_072, "programs/weaks/dropThenLockInterface.vale");
parse_sample_test!(parse_sample_073, "programs/weaks/lockWhileLiveInterface.vale");
parse_sample_test!(parse_sample_074, "programs/weaks/dropWhileLockedStruct.vale");
parse_sample_test!(parse_sample_075, "programs/weaks/weakFromCRefInterface.vale");
parse_sample_test!(parse_sample_076, "programs/weaks/weakFromCRefStruct.vale");
parse_sample_test!(parse_sample_077, "programs/weaks/lockWhileLiveStruct.vale");
parse_sample_test!(parse_sample_078, "programs/weaks/loadFromWeakable.vale");
parse_sample_test!(parse_sample_079, "programs/weaks/dropWhileLockedInterface.vale");
parse_sample_test!(parse_sample_080, "programs/weaks/weakFromLocalCRefStruct.vale");
parse_sample_test!(parse_sample_081, "programs/weaks/weakFromLocalCRefInterface.vale");
parse_sample_test!(parse_sample_082, "programs/weaks/dropThenLockStruct.vale");
parse_sample_test!(parse_sample_083, "programs/readwriteufcs.vale");
parse_sample_test!(parse_sample_084, "programs/tuples/immtupleaccess.vale");
parse_sample_test!(parse_sample_085, "programs/downcast/downcastPointerFailed.vale");
parse_sample_test!(parse_sample_086, "programs/downcast/downcastPointerSuccess.vale");
parse_sample_test!(parse_sample_087, "programs/downcast/downcastBorrowFailed.vale");
parse_sample_test!(parse_sample_088, "programs/downcast/downcastOwningSuccessful.vale");
parse_sample_test!(parse_sample_089, "programs/downcast/downcastBorrowSuccessful.vale");
parse_sample_test!(parse_sample_090, "programs/downcast/downcastOwningFailed.vale");
parse_sample_test!(parse_sample_091, "programs/if/ifnevers.vale");
parse_sample_test!(parse_sample_092, "programs/if/nestedif.vale");
parse_sample_test!(parse_sample_093, "programs/if/if.vale");
parse_sample_test!(parse_sample_094, "programs/if/upcastif.vale");
parse_sample_test!(parse_sample_095, "programs/if/neverif.vale");
parse_sample_test!(parse_sample_096, "programs/virtuals/callingThroughBorrow.vale");
parse_sample_test!(parse_sample_097, "programs/virtuals/calling.vale");
parse_sample_test!(parse_sample_098, "programs/virtuals/round.vale");
parse_sample_test!(parse_sample_099, "programs/virtuals/upcasting.vale");
parse_sample_test!(parse_sample_100, "programs/virtuals/interfaceimm.vale");
parse_sample_test!(parse_sample_101, "programs/virtuals/retUpcast.vale");
parse_sample_test!(parse_sample_102, "programs/virtuals/ordinarylinkedlist.vale");
parse_sample_test!(parse_sample_103, "programs/virtuals/interfacemut.vale");
parse_sample_test!(parse_sample_104, "programs/genericvirtuals/getOr.vale");
parse_sample_test!(parse_sample_105, "programs/genericvirtuals/templatedinterface.vale");
parse_sample_test!(parse_sample_106, "programs/genericvirtuals/templatedlinkedlist.vale");
parse_sample_test!(parse_sample_107, "programs/genericvirtuals/stampMultipleAncestors.vale");
parse_sample_test!(parse_sample_108, "programs/genericvirtuals/foreachlinkedlist.vale");
parse_sample_test!(parse_sample_109, "programs/genericvirtuals/mapFunc.vale");
parse_sample_test!(parse_sample_110, "programs/genericvirtuals/callingAbstract.vale");
parse_sample_test!(parse_sample_111, "programs/genericvirtuals/templatedoption.vale");
parse_sample_test!(parse_sample_112, "programs/addret.vale");
parse_sample_test!(parse_sample_113, "programs/nestedblocks.vale");
parse_sample_test!(parse_sample_114, "programs/panicnot.vale");
parse_sample_test!(parse_sample_115, "programs/floateq.vale");
parse_sample_test!(parse_sample_116, "programs/roguelike.vale");
parse_sample_test!(parse_sample_117, "programs/truncate.vale");
parse_sample_test!(parse_sample_118, "programs/multiUnstackify.vale");
parse_sample_test!(parse_sample_119, "programs/borrowRef.vale");
parse_sample_test!(parse_sample_120, "programs/structs/constructor.vale");
parse_sample_test!(parse_sample_121, "programs/structs/structmutstore.vale");
parse_sample_test!(parse_sample_122, "programs/structs/getMember.vale");
parse_sample_test!(parse_sample_123, "programs/structs/structs.vale");
parse_sample_test!(parse_sample_124, "programs/structs/mutate.vale");
parse_sample_test!(parse_sample_125, "programs/structs/deadmutstruct.vale");
parse_sample_test!(parse_sample_126, "programs/structs/structmutstoreinner.vale");
parse_sample_test!(parse_sample_127, "programs/structs/bigstructimm.vale");
parse_sample_test!(parse_sample_128, "programs/structs/structmut.vale");
parse_sample_test!(parse_sample_129, "programs/structs/structimm.vale");
parse_sample_test!(parse_sample_130, "programs/structs/memberrefcount.vale");
parse_sample_test!(parse_sample_131, "programs/arrays/ssaimmfromvalues.vale");
parse_sample_test!(parse_sample_132, "programs/arrays/rsamutdestroyintocallable.vale");
parse_sample_test!(parse_sample_133, "programs/arrays/ssamutfromcallable.vale");
parse_sample_test!(parse_sample_134, "programs/arrays/ssaimmfromcallable.vale");
parse_sample_test!(parse_sample_135, "programs/arrays/rsamutlen.vale");
parse_sample_test!(parse_sample_136, "programs/arrays/ssamutfromvalues.vale");
parse_sample_test!(parse_sample_137, "programs/arrays/rsaimmlen.vale");
parse_sample_test!(parse_sample_138, "programs/arrays/rsamut.vale");
parse_sample_test!(parse_sample_139, "programs/arrays/swaprsamutdestroy.vale");
parse_sample_test!(parse_sample_140, "programs/arrays/rsamutfromcallable.vale");
parse_sample_test!(parse_sample_141, "programs/arrays/rsaimm.vale");
parse_sample_test!(parse_sample_142, "programs/arrays/rsaimmfromcallable.vale");
parse_sample_test!(parse_sample_143, "programs/arrays/rsamutcapacity.vale");
parse_sample_test!(parse_sample_144, "programs/arrays/ssamutdestroyintocallable.vale");
parse_sample_test!(parse_sample_145, "programs/arrays/inlssaimm.vale");
parse_sample_test!(parse_sample_146, "programs/ufcs.vale");
parse_sample_test!(parse_sample_147, "programs/functions/overloads.vale");
parse_sample_test!(parse_sample_148, "programs/functions/recursion.vale");
parse_sample_test!(parse_sample_149, "programs/printfloat.vale");
parse_sample_test!(parse_sample_150, "programs/mutswaplocals.vale");
parse_sample_test!(parse_sample_151, "programs/mutlocal.vale");
parse_sample_test!(parse_sample_152, "programs/unstackifyret.vale");
parse_sample_test!(parse_sample_153, "programs/while/while.vale");
parse_sample_test!(parse_sample_154, "programs/while/foreach.vale");
parse_sample_test!(parse_sample_155, "programs/invalidaccess.vale");
parse_sample_test!(parse_sample_156, "programs/floatarithmetic.vale");
parse_sample_test!(parse_sample_157, "programs/panic.vale");
parse_sample_test!(parse_sample_158, "list/list.vale");
parse_sample_test!(parse_sample_159, "ifunction/ifunction1/ifunction1.vale");
parse_sample_test!(parse_sample_160, "string/string.vale");
parse_sample_test!(parse_sample_161, "panicutils/panicutils.vale");
parse_sample_test!(parse_sample_162, "castutils/castutils.vale");

