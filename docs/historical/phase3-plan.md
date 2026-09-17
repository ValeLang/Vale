# Phase 3 — 4-Thread Thematic Split

**State:** 65 stubs remaining + 1 explicit CoordSendSR-deferred (`panic_in_expr`). CoordSendSR fix stays unlanded per architect — `[~]` accumulation is the data we want.

## Theme map (dep layer first, then independent)

### Foundation — land before T2 starts

**T1 — Probe primitives (`simple_program_with_*` cluster — 8 tests)**

All in `integration_tests_a.rs`. Byte-identical Scala shape:
```scala
test("Simple program with X") {
  val compile = RunCompilation.testNoBuiltins("import v.builtins.X.*; exported func main() int { return 3; }")
  compile.evalForKind(Vector()) match { case VonInt(3) => }
}
```

| Test | Builtin imported |
|---|---|
| `simple_program_with_drop` | `v.builtins.drop` |
| `simple_program_with_migrate` | `v.builtins.migrate` |
| `simple_program_with_arrays` | `v.builtins.arrays` |
| `simple_program_with_as` | `v.builtins.as` |
| `simple_program_with_tup` | `v.builtins.tup2` |
| `simple_program_with_opt` | `v.builtins.opt` |
| `simple_program_with_result` | `v.builtins.result` |
| `simple_program_with_weak` | `v.builtins.weak` |

**Cluster reuse:** first test lands the pattern + whichever builtin import path is unwired; remaining 7 are 7 follow-on Edit-tool calls each changing one builtin name (well under the ~40-invocation Python threshold). Whole cluster ~30 min if no infra gaps; longer if a builtin's import path is still gated.

**Why first:** these probe whether every `v.builtins.*` package resolves end-to-end through compile + monomorphize + run. If any builtin import is gated, downstream tests that pull in that builtin (`simple_program_with_arrays` → `array_list_test.rs`'s ArrayList) hit the same wall.

---

### Layer 1 — independent themes (dispatch all 3 in parallel after T1)

**T2 — Collections (23 tests, array_list THEN hash_map)**

`array_list_test.rs` (10) — depends on RSA (already landed). All `ArrayList<int>` tests:
- `simple_array_list_no_optionals` (foundation — lands the pattern)
- `doubling_array_list`, `array_list_zero_constructor`, `array_list_len`, `array_list_set`
- `array_list_with_optionals_with_mutable_element` (steps into Opt)
- `mutate_mutable_from_in_lambda`, `move_mutable_from_in_lambda` (intersects T3 lambda)
- `remove_from_middle`, `remove_from_beginning`

`hash_map_tests.rs` (13) — depends on `array_list`:
- `monomorphize_problem`, `supply_bounds_to_child_functions` (generic-bound subset)
- `hash_map_update`, `hash_map_collisions`, `hash_map_with_functors`, `hash_map_with_struct_as_key`, `hash_map_has`, `hash_map_values`, `hash_map_with_mutable_values`
- `hash_map_remove`, `hash_map_remove_2` (pair — second is variation of first)
- `gathers_substitutes_bounds_for_{structs,interfaces}_inside_things_accessed_from_dots` (paired — interface vs struct variants)

**Cluster reuse within T2:**
- All `array_list_*` share the ArrayList instantiation scaffolding.
- All `hash_map_*` share the HashMap fixture.
- `gathers_substitutes_bounds_*` pair: struct version lands the pattern, interface version is a 5-line rewrite.
- `hash_map_remove`/`hash_map_remove_2`: same shape, second is a variant scenario.

**Sequencing:** T2 owner finishes all of array_list before opening hash_map (hash_map uses ArrayList internally — any gap hash_map hits is fixed in array_list first).

---

**T3 — Virtuals + generics-on-interfaces (~16 tests)**

`virtual_tests.rs` (12):
- `simple_override_with_param_and_bound` (foundation)
- `struct_with_different_ordered_runes`
- `struct_with_less_generic_params_than_interface`, `struct_with_more_generic_params_than_interface` (paired arity variations)
- `struct_repeating_generic_params_for_interface`
- `interface_with_method_with_param_of_substruct`
- `feeding_instantiation_bounds_for_something_created_in_same_function`
- `generic_interface_forwarder_with_bound`, `generic_interface_forwarder_with_drop_bound` (paired)
- `open_interface_constructor`, `open_interface_constructor_multiple_methods` (paired)
- `lambda_is_compatible_anonymous_interface`

Plus `after_regions_*.rs` implementables in the same domain:
- `interface_method_call_on_impl_bounded_generic_dispatches_through_interface`
- `upcasting_in_a_generic_function`

**Cluster reuse within T3:**
- `struct_with_*_generic_params_*` trio: arity-difference variations; land first, two follow-on Edits for the other two.
- `generic_interface_forwarder_*` pair.
- `open_interface_constructor*` pair.

---

**T4 — Extern/FFI + tail cleanup (~12–18 tests)**

`integration_tests_a.rs` extern cluster (4):
- `extern_function_returning_extern_struct`
- `extern_rust_vec`, `extern_rust_vec_capacity` (paired)
- `extern_method_on_generic_extern_struct_returns_expected_value`

`hammer_tests.rs` wire-format cluster (2):
- `mixed_own_inherited_template_args_split_correctly_in_wire_format_simple_id`
- `extern_method_in_generic_extern_struct_puts_container_args_on_citizen_step_in_wire_format_simple_id`

`after_regions_*.rs` tail (implementables — 3-5):
- `imm_tuple_access`
- `borrowing_to_array`, `make_array_without_type`, `call_array_without_element_type` (paired-ish)
- `test_returning_empty_seq`
- `todo`

`after_regions_*.rs` Scala-ignored deferrals (skip — leave stubbed):
- `map_function`, `test_overload_set`, `pass_overload_set_into_placeholder_parameter_posipp`, `diff_iter`, `infinite_lambda_call` (matches Scala `ignore()` list)

`integration_tests_c.rs` residuals (2 — likely CoordSendSR-flagged, expect `[~]`):
- `test_overloading_between_borrow_and_weak` (weak + overload)
- `test_catch_deref_after_drop`

**Cluster reuse within T4:**
- `extern_rust_vec*` pair.
- `extern_function_returning_extern_struct` ↔ `extern_method_on_generic_extern_struct_*`: share extern-call scaffolding.
- `borrowing_to_array` ↔ `make_array_without_type` ↔ `call_array_without_element_type`: type-inference variants on arrays.

---

## Cross-thread similarity flags

| Pattern | Threads touched | Note |
|---|---|---|
| `mutate_mutable_from_in_lambda`, `move_mutable_from_in_lambda` (T2) ↔ closure tests already done | T2 | If T2 hits closure-capture scaffolding gaps, the existing closure tests' machinery should be reused, not re-derived |
| `gathers_substitutes_bounds_for_*` (T2/hash_map) ↔ `supply_bounds_to_child_functions` (T2) ↔ generic-bound tests in T3 | T2/T3 | Generic-bound substitution machinery — if T3 lands the typing-pass bound-threading pattern first, T2's hash_map bound tests inherit it |
| Wire-format tests (T4) depend on hammer infra — likely already wired via simplifying buckets in Phase 2 | T4 | Confirm by running hammer_tests baseline before dispatch |
| `simple_program_with_arrays` (T1) → if it works, `array_list_test.rs` (T2) is unblocked | T1 → T2 | T1 sequencing is load-bearing |

---

## Recommended dispatch order

1. **Dispatch T1 alone.** Whoever takes it lands the probe-primitives cluster fast (or surfaces a `v.builtins.*` gap that needs architect attention). Other 3 TLs stay parked or do prep reading on their themes.
2. **After T1 lands** (or T1's blocker is identified), **dispatch T2/T3/T4 in parallel.**
3. **Cross-bucket stealing OK** — if T2 finishes array_list and hash_map quickly, owner can pull from T3's `after_regions` generic tail; if T4 hits `[~]` walls early, owner pulls from T2's hash_map tail.
4. **Sync cadence:** same as Phase 2 — per-green sync-ready, parity review, architect-gated fire commit, rebase + ff.

## Counts after Phase 3

Best case (all 65 land): **0 stubs.** Then on to typing-pass-todo.md residuals + Roadmap items (pipeline wiring, final_ast, VON parity, cut-over, cleanup).

Realistic case (Scala-deferred + CoordSendSR-blocked stay `[~]`): ~50–55 land, ~10–15 `[~]` accumulate — and the `[~]` pile is the data for evaluating the CoordSendSR fix's true blast radius.

---

## Open questions for architect

1. **T1 sequencing strict?** Or can T1 + the most-independent-theme (T3 virtuals) start concurrently to keep all 4 TLs busy from second 1?
2. **`after_regions` Scala-ignored set** — confirm which exact tests stay `[~]` permanently (mirror Scala's `ignore()` plus the migrate-tl.md "~14 deferrals" map).
3. **CoordSendSR-flagged identification** — `test_overloading_between_borrow_and_weak` and `test_catch_deref_after_drop` haven't been confirmed CoordSendSR-blocked; might just be standard body-fills. Investigate during T4.
4. **`monomorphize_problem` + `supply_bounds_to_child_functions`** in hash_map_tests — these are generics-bound tests that happen to use hash_map fixtures; topologically may belong in T3 not T2. Re-classify if T3 owner has bandwidth.
