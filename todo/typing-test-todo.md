# RETIRED — typing-pass core test migration complete

All tracked tests below are done (`- [x]`). This was the driver for the core
typing-pass test suite (compiler_tests / solver / virtual / mutate); the active
migration frontier has since moved to the **instantiating pass**. The remaining
`after_regions_*` typing tests were never tracked here — their pass/deferred split
is the Scala-side record at `docs/historical/after-regions-test-fixing-quest.md`.
Kept for historical reference only.

---

- [x] parenthesized_method_syntax_will_move_instead_of_borrow
- [x] calling_a_method_on_a_returned_own_ref_will_supply_owning_arg
- [x] explicit_borrow_method_call
- [x] calling_a_method_on_a_local_will_supply_borrow_ref
- [x] calling_a_method_on_a_member_will_supply_borrow_ref
- [x] no_derived_or_custom_drop_gives_error
- [x] opt_with_undroppable_contents
- [x] opt_with_undroppable_mutable_ref_contents
- [x] restackify
- [x] loop_restackify
- [x] destructure_restackify

## Deferred — blocked on TL-slab (rune-type/resolve-solver cascade)

- [x] reports_when_we_try_to_mutate_an_element_in_an_imm_static_sized_array
- [x] can_mutate_an_element_in_a_runtime_sized_array
- [x] humanize_errors (compiler_mutate_tests.rs)

## Next file: compiler_virtual_tests.rs

- [x] regular_interface_and_struct
- [x] regular_open_interface_and_struct_no_anonymous_interface
- [x] implementing_two_interfaces_causes_no_vdrop_conflict
- [x] upcast
- [x] virtual_with_body
- [x] templated_interface_and_struct
- [x] custom_drop_with_concept_function
- [x] test_complex_interface
- [x] test_specializing_interface
- [x] use_bound_from_struct
- [x] basic_interface_forwarder
- [x] generic_interface_forwarder
- [x] generic_interface_forwarder_with_bound
- [x] basic_interface_anonymous_subclass
- [x] integer_is_compatible_with_interface_anonymous_substruct
- [x] lambda_is_compatible_with_interface_anonymous_substruct
- [x] implementing_a_non_generic_interface_call
- [x] anonymous_substruct_8

## Next file: compiler_solver_tests.rs

- [x] test_simple_generic_function
- [x] test_lacking_drop_function
- [x] test_having_drop_function_concept_function
- [x] test_calling_a_generic_function_with_a_concept_function
- [x] test_rune_type_in_generic_param
- [x] test_single_parameter_function
- [x] test_calling_a_generic_function_with_a_drop_concept_function
- [x] humanize_errors
- [x] simple_int_rule
- [x] equals_transitive
- [x] one_of
- [x] components
- [x] prototype_rule_call_via_rune
- [x] prototype_rule_call_directly
- [x] send_struct_to_struct
- [x] send_struct_to_interface
- [x] assume_most_specific_generic_param
- [x] assume_most_specific_common_ancestor
- [x] descendant_satisfying_call
- [x] reports_incomplete_solve (deferred — Rust returns TypingPassResolvingError where Scala expects TypingPassSolverError; escalated for TL to choose reorder vs test adjustment)
- [x] stamps_an_interface_template_via_a_function_return
- [x] pointer_becomes_share_if_kind_is_immutable
- [x] detects_conflict_between_types (8 arms: 6 Scala-parity + 2 Rust-only `RuleError(InternalSolverError(...))` shapes per docs/historical/nondeterministic-solver.md)
- [x] can_match_kind_templata_type_against_struct_env_entry_struct_templata
- [x] can_destructure_and_assemble_static_sized_array
- [x] test_equivalent_identifying_runes_in_functions
- [x] iragp_test_equivalent_identifying_runes_in_struct
