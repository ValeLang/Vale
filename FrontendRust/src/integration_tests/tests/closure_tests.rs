use crate::collect_only_tnode;
use crate::collect_where_tnode;
use crate::integration_tests::tests::run_compilation::test;
use crate::interner::StrI;
use crate::keywords::Keywords;
use crate::parse_arena::ParseArena;
use crate::postparsing::expressions::LocalS;
use crate::postparsing::names::IVarNameS;
use crate::scout_arena::ScoutArena;
use crate::simplifying::hammer_interner::HammerInterner;
use crate::tests::tests::load_expected;
use crate::typing::ast::ast::PrototypeT;
use crate::typing::ast::citizens::AddressMemberTypeT;
use crate::typing::ast::citizens::IMemberTypeT;
use crate::typing::ast::citizens::IStructMemberT;
use crate::typing::ast::citizens::NormalStructMemberT;
use crate::typing::ast::citizens::ReferenceMemberTypeT;
use crate::typing::ast::expressions::AddressExpressionTE;
use crate::typing::ast::expressions::AddressMemberLookupTE;
use crate::typing::ast::expressions::ConstructTE;
use crate::typing::ast::expressions::FunctionCallTE;
use crate::typing::ast::expressions::LetNormalTE;
use crate::typing::ast::expressions::LocalLookupTE;
use crate::typing::ast::expressions::MutateTE;
use crate::typing::ast::expressions::ReferenceMemberLookupTE;
use crate::typing::compiler::Compiler;
use crate::typing::env::function_environment_t::AddressibleLocalVariableT;
use crate::typing::env::function_environment_t::ILocalVariableT;
use crate::typing::env::function_environment_t::ReferenceLocalVariableT;
use crate::typing::names::names::CodeVarNameT;
use crate::typing::names::names::FunctionNameT;
use crate::typing::names::names::FunctionTemplateNameT;
use crate::typing::names::names::INameT;
use crate::typing::names::names::IVarNameT;
use crate::typing::names::names::IdT;
use crate::typing::names::names::LambdaCallFunctionNameT;
use crate::typing::names::names::LambdaCitizenNameT;
use crate::typing::names::names::LambdaCitizenTemplateNameT;
use crate::typing::templata::templata::ITemplataT;
use crate::typing::templata::templata::MutabilityTemplataT;
use crate::typing::test::traverse::NodeRefT;
use crate::typing::types::types::CoordT;
use crate::typing::types::types::IRegionT;
use crate::typing::types::types::IntT;
use crate::typing::types::types::KindT;
use crate::typing::types::types::MutabilityT;
use crate::typing::types::types::OwnershipT;
use crate::typing::types::types::RegionT;
use crate::typing::types::types::StructTT;
use crate::typing::types::types::VariabilityT;
use crate::typing::typing_interner::TypingInterner;
use crate::utils::code_hierarchy::FileCoordinate;
use crate::utils::code_hierarchy::PackageCoordinate;
use crate::utils::range::CodeLocationS;
use crate::von::ast::IVonData;
use crate::von::ast::VonInt;
use std::marker::PhantomData;
use crate::postparsing::expressions::IVariableUseCertainty::NotUsed;
use crate::postparsing::expressions::IVariableUseCertainty::Used;
pub struct ClosureTests;

#[test]
pub fn addressibility() {
    let scout_bump = bumpalo::Bump::new();
    let scout_arena = ScoutArena::new(&scout_bump);
    let calc = |self_borrowed, self_moved, self_mutated, child_borrowed, child_moved, child_mutated| {
        let local_s = LocalS {
            var_name: IVarNameS::CodeVarName(scout_arena.intern_str("x")),
            self_borrowed,
            self_moved,
            self_mutated,
            child_borrowed,
            child_moved,
            child_mutated,
        };
        let local_a: &LocalS = scout_arena.alloc(local_s);
        let addressible_if_mutable = Compiler::determine_if_local_is_addressible(
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable }),
            local_a);
        let addressible_if_immutable = Compiler::determine_if_local_is_addressible(
            ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Immutable }),
            local_a);
        (addressible_if_mutable, addressible_if_immutable)
    };
    // If we don't do anything with the variable, it can be just a reference.
    assert_eq!(calc(NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed), (false, false));
    // If we or our children only ever read, it can be just a reference.
    assert_eq!(calc(Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed), (false, false));
    assert_eq!(calc(NotUsed, NotUsed, NotUsed, Used, NotUsed, NotUsed), (false, false));
    // If only we mutate it, it can be just a reference.
    assert_eq!(calc(NotUsed, NotUsed, Used, NotUsed, NotUsed, NotUsed), (false, false));
    // Even if we're certain it's moved, it must be addressible.
    // Imagine:
    // exported func main() int {
    //   m = Marine();
    //   if (something) {
    //     something.consume(m);
    //   } else {
    //     otherthing.consume(m);
    //   }
    // }
    // (or, we can change it so we move it into the closure struct, but that
    // seems weird, i like thinking that closures only ever have borrows or
    // addressibles)
    // However, this doesnt apply to immutable, since move = copy.
    assert_eq!(calc(NotUsed, NotUsed, NotUsed, NotUsed, Used, NotUsed), (true, false));
    // If we're certain children mutate it, it also has to be addressible.
    assert_eq!(calc(NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, Used), (true, true));
}

#[test]
pub fn captured_own_is_borrow() {
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
    // Here, the scout determined that the closure is only ever borrowing
    // it (during the dereference to get its member) so typingpass doesn't put
    // an address into the closure, it instead puts a reference. Specifically,
    // a borrow reference (because why would we want to move this into the
    // closure struct?).
    // This means the closure struct contains a borrow reference. This means
    // the environment in the closure has to match this; the environment has
    // to have a borrow reference instead of an owning reference.
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        r"
struct Marine {
  hp int;
}
exported func main() int {
  m = Marine(9);
  return { m.hp }();
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 9 }) => {}
        other => panic!("expected VonInt(9), got {:?}", other),
    }
}

#[test]
fn test_closure_s_local_variables() {
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
        "exported func main() int { x = 4; return {x}(); }",
    );
    let coutputs = compile.expect_compiler_outputs();
    let main = coutputs.lookup_lambda_in("main");
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::LetNormal(LetNormalTE {
            variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                name: IVarNameT::ClosureParam(_),
                variability: VariabilityT::Final,
                coord: CoordT {
                    ownership: OwnershipT::Share,
                    kind: KindT::Struct(StructTT {
                        id: IdT {
                            init_steps: &[INameT::Function(FunctionNameT {
                                template: FunctionTemplateNameT {
                                    human_name: StrI("main"), ..
                                },
                                template_args: &[],
                                parameters: &[],
                                ..
                            })],
                            local_name: INameT::LambdaCitizen(LambdaCitizenNameT {
                                template: LambdaCitizenTemplateNameT { .. },
                            }),
                            ..
                        },
                        ..
                    }),
                    ..
                },
            }),
            ..
        }) => Some(())
    );
    collect_only_tnode!(
        NodeRefT::FunctionDefinition(main),
        NodeRefT::LetNormal(LetNormalTE {
            variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                name: IVarNameT::TypingPassBlockResultVar(_),
                variability: VariabilityT::Final,
                coord: CoordT {
                    ownership: OwnershipT::Share,
                    kind: KindT::Int(IntT { bits: 32 }),
                    ..
                },
            }),
            ..
        }) => Some(())
    );
}

#[test]
fn test_returning_a_nonmutable_closured_variable_from_the_closure() {
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
        "exported func main() int { x = 4; return {x}(); }",
    );
    {
        let interner = compile.interner;
        let coutputs = compile.expect_compiler_outputs();

        // The struct should have an int x in it which is a reference type.
        // It's a reference because we know for sure that it's moved from our child,
        // which means we don't need to check afterwards, which means it doesn't need
        // to be boxed/addressible.
        let closured_vars_struct_tt =
            coutputs.lookup_lambda_in("main").header.params.first().unwrap().tyype.kind.expect_struct();
        let closured_vars_struct_def =
            coutputs.structs.iter().find(|struct_def| {
                *crate::typing::compiler::Compiler::get_template(interner, struct_def.instantiated_citizen.id) ==
                    *crate::typing::compiler::Compiler::get_template(interner, closured_vars_struct_tt.id)
            }).expect("closured_vars_struct_def not found");

        let expected_members = vec![
            IStructMemberT::Normal(NormalStructMemberT {
                name: IVarNameT::CodeVar(interner.intern_code_var_name(CodeVarNameT { name: scout_arena.intern_str("x")})),
                variability: VariabilityT::Final,
                tyype: IMemberTypeT::Reference(ReferenceMemberTypeT {
                    reference: CoordT {
                        ownership: OwnershipT::Share,
                        region: RegionT { region: IRegionT::Default },
                        kind: KindT::Int(IntT { bits: 32 }),
                    },
                }),
            }),
        ];
        assert_eq!(closured_vars_struct_def.members, expected_members.as_slice());

        let lambda = coutputs.lookup_lambda_in("main");
        // Make sure we're doing a referencememberlookup, since it's a reference member
        // in the closure struct.
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(lambda),
            NodeRefT::ReferenceMemberLookup(ReferenceMemberLookupTE {
                member_name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("x"), .. }),
                ..
            }) => Some(())
        );

        // Make sure there's a function that takes in the closured vars struct, and returns an int
        let function_calls = collect_where_tnode!(
            NodeRefT::FunctionDefinition(coutputs.lookup_function_by_str("main")),
            NodeRefT::FunctionCall(FunctionCallTE {
                callable: p @ PrototypeT { id: IdT { local_name: INameT::LambdaCallFunction(_), .. }, .. },
                ..
            }) => Some(*p)
        );
        assert_eq!(function_calls.len(), 1);
        let prototype: PrototypeT<'_, '_> = function_calls[0];
        let lambda_call_name: &LambdaCallFunctionNameT = match prototype.id.local_name {
            INameT::LambdaCallFunction(n) => n,
            _ => panic!("expected LambdaCallFunction local_name"),
        };
        let params = lambda_call_name.parameters;
        let return_type = prototype.return_type;
        match params.first().unwrap() {
            CoordT {
                ownership: OwnershipT::Share,
                kind: KindT::Struct(StructTT {
                    id: IdT {
                        init_steps: &[INameT::Function(FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("main"), .. },
                            template_args: &[],
                            parameters: &[],
                            ..
                        })],
                        local_name: INameT::LambdaCitizen(_),
                        ..
                    },
                    ..
                }),
                ..
            } => {}
            other => panic!("expected lambda struct param, got {:?}", other),
        }
        assert_eq!(return_type, CoordT {
            ownership: OwnershipT::Share,
            region: RegionT { region: IRegionT::Default },
            kind: KindT::Int(IntT { bits: 32 }),
        });

        // Make sure we make it with a function pointer and a constructed vars struct
        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::Construct(ConstructTE {
                struct_tt: StructTT {
                    id: IdT {
                        init_steps: &[INameT::Function(FunctionNameT {
                            template: FunctionTemplateNameT { human_name: StrI("main"), .. },
                            template_args: &[],
                            parameters: &[],
                            ..
                        })],
                        local_name: INameT::LambdaCitizen(_),
                        ..
                    },
                    ..
                },
                ..
            }) => Some(())
        );

        // Make sure we call the function somewhere
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::FunctionCall(_) => Some(())
        );

        collect_only_tnode!(
            NodeRefT::FunctionDefinition(lambda),
            NodeRefT::LocalLookup(LocalLookupTE {
                local_variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
                    name: IVarNameT::ClosureParam(_),
                    variability: VariabilityT::Final,
                    ..
                }),
                ..
            }) => Some(())
        );
    }

    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 4 }) => {}
        other => panic!("expected VonInt(4), got {:?}", other),
    }
}

#[test]
fn mutates_from_inside_a_closure() {
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
  x = 4;
  { set x = x + 1; }();
  return x;
}
",
    );
    {
        let interner = compile.interner;
        let coutputs = compile.expect_compiler_outputs();

        // The struct should have an int x in it.
        let closure = coutputs.lookup_lambda_in("main");
        let closure_struct = closure.header.params.first().unwrap().tyype.kind.expect_struct();
        let closure_struct_def = coutputs.lookup_struct(closure_struct.id);
        let expected_members = vec![
            IStructMemberT::Normal(NormalStructMemberT {
                name: IVarNameT::CodeVar(interner.intern_code_var_name(CodeVarNameT { name: scout_arena.intern_str("x")})),
                variability: VariabilityT::Varying,
                tyype: IMemberTypeT::Address(AddressMemberTypeT {
                    reference: CoordT {
                        ownership: OwnershipT::Share,
                        region: RegionT { region: IRegionT::Default },
                        kind: KindT::Int(IntT { bits: 32 }),
                    },
                }),
            }),
        ];
        assert_eq!(closure_struct_def.members, expected_members.as_slice());

        let lambda = coutputs.lookup_lambda_in("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(lambda),
            NodeRefT::Mutate(MutateTE {
                destination_expr: AddressExpressionTE::AddressMemberLookup(AddressMemberLookupTE {
                    member_name: IVarNameT::CodeVar(CodeVarNameT { name: StrI("x"), .. }),
                    result_type2: CoordT {
                        ownership: OwnershipT::Share,
                        kind: KindT::Int(IntT { bits: 32 }),
                        ..
                    },
                    ..
                }),
                ..
            }) => Some(())
        );

        let main = coutputs.lookup_function_by_str("main");
        collect_only_tnode!(
            NodeRefT::FunctionDefinition(main),
            NodeRefT::LetNormal(LetNormalTE {
                variable: ILocalVariableT::Addressible(AddressibleLocalVariableT {
                    variability: VariabilityT::Varying,
                    ..
                }),
                ..
            }) => Some(())
        );
    }

    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 5 }) => {}
        other => panic!("expected VonInt(5), got {:?}", other),
    }
}

#[test]
pub fn mutates_from_inside_a_closure_inside_a_closure() {
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
        "exported func main() int { x = 4; { { set x = x + 1; }(); }(); return x; }",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 5 }) => {}
        other => panic!("expected VonInt(5), got {:?}", other),
    }
}

#[test]
fn read_from_inside_a_closure_inside_a_closure() {
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
  x = 42;
  return { { x }() }();
}
",
    );
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

#[test]
pub fn mutable_lambda() {
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
    let source = load_expected("programs/lambdas/lambdamut.vale");
    let mut compile = test(
        &compilation_bump,
        &hammer_interner, &typing_interner, &scout_arena, &keywords, &parser_keywords, &parse_arena,
        &instantiating_bump,
        &source,
    );
    {
        let coutputs = compile.expect_compiler_outputs();
        let closure_structs: Vec<_> = coutputs.structs.iter().filter(|s| matches!(
            s.instantiated_citizen.id.local_name,
            INameT::LambdaCitizen(LambdaCitizenNameT {
                template: LambdaCitizenTemplateNameT {
                    code_location: CodeLocationS {
                        file: FileCoordinate {
                            package_coord: PackageCoordinate {
                                module: StrI("test"),
                                packages,
                            },
                            ..
                        },
                        ..
                    },
                    ..
                },
                ..
            }) if packages.is_empty()
        )).collect();
        assert_eq!(closure_structs.len(), 1);
        let closure_struct = closure_structs[0];
        assert!(matches!(closure_struct.mutability, ITemplataT::Mutability(MutabilityTemplataT { mutability: MutabilityT::Mutable })));
    }
    match compile.eval_for_kind_primitive_args(Vec::new()).unwrap() {
        IVonData::Int(VonInt { value: 42 }) => {}
        other => panic!("expected VonInt(42), got {:?}", other),
    }
}

