---
g_read_when: Read when planning or making a large change to the typing pass (FrontendRust/src/typing/).
g_mention_in:
  - CLAUDE.md
---

# Typing Pass — AI Change Guide

**What this is.** The things to keep in mind *before and while* making any large change to the typing pass (`FrontendRust/src/typing/`). It is the "what's easy to get wrong / what to never do" layer that sits on top of the architecture. The authoritative architecture lives in `docs/architecture/typing-pass-design-v3.md` — this guide points into it rather than restating it.

**Audience.** Whoever is reasoning about a non-trivial typing-pass change: the architect, the TL/reviewer, or an AI doing the work. (The migration junior "JR" does *not* have access to this file; when citing it to JR, paraphrase the rule inline.)

**Meta-rules for this doc.** Re-read it whenever you compact — it changes often and the surrounding conversation does not survive. Any *addition* to this file is one sentence, ≤25 words, unless the architect asks for more.

---

## Read First

Before a large change, read these in full:

1. **This file**, top to bottom.
2. **`docs/architecture/typing-pass-design-v3.md`** — arena/lifetime model (Part 1), the god struct (Part 2), envs (Part 3), CompilerOutputs (Part 4), the type system (Part 6). This is the architecture; everything below assumes it.
3. **`FrontendRust/docs/arcana/SealedInternedConstruction-SICZ.md`** — the `MustIntern` seal; `IdT`-style types are constructible only via the interner.
4. **`FrontendRust/docs/arcana/IdentityEqualityOnIdentityBearingTypes-IEOIBZ.md`** — identity-bearing types impl `PartialEq` via `std::ptr::eq`; wrappers derive.
5. **`FrontendRust/docs/arcana/WhenValuesShouldBeInterned-WVSBIZ.md`** — Interned vs Value-type, Scala-parity rule.
6. **`FrontendRust/docs/arcana/IdenticalInputsIdenticalOutputs-IIIOZ.md`**.
7. **`FrontendRust/zen/migration_principles.md`** — DCCR, RCSBASC, architect escape hatch.
8. **`Luz/shields/ScalaParityDuringMigration-SPDMX.md`** and **`.../ScalaCommentParity-SCPX.md`** — the two shields you'll fight most.

---

## The Prime Directive: 1:1 Scala Parity

**Matching the Scala source's structure, naming, and behavior remains the priority for any body still being ported — above Rust idiom, above cleverness, above brevity.** No novel logic, no reorganization, no "improvements" beyond what Rust strictly requires to compile.

**Every Rust definition is immediately followed, on the next line, by a `/* ... */` block holding its Scala equivalent.** That block is the audit trail (SCPX checks it line-for-line, order-sensitive). Pre-existing defs lacking an adjacent block are tech debt, not precedent.

**Don't hack short-term against the long-term design.** When the faithful port wants a populated narrow enum / a mirror of the T-side / a real type, build that — don't reach for a convenience method on a wide enum or a one-off shortcut because it's smaller right now. The cheap divergence compounds: it has to be unwound later *and* it misleads the next reader about what the design is. (This is why `+T` erasure widens to the bound's *narrow* enum and narrows at use sites, rather than bolting accessors onto the widest type — see "Easy To Get Wrong" below.)

**Guardian isn't perfect — bad edits slip through.** Some Scala-divergences land without firing any shield. Review every diff against the Scala source yourself even when the shields are silent. When you spot a divergence that a prior commit also made, fix *both* sites — don't cite the earlier slip as precedent for the new one. Treat "the same pattern was already approved in X" as a possible pointer to a bug, not a license.

**Known principled divergence — covariant generics.** Scala uses covariant params (`IdT[+T <: INameT]`, `PrototypeT[+T]`) to thread compile-time narrowing through containers. Rust does not mimic this: containers carry the wide enum (`IdT<'s,'t>` with `local_name: INameT`), and use sites narrow at runtime via `TryFrom` (`IFunctionNameT::try_from(name).unwrap()`) or the matching `expect_*` accessor. The `.unwrap()`/`expect()` is the documented runtime stand-in for Scala's compile-time `T <: …` bound. See design-doc §6.0.

---

## The Arena & Lifetime Model — Don't Fight It

Full treatment: design-doc Part 1. The load-bearing facts for a large change:

- **Three arenas.** `'p` parser (`ParseArena`), `'s` scout (`ScoutArena`: postparser/higher-typing output + interned scout names), `'t` typing (`TypingInterner`: interned typing types, output AST, **and envs**). All `bumpalo`.
- **`'s` outlives `'t`.** Declare `where 's: 't` on every output type that transitively holds `&'s` data. Rust won't infer the outlives from drop order — you must write the bound.
- **Envs live in `'t`, not `'s`.** They hold `&'t` refs to interned typing payloads (via `TemplatasStoreT`/`ITemplataT`), and an `'s`-allocated struct can't hold `&'t` refs. Design-doc §3.1.
- **Arena parameters take a short borrow, never the arena's own lifetime.** Write `&ScoutArena<'s>` or `&'ctx ScoutArena<'s>`, never `&'s ScoutArena<'s>`. `alloc(&self, T) -> &'t mut T` returns arena-lifetimed data from a short `&self`. §1.2.5.
- **`'t: 'ctx` / `'s: 'ctx` are already implied by the `Compiler` struct** (its `&'ctx X<'s,'t>` fields couldn't exist otherwise). Restating them on a local `impl where` clause is fine when rustc fails to propagate through HRTB/invariance. **Never declare the reverse `'ctx: 't`** — that's the bound rustc *suggests*, but it's architecturally backwards (`Compiler` is `'ctx` stack data and dies before `'t`).
- **Never `'static`.** A `&'static T` has a different pointer than a structurally identical `&'s T`, silently breaking pointer-equality for interned types. NUSLX. §11.2.
- **AASSNCMCX:** no `Vec`/`HashMap`/`String` *inside* arena-allocated types. Use `&'t [T]` slices and `ArenaIndexMap`. (`CompilerOutputs` is the exception — it's stack-owned, so its `HashMap`s are fine.)

---

## Recurring Traps

These have each bitten at least once. Scan this list before a large change.

- **Hand-rolled enumerations of an "is-a-Trait" set.** Scala's `case x: ITrait =>` matches every subtype automatically; a Rust port that hand-lists the variants silently drifts the moment one is added. Always use `TryFrom<WideEnum> for NarrowEnum` / `::try_from(..).is_ok()` — the scaffolding-generated `TryFrom` impls cover every variant.

- **Hand-rolled `ptr::eq(self, other)` on a Polyvalue's outer `&self`.** Works while the enum is always behind `&'t Outer` (outer address coincides with arena address); silently breaks the moment it's held by value — `self` becomes a stack address, two by-value copies of the same logical wrapper compare unequal, and any `HashMap`/`HashSet` keyed on them corrupts. Polyvalue enums (`IEnvironmentT` family, `INameT`, `KindT`, `ITemplataT`) must `#[derive(PartialEq, Eq, Hash)]` and let the derive delegate into each variant's inner identity. See @PVECFPZ / design-doc §3.1, §1.5.

- **Parallel Builder/Frozen APIs diverging asymmetrically.** When one API (e.g. `TemplatasStore.addEntries`) is split into a Builder + Frozen pair (`TemplatasStoreBuilder::add_entries` vs `TemplatasStoreT::add_entries`), *both* must mirror the same logic including special-case branches. Review them side-by-side.

- **Two-channel errors collapsed into one.** When a Scala fn both `throw`s `CompileErrorExceptionT` *and* returns `Result[_, SomeLocalError]`, the Rust mirror is nested `Result<Result<_, SomeLocalError>, ICompileErrorT>` — outer is the exception channel (every caller `?`-propagates), inner is the business channel (callers inspect and react). Merging them loses the "always propagate" vs "caller decides" distinction.

- **Structural-shape diff that's actually a Rust→Scala bug-fix.** Seen on the "Automatically drops struct" test, where the Rust pattern matched `template_args` against the coord but Scala's third `FunctionNameT` field is `parameters`. SPDMX flags the corrective re-shape; the re-shape is right. If you hit this, the fix is to match Scala's actual field, not to suppress the shield.

- **Skeleton-with-panics tripping SPDMX.** See "Good Partial Implementing" — the iteration skeleton (`.map(|_| panic!())`, `for x in xs { panic!() }`) IS the Scala parity, but SPDMX reads it as "novel scaffolding." Resolution is a TL temp-disable, not a rewrite.

---

## Design Decisions That Are Easy To Get Wrong

Each is fully argued in the design doc; this is the "remember the conclusion" index.

- **`+T` erasure widens to the *bound*, not the enclosing trait.** `IdT[+T <: INameT]` → field `local_name: INameT`. `PlaceholderTemplataT[+T <: ITemplataType]` → field `tyype: ITemplataType<'s>` (a type *descriptor*), **not** `ITemplataT` (a templata *value*) — different families. §6.0.

- **`PtrKey<'t, T>` for HashMap keys on identity-bearing refs — but NOT for content-canonical types.** `IdT`'s own `==` is already pointer-equality on its inner fields; wrapping it in `PtrKey` compares outer addresses and breaks. Use `PtrKey` only for `@IEOIBZ`-style types. §4.2, §11.1.

- **Method on `Compiler` vs free function.** Decide by the Scala body: uses `this`/fields → must be `&self` method on `Compiler` (the god struct, §2.1, even though the Scala class collapsed). Pure on its args → may be a free fn, and *must* be one if it gets stored in a `Box<dyn Fn>` (whose `'static` default forces a lifetime workaround). Don't generalize the postparser solvers' free-fn shape to the typing pass — those have no `Compiler`/delegate; typing-pass code touches delegate methods constantly. §2.5.

- **`&'t self` promotion.** Default to `&self`. Promote to `&'t self` *only* when the output's `'t` is `self`'s own arena lifetime — embedding self as a back-pointer, wrapping self in a wrapper enum, or returning a borrow into a by-value field that a Scala-fixed `&'t T` consumer requires. Pure getters of already-`'t`-ref fields stay `&self`. §3.4a.

- **`Box<dyn Fn> + &self` deferred-borrow deadlock.** A boxed closure capturing `&self` keeps that shared borrow live for the box's whole lifetime; holding it across other `&self` calls deadlocks the borrow checker, and threading a closure-lifetime param only sidesteps the `'static` default without resolving the conflict. The fix is always to drop the receiver (free fn) or pass needed data by value. Check call sites before proposing lifetime-threading. §11.3.

- **Interning seal soundness.** `IdT` and the slice-bearing names are sealed (`MustIntern`) because their `eq` uses pointer comparison on arena slices; constructing one outside `intern_*` reintroduces the `signature-id-mismatch` class of bug. Add a new Interned type only after reading @SICZ/@WVSBIZ/@DSAUIMZ. §6.1, §6.3.

---

## How To Make Changes Safely

### Good Partial Implementing

When replacing a `panic!` stub with real logic, write only the **shallow structure** of that scope — straight-line bindings, calls, match expressions with all arms — but put `panic!` inside every new branch body, loop body, closure/lambda body, and match arm. Then fill in *only* the arms the driving test actually hits. Recursive: when a test reaches an inner panic, expand *that* one a layer. **Aggressively panic for anything not exercised by current tests** — untested paths must crash loudly, not silently return wrong results.

"Untested branches" means untested *code paths*, not untested *data values*. A field set to `HashMap::new()` runs on the test path; if Scala produces an empty map on the same input, the empty Rust map is correct parity. Panic only inside branches that wouldn't be reached on the input: loop bodies over empty collections, match arms for absent variants, closures never invoked. In doubt: does this line *run* on the test path? Yes → produce Scala's value (empty is fine). No → panic.

**SPDMX caveat.** SPDMX sees `.map(|x| panic!())` / `for x in … { panic!() }` and flags it as "novel scaffolding," recommending a whole-function `panic!()`. Whole-function panic breaks the test path — the skeleton IS the parity. Resolution: the **TL** temp-disables SPDMX on that function (juniors escalate, never self-disable), with this rationale:

> Per "Good Partial Implementing": this function uses the skeleton-with-panics-in-closures pattern the migration design endorses. The iteration structure (.map / .for_each / nested for) mirrors Scala's call graph; panics live in the closure bodies. For the empty-input case (the driving test) the closures never fire and the function is a verified no-op; for non-empty inputs they panic loudly with named placeholders. Re-enable SPDMX when the closure bodies get real logic.

### Preserve The `/* scala */` Audit Trail

The typing skeleton has a `/* ... */` Scala block directly below every Rust definition. The `.claude/hooks/check-scala-comments` pre-commit hook does exact-match comparison and rejects edits inside those blocks. Rules:

- Replace the empty Rust stub in-place; keep the Scala block below unchanged.
- **Never move a Rust definition away from its Scala block** (NMDX). **Never rename a ported definition** off its Scala name (NRDX) — NRDX's heuristic flags consecutive context-swaps as renames, so wrap/edit one definition per Edit, not two adjacent ones at once.
- A def that grows to need a helper struct gets the companion **adjacent**, with a `// (no scala counterpart — …)` note.
- After any change that touches Scala comment blocks, run SCPX:
  `cargo run --manifest-path Luz/shields/ScalaCommentParity-SCPX/Cargo.toml --release -- --check-all`

### Slicing In New Definitions

When scaffolding is missing a legitimate Scala counterpart (JR gets NNDX-blocked), the fix is to **add the definition**, not to disable NNDX. Pre-flight: grep the target type for an existing `pub fn` — the Scala-parity port often already exists under operator translation (`add` for `def +`). Find the Scala `def` inside its `/* */` block, split the block (close `*/` before the def, insert the Rust `impl`, reopen `/*`), so the Scala `def` ends up **inside** the Rust `impl` as an inline `/* */` immediately after the Rust fn body. The existing Scala is unchanged — you add *around* it.

**Sub-case: name-collision disambiguation.** Rust flattens multiple Scala compiler classes onto one `Compiler`, so same-named Scala methods collide. Append a `_<distinguishing-arg-type>` suffix to the *newer* slice, leave existing call sites intact, and comment why (NNDX fires on the rename → TL territory).

**Guardian Annotations For New Definitions Without Scala Counterparts.** For Rust defs with no Scala counterpart (delegation wiring for trait inheritance, `From`/`TryFrom` impls, interning Val/Query structs):
- **Pure wiring (no logic)** — add `/* Guardian: disable-all */` after the fn/impl block.
- **Contains logic** (conditionals, assertions, non-trivial transforms) — add an empty `/* */` after the body (satisfies SCPX, signals "reviewed, no Scala counterpart").
JR never adds these annotations — they escalate to the TL.

### Proactively Add Inherited Dispatch Methods

The slice pipeline only stubs methods defined directly on a Scala trait body. Scala trait-extends-trait inheritance, abstract factory methods, and dispatch-tag enums all need explicit Rust delegation the pipeline doesn't generate. When you see a Scala child trait extending a parent (or a sealed trait with named implementors per SSTREX), add all inherited dispatch methods on the child enum proactively — don't wait for serial escalations. Annotate the new methods per "Guardian Annotations" above.

### Architect Approval Required

**Run structural solutions by the architect first** — lifetime-parameter additions, signature changes that propagate across files, new abstractions, any choice between alternatives. Don't start editing, even for "mechanical" fixes, until sign-off. The cost of a quick check is small; unwinding a wrong-direction change across ~20 call sites is large.

**Never add `// AFTERM:` or `// TODO:` comments** — yours or JR's. These are the architect's deferred-cleanup markers, added only when the architect asks. This holds even when the deferral seems obviously correct. Raise it to the architect; don't park it inline. If JR proposes one, tell them to drop it and escalate.

### Writing Scala-Parity Tests

Scala tests built on `Collector.only(scope, { case … })` port to the `collect_only_*node!` traverse macros (`typing/test/traverse.rs`) — one macro call per Scala `Collector.only`, the `case` pattern inlined verbatim — not to positional `expect_N` + `cast!` + `assert_eq!` chains, which over-constrain on element count and are invisible to a tree walker. Use literal patterns where Scala does (`StrI("x")` matches inline). Trailing `VoidSE` from semicolon-terminated blocks is invisible to recursive collectors — don't add Rust-only stripping to "fix" a port; rewrite the over-constrained test instead.

---

## Process & Coordination

The TL ↔ JR ↔ architect loop — the roles, the `for-tl.md`/`for-jr.md` escalation files, the "z" protocol, the "would Guardian fire? → JR vs TL" division of labor, when to temp-disable a shield, and the commit gate — lives in the **`guardian-tl`** skill (`Luz/skills/guardian-tl.md`), written generally. This guide stays focused on the typing-pass change content above.

Two typing-pass-specific notes the skill doesn't cover:

- **Test promotion.** Body migration is test-driven: pick the active (un-`#[ignore]`'d) test, run it (`cargo nextest run --manifest-path ./FrontendRust/Cargo.toml`), implement the panic it hits, then un-ignore the next-simplest test (roughly top-to-bottom within a file, simplest file first). Keep passed tests un-ignored as regression guards.
- **Scope discipline.** When a design diverges from `docs/architecture/typing-pass-design-v3.md`, record it in `FrontendRust/docs/reasoning/<topic>.md` and then update the design doc; announce any doc edits in the hand-back summary rather than folding them into a diff.
