use crate::integration_tests::tests::run_compilation::test;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::tests::tests::load_expected;
use crate::typing::typing_interner::TypingInterner;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;
pub struct AfterRegionsIntegrationTests;

#[test]
#[ignore = "ignored upstream in Scala"]
fn todo() { panic!("Unmigrated test: todo"); }

#[test]
fn test_returning_empty_seq() {
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
export () as Tup0;
exported func main() () {
  return ();
}
",
    );
    let _coutputs = compile.expect_compiler_outputs();

    compile.run_primitive_args(Vec::new()).unwrap();
}

#[test]
#[ignore = "ignored upstream in Scala"]
fn map_function() { panic!("Unmigrated test: map_function"); }

#[test]
fn imm_tuple_access() {
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
    let source = load_expected("programs/tuples/immtupleaccess.vale");
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        source.as_str(),
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("Expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn interface_method_call_on_impl_bounded_generic_dispatches_through_interface() {
    // The scenario: genericGetFuel<T> takes &T with a `where implements(T, IShip)` bound
    // and calls x.getFuel() in its body. The user expects this to find IShip's abstract
    // getFuel, then dispatch virtually to Raza's override at runtime.
    //
    // Why this isn't automatic: Vale's interface abstract methods don't sit at the package
    // level. They live inside the interface's own outer env, reachable only via
    // coutputs.getOuterEnvForType(getInterfaceTemplate(IShip)). For a *concrete* &IShip
    // receiver, OverloadResolver.getParamEnvironments mechanically returns IShip's outer
    // env (because the receiver's type names IShip directly). For a *placeholder* &T
    // receiver, the type doesn't name IShip — IShip is one indirection away, declared via
    // the where-clause as an IsaTemplataT(T, IShip) entry in genericGetFuel's near-env.
    // Without something following that indirection, the lookup of getFuel finds only the
    // free function `getFuel(self &Raza)` (which type-mismatches T) and never reaches
    // IShip's outer env where the abstract method lives. Pre-fix, this produced
    // "No ancestors satisfy call" and the program failed to type-check.
    //
    // What we changed: OverloadResolver.getCandidateBanners now also calls
    // getPlaceholderImplBoundEnvs alongside getParamEnvironments. For each placeholder-
    // typed param, it looks up ambient impl bounds keyed by the placeholder's imprecise
    // name (ImplSubCitizenImpreciseNameS, populated automatically when addRunedDataToNearEnv
    // writes the IsaTemplataT into the near-env), pulls each IsaTemplataT, and adds each
    // super-interface's outer env to the candidate search. With that, the abstract
    // getFuel(virtual self &IShip) becomes a candidate; the inner per-call-site solve
    // verifies T isa IShip via the same IsaTemplataT (through ImplCompiler.isParent); the
    // call resolves; the instantiator monomorphizes genericGetFuel<Raza>; and the backend
    // dispatches getFuel virtually through Raza's vtable, returning 42.
    //
    // The fix is principle-aligned with @BDPFWDZ (By Default Pull From Where Declared):
    // IShip's methods stay in IShip's outer env where they were declared; the resolver
    // walks (via the where-clause's IsaTemplataT link) to find them; nothing is copied
    // into the calling function's near-env. See
    // docs/arcana/ByDefaultPullFromWhereDeclared-BDPFWDZ.md for the broader principle.
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
sealed interface IShip {
  func getFuel(virtual self &IShip) int;
}
struct Raza { fuel int; }
impl IShip for Raza;
func getFuel(self &Raza) int { return self.fuel; }

func genericGetFuel<T>(x &T) int
where implements(T, IShip) {
  return x.getFuel();
}

exported func main() int {
  return genericGetFuel(&Raza(42));
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("Expected VonInt(42), got {:?}", other),
    }
}

#[test]
#[ignore = "ignored upstream in Scala"]
fn test_overload_set() { panic!("Unmigrated test: test_overload_set"); }

#[test]
#[ignore = "ignored upstream in Scala"]
fn pass_overload_set_into_placeholder_parameter_posipp() { panic!("Unmigrated test: pass_overload_set_into_placeholder_parameter_posipp"); }

#[test]
#[ignore = "ignored upstream in Scala (see audit comment): pending CoordT redesign — make CoordT contain a placeholder and move Ownership to a generic param so the return type's ownership is calculated from the parameter"]
fn upcasting_in_a_generic_function() {
    // This is testing two things:
    //  - Upcasting inside a generic function
    //  - The return type's ownership is actually calculated from the parameter. This will
    //    fail as long as we still have CoordT(Ownership, ITemplata[KindTemplataType])
    //    because that ownership isn't a templata. The call site will correctly have that
    //    ownership as borrow, but the definition will think it's an own, *not* a placeholder
    //    or variable-thing or anything like that. So, when it gets to the instantiator, it
    //    will actually make the wrong return type. I think the solution will be to make CoordT
    //    contain a placeholder, and move O to be a generic param.
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
func upcast<SuperKind Kind, SubType Ref>(left SubType) SuperType
where O Ownership,
  SubKind Kind,
  SuperType Ref = Ref[O, SuperKind],
  SubType Ref = Ref[O, SubKind],
  implements(SubType, SuperType)
{
  left
}

sealed interface IShip  {}
struct Serenity {}
impl IShip for Serenity;

exported func main() {
  ship &IShip = upcast<IShip>(&Serenity());
}

",
    );

    compile.eval_for_kind_primitive_args(Vec::new()).unwrap();
}

#[test]
#[ignore = "ignored upstream in Scala"]
fn diff_iter() { panic!("Unmigrated test: diff_iter"); }

#[test]
fn call_array_without_element_type() {
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
exported func main() int {
  a = Array<imm>(3, {13 + _});
  sum = 0;
  drop_into(a, &(e) => { set sum = sum + e; });
  return sum;
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("Expected VonInt(42), got {:?}", other),
    }
}

#[test]
fn make_array_without_type() {
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
exported func main() int {
  a = #[](10, {_});
  return a.3;
}
",
    );

    let _coutputs = compile.expect_compiler_outputs();
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 3 }) => {}
        other => panic!("Expected VonInt(3), got {:?}", other),
    }
}

#[test]
fn borrowing_to_array() {
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
import list.*;

func toArray<E>(list &List<E>) []<mut>&E {
  return []&E(list.len(), { list.get(_) });
}

exported func main() int {
  l = List<int>();
  add(&l, 5);
  add(&l, 9);
  add(&l, 7);
  return l.toArray()[1];
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 9 }) => {}
        other => panic!("Expected VonInt(9), got {:?}", other),
    }
}

#[test]
#[ignore = "ignored upstream in Scala"]
fn infinite_lambda_call() { panic!("Unmigrated test: infinite_lambda_call"); }

