use bumpalo::Bump;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::scout_arena::ScoutArena;
use crate::typing::names::names::{FunctionNameT, FunctionTemplateNameT, IdT, INameT};
use crate::typing::test::compiler_test_compilation::compiler_test_compilation;
use crate::utils::code_hierarchy::{self, IPackageResolver, PackageCoordinate};
use std::collections::HashMap;
use crate::typing::typing_interner::TypingInterner;
use crate::builtins::builtins::get_embedded_modulized_code_map;
use crate::tests::tests::get_package_to_resource_resolver;
use crate::tests::tests::load_expected;


#[test]
fn regular_interface_and_struct() {

    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
sealed interface Opt { }

struct Some { x int; }
impl Opt for Some;
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();

    let drop_func_names: Vec<_> = coutputs.functions.iter().map(|f| f.header.id).filter_map(|f| {
        match f {
            id @ IdT { local_name: INameT::Function(FunctionNameT { template: FunctionTemplateNameT { human_name: StrI("drop"), .. }, .. }), .. } => Some(id),
            _ => None,
        }
    }).collect();
    assert_eq!(drop_func_names.len(), 2);

    let interface = coutputs.lookup_interface_by_human_name("Opt");
    let _ = interface.internal_methods;
}

#[test]
fn regular_open_interface_and_struct_no_anonymous_interface() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveAnonymousSubstruct
interface Opt { }

struct Some { x int; }
impl Opt for Some;
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let drop_func_names: Vec<_> = coutputs.functions.iter().map(|f| f.header.id).filter_map(|f| {
        match f {
            id @ IdT { local_name: INameT::Function(FunctionNameT { template: FunctionTemplateNameT { human_name: StrI("drop"), .. }, .. }), .. } => Some(id),
            _ => None,
        }
    }).collect();
    assert_eq!(drop_func_names.len(), 2);
}

#[test]
fn implementing_two_interfaces_causes_no_vdrop_conflict() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
struct MyStruct {}

interface IA {}
impl IA for MyStruct;

interface IB {}
impl IB for MyStruct;

func bork(a IA) {}
func zork(b IB) {}
exported func main() {
  bork(MyStruct());
  zork(MyStruct());
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn upcast() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"

interface IShip {}
struct Raza { fuel int; }
impl IShip for Raza;

exported func main() {
  ship IShip = Raza(42);
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn virtual_with_body() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
interface IBork { }
struct Bork { }
impl IBork for Bork;

func rebork(virtual result *IBork) bool { true }
exported func main() {
  rebork(&Bork());
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let _compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
}

#[test]
fn templated_interface_and_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
sealed interface Opt<T Ref>
where func drop(T)void
{ }

struct Some<T>
where func drop(T)void
{ x T; }

impl<T> Opt<T> for Some<T>
where func drop(T)void;
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    let coutputs = compile.expect_compiler_outputs();
    let drop_func_names: Vec<_> = coutputs.functions.iter().map(|f| f.header.id).filter_map(|f| {
        match f {
            id @ IdT { local_name: INameT::Function(FunctionNameT { template: FunctionTemplateNameT { human_name: StrI("drop"), .. }, .. }), .. } => Some(id),
            _ => None,
        }
    }).collect();
    assert_eq!(drop_func_names.len(), 2);
}

#[test]
fn custom_drop_with_concept_function() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveInterfaceDrop
sealed interface Opt<T Ref> { }

abstract func drop<T>(virtual opt Opt<T>)
where func drop(T)void;

#!DeriveStructDrop
struct Some<T> { x T; }
impl<T> Opt<T> for Some<T>;

func drop<T>(opt Some<T>)
where func drop(T)void
{
  [x] = opt;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn test_complex_interface() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = load_expected("programs/genericvirtuals/templatedinterface.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn test_specializing_interface() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = load_expected("programs/genericvirtuals/specializeinterface.vale");
    let resolver = get_embedded_modulized_code_map(&parse_arena, &parser_keywords)
        .or(code_hierarchy::test_from_vec(&parse_arena, vec![code]))
        .or(get_package_to_resource_resolver());
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn use_bound_from_struct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveStructDrop
struct BorkForwarder<Lam>
where func __call(&Lam)int // 3
{
  lam Lam;
}


func bork<Lam>( // 1
  self &BorkForwarder<Lam> // 2
) int {
  return (self.lam)();
}

exported func main() {
  b = BorkForwarder({ 7 });
  b.bork();
  [_] = b;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn basic_interface_forwarder() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveInterfaceDrop
sealed interface Bork {
  func bork(virtual self &Bork) int;
}

#!DeriveStructDrop
struct BorkForwarder<Lam>
where func drop(Lam)void, func __call(&Lam)int {
  lam Lam;
}

impl<Lam> Bork for BorkForwarder<Lam>;

func bork<Lam>(self &BorkForwarder<Lam>) int {
  return (self.lam)();
}

exported func main() int {
  f = BorkForwarder({ 7 });
  z = f.bork();
  [_] = f;
  return z;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn generic_interface_forwarder() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveInterfaceDrop
sealed interface Bork<T Ref> {
  func bork(virtual self &Bork<T>) int;
}

#!DeriveStructDrop
struct BorkForwarder<T Ref, Lam>
where func drop(Lam)void, func __call(&Lam)T {
  lam Lam;
}

impl<T, Lam> Bork<T> for BorkForwarder<T, Lam>;

func bork<T, Lam>(self &BorkForwarder<T, Lam>) T {
  return (self.lam)();
}

exported func main() int {
  f = BorkForwarder<int>({ 7 });
  z = f.bork();
  [_] = f;
  return z;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn generic_interface_forwarder_with_bound() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveInterfaceDrop
sealed interface Bork<T Ref>
where func threeify(T)T {
  func bork(virtual self &Bork<T>) int;
}

#!DeriveStructDrop
struct BorkForwarder<T Ref, Lam>
where func drop(Lam)void, func __call(&Lam)T, func threeify(T)T {
  lam Lam;
}

impl<T, Lam> Bork<T> for BorkForwarder<T, Lam>;

func bork<T, Lam>(self &BorkForwarder<T, Lam>) T {
  return (self.lam)().threeify();
}

func threeify(x int) int { 3 }

exported func main() int {
  f = BorkForwarder<int>({ 7 });
  z = f.bork();
  [_] = f;
  return z;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn basic_interface_anonymous_subclass() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
interface Bork {
  func bork(virtual self &Bork) int;
}

exported func main() int {
  f = Bork({ 7 });
  return f.bork();
}

";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn integer_is_compatible_with_interface_anonymous_substruct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r#"
import v.builtins.drop.*;
interface AFunction2<R Ref, P1 Ref> {
  func doCall(virtual this &AFunction2<R, P1>, a P1) R;
}
func __call(x6 int, x42 int)str { "hi" }
exported func main() str {
  func = AFunction2<str, int>(6);
  return func.doCall(42);
}
"#;
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(get_embedded_modulized_code_map(&parse_arena, &parser_keywords))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn lambda_is_compatible_with_interface_anonymous_substruct() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.str.*;

interface AFunction2<R Ref, P1 Ref> {
  func __call(virtual this &AFunction2<R, P1>, a P1) R;
}
exported func main() str {
  func = AFunction2<str, int>((i) => { str(i) });
  return func(42);
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(get_embedded_modulized_code_map(&parse_arena, &parser_keywords))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn implementing_a_non_generic_interface_call() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
#!DeriveInterfaceDrop
interface IObserver<T Ref> { }

#!DeriveStructDrop
struct MyThing { }

impl<T> IObserver<T> for MyThing;

";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

#[test]
fn anonymous_substruct_8() {
    let parse_bump = Bump::new();
    let scout_bump = Bump::new();
    let typing_bump = Bump::new();
    let parse_arena = ParseArena::new(&parse_bump);
    let scout_arena = ScoutArena::new(&scout_bump);
    let keywords = Keywords::new_for_scout(&scout_arena);
    let parser_keywords = Keywords::new_for_parse(&parse_arena);
    let code = r"
import v.builtins.arrays.*;
//import array.make.*;

interface IThing {
  func __call(virtual self &IThing, i int) int;
}

struct MyThing { }
func __call(self &MyThing, i int) int { i }

impl IThing for MyThing;

exported func main() int {
  i IThing = MyThing();
  a = Array<imm, int>(10, &i);
  return a.3;
}
";
    let resolver = code_hierarchy::test_from_vec(&parse_arena, vec![code.to_string()])
        .or(get_embedded_modulized_code_map(&parse_arena, &parser_keywords))
        .or(|_: &PackageCoordinate<'_>| -> Option<HashMap<String, String>> { None });
    let typing_interner = TypingInterner::new(&typing_bump);
    let mut compile = compiler_test_compilation(&typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena, &resolver);
    compile.expect_compiler_outputs();
}

