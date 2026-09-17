use crate::integration_tests::tests::run_compilation::test;
use crate::integration_tests::tests::run_compilation::test_no_builtins;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::typing::typing_interner::TypingInterner;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;

pub struct HashMapTest;

#[test]
fn monomorphize_problem() {
    // See NBIFP, the instantiator has to grab bounds from its params too

    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test_no_builtins(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
struct IntHasher { }
func __call(this &IntHasher, x int) int { return x; }

#!DeriveStructDrop
struct HashMap<H> where func(&H, int)int {
  hasher H;
}

func moo<H>(self &HashMap<H>) {
  // Nothing needed in here, to cause the bug
}

exported func main() int {
  m = HashMap(IntHasher());
  moo(&m);
  destruct m;
  return 9;
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 9 }) => {}
        other => panic!("expected VonInt(9), got {:?}", other),
    }
}

#[test]
fn supply_bounds_to_child_functions() {
    // We need to supply our bounds to our lambdas and drop functions, see LCCPGB and LCNBAFA.
    // This test's `add` function will try to call
    //   add:204<int, int, ^IntHasher>(&HashMap<int, int, ^IntHasher>)
    //   .lam:281
    //   .drop<>(@add:204<int, int, ^IntHasher>(&HashMap<int, int, ^IntHasher>).lam:281)
    // and when instantiating that, `drop` needs to know bounds from `add` to understand that
    // `&HashMap<int, int, ^IntHasher>` parameter.
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test_no_builtins(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
import v.builtins.arrays.*;

struct IntHasher { }
func __call(this &IntHasher, x int) int { return x; }

#!DeriveStructDrop
struct HashMap<K Ref imm, V Ref, H Ref>
where func(&H, &K)int {
  hasher H;
}

func add<K Ref imm, V, H>(map &HashMap<K, V, H>) void {
  Array<mut, int>(2, {_});
}

exported func main() int {
  m = HashMap<int, int>(IntHasher());
  m.add();
  [h] = m;
  return 7;
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 7 }) => {}
        other => panic!("expected VonInt(7), got {:?}", other),
    }
}

#[test]
fn hash_map_update() {
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
import hashmap.*;
exported func main() int {
  m = HashMap<int, int>(IntHasher(), IntEquator());
  m.add(0, 100);
  m.add(4, 101);
  m.add(8, 102);
  m.add(12, 103);
  m.update(8, 108);
  return m.get(8).get();
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 108 }) => {}
        other => panic!("expected VonInt(108), got {:?}", other),
    }
}

#[test]
fn hash_map_collisions() {
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r#"
import hashmap.*;
import panicutils.*;
exported func main() int {
  m = HashMap<int, int>(IntHasher(), IntEquator());
  m.add(0, 100);
  m.add(4, 101);
  m.add(8, 102);
  m.add(12, 103);
  m.add(16, 104);
  m.add(20, 105);
  m.add(24, 106);
  m.add(28, 107);
  m.add(32, 108);
  m.add(36, 109);
  m.add(40, 110);
  m.add(44, 111);
  vassertEq(m.get(0).get(), 100, "val at 0 not 100!");
  vassertEq(m.get(4).get(), 101, "val at 1 not 101!");
  vassertEq(m.get(8).get(), 102, "val at 2 not 102!");
  vassertEq(m.get(12).get(), 103, "val at 3 not 103!");
  vassertEq(m.get(16).get(), 104, "val at 4 not 104!");
  vassertEq(m.get(20).get(), 105, "val at 5 not 105!");
  vassertEq(m.get(24).get(), 106, "val at 6 not 106!");
  vassertEq(m.get(28).get(), 107, "val at 7 not 107!");
  vassertEq(m.get(32).get(), 108, "val at 8 not 108!");
  vassertEq(m.get(36).get(), 109, "val at 9 not 109!");
  vassertEq(m.get(40).get(), 110, "val at 10 not 110!");
  vassertEq(m.get(44).get(), 111, "val at 11 not 111!");
  vassert(m.get(1337).isEmpty(), "expected nothing at 1337!");
  return m.get(44).get();
}
"#,
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 111 }) => {}
        other => panic!("expected VonInt(111), got {:?}", other),
    }
}

#[test]
fn hash_map_with_functors() {
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
import hashmap.*;
func add42(map &HashMap<int, int, IntHasher, IntEquator>) {
  map.add(42, 100);
}

exported func main() int {
  m = HashMap<int, int, IntHasher, IntEquator>(IntHasher(), IntEquator());
  add42(&m);
  return m.get(42).get();
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 100 }) => {}
        other => panic!("expected VonInt(100), got {:?}", other),
    }
}

#[test]
fn hash_map_with_struct_as_key() {
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
import hashmap.*;

struct Location imm {
  groupX int;
  groupY int;
  indexInGroup int;
}

struct LocationHasher { }
func __call(this &LocationHasher, loc Location) int {
  hash = 0;
  set hash = 41 * hash + loc.groupX;
  set hash = 41 * hash + loc.groupY;
  set hash = 41 * hash + loc.indexInGroup;
  return hash;
}

struct LocationEquator { }
func __call(this &LocationEquator, a Location, b Location) bool {
  return (a.groupX == b.groupX) and (a.groupY == b.groupY) and (a.indexInGroup == b.indexInGroup);
}

exported func main() int {
  m = HashMap<Location, int>(LocationHasher(), LocationEquator());
  m.add(Location(4, 5, 6), 100);
  return m.get(Location(4, 5, 6)).get();
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 100 }) => {}
        other => panic!("expected VonInt(100), got {:?}", other),
    }
}

#[test]
fn hash_map_has() {
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
import hashmap.*;
import panicutils.*;
exported func main() int {
  m = HashMap<int, int>(IntHasher(), IntEquator());
  m.add(0, 100);
  m.add(4, 101);
  m.add(8, 102);
  m.add(12, 103);
  vassert(m.has(0));
  vassert(not(m.has(1)));
  vassert(not(m.has(2)));
  vassert(not(m.has(3)));
  vassert(m.has(4));
  vassert(m.has(8));
  vassert(m.has(12));
  return 111;
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 111 }) => {}
        other => panic!("expected VonInt(111), got {:?}", other),
    }
}

#[test]
fn gathers_substitutes_bounds_for_structs_inside_things_accessed_from_dots() {
    // See SBITAFD, we had a problem where we didn't register coutputs for new instantiations that
    // come from substituting existing ones.

    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test_no_builtins(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r#"
import v.builtins.arith.*;

extern func __vbi_panic() __Never;

extern("vale_runtime_sized_array_len")
func len<M Mutability, E>(arr &[]<M>E) int;

extern("vale_runtime_sized_array_mut_new")
func Array<M Mutability, E Ref>(size int) []<M>E
where M = mut;

func __pretend<T>() T { __vbi_panic() }

#!DeriveStructDrop
struct HashMapNode<K Ref imm> {
  key K;
}

#!DeriveStructDrop
struct HashMap<K Ref imm> {
  table! Array<mut, HashMapNode<K>>;
}

func keys<K Ref imm>(self &HashMap<K>) {
  self.table.len();
}

exported func main() int {
  m = HashMap<int>([]HashMapNode<int>(0));
  m.keys();
  [arr] = m;
  [] = arr;
  return 1337;
}
"#,
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 1337 }) => {}
        other => panic!("expected VonInt(1337), got {:?}", other),
    }
}

#[test]
fn gathers_substitutes_bounds_for_interfaces_inside_things_accessed_from_dots() {
    // See SBITAFD, we had a problem where we didn't register coutputs for new instantiations that
    // come from substituting existing ones.

    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test_no_builtins(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r#"
import v.builtins.arith.*;

extern func __vbi_panic() __Never;

extern("vale_runtime_sized_array_len")
func len<M Mutability, E>(arr &[]<M>E) int;

extern("vale_runtime_sized_array_mut_new")
func Array<M Mutability, E Ref>(size int) []<M>E
where M = mut;

func __pretend<T>() T { __vbi_panic() }

#!DeriveStructDrop
interface HashMapNode<K Ref imm> { }

#!DeriveStructDrop
struct HashMap<K Ref imm> {
  table! Array<mut, HashMapNode<K>>;
}

func keys<K Ref imm>(self &HashMap<K>) {
  self.table.len();
}

exported func main() int {
  m = HashMap<int>([]HashMapNode<int>(0));
  m.keys();
  [arr] = m;
  [] = arr;
  return 1337;
}
"#,
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 1337 }) => {}
        other => panic!("expected VonInt(1337), got {:?}", other),
    }
}

#[test]
fn hash_map_values() {
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
import hashmap.*;
import panicutils.*;
exported func main() int {
  m = HashMap<int, int>(IntHasher(), IntEquator());
  m.add(0, 100);
  m.add(4, 101);
  m.add(8, 102);
  m.add(12, 103);
  k = m.values();
  vassertEq(k.len(), 4);
  vassertEq(k[0], 100);
  vassertEq(k[1], 101);
  vassertEq(k[2], 102);
  vassertEq(k[3], 103);
  return 1337;
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 1337 }) => {}
        other => panic!("expected VonInt(1337), got {:?}", other),
    }
}

#[test]
fn hash_map_with_mutable_values() {
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
import hashmap.*;
import panicutils.*;
struct Plane {}

exported func main() int {
  m = HashMap<int, Plane>(IntHasher(), IntEquator());
  m.add(0, Plane());
  m.add(4, Plane());
  m.add(8, Plane());
  m.add(12, Plane());
  vassert(m.has(0));
  vassert(m.has(4));
  vassert(m.has(8));
  vassert(m.has(12));
  m.remove(12);
  vassert(not m.has(12));
  return 1337;
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 1337 }) => {}
        other => panic!("expected VonInt(1337), got {:?}", other),
    }
}

#[test]
fn hash_map_remove() {
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
import hashmap.*;
import panicutils.*;
exported func main() int {
  m = HashMap<int, int>(IntHasher(), IntEquator());
  m.add(0, 100);
  m.add(4, 101);
  m.add(8, 102);
  m.add(12, 103);
  vassert(m.has(8));
  m.remove(8);
  vassert(not m.has(8));
  m.add(8, 102);
  vassert(m.has(8));
  vassert(m.has(4));
  m.remove(4);
  vassert(not m.has(4));
  return 1337;
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 1337 }) => {}
        other => panic!("expected VonInt(1337), got {:?}", other),
    }
}

#[test]
fn hash_map_remove_2() {
    let compilation_bump = bumpalo::Bump::new();
    let parse_bump = bumpalo::Bump::new();
    let scout_bump = bumpalo::Bump::new();
    let typing_bump = bumpalo::Bump::new();
    let instantiating_bump = bumpalo::Bump::new();
    let hammer_bump = bumpalo::Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let hammer_interner = HammerInterner::new(&hammer_bump);
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r#"
import hashmap.*;
import panicutils.*;

exported func main() int {
  m = HashMap<int, int>(IntHasher(), IntEquator());
  m.add(0, 0);
  m.add(1, 1);
  m.add(2, 2);
  m.add(3, 3);
  m.remove(1);
  m.remove(2);
  m.add(4, 4);

  values = m.values();
  vassertEq(values.len(), 3, "wat");
  vassertEq(values[0], 0, "wat");
  vassertEq(values[1], 3, "wat");
  vassertEq(values[2], 4, "wat");
  return 1337;
}
"#,
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 1337 }) => {}
        other => panic!("expected VonInt(1337), got {:?}", other),
    }
}

