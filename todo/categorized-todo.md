# Categorized integration-tests todo (ordered with checkpoints)

Per-test classification of every unmigrated test across `FrontendRust/src/integration_tests/tests/`, organized for parallel-worktree execution.

Categories:
- **CL** — closures-heavy (arrays, closures, lambdas)
- **SI** — structs/interfaces (struct/virtual/interface, ownership/drops, tuples, packs, weak refs)
- **GE** — generics-heavy (generic fns/structs/interfaces, bounds, infer-template, polymorphic lambdas)
- **MI** — miscellaneous (control flow, primitives, imports, basic externs, hammer wire-format, basic patterns)
- **X** — cross-cutting fifth bucket. Run *after* the first four finish.

## How to run this

The four main buckets (CL, SI, GE, MI) run in parallel across worktrees (Vale, Vale2, Vale3, Vale4 — assign as you like).

Each bucket is split into **Phase 1** (foundations — ~1/3 of the bucket, tests that need no other bucket's infrastructure and lay down machinery others will reuse) and **Phase 2** (the rest — feature-y tests and tests with cross-bucket dependencies).

Between the phases is the **synchronized checkpoint**: when all four worktrees finish their Phase 1, merge every worktree's branch into every other (4-way cross-merge), run SCPX `--check-all` and `cargo check --lib` on each, then all four start Phase 2. Phase 2 work benefits from everyone else's Phase 1 cascade-fills.

After all four finish Phase 2, run the **X bucket** serially (or with a single worker; it's only 26 tests).

Notes inline as `— *note: ...*` mark cross-bucket dependencies that survived the X-pull. Test name → file is annotated per line so the worker can find the stub.

---

# CL bucket (closures-heavy) — 64 tests

## Phase 1 — foundations (21 tests)

Basic lambda lowering and basic array primitives. Lands the closure-to-struct synthesis and core array machinery others will benefit from after the merge.

- [CL] simple_lambda *(integration_tests_a.rs)*
- [CL] lambda_with_one_magic_arg *(integration_tests_a.rs)*
- [CL] lambda_with_a_type_specified_param *(integration_tests_a.rs)*
- [CL] test_closure_s_local_variables *(closure_tests.rs)*
- [CL] test_returning_a_nonmutable_closured_variable_from_the_closure *(closure_tests.rs)*
- [CL] read_from_inside_a_closure_inside_a_closure *(closure_tests.rs)*
- [CL] mutates_from_inside_a_closure *(closure_tests.rs)*
- [CL] mutates_from_inside_a_closure_inside_a_closure *(closure_tests.rs)*
- [CL] mutable_lambda *(closure_tests.rs)*
- [CL] addressibility *(closure_tests.rs)* — *note: scout-level unit test, not a Vale-source integration test*
- [CL] lambda_can_call_sibling_lambda *(integration_tests_a.rs)*
- [CL] tests_lambda *(integration_tests_b.rs)*
- [CL] tests_double_closure *(integration_tests_b.rs)*
- [CL] simple_static_array_and_runtime_index_lookup *(array_tests.rs)*
- [CL] new_rsa *(array_tests.rs)*
- [CL] test_array_length *(array_tests.rs)*
- [CL] array_foreach *(array_tests.rs)*
- [CL] array_has *(array_tests.rs)*
- [CL] mutate_array *(array_tests.rs)*
- [CL] change_mutability *(array_tests.rs)*
- [CL] returning_static_array_from_function_and_dotting_it *(array_tests.rs)*

### → CHECKPOINT (CL): all four worktrees synchronize, 4-way merge, SCPX + cargo check, resume.

## Phase 2 — rest (43 tests)

- [CL] captured_own_is_borrow *(closure_tests.rs)* — *note: also struct (SI)*
- [CL] simple_array_map_with_runtime_index_lookup *(array_tests.rs)*
- [CL] take_arraysequence_as_a_parameter *(array_tests.rs)*
- [CL] borrow_arraysequence_as_a_parameter *(array_tests.rs)* — *note: also struct (SI)*
- [CL] each_on_ssa *(array_tests.rs)* — *note: `foreach` + print (MI)*
- [CL] array_map_with_int *(array_tests.rs)*
- [CL] array_map_with_lambda *(array_tests.rs)* — *note: `where F Prot` generic prototype constraint (GE)*
- [CL] make_array_map_with_lambda *(array_tests.rs)*
- [CL] array_map_taking_a_closure_which_captures_something *(array_tests.rs)*
- [CL] map_using_array_construct *(array_tests.rs)*
- [CL] nested_array *(array_tests.rs)*
- [CL] nested_imm_arrays *(array_tests.rs)*
- [CL] two_dimensional_array *(array_tests.rs)* — *note: nested lambda*
- [CL] array_with_capture *(array_tests.rs)* — *note: also struct (SI)*
- [CL] swap_out_of_array *(array_tests.rs)* — *note: also struct (SI)*
- [CL] make_array_map_with_struct *(array_tests.rs)* — *note: also imm struct (SI)*
- [CL] array_map_with_interface *(array_tests.rs)* — *note: also interfaces + virtual (SI)*
- [CL] destroy_ssa_of_imms_into_function *(array_tests.rs)* — *note: lambda passed to drop_into*
- [CL] destroy_rsa_of_imms_into_function *(array_tests.rs)* — *note: lambda*
- [CL] destroy_ssa_of_muts_into_function *(array_tests.rs)* — *note: also defines struct (SI)*
- [CL] destroy_rsa_of_muts_into_function *(array_tests.rs)* — *note: also defines struct (SI)*
- [CL] migrate_rsa *(array_tests.rs)* — *note: struct + lambda (SI)*
- [CL] migrate_ssa *(array_tests.rs)* — *note: struct + lambda (SI)*
- [CL] unspecified_mutability_static_array_from_lambda_defaults_to_mutable *(array_tests.rs)*
- [CL] immutable_static_array_from_lambda *(array_tests.rs)*
- [CL] mutable_static_array_from_lambda *(array_tests.rs)*
- [CL] immutable_static_array_from_values *(array_tests.rs)*
- [CL] mutable_static_array_from_values *(array_tests.rs)*
- [CL] unspecified_mutability_runtime_array_from_lambda_defaults_to_mutable *(array_tests.rs)*
- [CL] immutable_runtime_array_from_lambda *(array_tests.rs)*
- [CL] mutable_runtime_array_from_lambda *(array_tests.rs)*
- [CL] new_immutable_array *(array_tests.rs)* — *note: generic helper fn with bound (GE)*
- [CL] capture *(array_tests.rs)* — *note: heavily uses generics with bounds (GE)*
- [CL] capture_mutable_array *(array_tests.rs)* — *note: also struct + print (SI/MI)*
- [CL] map_from_hardcoded_values *(array_tests.rs)* — *note: heavy generics with bounds (GE)*
- [CL] reports_when_making_new_imm_rsa_without_lambda *(array_tests.rs)* — *note: error-reporting case*
- [CL] test_array_push_pop_len_capacity_drop *(integration_tests_b.rs)*
- [CL] tests_generic_with_a_lambda *(integration_tests_b.rs)* — *note: requires generics (GE)*
- [CL] tests_generic_s_lambda_calling_parent_function_s_bound *(integration_tests_b.rs)* — *note: requires generics with bounds (GE)*
- [CL] tests_generic_with_a_polymorphic_lambda *(integration_tests_b.rs)* — *note: requires generics (GE)*
- [CL] tests_generic_with_a_polymorphic_lambda_invoked_twice *(integration_tests_b.rs)* — *note: requires generics (GE)*
- [CL] tests_a_foreach_for_a_linked_list *(integration_tests_b.rs)* — *note: also uses interfaces (SI)*
- [CL] exporting_array *(integration_tests_c.rs)* — *note: also exercises export wiring (MI)*

---

# SI bucket (structs/interfaces) — 85 tests

## Phase 1 — foundations (29 tests)

Basic struct CRUD, basic interfaces, downcast, tuples, packs, basic ownership/drop. This is the *most-shared* infrastructure across buckets — once these land and merge, CL/MI/GE workers all benefit.

- [SI] make_empty_imm_struct *(struct_tests.rs)*
- [SI] make_empty_mut_struct *(struct_tests.rs)*
- [SI] make_imm_struct_with_one_member *(struct_tests.rs)*
- [SI] make_struct *(struct_tests.rs)*
- [SI] make_struct_and_get_member *(struct_tests.rs)*
- [SI] mutate_struct *(struct_tests.rs)*
- [SI] constructor_with_self *(struct_tests.rs)*
- [SI] make_nested_imm_struct *(struct_tests.rs)*
- [SI] reads_a_struct_member *(integration_tests_a.rs)*
- [SI] normal_destructure *(struct_tests.rs)*
- [SI] sugar_destructure *(struct_tests.rs)*
- [SI] simple_tuple_with_one_int *(tuple_tests.rs)*
- [SI] tuple_with_two_things *(tuple_tests.rs)*
- [SI] tuple_type *(tuple_tests.rs)*
- [SI] returning_tuple_from_function_and_dotting_it *(tuple_tests.rs)*
- [SI] extract_seq *(pack_tests.rs)*
- [SI] nested_seqs *(pack_tests.rs)*
- [SI] nested_tuples *(pack_tests.rs)*
- [SI] simple_program_containing_a_virtual_function *(virtual_tests.rs)*
- [SI] can_call_virtual_function *(virtual_tests.rs)*
- [SI] imm_interface *(virtual_tests.rs)*
- [SI] can_call_interface_envs_function_from_outside *(virtual_tests.rs)*
- [SI] successful_pointer_downcast_with_as *(virtual_tests.rs)*
- [SI] failed_pointer_downcast_with_as *(virtual_tests.rs)*
- [SI] successful_owning_downcast_with_as *(virtual_tests.rs)*
- [SI] failed_owning_downcast_with_as *(virtual_tests.rs)*
- [SI] derive_drop *(ownership_tests.rs)*
- [SI] owning_ref_method_call *(ownership_tests.rs)*
- [SI] borrowing_a_temporary_mutable_makes_a_local_var *(ownership_tests.rs)*

### → CHECKPOINT (SI): all four worktrees synchronize, 4-way merge, SCPX + cargo check, resume.

## Phase 2 — rest (56 tests)

- [SI] destroy_members_at_right_times *(struct_tests.rs)* — *note: also uses print (MI)*
- [SI] panic_function *(struct_tests.rs)* — *note: heavy generics + bounds + sealed interface (GE)*
- [SI] odmfrc *(struct_tests.rs)* — *note: structurally about generic bound resolution (GE)*
- [SI] equals_equals_equals_true *(integration_tests_a.rs)*
- [SI] equals_equals_equals_false *(integration_tests_a.rs)*
- [SI] owning_interface *(virtual_tests.rs)* — *note: uses Opt<int> (MI)*
- [SI] calls_destructor_on_local_var *(ownership_tests.rs)*
- [SI] calls_destructor_on_local_var_unless_moved *(ownership_tests.rs)*
- [SI] custom_drop_result_is_an_owning_ref_calls_destructor *(ownership_tests.rs)*
- [SI] saves_return_value_then_destroys_temporary *(ownership_tests.rs)*
- [SI] saves_return_value_then_destroys_local_var *(ownership_tests.rs)*
- [SI] gets_from_temporary_struct_a_members_member *(ownership_tests.rs)*
- [SI] basic_builder_pattern *(ownership_tests.rs)*
- [SI] member_access_on_returned_owning_ref *(ownership_tests.rs)*
- [SI] test_constraint_ref *(integration_tests_a.rs)* — *note: loads constraintRef.vale*
- [SI] test_borrow_ref *(integration_tests_a.rs)* — *note: loads borrowRef.vale*
- [SI] tests_unstackifying_a_variable_multiple_times_in_a_function *(integration_tests_a.rs)* — *note: move-semantics*
- [SI] make_weak_ref_from_temporary *(weak_tests.rs)*
- [SI] make_and_lock_weak_ref_then_destroy_own_with_struct *(weak_tests.rs)*
- [SI] make_and_lock_weak_ref_from_borrow_local_then_destroy_own_with_struct *(weak_tests.rs)*
- [SI] make_and_lock_weak_ref_from_borrow_then_destroy_own_with_struct *(weak_tests.rs)*
- [SI] destroy_own_then_locking_gives_none_with_struct *(weak_tests.rs)*
- [SI] drop_while_locked_with_struct *(weak_tests.rs)*
- [SI] make_and_lock_weak_ref_then_destroy_own_with_interface *(weak_tests.rs)*
- [SI] make_and_lock_weak_ref_from_borrow_local_then_destroy_own_with_interface *(weak_tests.rs)*
- [SI] make_and_lock_weak_ref_from_borrow_then_destroy_own_with_interface *(weak_tests.rs)*
- [SI] destroy_own_then_locking_gives_none_with_interface *(weak_tests.rs)*
- [SI] drop_while_locked_with_interface *(weak_tests.rs)*
- [SI] call_weak_self_method_after_drop *(weak_tests.rs)*
- [SI] call_weak_self_method_while_alive *(weak_tests.rs)*
- [SI] weak_yonder_member *(weak_tests.rs)*
- [SI] test_matching_a_multiple_member_seq_of_immutables *(pattern_tests.rs)*
- [SI] test_matching_a_multiple_member_seq_of_mutables *(pattern_tests.rs)*
- [SI] test_matching_a_multiple_member_pack_of_immutable_and_own *(pattern_tests.rs)*
- [SI] test_matching_a_multiple_member_pack_of_immutable_and_borrow *(pattern_tests.rs)*
- [SI] test_destructuring_a_shared *(pattern_tests.rs)* — *note: uses static array (CL)*
- [SI] ignore_destructure *(pattern_tests.rs)*
- [SI] tests_single_expression_and_single_statement_functions_returns *(integration_tests_b.rs)*
- [SI] tests_calling_a_virtual_function *(integration_tests_b.rs)*
- [SI] tests_upcasting_from_a_struct_to_an_interface *(integration_tests_b.rs)*
- [SI] tests_upcasting_from_if *(integration_tests_b.rs)* — *note: also uses if control flow (MI)*
- [SI] tests_from_subdir_file *(integration_tests_b.rs)* — *note: virtuals/round.vale exercises interfaces*
- [SI] tests_calling_an_abstract_function *(integration_tests_b.rs)*
- [SI] tests_a_linked_list *(integration_tests_b.rs)*
- [SI] tests_making_a_variable_with_a_pattern *(integration_tests_b.rs)* — *note: also uses generic interface/impl (GE)*
- [SI] function_return_with_return_upcasts *(integration_tests_c.rs)*
- [SI] panic_on_drop_because_of_outstanding_borrow *(integration_tests_c.rs)*
- [SI] unlet_to_avoid_an_outstanding_borrow_panic *(integration_tests_c.rs)*
- [SI] test_catch_deref_after_drop *(integration_tests_c.rs)*
- [SI] test_overloading_between_borrow_and_weak *(integration_tests_c.rs)* — *note: uses sealed interface + virtual*
- [SI] using_same_constraint_ref_from_both_branches_of_if *(integration_tests_c.rs)* — *note: also uses if control flow (MI)*
- [SI] moving_same_thing_from_both_branches_of_if *(integration_tests_c.rs)* — *note: also uses if control flow (MI)*
- [SI] restackify *(integration_tests_c.rs)*
- [SI] destructure_restackify *(integration_tests_c.rs)*
- [SI] loop_restackify *(integration_tests_c.rs)* — *note: also uses loop control flow (MI)*
- [SI] ignoring_receiver *(integration_tests_c.rs)* — *note: tuple destructure with ignore*

---

# GE bucket (generics-heavy) — 18 tests

## Phase 1 — foundations (6 tests)

Basic generic fns, basic templated struct/fn, basic infer-template. Smallest bucket; the foundations cluster is small but everyone-else-depends-on-it.

- [GE] test_generic *(integration_tests_a.rs)* — *generic with `where func drop(T)void` bound*
- [GE] test_multiple_invocations_of_generic *(integration_tests_a.rs)*
- [GE] test_int_generic *(integration_tests_b.rs)* — *note: also fixed-size array (CL)*
- [GE] test_generic_param_default *(integration_tests_b.rs)*
- [GE] tests_calling_a_templated_struct_constructor *(integration_tests_b.rs)* — *note: also struct destructure (SI)*
- [GE] test_inferring_a_borrowed_argument *(infer_template_tests.rs)* — *note: also struct (SI)*

### → CHECKPOINT (GE): all four worktrees synchronize, 4-way merge, SCPX + cargo check, resume.

## Phase 2 — rest (12 tests)

- [GE] test_inferring_a_borrowed_static_sized_array *(infer_template_tests.rs)* — *note: also array + struct (CL/SI)*
- [GE] test_inferring_an_owning_static_sized_array *(infer_template_tests.rs)* — *note: also array + struct (CL/SI)*
- [GE] test_taking_a_callable_param *(integration_tests_a.rs)* — *note: also lambda passing (CL)*
- [GE] stamps_an_interface_template_via_a_function_parameter *(integration_tests_a.rs)* — *note: generic interface + struct + impl (SI)*
- [GE] tests_a_templated_linked_list *(integration_tests_b.rs)* — *note: also uses interfaces (SI)*
- [GE] template_overrides_are_stamped *(integration_tests_b.rs)* — *note: also uses interfaces (SI)*
- [GE] tests_generic_recursion *(integration_tests_b.rs)* — *note: heavy bounds*
- [GE] get_or_function *(integration_tests_c.rs)* — *note: interfaces (SI)*
- [GE] test_narrowing_between_borrow_and_owning_overloads *(integration_tests_c.rs)* — *note: sealed interface + virtual (SI)*
- [GE] supplying_bounded_struct_to_struct_accepting *(integration_tests_c.rs)* — *note: struct field access (SI)*
- [GE] same_type_multiple_times_in_an_invocation *(integration_tests_c.rs)* — *note: struct field access (SI)*
- [GE] call_borrow_parameter_with_shared_reference *(integration_tests_c.rs)*

---

# MI bucket (miscellaneous) — 99 tests

## Phase 1 — foundations (33 tests)

Truly-independent primitives, basic arithmetic/strings/print, basic blocks, basic if/while *without struct dependencies*, basic imports. MI's Phase 1 doesn't build much shared infrastructure (the bucket is mostly self-contained), but it's still ordered so MI worker isn't blocked by other workers' Phase 1 missing.

- [MI] simple_program_returning_an_int *(integration_tests_a.rs)*
- [MI] hardcoding_negative_numbers *(integration_tests_a.rs)*
- [MI] taking_an_argument_and_returning_it *(integration_tests_a.rs)*
- [MI] test_block *(integration_tests_a.rs)*
- [MI] test_mutating_a_local_var *(integration_tests_a.rs)*
- [MI] test_returning_a_local_mutable_var *(integration_tests_a.rs)*
- [MI] set_swapping_locals *(integration_tests_a.rs)* — *note: loads mutswaplocals.vale*
- [MI] tests_adding_two_numbers *(integration_tests_a.rs)*
- [MI] tests_inline_adding *(integration_tests_a.rs)*
- [MI] tests_inline_adding_more *(integration_tests_a.rs)*
- [MI] tests_adding_two_floats *(integration_tests_a.rs)*
- [MI] add_two_i64 *(integration_tests_a.rs)*
- [MI] dividing *(arithmetic_tests_a.rs)*
- [MI] truncate_i64_to_i32 *(integration_tests_c.rs)*
- [MI] tests_floats *(integration_tests_c.rs)*
- [MI] float_arithmetic *(float_tests.rs)*
- [MI] float_equals *(float_tests.rs)*
- [MI] print_float *(float_tests.rs)*
- [MI] concat_string_and_float *(float_tests.rs)*
- [MI] empty_string *(string_tests.rs)*
- [MI] simple_string *(string_tests.rs)*
- [MI] string_with_escapes *(string_tests.rs)*
- [MI] string_with_hex_escape *(string_tests.rs)*
- [MI] int_to_string *(string_tests.rs)*
- [MI] i64_to_string *(string_tests.rs)*
- [MI] string_length *(string_tests.rs)*
- [MI] strings_equal *(string_tests.rs)*
- [MI] string_interpolate *(string_tests.rs)*
- [MI] printlning_an_int *(print_tests.rs)*
- [MI] printlning_a_bool *(print_tests.rs)*
- [MI] empty_block *(block_tests.rs)*
- [MI] simple_block_with_a_variable *(block_tests.rs)*
- [MI] simple_block_with_a_variable_another_variable_outside_with_same_name *(block_tests.rs)*

### → CHECKPOINT (MI): all four worktrees synchronize, 4-way merge, SCPX + cargo check, resume.

## Phase 2 — rest (66 tests)

After the merge, MI worker has SI's basic structs available, so the if/while-with-struct tests become workable. CL's foreach machinery is also available, so the foreach-using tests work.

- [MI] and *(conjunction_tests.rs)*
- [MI] or *(conjunction_tests.rs)*
- [MI] and_short_circuiting *(conjunction_tests.rs)*
- [MI] or_short_circuiting *(conjunction_tests.rs)*
- [MI] simple_program_with_arith *(integration_tests_a.rs)*
- [MI] simple_program_with_logic *(integration_tests_a.rs)*
- [MI] simple_program_with_str *(integration_tests_a.rs)*
- [MI] simple_program_with_print *(integration_tests_a.rs)*
- [MI] simple_program_with_panic *(integration_tests_a.rs)*
- [MI] simple_program_with_mainargs *(integration_tests_a.rs)*
- [MI] simple_program_with_sameinstance *(integration_tests_a.rs)*
- [MI] simple_true_branch_returning_an_int *(if_tests.rs)*
- [MI] simple_false_branch_returning_an_int *(if_tests.rs)*
- [MI] ladder *(if_tests.rs)*
- [MI] if_with_condition_declaration *(if_tests.rs)*
- [MI] if_nevers *(if_tests.rs)*
- [MI] if_with_panics_and_rets *(if_tests.rs)*
- [MI] toast *(if_tests.rs)*
- [MI] if_with_complex_condition *(if_tests.rs)* — *note: defines struct Marine (SI dep — now available post-merge)*
- [MI] moving_from_inside_if *(if_tests.rs)* — *note: defines struct Marine (SI dep)*
- [MI] destructure_inside_if *(if_tests.rs)* — *note: nested struct destructure (SI)*
- [MI] ret_from_inside_if_will_destroy_locals *(if_tests.rs)* — *note: custom struct drop (SI)*
- [MI] can_continue_if_other_branch_would_have_returned *(if_tests.rs)* — *note: custom struct drop (SI)*
- [MI] simple_while_loop_that_doesnt_execute *(while_tests.rs)*
- [MI] test_a_for_ish_while_loop *(while_tests.rs)*
- [MI] tests_a_while_loop_with_a_complex_condition *(while_tests.rs)*
- [MI] tests_a_while_loop_with_a_set_in_it *(while_tests.rs)*
- [MI] tests_a_while_loop_with_a_declaration_in_it *(while_tests.rs)*
- [MI] while_with_condition_declaration *(while_tests.rs)*
- [MI] return_from_infinite_while_loop *(while_tests.rs)*
- [MI] infinite_while_loop_conditional_break *(while_tests.rs)*
- [MI] infinite_while_loop_unconditional_break *(while_tests.rs)*
- [MI] infinite_while_loop_conditional_break_from_both_sides *(while_tests.rs)*
- [MI] infinite_while_loop_conditional_return *(while_tests.rs)*
- [MI] infinite_while_loop_unconditional_return *(while_tests.rs)*
- [MI] infinite_while_loop_conditional_return_from_both_sides *(while_tests.rs)*
- [MI] each_on_int_range *(while_tests.rs)* — *note: uses `foreach` (CL dep — available post-merge)*
- [MI] each_on_int_range_with_conditional_break *(while_tests.rs)* — *note: foreach (CL)*
- [MI] each_on_int_range_with_unconditional_break *(while_tests.rs)* — *note: foreach (CL)*
- [MI] each_on_int_range_with_conditional_break_from_both_branches *(while_tests.rs)* — *note: foreach (CL)*
- [MI] parallel_foreach *(while_tests.rs)* — *note: parallel foreach + list printing*
- [MI] mutable_foreach *(while_tests.rs)* — *note: structs + foreach (SI + CL)*
- [MI] simple_pure_function *(pure_function_tests.rs)* — *note: defines structs (SI)*
- [MI] slice_a_slice *(string_tests.rs)* — *note: defines imm struct StrSlice (SI)*
- [MI] test_empty_and_get_for_some *(opt_tests.rs)*
- [MI] test_empty_and_get_for_none *(opt_tests.rs)*
- [MI] test_empty_and_get_for_borrow *(opt_tests.rs)* — *note: generic fn + struct (GE/SI)*
- [MI] test_borrow_is_ok_and_expect_for_ok *(result_tests.rs)*
- [MI] test_is_err_and_borrow_expect_err_for_err *(result_tests.rs)*
- [MI] test_owning_expect *(result_tests.rs)*
- [MI] test_owning_expect_err *(result_tests.rs)*
- [MI] test_expect_panics_for_err *(result_tests.rs)*
- [MI] test_expect_err_panics_for_ok *(result_tests.rs)*
- [MI] unstackifies_local_vars *(ownership_tests.rs)* — *note: classed MI; pure let-unlet test*
- [MI] tests_recursion *(integration_tests_b.rs)*
- [MI] tests_import *(import_tests.rs)*
- [MI] tests_non_imported_module_isnt_brought_in *(import_tests.rs)*
- [MI] tests_import_with_paackage *(import_tests.rs)*
- [MI] tests_import_of_directory_with_no_vale_files *(import_tests.rs)*
- [MI] test_shaking *(integration_tests_c.rs)*
- [MI] test_overloads *(integration_tests_a.rs)* — *note: loads overloads.vale*
- [MI] simple_extern_function *(integration_tests_a.rs)* — *note: non-generic extern*
- [MI] return_without_return *(integration_tests_c.rs)*
- [MI] test_export_functions *(integration_tests_c.rs)*
- [MI] test_extern_functions *(integration_tests_c.rs)*
- [MI] roguelike_typing_pass *(integration_tests_a.rs)* — *note: loads roguelike.vale (large program); smoke test*

---

# X bucket (cross-cutting, do after the first four finish) — 26 tests

No internal checkpoint; run serially or with a single worker.

## From virtual_tests.rs — GE-on-SI bound-propagation cluster

- [X] simple_override_with_param_and_bound *(virtual_tests.rs)* — *generic interface + bound override*
- [X] struct_with_different_ordered_runes *(virtual_tests.rs)*
- [X] struct_with_less_generic_params_than_interface *(virtual_tests.rs)*
- [X] struct_with_more_generic_params_than_interface *(virtual_tests.rs)*
- [X] struct_repeating_generic_params_for_interface *(virtual_tests.rs)*
- [X] interface_with_method_with_param_of_substruct *(virtual_tests.rs)* — *uses generic List<T>*
- [X] feeding_instantiation_bounds_for_something_created_in_same_function *(virtual_tests.rs)* — *bound propagation*
- [X] generic_interface_forwarder_with_bound *(virtual_tests.rs)* — *generics + bounds + lambda*
- [X] generic_interface_forwarder_with_drop_bound *(virtual_tests.rs)*

## From virtual_tests.rs — CL-on-SI anonymous-substruct trio

- [X] open_interface_constructor *(virtual_tests.rs)* — *anonymous-substruct via lambda*
- [X] open_interface_constructor_multiple_methods *(virtual_tests.rs)*
- [X] lambda_is_compatible_anonymous_interface *(virtual_tests.rs)* — *generic interface + lambda-as-interface*

## From integration_tests_a.rs — extern + generics (panic_in_expr territory)

- [X] extern_function_returning_extern_struct *(integration_tests_a.rs)*
- [X] extern_rust_vec *(integration_tests_a.rs)*
- [X] extern_rust_vec_capacity *(integration_tests_a.rs)*
- [X] extern_method_on_generic_extern_struct_returns_expected_value *(integration_tests_a.rs)*

## From hammer_tests.rs — extern+generic wire-format reshapes

- [X] mixed_own_inherited_template_args_split_correctly_in_wire_format_simple_id *(hammer_tests.rs)*
- [X] extern_method_in_generic_extern_struct_puts_container_args_on_citizen_step_in_wire_format_simple_id *(hammer_tests.rs)*

## From integration_tests_a.rs — subsystem-heavy import-builtin smoke tests

*Trivial body but the `import v.builtins.X.*` forces the whole X builtin to compile. Hold until the relevant subsystem's worker has its builtin's source compiling.*

- [X] simple_program_with_drop *(integration_tests_a.rs)* — *drop builtin → SI*
- [X] simple_program_with_arrays *(integration_tests_a.rs)* — *arrays builtin → CL*
- [X] simple_program_with_as *(integration_tests_a.rs)* — *as builtin → SI*
- [X] simple_program_with_tup *(integration_tests_a.rs)* — *tup2 builtin → SI*
- [X] simple_program_with_opt *(integration_tests_a.rs)* — *opt builtin → SI + GE*
- [X] simple_program_with_result *(integration_tests_a.rs)* — *result builtin → SI + GE*
- [X] simple_program_with_weak *(integration_tests_a.rs)* — *weak builtin → SI weak refs*
- [X] simple_program_with_migrate *(integration_tests_a.rs)* — *migrate builtin → SI*

---

# Held out (final-boss / deferred — not in any of the five buckets)

### after_regions_integration_tests.rs (13 tests)
Deferred — not supposed to pass yet.

### hash_map_tests.rs (13 tests)
Final-boss territory.

### array_list_test.rs (10 tests)
Final-boss territory — every test is `List<T>` with drop-bound generics + struct + lambda.

---

# Bucket weight tally

| Bucket | Phase 1 | Phase 2 | Total |
|--------|--------:|--------:|------:|
| CL     | 21 | 43 | 64 |
| SI     | 29 | 56 | 85 |
| GE     |  6 | 12 | 18 |
| MI     | 33 | 66 | 99 |
| X      | (no phases) | | 26 |
| **Sum**| | | **292** |

(Held out: 13 after-regions + 13 hash-map + 10 array_list = 36. Master total = 328.)
