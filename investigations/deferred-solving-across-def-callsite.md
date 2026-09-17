# Deferred Solving Across the Definition/Call-Site Boundary

**Status:** Design exploration, no code changes yet. Captured 2026-04-26 from a session that started as a Family 4.1 follow-up and broadened into an architectural sketch. Two competing approaches are documented (Option A: deferred solving; Option B: lift to generic parameter); the session's read leans toward A as the long-term model and B as a possible stepping-stone implementation.

**TL;DR.** The typing pass currently requires every non-identifying rune to be concluded at definition time. That assumption is what's keeping "imm-when-fields-imm" tuples from working, and it's what would force associated types to be a one-off. The two viable ways to fix it:

- **Option A — deferred solving.** Let the def-time solve be incomplete; finish it at each call site. Adds a new "deferred rune" category alongside identifying runes; keeps signatures narrow.
- **Option B — lift to generic parameter.** Make any unsolved rune an identifying rune. No new typology; reuses existing machinery. But signatures cascade-widen.

Both end up running the rule with concrete arg values somewhere; both store the result as a placeholder substitution. They're nearly isomorphic. Option A is structurally cleaner and composes better with associated types; Option B is a smaller code change and can be a stepping stone. This doc records the chain of reasoning so it can be picked up cold later.

## Why we got here: Family 4.1 imm tuple access

The AfterRegions test "imm tuple access" (`Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala:72`) was disabled in 2022 with `vfail() // these tuples are actually mutable`. The test body is just:

```vale
exported func main() int {
  t = (true, 42);
  return t.1;
}
```

Removing the `vfail()` made the test pass — the disabling was overcorrection, since the body never actually verifies imm-ness. But the *underlying question* (should `(true, 42)` produce an imm tuple, given that both fields are share-able primitives?) was real and unresolved. We added a new ignored test "Tuple with all imm fields is imm" (`Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala`, near the existing "Can destructure and assemble tuple") that asserts `Tup2`'s instantiated mutability is `ImmutableT` for `(true, 42)`. It's currently `ignore`d because the feature doesn't exist.

This doc is about what would have to change to make that test pass, and the broader cleanup that fix sits inside.

## What's in the compiler today

Two relevant mechanisms exist:

### Mechanism A: `getCompoundTypeMutability`

`Frontend/TypingPass/src/dev/vale/typing/citizen/StructCompiler.scala:288-293`:

```scala
def getCompoundTypeMutability(memberTypes2: Vector[CoordT]): MutabilityT = {
  val membersOwnerships = memberTypes2.map(_.ownership)
  val allMembersImmutable = membersOwnerships.isEmpty || membersOwnerships.toSet == Set(ShareT)
  if (allMembersImmutable) ImmutableT else MutableT
}
```

**Defined but never called.** Orphan helper. Probably what was supposed to be wired into instantiation-time mutability resolution and never got hooked up.

### Mechanism B: `RefListCompoundMutabilitySR`

`Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala:1048-1051`:

```scala
case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => {
  val CoordListTemplataT(coords) = vassertSome(solverState.getConclusion(coordListRune.rune))
  val mutability = if (coords.forall(_.ownership == ShareT)) MutabilityTemplataT(ImmutableT) else MutabilityTemplataT(MutableT)
  solverState.commitStep(...)
}
```

User-facing rule, exposed via the `refListCompoundMutability(...)` keyword in `.vale` source. Historical pre-Sept-2022 `Tup<T RefList>` used it:

```vale
struct Tup<T RefList> M
where T RefList, M Mutability, M = refListCompoundMutability(T) {
  _ ..T;
}
```

This rule still works in isolation. The reason it stopped getting `Tup2<bool, int>` to imm in the modern codebase is below.

## The experiment that didn't work

Restored the rule on the modern non-variadic `Tup2`:

```vale
struct Tup2<T0, T1> M
where M Mutability, M = refListCompoundMutability(Refs(T0, T1))
{ 0 T0; 1 T1; }
```

Parsed cleanly. But `Tup2`'s `StructDefinitionT.mutability` came out as `MutabilityTemplataT(mut)` regardless. **The rule fires once, eagerly, at struct-definition time** with placeholder coords for T0 and T1.

Critical detail at `TemplataCompiler.scala:1234-1239`:

```scala
case CoordGenericParameterTypeS(coordRegion, kindMutable, regionMutable) =>
  (if (kindMutable) OwnT else ShareT, regionMutable)
```

A coord generic param without an `imm` modifier has `kindMutable=true`, so its placeholder coord gets `OwnT` ownership. The rule's `coords.forall(_.ownership == ShareT)` evaluates to false, baking in `MutableT` permanently. By the time `Tup2<bool, int>` is instantiated with concrete `ShareT` coords, the mutability has long since been written to the def.

The architectural gap: **mutability is computed eagerly at struct-definition time, not at each instantiation site**. There's no rerun.

The `tup2.vale` modification was reverted; the experiment cost nothing but informs the design below.

## Why the obvious workarounds aren't quite right

A few mitigations were considered and rejected:

- **Wire `getCompoundTypeMutability` to a per-instantiation hook gated by `#!ImmIfFieldsImm`.** Smallest change, fixes the imm-tuple case. But it's a one-off — solves nothing else, and adds a magic attribute. Specifically wrong because a future feature that wants the same shape (assoc types, etc.) would re-invent it.
- **Mark `T0`, `T1` as `imm`-only on Tup2.** Would make Tup2 unconditionally imm. Wrong in the other direction — we want Tup2 to be mut when fields are mut.
- **Add a sibling `ImmTup2<T0 imm, T1 imm>` and have the parser pick which based on field types.** Doubles the surface and pushes the conditionality into the parser/postparser instead of the type system.

The right shape, on reflection, is to make the type system itself tolerate "we don't know yet" in the right way.

## Option A: Deferred solving

### Initial sketch (rejected during the same session)

First draft of the model had three rune categories:
1. Identifying — caller supplies.
2. Eager-derived — concludable at def time from rule structure (or other eager runes' identities) even with identifying-rune placeholders.
3. Late-bound — concludable in principle but conclusion depends on the *values* of identifying runes.

The "eager-derived" category was mostly an artifact of importing the existing rule-type-split mental model (`DefinitionFuncSR`/`CallSiteFuncSR` etc.) and reading it as a real distinction.

### Refined model: deferred solving

The simpler frame is just rune *state*, no rule classification:

1. **Identifying** — caller supplies.
2. **Concluded** — solver has reached a value (concrete templata, or placeholder-flavored templata where the placeholder represents an identifying rune).
3. **Deferred** — solver hit a wall (input wasn't concluded yet, or rule reads a value-property of a placeholder); rune carries forward unsolved into the def.

The solver doesn't need to know in advance which rules are eager or late-bound. It just runs at def time, propagates as far as it goes, and parks whatever it can't finish. Each call site picks up from there.

### What this changes about the solver's contract

Today: def-time solve must complete; anything unsolved is a `TypingPassSolverError`.

Proposed: def-time solve runs as far as it can. Whatever conclusions it makes are stored. Whatever's unsolved is held over. The call-site solve seeds with caller-supplied identifying-rune values + def's stored conclusions + def's stored unsolved rules, and runs to completion.

The check that keeps this sound is **identifiability** — the postparser already has an analysis (`postparsing/IdentifiabilitySolver.scala`) that proves "given identifying-rune values, every other rune is uniquely concludable." That's the contract. The def is allowed to be incomplete only if identifiability holds for its rule graph.

### Why the existing rule-type splits collapse

Today the codebase has paired rules to forbid the def-time solve from running things it can't finish:

- `DefinitionFuncSR(...)` / `CallSiteFuncSR(...)` for `func` bounds.
- `DefinitionCoordIsaSR(...)` / `CallSiteCoordIsaSR(...)` for `implements` clauses.
- `ResolveSR(...)` skipped from def solves (BRRZ-related).

These splits exist because the solver doesn't tolerate incomplete. Once it does, you emit one rule per logical clause; deferral happens automatically when inputs aren't ready. `InferCompiler.includeRuleInDefinitionSolve` and `includeRuleInCallSiteSolve` (`InferCompiler.scala:790-808`) become unnecessary in their current form.

This is a strict simplification — fewer rule variants, fewer phase-aware filters.

## What changes at each layer

| Layer | Change |
|---|---|
| Solver | Allow incomplete def-time solves: change "incomplete → throw" to "incomplete → return (partial conclusions, remaining rules, unsolved runes)." |
| Identifiability solver | (Likely no code change needed — already proves the right invariant. May need a check that runs against the resulting partial state to confirm everything-still-concludable.) |
| `StructS` / `FunctionS` / `InterfaceS` (postparser AST) | (No change — already carry the rule set.) |
| `StructDefinitionT` / `FunctionDefinitionT` / `InterfaceDefinitionT` (typing AST) | Add fields for `deferredRuneToType: Map[IRuneS, ITemplataType]` and `deferredRules: Vector[IRulexSR]`. Allow `mutability` (and other formerly-must-be-concrete fields) to hold `PlaceholderTemplataT`. |
| Call-site solve sites | Seed with def's partial conclusions + remaining rules in addition to caller-supplied identifying runes. |
| Substitution / instantiator | (Mostly no change — `getPlaceholderSubstituter` is already origin-agnostic. The placeholders that come from deferred runes are substituted from call-site solve conclusions instead of caller args, but the substitution mechanism is identical.) |
| `RuleScout` | (Mid-term, optional cleanup) Stop emitting `DefinitionXxx`/`CallSiteXxx` rule pairs — emit one rule, let deferral handle it. |

Three or four files for the kernel of the change, plus a sweep through consumers of fields whose types widen to include placeholders.

## How `PlaceholderT` participates

Storage-wise, deferred runes use the same `PlaceholderT` machinery as identifying runes. No new templata kind needed.

The semantic shift: `PlaceholderT` today means "stand-in for an identifying generic param the caller will supply." Generalized to "stand-in for any rune that was not concluded at def time." Identifying runes become a degenerate case where the call-site "solve" trivially binds them to caller-supplied args; deferred runes are the non-trivial case where the solve has to actually run rules.

The discriminator (caller-bindable vs solver-bindable) lives in the def metadata — which list a rune is in (`genericParameters` vs `deferredRunes`) — not in the placeholder type. This keeps `KindPlaceholderT` and friends uniform.

Index handling: the cleanest option is to continue the index space — identifying runes use indices `0..N`, deferred runes use `N..N+M`. The placeholder type stays unique-by-index, and the def's metadata lets consumers know which range corresponds to caller args vs solver-supplied values. Index-space details are an implementation choice, not load-bearing.

One nicety: the existing Family 3 distinction between `DispatcherRuneFromImplS` (impl-mimicked) and the unnamed-fresh-placeholder case (currently bare `CodeRuneS`) collapses naturally into "this rune was resolved by impl lookup vs by abstract's solve" — both flow through the same placeholder substitution, both stored as deferred runes on the dispatcher.

## Diagnostic implications

The diagnostic property is preserved for cases that can be caught at def time:
- Contradictions where two rules conclude the same rune to incompatible literal values (e.g., `where M = imm, M = mut`) still fire during the def-time partial solve, since both rules can evaluate.
- Identifiability failures (a rune that cannot be concluded even given identifying-rune values) still fire at the postparser, before any solve runs.

What moves to call site:
- Errors where a deferred rule fails for a *specific* combination of caller args. E.g., a hypothetical "all fields must be droppable" rule fails only when the caller specifies a non-droppable type — that's intrinsically a call-site error and there's no def-time analog.

This is the right boundary. Errors that depend on caller values *should* fire at the call site, not be faked at def time with placeholder defaults.

## Implications for associated types

Associated types fit this model with one addition: a new rule kind for impl-scoped lookup.

```vale
interface Iterator<Self> { type Item; func next(self &Self) Opt<Item>; }
impl Iterator<MyVec> for MyVec { type Item = int; ... }
```

- `Self` — identifying rune of the interface.
- `Item` — deferred rune. Has a name, has a type. At interface-def time the value is unknowable. At a call site that dispatches through a specific impl, it's concretely known.

What's added on top of the deferred-solving baseline:

1. **A new rule type — `AssocTypeLookupSR(implRune, assocTypeName, resultRune)`** (or similar). At call-site solve, given a concluded impl, it reads the impl's binding for the assoc type and concludes the result rune. Sibling to `ResolveSR`.
2. **Syntax + per-impl bindings.** `interface Foo { type Bar; }` declares the slot. `impl Foo for X { type Bar = SomeType; }` populates it. Postparser captures these.
3. **Cross-impl coherence question** — Vale's interface dispatch is open by default. An assoc type could resolve differently depending on which impls are in scope at the call site (Rust's orphan-rules problem in Vale flavor). Worth thinking about whether assoc types should be sealed-only. Feature design, not infrastructure.

The infrastructure piece (deferred-solving) is the same for assoc types as for imm-tuples. That's the win — no per-feature plumbing.

## Why this is the right ordering

If you build associated types *without* deferred solving first, you end up special-casing them: assoc types become "this kind of placeholder that exists at interface def time but isn't a generic param and is filled by impl resolution." That's a one-off concept. Whereas if deferred solving exists, assoc types are "ordinary deferred runes filled by a new lookup rule type."

Same argument applies to imm-tuples. Without deferred solving, you write `getCompoundTypeMutability` wiring + an `#!ImmIfFieldsImm` attribute — one-off. With deferred solving, you write `M = refListCompoundMutability(Refs(T0, T1))` on `Tup2` and the rest is free.

Migration is incremental: ship the deferred-solving substrate, prove it on imm-tuples, collapse the existing per-rule-type splits, then assoc types as a follow-up feature.

## Option B: Lift unsolved runes to generic parameters

An alternative considered in the same session. Instead of introducing a new "deferred rune" category, **widen each def's identifying-rune set to include any rune the def-time solve couldn't conclude**. The rune becomes an identifying-rune-shaped thing — same `PlaceholderT` storage, same substitution machinery, same caller-supplied bookkeeping — except the value isn't supplied by the caller, it's inferred from the rule at the call site via generic-arg inference.

Concretely: postparser/typing detects "M is unconcluded after def-time solve, but the rule graph still has rules that could conclude it given concrete T0/T1." Lift M into the def's effective generic parameters with an "auto-inferred" flag. Now `Tup2`'s internal arity is 3 (T0, T1, M), even though user-facing arity stays 2. At each call site, generic-arg inference fills in M from the rule.

### What's good about it

- **No new typology.** `PlaceholderT` already means "identifying rune awaiting binding." We're just expanding what counts as identifying.
- **Existing machinery does everything.** No new `deferredRunes` field on def. No new "incomplete solve" semantics. No new call-site-solve phase. The instantiator's substitution map gets a couple more entries; no code path changes.
- **Storage is uniform.** `StructDefinitionT.mutability` is always either concrete or `PlaceholderT`. No new "depends on these rules" widening.
- **Composes orthogonally with the existing rule-type splits.** `DefinitionFuncSR`/`CallSiteFuncSR` for func bounds stay as-is; lift handles the value-dependent rune case independently.

### What's hard about it

#### The cascade

If `Tup2<T0, T1>` lifts M, then any def that uses `Tup2<X, Y>` with X/Y abstract has to lift its own M-equivalent. Worked example:

```vale
struct Tup2<T0, T1> M  where M Mutability, M = refListCompoundMutability(Refs(T0, T1)) { ... }

func foo<T>(x T, y T) Tup2<T, T> { ... }
func bar<T>(x T, y T, z T) Tup2<T, Tup2<T, T>> { ... }
```

- Tup2's identifying runes widen: `[T0, T1]` → `[T0, T1, M]`.
- foo's body uses `Tup2<T, T>`; foo's return type contains `Tup2<T, T, M_inner>`. M_inner can't conclude at foo's def time (T is placeholder), so it lifts into foo's identifying runes: `[T]` → `[T, M_inner]`. Foo's signature widens.
- bar uses foo's lifted form *and* an outer Tup2; bar lifts both M_outer and M_inner. `[T]` → `[T, M_outer, M_inner]`. Bar's signature widens further.

This cascade tracks the actual dependency graph faithfully, but it propagates through the codebase. Any function transitively using a lifted-rune type widens.

#### User-facing arity

User wrote `Tup2<T0, T1>` and `func foo<T>(...)`. Internally we have `Tup2<T0, T1, M>` and `func foo<T, M_inner>(...)`. Two consequences:

1. **Error messages.** "Tup2 expected 3 args, got 2" is wrong from the caller's perspective — they think it's a 2-arg type. The humanizer has to know which params are user-declared vs lifted.
2. **Generic-arg inference.** When the user writes `Tup2<bool, int>`, the typing pass has to recognize this is a 2-arg user call against a 3-arg internal def, then infer M=imm from the rule. Vale already has generic-arg inference for some cases; whether it extends cleanly to "infer from a rule that mentions all the user-supplied args" is a real question.

#### Naming and identity

`StructTemplateNameT(StrI("Tup2"))` identifies Tup2 by name + identifying-rune count for some purposes. Lift changes the count. Existing equality/hashing might or might not handle the wider arity cleanly. Worth a careful audit before committing.

#### Compositional correctness

For `Tup2<Tup2<int, bool>, str>`:
- Inner `Tup2<int, bool>` resolves to `Tup2<int, bool, imm>` (both fields share).
- Outer `Tup2<Tup2<int, bool, imm>, str>` — what's outer's M? Depends on inner Tup2's coord ownership. An imm struct's coord has ShareT ownership, so outer's `refListCompoundMutability(Refs(inner, str))` checks: inner is share, str is share, so outer M = imm.

The lifted model handles this if the rule fires correctly at each level with the inner-already-resolved coord type. Doable; just requires that the order of lifting/instantiating be inside-out. Recursion in the type system, basically.

## Comparison

| Aspect | Option A: Deferred | Option B: Lift |
|---|---|---|
| New runtime concept | New "deferred" category | None — reuses identifying runes |
| Storage on def | Adds `deferredRuneToType`, `deferredRules` | Wider `genericParameters` (with auto-inferred flag) |
| Solver phases | New: def-time tolerates incomplete | Same as today (def-time complete) |
| Cascade visibility | Implicit, propagates through rule chaining | Explicit in widened signatures |
| Caller-facing arity | Unchanged | Needs internal/external distinction |
| Existing rule-type splits | Collapse (subsumed) | Stay (orthogonal) |
| Generic-arg inference | Unchanged | Has to learn rule-driven inference |
| Diagnostics shape | "Couldn't solve M at this call site" | "M was lifted from Tup2's rule" |
| Where bookkeeping lives | Separate sibling list on def | Identifying-rune set itself |
| Subsumes assoc types? | Yes (assoc types are deferred runes filled by impl-lookup rule) | Awkwardly (assoc types as auto-inferred generics, with separate impl-lookup mechanism) |
| Code change size for imm-tuple | Larger — touches solver, def storage, several consumers | Smaller — postparser lift + generic-arg inference extension |
| Long-term cleanliness | Higher — fewer concepts overall | Lower — identifying runes overloaded with two meanings |

## Synthesis: which to pick

The two options are nearly isomorphic in expressive power. Both end up running the rule with concrete arg values somewhere; both store the result as a placeholder substitution. The architectural difference is **where the bookkeeping lives**:

- **Option A** keeps the identifying-rune concept tied to its name (literally what callers supply) and adds a sibling concept for solver-supplied substitutions.
- **Option B** makes the identifying-rune set encompass everything that's ultimately substituted at call site — caller-supplied or solver-inferred.

Three reasons to prefer Option A as the long-term model:

1. **Signature stability.** User-facing signatures of generic functions affect what the language *looks like*. With Option B, cascading widening means a refactor that changes Tup2's mutability rule could ripple through every generic function in the codebase that mentions Tup2. With Option A, the change stays contained to Tup2.

2. **Existing rule-type splits collapse cleanly.** `DefinitionFuncSR`/`CallSiteFuncSR`, `DefinitionCoordIsaSR`/`CallSiteCoordIsaSR`, `ResolveSR` — these are all "this rule can't fire at def time, defer it." Option A subsumes them with a single mechanism. Option B leaves them in place as a separate orthogonal mechanism. That's a smell — two mechanisms for the same essential need.

3. **Assoc types fit Option A more naturally.** An associated type's value comes from impl resolution, not from a rule that runs against identifying-rune values. Lifting an assoc type into the interface's generic params means the interface's "arity" depends on how many assoc types it has, and every call site has to infer them via impl lookup. With Option A, assoc types are just deferred runes filled by an impl-lookup rule — same machinery as the rest, no special-casing.

But — and this matters — **Option B is the easier first step.** If the goal is to ship the imm-tuple feature without committing to the broader architectural cleanup, Option B could probably be implemented in a few hundred lines:

- Postparser detects "this rune is unsolved given identifying runes' identities" and adds it to genericParameters with an "auto-inferred" flag.
- Generic-arg inference at call sites runs the rule with concrete identifying-rune values to fill in the inferred ones.
- Storage and substitution unchanged.

Option B can be a **stepping stone**: ship it for imm-tuples, get the user-visible win, then later when assoc types come up or when the rule-type split smell becomes intolerable, do the Option A refactor and replace lifted-with-auto-inferred-flag with proper deferred runes. The substitution is mechanical: any rune currently in `genericParameters` with the auto-inferred flag becomes a deferred rune with the same rule attached.

Worth being explicit that the stepping-stone path *does* have a cost: while B is in place, code accumulates against B's shape (consumers of `genericParameters` that would need to be retaught the deferred-rune shape; the auto-inferred flag spreading through the codebase). The migration from B to A is not free. Whether to pay that cost depends on how soon assoc types matter — if they're months out, B-then-A makes sense; if they're imminent, going straight to A saves the migration tax.

## What's NOT in this proposal

- **Performance work.** Per-call-site solves get bigger (more rules to run). Probably fine — instantiator caching at the (template, args) level should keep the actual cost bounded — but worth measuring. Out of scope for the design.
- **Re-classification of existing runes.** The model says "everything's a rune, deferred when not concluded yet." Existing identifying-rune handling stays the same. We're not migrating eager-solved runes to deferred or vice versa.
- **Body type-checking changes.** Function bodies already get type-checked with placeholders; the placeholder type for a deferred rune looks the same as for an identifying rune at the body's level. Body code shouldn't need to change.
- **Backward-compat shim.** The split-rule pattern (`DefinitionFuncSR` etc.) can stay in place during migration; the def-solve filter is the safety net while incomplete-solves are being introduced. Collapsing those to single rules is a clean-up phase, not part of the kernel change.

## What we know we don't know

- **Solver order sensitivity.** The current solver's ordering produces specific intermediate states. Letting solves be incomplete means partial conclusions get serialized into the def and re-hydrated at each call site. The serialization format and the order in which deferred rules are re-tried at call site need scrutiny — there might be subtle issues with rule ordering causing different conclusions for the same input.
- **Effect on error humanizer.** When a call-site solve fails because of a deferred rule, the error currently says "couldn't solve at definition X" — but the actual contradiction lives at the call site. The humanizer should explain "this struct's mutability depends on T0 in a way that, with your concrete types, can't be resolved." Today's machinery doesn't quite frame errors that way.
- **Whether the identifiability check needs strengthening.** Today it proves "everything concludable from identifying runes." Whether it needs to additionally prove "and the partial def-time solve will produce a sensible state to seed call-site solves with" is unclear without trying.
- **Interaction with regions.** `RegionT` and region placeholders have their own placeholder lifecycle. Whether deferred *region* runes work the same way as deferred coord/mutability runes is untested.
- **Interaction with bound resolution (BRRZ).** BRRZ already establishes a "real lookup at call-site mid-solve" precedent for return types. Whether deferred-solving subsumes BRRZ or sits beside it cleanly is the kind of thing you only find out by trying.

## Where this leaves the imm-tuple test

`Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala` has an `ignore("Tuple with all imm fields is imm")` that asserts `Tup2`'s instantiated mutability is `ImmutableT` for `(true, 42)`. Once deferred-solving lands and `tup2.vale` is rewritten to:

```vale
struct Tup2<T0, T1> M
where M Mutability, M = refListCompoundMutability(Refs(T0, T1))
{ 0 T0; 1 T1; }
```

…flipping the test from `ignore` to `test` and re-running should pass. That's the canary for "did this work end-to-end."

Same rewrite goes on `Tup1` and `Tup3` (and the commented-out `Tup4`–`Tup9`).

## How this connects to active quests

- **AfterRegions quest (`quest.md`).** Family 4.1 is resolved at the surface level (test passes). The deeper question is captured here. Doesn't block AfterRegions completion.
- **Family 1 (CFWG impl-bound propagation, 6 tests).** May be informed by deferred-solving — impl bound propagation is structurally similar to "run a rule at call site." Worth re-examining Family 1 after deferred-solving lands; some of those tests might fall out for free.
- **Family 3 (Map function dispatcher).** Layer 4 patch documented in quest.md. Deferred-solving doesn't directly help Family 3 (the issue there is in instantiator substitution, not solver phase ordering). But the cleanup mentioned for Family 3's "smell 1" — making fresh dispatcher placeholders self-describing — would compose with deferred-solving's view that all such placeholders are uniformly "deferred runes resolved at instantiator time."
- **BRRZ.** Established the precedent for non-trivial lookups mid-solve. Deferred-solving generalizes the substrate. BRRZ's `ResolveSR` becomes "a deferred-solving rule that does function lookup"; assoc types' `AssocTypeLookupSR` would be "a deferred-solving rule that does impl lookup."

## Acronym placeholder

The session called it **DSDCBZ** ("Deferred Solving Across Def/Callsite Boundary"), as a placeholder. Pick a real one when this becomes a quest. The Z suffix is the project's arcana convention; the rest is up for grabs.

## Concrete next steps when picking this up

First decision: which option. Re-read the comparison and synthesis sections, decide A-as-final-target vs B-as-stepping-stone vs B-as-final-answer. The work below splits.

### If pursuing Option A (deferred solving)

1. Read `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala` and identify the def-time-completion-required check sites (search for `vassertSome` calls on `solverState.getConclusion` and the `TypingPassSolverError` throws).
2. Read `Frontend/TypingPass/src/dev/vale/typing/InferCompiler.scala` — `interpretResults` is the place that decides "is this solve done." Decide what "partially done" looks like there.
3. Pick a small concrete struct to exercise the change (probably `Tup2` directly, since that's the motivating case). Don't migrate everything at once; let `Tup2`'s rule live alongside the existing rule-type splits during development.
4. Touch the def-storage types to optionally hold deferred state. Plumb through the consumer of `StructDefinitionT.mutability` first, since that's the most visible field that needs widening.
5. Run the imm-tuple ignored test after each step. The end-to-end pass is the success criterion.
6. Once green, scan the existing `DefinitionXxx`/`CallSiteXxx` rule pairs and see which collapse cleanly to single rules. Don't force it — the splits can stay if they serve clarity.

### If pursuing Option B (lift to generic parameter)

1. Read `Frontend/PostParsingPass/src/dev/vale/postparsing/PostParser.scala`'s `scoutStruct` (around line 560-700) — that's where `genericParametersS` is assembled. The lift step adds to this.
2. Read `Frontend/PostParsingPass/src/dev/vale/postparsing/IdentifiabilitySolver.scala` to understand what's already known about which runes are concludable from which.
3. Add an "auto-inferred" flag to `GenericParameterS` (or a sibling type) so user-declared generics can be distinguished from lifted ones.
4. After the def-time identifiability pass, lift any rune that's "unconcluded but concludable given identifying-rune values." Add it to `genericParametersS` with the auto-inferred flag.
5. Extend generic-arg inference at call sites to run lifted rules with concrete user-supplied args, filling in the auto-inferred params before the typing pass instantiates.
6. Audit error-message sites to suppress the auto-inferred params from caller-facing arity counts.
7. Run the imm-tuple ignored test. Run the broader AfterRegions suite to catch cascade-related issues.

### If pursuing B-as-stepping-stone-toward-A

Do the B steps first. Once stable, the A migration is mechanical: for each `GenericParameterS` with the auto-inferred flag, move it from `genericParameters` to a sibling `deferredRunes` list on the def, and teach the call-site solve to treat it as a deferred rune (running the same rule, just from a different storage location). The auto-inferred flag and the lift logic in postparser go away at that point.

## Files we touched in this session that relate

- `Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala` — removed `vfail()` from "imm tuple access" (Family 4.1 surface fix, landed).
- `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala` — added `ignore("Tuple with all imm fields is imm")` (the canary; landed).
- `Frontend/Builtins/src/dev/vale/resources/tup2.vale` — experiment with restoring the rule, reverted.
- `quest.md` — Family 4.1 marked RESOLVED; Family 3 Layer 4 attempt documented.
- This file — the design exploration.
