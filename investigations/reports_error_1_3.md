# Investigation: AfterRegions test "Reports error" (1.3) — where the failure is produced

## The test

`Frontend/TypingPass/test/dev/vale/typing/AfterRegionsErrorTests.scala:108`:

```vale
interface A {
  func foo(virtual a &A) int;
}

struct B imm { val int; }
impl A for B;

func foo(b &B) int { return b.val; }
```

Test calls `expectCompilerOutputs()` and ends with `vimpl()` — it doesn't yet
assert any specific error. The intent is to surface a "missing override" error
because B implements A but doesn't supply a real override for the abstract
`foo(virtual &A)` (the standalone `foo(b &B)` is a free function, not a
registered override).

## Surface symptom

Compile aborts with `CouldntFindOverrideT`, humanized as:

```
Couldn't find an override:
Couldn't find a suitable function foo(&B). Rejected candidates:

Candidate 1 (of 3): test:test.vale:3:1: (the abstract foo(virtual &A))
  Index 0 argument &B isn't the same exact type as expected parameter &A

Candidate 2 (of 3): test:test.vale:9:1: (the standalone foo(b &B))
  Bad super kind in isa: B

Candidate 3 (of 3): test:test.vale:3:1: (synthesized anonymous-subclass dispatch)
  No ancestors satisfy call: (arg 0) = &B
  Unsolved runes: $A.anon.foo:$A.anon.foo.functor (self kind) (self ref)
```

Stack trace bottom: `vfail` → `expectCompilerOutputs` (Compilation.scala:73). The
test's `expectCompilerOutputs()` itself throws because compilation produced an
error rather than success.

## Where the error is constructed

The outer `CouldntFindOverrideT` is thrown at exactly one place:

- `EdgeCompiler.scala#lookForOverride` (the `findFunction(...)` call inside,
  whose `Err(e)` arm becomes `throw CompileErrorExceptionT(CouldntFindOverrideT(...))`)

`lookForOverride` is invoked from `EdgeCompiler.scala#compileITables`. For each
interface blueprint and each `impl` registered for it, for each abstract method
of the interface, `lookForOverride` is called to find the concrete override on
the sub-citizen. With three rejected candidates and no successful match, it
throws.

## Collapsed call tree

```
- EdgeCompiler#compileITables ... iterates impls, abstract methods:
  - EdgeCompiler#lookForOverride builds dispatcher placeholder env, then:
    - OverloadResolver#findFunction("foo", paramTypes=[&B], extra envs=[A's outer env, B's outer env]):
      Returns Err(FindFunctionFailure) because all three candidates fail (see below).
    - Throws CouldntFindOverrideT (line 579).
- Three rejected candidates (in OverloadResolver / CompilerSolver):
  - Abstract foo(virtual &A): rejected with "Index 0 argument &B isn't the same exact type as expected parameter &A".
    The override-search expects an exact-type match against the abstract param's substituted type.
    KEY: this is the strict-equality candidate; expected since &B ≠ &A textually.
  - Standalone foo(b &B): rejected with "Bad super kind in isa: B".
    LIKELY FACTOR: the call-isa check on the sub-citizen-substituted param hits
    CompilerSolver.scala line ~739 (the CallSiteCoordIsaSR handler), which
    requires the super kind to implement ISuperKindTT. B is a struct, not an
    interface, so it fails — even though &B ↔ &B should match trivially. The
    `subCoord == superCoord` short-circuit at line 726 isn't firing here;
    investigate why (probably ownership/region differs after substitution).
  - Synthesized anonymous-subclass dispatch (A._AnonSub<functor>): unsolved.
    LIKELY FACTOR: this is the "implicit anonymous subclass" mechanism the
    compiler synthesizes per @LAGTNGZ-related machinery. It expects a functor
    rune to be filled in but none is supplied, so the (self ref) rune stays
    unsolved.
```

## Why this isn't really "Couldn't find an override"

The compiler IS being told there's an impl `B isa A` and there's a `foo(&B)`
in scope — what it can't do is connect them. Specifically:

- The "exact match" candidate (Candidate 1) demands &B = &A, which is wrong
  for an override search; it should be ", &B" with B-substituted-for-A".
- Candidate 2 is the standalone free function `foo(&B)`. Conceptually it has
  the right signature, but it isn't registered as an override of `A.foo`.
  That's the actual user-facing condition the test wants to surface: "you
  declared `impl A for B` but didn't write a real override". Instead the
  search rejects it via a low-level solver error (`BadIsaSuperKind(B)`)
  rather than a clean "this function isn't marked as an override".
- Candidate 3 is the synthesized anonymous-subclass path, which doesn't apply
  since `B` is not anonymous.

So the real test-shape the user wants is a `MissingOverride`-like error
identifying B as a sub-citizen of A that fails to provide a foo override. The
current path produces a generic `CouldntFindOverrideT` whose body is the same
"Couldn't find a suitable function" mishmash you'd get for any unrelated call.

## ROOT CAUSE: imm/mut ownership mismatch in disguise

**The whole error cascade is gated on `struct B imm`.** Pivoting the test source:

| `interface A` | `struct B` | Result |
|---|---|---|
| (mut default) | `imm` | Original failure: 3 rejected candidates, `BadIsaSuperKind(B)` etc. |
| (mut default) | (mut default) | **Compile succeeds.** Override `foo(&B)` resolves cleanly. |
| `imm` | `imm` | Different failure: `MatchError` in CompilerErrorHumanizer (humanizer bug, secondary). |

So the answer to "is this an imm/mut mismatch in disguise?" — **yes, partly.**
The free function `foo(b &B)` IS recognized as a valid override of `A.foo` when
mutabilities align. The dramatic three-candidate rejected dump only appears
because of the imm-vs-default-mut interaction.

### Confirmed via printlns in `CompilerSolver.scala#CallSiteCoordIsaSR`

Adding `println(s"...subCoord=$subCoord superCoord=$superCoord eq=${...}")`
showed two call-isa firings during the override search:

- **Candidate 1** (abstract foo): `sub=(borrow, RegionT, B), super=(borrow, RegionT, A)`.
  Same ownership (`borrow`), different kind (`B` vs `A`). This is the
  legit "exact type" mismatch. Unrelated to imm/mut.
- **Candidate 2** (standalone `foo(b &B)`): `sub=(borrow, RegionT, B), super=(share, RegionT, B)`.
  Same kind (`B`), **different ownership** (`borrow` vs `share`).
  - `share` comes from B being `imm`: in Vale, `&B` where B is an imm struct
    coerces ownership to `share` because imm types are shared by value, not
    borrowed. So the candidate's actual param coord is `share B`.
  - `borrow` comes from the override-search synthesizing the expected param
    type by substituting B's kind into the abstract `&A` (which had
    ownership=`borrow` because A is mut). The substitution preserves the
    abstract function's ownership.
  - These coords aren't equal → `subCoord == superCoord` short-circuit at
    line 726 doesn't fire → falls into the kind-check arm at line 731+ →
    `B` is `ISubKindTT` (struct) but not `ISuperKindTT` (only interfaces
    are) → returns `Err(BadIsaSuperKind(B))`.

So `BadIsaSuperKind` is the **symptom**, the imm-vs-mut-coerced-ownership is
the **cause**.

## Reframe: this is a compiler bug, not a test-design issue

The user is right that the imm/mut mismatch deserves a clean diagnostic. Today
`impl A for B;` (where A is mut-by-default and B is imm) is silently accepted
by `ImplCompiler`. There is **no impl-mutability validation anywhere**:

- `Frontend/TypingPass/src/dev/vale/typing/citizen/ImplCompiler.scala` — grep
  for "Mutab" or "imm" returns zero hits beyond the standard library import.
- `Frontend/TypingPass/src/dev/vale/typing/CompilerErrorReporter.scala` —
  existing impl errors are only `CantImplNonInterface` and `NonCitizenCantImpl`.
  There's an `ImmStructCantHaveMutableMember` for member-level checks, but
  nothing for impl-level mut-vs-imm mismatch.

So the user's program — `impl A for B` with A mut and B imm — passes impl
validation, makes it to edge compilation, and then explodes mid-override-search
with a confusing 3-rejected-candidates dump where the actual cause (ownership
mismatch from B being imm) is buried in `BadIsaSuperKind(B)`.

The companion case (`interface A imm + struct B imm`, the user's newly added
`ignore`d test "Reports error (imm interface + imm struct)") fails with
`Immutable struct ("A.anonymous") cannot have mutable member` — this is the
existing imm/mut machinery firing on the synthesized anonymous substruct, then
hitting a `MatchError` in `CompilerErrorHumanizer$.printableName` (line ~344)
when trying to print `AnonymousSubstructTemplateNameS`. So both directions of
the mismatch produce broken output — the mut-interface-for-imm-struct case
emits a confusing override error, the imm-interface case emits an internal
humanizer crash.

## Recommended fix paths (compiler bugs)

### A. Validate impl mutability at declaration time (primary)

Add a check in `ImplCompiler` (where the `impl A for B;` declaration is
processed) that the interface and sub-citizen mutabilities are compatible.
Emit a new error variant `ImplMutabilityMismatch(range, interfaceName,
subCitizenName, interfaceMutability, subCitizenMutability)` (or similar),
add it to `CompilerErrorReporter.scala`, humanize it in
`CompilerErrorHumanizer.scala`. Then this test's source produces that
error directly, and the override-search never even runs.

What does "compatible" mean here? Probably: the interface and sub-citizen
must agree on mutability. A mut interface can only be implemented by a mut
struct; an imm interface only by an imm struct. (Mixed cases don't make
sense — an imm interface contract means callers share by value, but a mut
sub-citizen would need borrowing.)

### B. Fix the humanizer's MatchError on AnonymousSubstructTemplateNameS

Independent secondary bug. `CompilerErrorHumanizer.scala#printableName` line
~344 doesn't have a case for `AnonymousSubstructTemplateNameS`. Add one,
producing something like `<interface>.anonymous`. This is needed regardless
of fix A, because anonymous substruct names will appear in other error
contexts too.

### C. (Optional) Fix the BadIsaSuperKind misdiagnosis

Even without imm/mut, if subCoord and superCoord differ only in ownership
but kind matches and is a struct, `BadIsaSuperKind(B)` is the wrong error.
A more accurate path: in `CompilerSolver.scala#CallSiteCoordIsaSR`, when
falling out of the equality short-circuit because ownership differs but
kinds match, surface that distinction (e.g. `OwnershipMismatch(subCoord,
superCoord)`) instead of casting through the kind-isa check.

Probably not worth fixing on its own — fix A makes this code path
unreachable for this test. But worth noting in case it surfaces in other
tests.

## What to do with this test

Once fix A lands:

1. The `test("Reports error")` can assert the new error type:
   ```scala
   compile.getCompilerOutputs() match {
     case Err(ImplMutabilityMismatch(_, _, _, MutableT, ImmutableT)) =>
   }
   ```
2. The user's new `ignore("Reports error (imm interface + imm struct)")`
   becomes a valid test (no longer hits the humanizer crash) and can also
   be `un-ignored`. With imm A + imm B, no mismatch — it should compile
   successfully or fail on the legitimate "no real override" case (which
   would be its own follow-up).

## Files implicated

- `Frontend/TypingPass/src/dev/vale/typing/EdgeCompiler.scala` —
  `lookForOverride` and `compileITables`. Throws `CouldntFindOverrideT`.
- `Frontend/TypingPass/src/dev/vale/typing/OverloadResolver.scala` —
  `findFunction`. Returns `Err(FindFunctionFailure)` from the override search.
- `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala` —
  `CallSiteCoordIsaSR` handler at line ~719. Source of `BadIsaSuperKind(B)`
  on Candidate 2 due to ownership mismatch.
- `Frontend/TypingPass/src/dev/vale/typing/CompilerErrorReporter.scala` —
  defines `CouldntFindOverrideT` and `BadIsaSuperKind`. NO `MissingOverride`
  variant exists yet (would need to add one for option 1 above).
- `Frontend/TypingPass/src/dev/vale/typing/CompilerErrorHumanizer.scala` —
  prints "Couldn't find an override:" and "Bad super kind in isa: ".
  Has a separate bug: `MatchError` on `AnonymousSubstructTemplateNameS` at
  line 344, surfaced when running with `interface A imm + struct B imm`.

## Files implicated (no line numbers per skill rules)

- `Frontend/TypingPass/src/dev/vale/typing/EdgeCompiler.scala` —
  `lookForOverride` and `compileITables`. Throws `CouldntFindOverrideT`.
- `Frontend/TypingPass/src/dev/vale/typing/OverloadResolver.scala` —
  `findFunction`. Returns `Err(FindFunctionFailure)` from the override search.
- `Frontend/TypingPass/src/dev/vale/typing/infer/CompilerSolver.scala` —
  `CallSiteCoordIsaSR` handler. Source of `BadIsaSuperKind(B)` on Candidate 2.
- `Frontend/TypingPass/src/dev/vale/typing/CompilerErrorReporter.scala` —
  defines `CouldntFindOverrideT` and `BadIsaSuperKind`.
- `Frontend/TypingPass/src/dev/vale/typing/CompilerErrorHumanizer.scala` —
  prints "Couldn't find an override:" and "Bad super kind in isa: ".
