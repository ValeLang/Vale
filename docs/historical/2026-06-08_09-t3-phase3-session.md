# T3 Phase 3 — full session report (2026-06-08 → 2026-06-09)

T3 worktree: `/Volumes/V/Vale3`, branch `experimental-3`.
JR: T3's junior agent.
Architect: Evan.
Cross-bucket peers: T1 (`Vale`), T2 (`Vale2`), T4 (`Vale4`).

Final tip on `experimental` at session end: **`0276d4caf`**.
Suite at start (per Vale T1's readiness ping): 1043/1043.
Suite at end: **1089/1089**.

## TL;DR — what landed across the session

T3 committed **6 commits** on `experimental`. End-of-session bucket:
- **18 committed green tests** (14 original T3 + 1 Scala-side-ignored migrate-and-ignore + 1 testvm-leak via tup0 resync + 1 bonus cross-bucket pickup + 1 deferred-then-unblocked humanizer)
- **2 `[~]`** remaining: `open_interface_constructor_multiple_methods` (architect-deferred `templata_compiler` arm-widen) and a documented latent `extra_envs_to_look_in` bonus bug (silent on the entire corpus).

Cross-bucket interaction:
- T4 → T3: `borrowing_to_array` accepted (1 test).
- T1 → T3: 4 parked T4-handoffs accepted; net T3 closed 3 of 4 (the 4th — `call_array_without_element_type` — required a typing-pass port).
- T2 → T3: offer declined per architect.
- T3 → T1: heads-up that lambda would auto-unblock on cascade; T1's `[~]` on `open_interface_constructor` actually misread the discriminant — T3's fix unblocked theirs as well.

Substantive code beyond test bodies:
1. **`instantiator.rs:1138-1153`** — `translate_override` case-placeholder closure body (Milano's independent-runes path).
2. **`overload_resolver.rs:1054-1093`** — port of Scala's `getPlaceholderExtraCallEnvs` (~30 lines, brand-new fn).
3. **`overload_resolver.rs:386-391`** — wired the new helper into `get_candidate_banners` + fixed a `for _e in ... { ...(env, ...) }` typo (latent + silent on the corpus).
4. **`compiler_solver.rs:1405-1446`** — `ResolveSR @BRRZ None` branch body, 1 SPDMX temp-disable (Luz #097 Exception B).
5. **`heap.rs:1869-1888`** — `to_von` `ArrayInstance` + `StructInstance` arms.
6. **`values.rs:217-225`** — `print_refs` body (JR-initiated bonus during the fix cascade).
7. **`instantiated_humanizer.rs:265`** — `INameI::Self_(_) => "self"` arm (one-line port; closes both lambda + T1's parked open_interface).
8. **`builtins/resources/tup0.vale`** — `struct Tup0 imm` resource resync (one-word diff with downstream leak fix).
9. **`overload_resolver.rs:386`** — deadness comment documenting a verified-silent bonus bug.

Plus Luz submodule commit #097 (SPDMX curate-queue rationale), pushed to `origin/main`.

---

## Session arc by phase

### Phase A — readings & T3 Phase 3 dispatch (commit `68248d31e`)

1. Read `migrate-tl.md` + all referenced docs (`Luz/skills/guardian-tl.md`, `docs/architecture/typing-pass-ai-guide.md`, `docs/skills/migrate-diagnoser.md`, `FrontendRust/src/typing/typing-pass-todo.md`, `FrontendRust/docs/migration/migration-policy.md`, `investigations/coord_send_some_branch_fix.md`, `phase3-plan.md`).
2. Vale (T1) pinged for Phase 3 readiness; replied: clean tree, `experimental-3 @ d714e6e45`, build green / 0 warnings, JR primed.
3. Architect dispatched T1 → Vale T1 dispatched T3 with the **Virtuals + generics-on-interfaces** theme — 14 tests total in `virtual_tests.rs` (12) + `after_regions_integration_tests.rs` (2).
4. JR drove the cluster test-by-test under the standing "per-green-sync-ready, accumulate-test-only-until-substantive" rule. Cluster-reuse pairs/trios sequenced per `phase3-plan.md`.

T3's JR results within Phase 3 (initial dispatch):
- ✓ `simple_override_with_param_and_bound` (Serenity)
- ✓ `struct_with_different_ordered_runes` (Firefly)
- ✓ `struct_with_less_generic_params_than_interface` (Raza)
- `[~]` `struct_with_more_generic_params_than_interface` (Milano) → `instantiator.rs:1140` `translate_override: case placeholder closure body` stub
- ✓ `struct_repeating_generic_params_for_interface` (Enterprise) — confirmed Milano stub was Milano-only (Enterprise didn't hit it)
- ✓ `interface_with_method_with_param_of_substruct`
- ✓ `feeding_instantiation_bounds_for_something_created_in_same_function`
- ✓ `generic_interface_forwarder_with_bound`
- ✓ `generic_interface_forwarder_with_drop_bound`
- `[~]` `open_interface_constructor` → `instantiator.rs:6269` `translate_struct_name: AnonymousSubstruct branch` stub → **handed off to T1**
- `[~]` `open_interface_constructor_multiple_methods` → `templata_compiler.rs:2239` `(_, KindT::Interface(_))` arm narrowing of Scala's `(ISubKindTT, ISuperKindTT)` (documented typing-pass-ai-guide trap "Hand-rolled enumerations of an is-a-Trait set") → **architect-deferred**
- `[~]` `lambda_is_compatible_anonymous_interface` → same site as `open_interface_constructor` → blocked on T1's cascade
- ✓ `interface_method_call_on_impl_bounded_generic_dispatches_through_interface`
- ✓ `upcasting_in_a_generic_function` — migrate-and-ignore (Scala uses `ignore(...)` upstream pending CoordT redesign; body migrated, `#[ignore]` reason mirrors Scala intent verbatim)

**Cluster commit `68248d31e`** (rebased from `bb4e4ee10`): 8 green + 1 migrate-and-ignore + 5 `[~]`. Suite 1043 → 1062 post-rebase (picked up T1's 8 probe primitives at `e9e29e5b1`, T2's array_list 10 at `fd526f0a4`, T4's foundation extern at `943484b0f`).

### Phase B — Milano body-fill (commit `a910df1ba`)

Architect cleared the Milano deferral. JR dispatched with explicit spec: mirror the dispatcher-placeholder closure right above at `instantiator.rs:1117-1133`, with three differences (`independent_impl_template_args` source iter, `_impl_placeholder_to_case_placeholder` lookup, KindPlaceholder-only `let ... else` destructure). Scala parity port of audit-block lines 1311-1324.

JR landed clean. Milano un-`[~]`'d + body migrated. Suite 1062 → 1063. Fire commit + sync.

### Phase C — `/migrate-diagnoser` on the 4 `[~]`s

Per architect's diagnose-all instruction:
- **#1 `open_interface_constructor`** + **#3 `lambda_is_compatible_anonymous_interface`** — same `instantiator.rs:6269` stub arm (`IStructNameT::AnonymousSubstruct`). JR-scope body fill; handed to T1 for #1, #3 awaits T1's cascade rebase.
- **#2 `open_interface_constructor_multiple_methods`** — `templata_compiler.rs:2239` narrowed arm pattern. Architect-deferred ("leave the bug in").
- **#4 `interface_method_call_on_impl_bounded_generic`** (`[x]` by then) — diagnosed before the fix: `compilation.rs:210` humanizer stub masked a typing-pass error. Underlying root cause: `overload_resolver.rs:371-388` `get_candidate_banners` skipped the entire `getPlaceholderExtraCallEnvs` call + helper fn that walks placeholder → `ImplSubCitizenImpreciseNameS` → IShip's outer env. Plus a `_e` typo (silent).

### Phase D — `getPlaceholderExtraCallEnvs` port + wire-in + `_e` fix (commit `0f76b7c78`)

JR dispatched with the full spec: 30-line helper port (Scala audit block lines 1064-1099), wire into `get_candidate_banners` between `get_param_environments` and `extra_envs_to_look_in` loops, fix the `_e` typo.

JR landed with one SPDMX flatten-the-nested-match nudge satisfied. Suite 1063 → 1064. Fire commit + sync.

### Phase E — `_e` bonus bug repro investigation

Architect queued the question "can we minimal-repro the bonus bug?" in `migration-drive-todo.md` with a `[ ]` after fire-commit. T3 investigated by:
1. Reverting just the loop fix (`for e ... { ...(*e, ...) }` → `for _e ... { ...(env, ...) }`).
2. Running the full suite — **1064/1064 passing**, no regressions.
3. Auditing call sites: only `EdgeCompiler.scala:572` (override resolution) passes non-empty `extra_envs_to_look_in`, and both envs it passes (interface_template outer env + sub_citizen_template outer env) are redundantly reachable: sub-citizen via `get_param_environments`, interface via the calling env's parent chain (`dispatcherCaseEnv → ... → packageEnv`).
4. Vale syntax doesn't allow declaring concrete functions inside an interface body (only abstract methods, which mismatch the override's filters).

**Conclusion: the bug is empirically silent on the corpus and there's no Vale source that exercises it.** Marked `[-]` in todo with the structural rationale. The fix stays committed (Scala parity holds); a 5-line deadness comment was added above the loop documenting the empirical-silence finding for future readers.

### Phase F — `borrowing_to_array` cross-bucket steal from T4 (commit `c1bf939e7`)

T4 offered `borrowing_to_array` (independent of T4's extern path; rides on T2's `List<int>` machinery). Architect cleared. JR dispatched with body spec.

JR landed first try — runtime-sized-array-of-borrow constructor `[]<mut>&E(len, lambda)`, `&List<E>` borrowing through a generic, RSA-of-borrow indexing all worked through cleanly on T2's array_list scaffolding. Suite 1064 → 1065 → 1069 (rebased onto T2's `8cbb45233` hash_map first wave + TL vivem dispatch table reorder at `211ff8ea5`). Fire commit + sync.

### Phase G — full bucket tally (4-way poll)

Architect asked all 4 TLs to report progress + steal candidates. Replies:
- **T1**: 8 committed green, mid-cascade on `open_interface_constructor`. Blocked on a Guardian MCP schema bug. Architect-parked after their next green. 4 queued T4-handoffs flagged as routable to T3.
- **T2**: 19/23 done, 15 uncommitted greens accumulating. 3 tests remaining. Not maxed, offered the `hash_map_remove` pair.
- **T4**: 2 green committed, JR mid-flight on `extern_rust_vec` driver. 4 left to drive. Offered `borrowing_to_array` (already taken).

Total Phase 3 across buckets: ~26 committed green + 41 if counting T2's accumulator.

### Phase H — 4-test T1-handoff cluster steal (commit `c6a93c261` and others)

Architect cleared T3 to take the 4 parked T4-handoffs from T1. JR dispatched with the full cluster spec + load_expected precedent for `imm_tuple_access`.

**Foundation `call_array_without_element_type` escalated** with a typing-pass panic at `compiler_solver.rs:1412`: `Unimplemented: ResolveSR @BRRZ None branch`. JR correctly escalated rather than `[~]`-ing (substantive panic in test infrastructure).

Architect chose **option 2: skip the foundation, probe the other 3**. JR's sweep:
- ✓ `make_array_without_type` — `#[]()` shorthand bypasses the `compiler_solver.rs:1412` site.
- ✓ `imm_tuple_access` — `load_expected("programs/tuples/immtupleaccess.vale")` per weak_tests.rs precedent.
- `[~]` `test_returning_empty_seq` — different blocker: `testvm/heap.rs:1870` `to_von: StructInstance — pilot doesn't exercise`.

**Cluster heterogeneity confirmed** — foundation's blocker was single-test-isolated. The 2 quick greens + held foundation + held empty-seq committed as `c6a93c261` (rebased to `ea0341b1e`, picking up T2's bucket-complete + T4's `extern_rust_vec_capacity`).

### Phase I — dispatch Fix 1 + Fix 2 (commit `75d6b0dbd` rebased to `df067138c`)

Architect: "JR fixes both."

Fix 1: `heap.rs` `to_von` `ArrayInstance` + `StructInstance` arms (~7 lines, mirrors adjacent audit-block Scala).
Fix 2: `compiler_solver.rs` `ResolveSR @BRRZ None` branch (~30 lines, Scala audit-block 668-691; `self.resolve_function(env.original_calling_env, state, ranges_slice, env.call_location, resolve.name, param_coords, env.context_region, true)` with `.expect()` on outer `ICompileErrorT`).

JR landed both + a bonus port of `values.rs:217-225` `print_refs` body (encountered after Fix 1 landed — Scala-faithful with one inner closure-panic for the `vimpl(memberH.name.toString)` analogue). Substantive: 1 SPDMX temp-disable on Fix 2's extra Rust args (Luz #097 Exception B — Scala has 5-arg delegate trait wrapping 8-arg underlying, Rust calls underlying directly).

`call_array_without_element_type` → green via Fix 2. `test_returning_empty_seq` → still failing past Fix 1 + print_refs, hitting `heap.rs:310 check_for_leaks` "Memory leaks!".

Architect: "as long as things are scala parity, im fine ... #ignore and `[~]` the test. dont revert the test though. fire commit and sync." Test body kept migrated, `#[ignore]` re-added with the third-layer-cascade note. Luz `48962f3` committed + pushed for the SPDMX rationale. Parent commit landed.

### Phase J — `/migrate-diagnoser` on the leak

Per skill workflow:
1. Un-`#[ignore]` `test_returning_empty_seq`, run, capture trace + panic.
2. Trace: `o502 NewStruct rc0→1 → Stackify rc1→2 → rc2→1 → Unstackify rc1→2 → rc2→1 → Ending program rc1→0` then `Checking for leaks → o502 → Memory leaks!`
3. Read `heap.rs:310 check_for_leaks` — filters `objects_by_id.values` by `!matches!(a.kind, KindV::Void(_))`. Non-empty → leak. So o502 isn't removed despite rc=0.
4. `deallocate_if_no_weak_refs` at `heap.rs:880` is the only `objects_by_id.remove(...)` site. Need to identify where it should fire for o502.
5. Audit `expression_vivem.rs:2406 discard → 2431 cleanup` (the end-of-program call from `vivem.rs:225`). Match arms:
   - `OwnH => {}` — deliberate no-op (matches Scala 2505 comment "Vivem often discards owning things, if we're making a new owning reference to it").
   - `MutableShareH | ImmutableShareH → StructHT` — destructures (calls `deallocate_if_no_weak_refs` via `destructure`).
6. Temporarily instrumented `cleanup` to print `expected.ownership` + `expected.kind` + `actual.ownership` + `rc`. Verified: o502 reached cleanup with `expected.ownership=OwnH, rc=0` → hit the no-op arm. **OwnH was the question — why is `()` returning OwnH?**
7. Found pre-existing investigation doc `investigations/test_returning_empty_seq_vm_leak.md` — exact same bug already diagnosed Scala-side; fix was a one-line resource: `struct Tup0 imm { }` (was `struct Tup0 { }`).
8. **Verified Rust resource drift**: `FrontendRust/src/builtins/resources/tup0.vale` was still `struct Tup0 { }` (pre-fix), while `Frontend/Builtins/.../tup0.vale` (canonical) had been `struct Tup0 imm { }` for some time. The Rust port carried the pre-fix copy and never picked up the upstream resync.

Diagnosed. Cleaned up the debug print, ran SCPX (clean), confirmed no Scala touched, **wrote one-line JR dispatch**: just sync the `imm` keyword into the Rust resource.

### Phase K — tup0 resource resync + un-`[~]` empty_seq (commit `3b6625969` rebased to `4f7601208`)

JR landed the one-line resource sync + un-`#[ignore]`. Suite 1085 → 1086 with `test_returning_empty_seq` passing.

T1 watcher fired with their reply to the lambda hand-back offer: **declined**, already fire-committed at `b1aa550f6`. Lambda comes back to T3. T1's note flagged: "open_interface_constructor ended `[~]` on `humanize_name: unimplemented variant Discriminant(57)` (= `INameI::ConstructorTemplate`). Scala's `InstantiatedHumanizer.scala:107-188` has no `case ConstructorTemplateNameI` arm either — real upstream Scala MatchError."

Commit + rebase past `b1aa550f6` + `d56c02675` (T4 Phase 3B finish: `mixed_own_inherited_template_args_split_correctly_in_wire_format_simple_id`). Suite 1087/1087 post-rebase.

### Phase L — Lambda body + `Self_` humanizer arm port (commit `0276d4caf`)

Un-`#[ignore]`'d lambda, body-filled (interface `AFunction2<R,P1,P2>`, `((i,b) => { str(i) + str(b) })`, match on `VonStr("42true")`). First run: hit the same `humanize_name: Discriminant(57)` panic T1 warned about. About to `[~]` with T1's framing when architect pushed back: **"hold please. why arent we migrating the unimplemented panic"**.

Re-investigated:
1. Read Scala `InstantiatedHumanizer.scala` — has many name arms; **no `case ConstructorTemplateNameI` confirmed**.
2. Then read `ConstructorTemplateNameI` definition: exists as a Scala class at `instantiating/ast/names.scala:445`, but is never constructed anywhere in the Scala instantiating pass (`grep -rn "ConstructorTemplateNameI" InstantiatingPass/src` finds only the definition).
3. The typing-pass → instantiating-pass `translateFunctionTemplateName` at `Instantiator.scala:3760+` falls through to `vimpl(other)` for unhandled cases — meaning Scala itself would panic if a typing-pass `ConstructorTemplateNameT` reached the translator.
4. **Suspected T1 misread the discriminant.** Counted variants of Rust's `INameI` enum (`instantiating/ast/names.rs:135-208`):
   - Line 136 = variant 0 (`RegionName`)
   - Line 192 = variant 56 (`ConstructorTemplate`)
   - **Line 193 = variant 57 (`Self_`)**
5. Temporarily added `match other { Self_ => "Self_", ConstructorTemplate => "ConstructorTemplate", _ => "neither" }` to the wildcard panic message. Re-ran lambda: **panic confirmed `Discriminant(57) dbg="Self_"`**.
6. Scala has the arm at `InstantiatedHumanizer.scala:121`: `case SelfNameI() => "self"`.
7. Reverted the debug-only match. Added the one-line port: `INameI::Self_(_) => "self".to_string(),`
8. Lambda → green. Bonus: tried un-`#[ignore]`-ing T1's `open_interface_constructor` (same misread, same site) — **also green** with the one-line fix.

Suite 1087 → 1089. SCPX clean. Commit `0276d4caf` fire-committed + synced.

T3 bucket final: **18 committed green + 2 `[~]`**.

---

## Commits T3 landed on `experimental` (this session)

| Commit | Theme | Suite delta |
|---|---|---|
| `68248d31e` | T3 Phase 3 — virtuals + generics cluster (9 tests, 5 `[~]`) | 1043 → 1062 (post-rebase pulled T1/T2/T4 advances) |
| `a910df1ba` | Milano body-fill (`translate_override` case-placeholder closure) | 1063/1063 |
| `0f76b7c78` | `getPlaceholderExtraCallEnvs` port + wire-in + `_e` typo fix | 1064/1064 |
| `c1bf939e7` | `borrowing_to_array` (cross-bucket steal from T4) + deadness comment | 1065 → 1069 (rebase) |
| `c6a93c261` | T1-steal sweep: `make_array_without_type` + `imm_tuple_access` | 1071 |
| `75d6b0dbd` → rebased `df067138c` | Fix 1 (heap.rs `to_von`) + Fix 2 (`ResolveSR` None) + bonus `print_refs` + `call_array_without_element_type` green + `test_returning_empty_seq` `[~]` | 1083 → 1085 |
| `3b6625969` → rebased `4f7601208` | tup0.vale `imm` resource resync + un-`[~]` empty_seq | 1086 → 1087 |
| `0276d4caf` | `Self_` humanizer arm + lambda body + un-`[~]` `open_interface_constructor` | 1088 → 1089 |

Luz: 1 SPDMX curate-queue case file commit (`48962f3` for #097, pushed).

---

## Inter-bucket coordination (high-leverage moments)

- **T4 → T3 steal**: `borrowing_to_array` — clean single-test handoff, landed first-try.
- **T1 → T3 steal**: 4 T4-originated tests; T1 was queue-parked behind a Guardian MCP blocker. T3 closed 3 of 4.
- **T3 → T1 heads-up on lambda**: T1 declined the offered hand-back; T3 picked up the test post-rebase and identified the `Self_` arm gap (which also closed T1's parked `open_interface_constructor`).
- **T2 → T3 offer declined per architect**: T2's `hash_map_remove` pair stayed with them.
- **Discriminant misread cross-correction**: T1's `b1aa550f6` framed the humanizer gap as `ConstructorTemplate / upstream Scala MatchError`. T3 verified the actual variant was `Self_` (off-by-one in the enum), found the Scala arm exists at line 121, and ported it. **Architect pushback on "why aren't we migrating it" was the trigger** — without that prompt, T3 would have `[~]`'d the test following T1's framing.

---

## New TL/JR patterns surfaced this session (candidates for `migrate-tl.md` rule additions)

These are worth considering as ≤25-word rules — surfacing here so the architect can decide which (if any) to land:

1. **JR's `git diff --name-only` listings often include already-committed files** — verify against `git status` before trusting JR's reported diff scope.
2. **Discriminant numerics in panic messages should be cross-checked against the enum declaration order** before adopting their identification. Off-by-one errors compound through TL bucket reports.
3. **"Unimplemented" in a panic message can mean either "this code is stubbed" or "this case isn't reached yet."** The distinction matters for whether `[~]` or body-fill is correct; read the surrounding code, not just the message.
4. **Bonus bugs that pass the full suite are valid Scala-parity fixes** — empirical silence isn't an objection. Document with a `// Empirically dead on the current Vale corpus (verified DATE)` comment so future readers don't re-investigate.
5. **Resource drift on `builtins/resources/*.vale`** is a class of bug distinct from Rust code drift — verify resource parity vs `Frontend/Builtins/` whenever a runtime/leak-shaped bug surfaces in the testvm with no obvious code-side cause.

---

## Open follow-ups

- **`open_interface_constructor_multiple_methods`** — architect-deferred. Documented typing-pass-ai-guide trap; fix is `(s, t) if ISubKindTT::try_from(*s).is_ok() && ISuperKindTT::try_from(*t).is_ok() => ...` at `templata_compiler.rs:2238-2240`.
- **`extra_envs_to_look_in` bonus bug** — investigation closed `[-]` (no Vale repro possible; latent/defensive). Documented inline at `overload_resolver.rs:386`.
- **VM `OwnH` cleanup gap** (per `investigations/test_returning_empty_seq_vm_leak.md` Option 2) — Vivem's end-of-program `cleanup` does nothing for OwnH refs. Currently only `test_returning_empty_seq` exercises this; fixed via the `imm` resource sync (Option 1). Option 2 stays open as a latent VM-side gap that would surface only if another test returns a user-defined mutable struct from `main`.
- **T1's parked status** — Architect "stop after next green" instruction holds. T1's cascade landed (`b1aa550f6`); their open_interface `[~]` now actually closed by T3's `0276d4caf` (architect chose not to ping T1 about the correction).
- **Phase 3 wrap-up** — T3 is at 18/14+steals committed. T2 done. T4 done (3B closed). T1 parked. The 4-bucket Phase 3 is nearing completion.

---

## Methods used this session

- `/migrate-diagnoser` invoked twice (on the 4 `[~]`s, then on the leak). Both produced actionable diagnoses; the leak one was particularly productive because it surfaced a pre-existing investigation doc that had the Scala-side fix already designed.
- Empirical silence verification (Phase E) — revert a fix, run the full suite, confirm zero behavioral diff. Useful for distinguishing latent bugs from active ones; the deadness comment is a good idiom to leave behind.
- Discriminant verification by temporary instrumentation (Phase L) — when a TL bucket report names a variant, count variants of the actual Rust enum + temporarily augment the panic match to confirm before acting. Off-by-one errors in this layer of the migration cost real time.
- Resource-drift diffing — `cat Frontend/Builtins/.../X.vale` vs `cat FrontendRust/src/builtins/resources/X.vale` is cheap and worth doing whenever a builtin-shaped runtime bug surfaces with no obvious code cause.
- TL parity-review checklist applied to every JR sync-ready: verify diff scope vs `git status`, parity-check each Rust edit against its adjacent `/* scala */` block, check for Guardian temp-disables (rationale principled? matches Exception precedent?), verify SPDMX flatten-nested-match hits are responses to real divergences. Comment-only and test-only changes accumulate uncommitted; substantive changes gate on architect fire-commit.

---

## Final state hand-off (for the next session, whoever picks up)

- T3 bucket effectively complete: 18 committed green + 2 `[~]` documented with full provenance in `migration-drive-todo.md`.
- T3 JR is idle.
- Working tree clean at `experimental-3 == experimental == 0276d4caf`.
- Suite 1089/1089 passing, 49 ignored. 0 warnings. SCPX 260/260.
- All open T3 follow-ups + cross-bucket notes captured in `migration-drive-todo.md` and this doc.
