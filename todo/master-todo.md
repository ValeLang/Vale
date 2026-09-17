# Integration-tests master todo

All unmigrated tests across `FrontendRust/src/integration_tests/tests/` — 328 stubs across 29 files. Each entry is the test fn name; the stub lives in the file under that file's section.

This is the unsplit master list. Per-worktree splits (Vale/Vale2/Vale3/Vale4) will be derived from this.

## after_regions_integration_tests.rs
- [ ] borrowing_to_array
- [ ] call_array_without_element_type
- [ ] diff_iter
- [ ] imm_tuple_access
- [ ] infinite_lambda_call
- [ ] interface_method_call_on_impl_bounded_generic_dispatches_through_interface
- [ ] make_array_without_type
- [ ] map_function
- [ ] pass_overload_set_into_placeholder_parameter_posipp
- [ ] test_overload_set
- [ ] test_returning_empty_seq
- [ ] todo
- [ ] upcasting_in_a_generic_function

## arithmetic_tests_a.rs
- [ ] dividing

## array_list_test.rs
- [ ] array_list_len
- [ ] array_list_set
- [ ] array_list_with_optionals_with_mutable_element
- [ ] array_list_zero_constructor
- [ ] doubling_array_list
- [ ] move_mutable_from_in_lambda
- [ ] mutate_mutable_from_in_lambda
- [ ] remove_from_beginning
- [ ] remove_from_middle
- [ ] simple_array_list_no_optionals

## array_tests.rs
- [ ] array_foreach
- [ ] array_has
- [ ] array_map_taking_a_closure_which_captures_something
- [ ] array_map_with_int
- [ ] array_map_with_interface
- [ ] array_map_with_lambda
- [ ] array_with_capture
- [ ] borrow_arraysequence_as_a_parameter
- [ ] capture
- [ ] capture_mutable_array
- [ ] change_mutability
- [ ] destroy_rsa_of_imms_into_function
- [ ] destroy_rsa_of_muts_into_function
- [ ] destroy_ssa_of_imms_into_function
- [ ] destroy_ssa_of_muts_into_function
- [ ] each_on_ssa
- [ ] immutable_runtime_array_from_lambda
- [ ] immutable_static_array_from_lambda
- [ ] immutable_static_array_from_values
- [ ] make_array_map_with_lambda
- [ ] make_array_map_with_struct
- [ ] map_from_hardcoded_values
- [ ] map_using_array_construct
- [ ] migrate_rsa
- [ ] migrate_ssa
- [ ] mutable_runtime_array_from_lambda
- [ ] mutable_static_array_from_lambda
- [ ] mutable_static_array_from_values
- [ ] mutate_array
- [ ] nested_array
- [ ] nested_imm_arrays
- [ ] new_immutable_array
- [ ] new_rsa
- [ ] reports_when_making_new_imm_rsa_without_lambda
- [ ] returning_static_array_from_function_and_dotting_it
- [ ] simple_array_map_with_runtime_index_lookup
- [ ] simple_static_array_and_runtime_index_lookup
- [ ] swap_out_of_array
- [ ] take_arraysequence_as_a_parameter
- [ ] test_array_length
- [ ] two_dimensional_array
- [ ] unspecified_mutability_runtime_array_from_lambda_defaults_to_mutable
- [ ] unspecified_mutability_static_array_from_lambda_defaults_to_mutable

## block_tests.rs
- [ ] empty_block
- [ ] simple_block_with_a_variable
- [ ] simple_block_with_a_variable_another_variable_outside_with_same_name

## closure_tests.rs
- [ ] addressibility
- [ ] captured_own_is_borrow
- [ ] mutable_lambda
- [ ] mutates_from_inside_a_closure
- [ ] mutates_from_inside_a_closure_inside_a_closure
- [ ] read_from_inside_a_closure_inside_a_closure
- [ ] test_closure_s_local_variables
- [ ] test_returning_a_nonmutable_closured_variable_from_the_closure

## conjunction_tests.rs
- [ ] and
- [ ] and_short_circuiting
- [ ] or
- [ ] or_short_circuiting

## float_tests.rs
- [ ] concat_string_and_float
- [ ] float_arithmetic
- [ ] float_equals
- [ ] print_float

## hammer_tests.rs
- [ ] extern_method_in_generic_extern_struct_puts_container_args_on_citizen_step_in_wire_format_simple_id
- [ ] mixed_own_inherited_template_args_split_correctly_in_wire_format_simple_id

## hash_map_tests.rs
- [ ] gathers_substitutes_bounds_for_interfaces_inside_things_accessed_from_dots
- [ ] gathers_substitutes_bounds_for_structs_inside_things_accessed_from_dots
- [ ] hash_map_collisions
- [ ] hash_map_has
- [ ] hash_map_remove
- [ ] hash_map_remove_2
- [ ] hash_map_update
- [ ] hash_map_values
- [ ] hash_map_with_functors
- [ ] hash_map_with_mutable_values
- [ ] hash_map_with_struct_as_key
- [ ] monomorphize_problem
- [ ] supply_bounds_to_child_functions

## if_tests.rs
- [ ] can_continue_if_other_branch_would_have_returned
- [ ] destructure_inside_if
- [ ] if_nevers
- [ ] if_with_complex_condition
- [ ] if_with_condition_declaration
- [ ] if_with_panics_and_rets
- [ ] ladder
- [ ] moving_from_inside_if
- [ ] ret_from_inside_if_will_destroy_locals
- [ ] simple_false_branch_returning_an_int
- [ ] simple_true_branch_returning_an_int
- [ ] toast

## import_tests.rs
- [ ] tests_import
- [ ] tests_import_of_directory_with_no_vale_files
- [ ] tests_import_with_paackage
- [ ] tests_non_imported_module_isnt_brought_in

## infer_template_tests.rs
- [ ] test_inferring_a_borrowed_argument
- [ ] test_inferring_a_borrowed_static_sized_array
- [ ] test_inferring_an_owning_static_sized_array

## integration_tests_a.rs
- [ ] add_two_i64
- [ ] equals_equals_equals_false
- [ ] equals_equals_equals_true
- [ ] extern_function_returning_extern_struct
- [ ] extern_method_on_generic_extern_struct_returns_expected_value
- [ ] extern_rust_vec
- [ ] extern_rust_vec_capacity
- [ ] hardcoding_negative_numbers
- [ ] lambda_can_call_sibling_lambda
- [ ] lambda_with_a_type_specified_param
- [ ] lambda_with_one_magic_arg
- [ ] reads_a_struct_member
- [ ] roguelike_typing_pass
- [ ] set_swapping_locals
- [ ] simple_extern_function
- [ ] simple_lambda
- [ ] simple_program_returning_an_int
- [ ] simple_program_with_arith
- [ ] simple_program_with_arrays
- [ ] simple_program_with_as
- [ ] simple_program_with_drop
- [ ] simple_program_with_logic
- [ ] simple_program_with_mainargs
- [ ] simple_program_with_migrate
- [ ] simple_program_with_opt
- [ ] simple_program_with_panic
- [ ] simple_program_with_print
- [ ] simple_program_with_result
- [ ] simple_program_with_sameinstance
- [ ] simple_program_with_str
- [ ] simple_program_with_tup
- [ ] simple_program_with_weak
- [ ] stamps_an_interface_template_via_a_function_parameter
- [ ] taking_an_argument_and_returning_it
- [ ] test_block
- [ ] test_borrow_ref
- [ ] test_constraint_ref
- [ ] test_generic
- [ ] test_multiple_invocations_of_generic
- [ ] test_mutating_a_local_var
- [ ] test_overloads
- [ ] test_returning_a_local_mutable_var
- [ ] test_taking_a_callable_param
- [ ] tests_adding_two_floats
- [ ] tests_adding_two_numbers
- [ ] tests_inline_adding
- [ ] tests_inline_adding_more
- [ ] tests_unstackifying_a_variable_multiple_times_in_a_function

## integration_tests_b.rs
- [ ] template_overrides_are_stamped
- [ ] test_array_push_pop_len_capacity_drop
- [ ] test_generic_param_default
- [ ] test_int_generic
- [ ] tests_a_foreach_for_a_linked_list
- [ ] tests_a_linked_list
- [ ] tests_a_templated_linked_list
- [ ] tests_calling_a_templated_struct_constructor
- [ ] tests_calling_a_virtual_function
- [ ] tests_calling_an_abstract_function
- [ ] tests_double_closure
- [ ] tests_from_subdir_file
- [ ] tests_generic_recursion
- [ ] tests_generic_s_lambda_calling_parent_function_s_bound
- [ ] tests_generic_with_a_lambda
- [ ] tests_generic_with_a_polymorphic_lambda
- [ ] tests_generic_with_a_polymorphic_lambda_invoked_twice
- [ ] tests_lambda
- [ ] tests_making_a_variable_with_a_pattern
- [ ] tests_recursion
- [ ] tests_single_expression_and_single_statement_functions_returns
- [ ] tests_upcasting_from_a_struct_to_an_interface
- [ ] tests_upcasting_from_if

## integration_tests_c.rs
- [ ] call_borrow_parameter_with_shared_reference
- [ ] destructure_restackify
- [ ] exporting_array
- [ ] function_return_with_return_upcasts
- [ ] get_or_function
- [ ] ignoring_receiver
- [ ] loop_restackify
- [ ] moving_same_thing_from_both_branches_of_if
- [ ] panic_on_drop_because_of_outstanding_borrow
- [ ] restackify
- [ ] return_without_return
- [ ] same_type_multiple_times_in_an_invocation
- [ ] supplying_bounded_struct_to_struct_accepting
- [ ] test_catch_deref_after_drop
- [ ] test_export_functions
- [ ] test_extern_functions
- [ ] test_narrowing_between_borrow_and_owning_overloads
- [ ] test_overloading_between_borrow_and_weak
- [ ] test_shaking
- [ ] tests_floats
- [ ] truncate_i64_to_i32
- [ ] unlet_to_avoid_an_outstanding_borrow_panic
- [ ] using_same_constraint_ref_from_both_branches_of_if

## opt_tests.rs
- [ ] test_empty_and_get_for_borrow
- [ ] test_empty_and_get_for_none
- [ ] test_empty_and_get_for_some

## ownership_tests.rs
- [ ] basic_builder_pattern
- [ ] borrowing_a_temporary_mutable_makes_a_local_var
- [ ] calls_destructor_on_local_var
- [ ] calls_destructor_on_local_var_unless_moved
- [ ] custom_drop_result_is_an_owning_ref_calls_destructor
- [ ] derive_drop
- [ ] gets_from_temporary_struct_a_members_member
- [ ] member_access_on_returned_owning_ref
- [ ] owning_ref_method_call
- [ ] saves_return_value_then_destroys_local_var
- [ ] saves_return_value_then_destroys_temporary
- [ ] unstackifies_local_vars

## pack_tests.rs
- [ ] extract_seq
- [ ] nested_seqs
- [ ] nested_tuples

## pattern_tests.rs
- [ ] ignore_destructure
- [ ] test_destructuring_a_shared
- [ ] test_matching_a_multiple_member_pack_of_immutable_and_borrow
- [ ] test_matching_a_multiple_member_pack_of_immutable_and_own
- [ ] test_matching_a_multiple_member_seq_of_immutables
- [ ] test_matching_a_multiple_member_seq_of_mutables

## print_tests.rs
- [ ] printlning_a_bool
- [ ] printlning_an_int

## pure_function_tests.rs
- [ ] simple_pure_function

## result_tests.rs
- [ ] test_borrow_is_ok_and_expect_for_ok
- [ ] test_expect_err_panics_for_ok
- [ ] test_expect_panics_for_err
- [ ] test_is_err_and_borrow_expect_err_for_err
- [ ] test_owning_expect
- [ ] test_owning_expect_err

## string_tests.rs
- [ ] empty_string
- [ ] i64_to_string
- [ ] int_to_string
- [ ] simple_string
- [ ] slice_a_slice
- [ ] string_interpolate
- [ ] string_length
- [ ] string_with_escapes
- [ ] string_with_hex_escape
- [ ] strings_equal

## struct_tests.rs
- [ ] constructor_with_self
- [ ] destroy_members_at_right_times
- [ ] make_empty_imm_struct
- [ ] make_empty_mut_struct
- [ ] make_imm_struct_with_one_member
- [ ] make_nested_imm_struct
- [ ] make_struct
- [ ] make_struct_and_get_member
- [ ] mutate_struct
- [ ] normal_destructure
- [ ] odmfrc
- [ ] panic_function
- [ ] sugar_destructure

## tuple_tests.rs
- [ ] returning_tuple_from_function_and_dotting_it
- [ ] simple_tuple_with_one_int
- [ ] tuple_type
- [ ] tuple_with_two_things

## virtual_tests.rs
- [ ] can_call_interface_envs_function_from_outside
- [ ] can_call_virtual_function
- [ ] failed_owning_downcast_with_as
- [ ] failed_pointer_downcast_with_as
- [ ] feeding_instantiation_bounds_for_something_created_in_same_function
- [ ] generic_interface_forwarder_with_bound
- [ ] generic_interface_forwarder_with_drop_bound
- [ ] imm_interface
- [ ] interface_with_method_with_param_of_substruct
- [ ] lambda_is_compatible_anonymous_interface
- [ ] open_interface_constructor
- [ ] open_interface_constructor_multiple_methods
- [ ] owning_interface
- [ ] simple_override_with_param_and_bound
- [ ] simple_program_containing_a_virtual_function
- [ ] struct_repeating_generic_params_for_interface
- [ ] struct_with_different_ordered_runes
- [ ] struct_with_less_generic_params_than_interface
- [ ] struct_with_more_generic_params_than_interface
- [ ] successful_owning_downcast_with_as
- [ ] successful_pointer_downcast_with_as

## weak_tests.rs
- [ ] call_weak_self_method_after_drop
- [ ] call_weak_self_method_while_alive
- [ ] destroy_own_then_locking_gives_none_with_interface
- [ ] destroy_own_then_locking_gives_none_with_struct
- [ ] drop_while_locked_with_interface
- [ ] drop_while_locked_with_struct
- [ ] make_and_lock_weak_ref_from_borrow_local_then_destroy_own_with_interface
- [ ] make_and_lock_weak_ref_from_borrow_local_then_destroy_own_with_struct
- [ ] make_and_lock_weak_ref_from_borrow_then_destroy_own_with_interface
- [ ] make_and_lock_weak_ref_from_borrow_then_destroy_own_with_struct
- [ ] make_and_lock_weak_ref_then_destroy_own_with_interface
- [ ] make_and_lock_weak_ref_then_destroy_own_with_struct
- [ ] make_weak_ref_from_temporary
- [ ] weak_yonder_member

## while_tests.rs
- [ ] each_on_int_range
- [ ] each_on_int_range_with_conditional_break
- [ ] each_on_int_range_with_conditional_break_from_both_branches
- [ ] each_on_int_range_with_unconditional_break
- [ ] infinite_while_loop_conditional_break
- [ ] infinite_while_loop_conditional_break_from_both_sides
- [ ] infinite_while_loop_conditional_return
- [ ] infinite_while_loop_conditional_return_from_both_sides
- [ ] infinite_while_loop_unconditional_break
- [ ] infinite_while_loop_unconditional_return
- [ ] mutable_foreach
- [ ] parallel_foreach
- [ ] return_from_infinite_while_loop
- [ ] simple_while_loop_that_doesnt_execute
- [ ] test_a_for_ish_while_loop
- [ ] tests_a_while_loop_with_a_complex_condition
- [ ] tests_a_while_loop_with_a_declaration_in_it
- [ ] tests_a_while_loop_with_a_set_in_it
- [ ] while_with_condition_declaration
