# Thoughts on Origin Tracking — a rejected approach to restoring MSAE

**Status: rejected.** This document records a design direction that was
considered and turned down. It's preserved so that future maintainers
understand the reasoning, and don't re-invent the same proposal without
the accompanying critique.

## The problem this was trying to solve

Two failing tests in today's compiler (all part of category G in
`quest.md`) and one minimal reproduction written during the investigation:

- `Frontend/IntegrationTests/test/dev/vale/AfterRegionsIntegrationTests.scala`
  — "Make array without type" and "Call Array&lt;&gt; without element type."
- `Frontend/TypingPass/test/dev/vale/typing/AfterRegionsTests.scala`
  — "Bound-driven return rune cannot be inferred from lambda (MSAE general)."

All three exhibit the same solver stall. A generic function like
`func callAndReturn<E, G>(g &G) E where func(&G)E` is called with a real
closure argument. The solver infers `G` from the arg via `InitialSend`,
but then stalls because no rule can fire to discover `E` — `ResolveSR`
and `CallSiteFuncSR` both require either the prototype or the return
already known, and the solver has no "look up a function by name+params
and learn its return" capability.

The capability was present in Vale's pre-2022 templates system, where an
imperative template evaluator could reach into a lambda's `__call` and
read its return type as a step. The rule-based generics system that
replaced templates deliberately dropped that capability because, when
unrestricted, it enabled the HashMap regression Evan documents at
`docs/Generics.md:529-541`:

```vale
func HashMap<K Ref imm, V, H, E>(hasher H, equator E) HashMap<K, V, H, E> {
  return HashMap<K, V, H, E>(hasher, equator, 0);
}
```

If callers could infer K, V, H, E from a return-type annotation, they'd
implicitly satisfy bounds (like `drop(H)`) that they never explicitly
declared. That's the bug.

The open question when this document was being drafted: **can we restore
the useful subset (infer return ruins from actual caller-supplied
values) without re-enabling the dangerous subset (infer from caller's
declared annotations)?**

Origin tracking was the first mechanism proposed to answer "yes."

---

## The proposal (rejected)

Add a small piece of metadata to every solver conclusion: an **origin**
tag recording where the conclusion's value ultimately came from.

- `FromArg`: the value transitively derives from an `InitialSend` (an
  actual caller-supplied argument).
- `FromAnnotation`: the value transitively derives from an
  `InitialKnown` (a caller-declared annotation, a `LiteralSR`, etc.).
- `FromBoundProjection`: the value was produced by the new pre-inference
  step, and inherits the source rune's tag.

Rule handlers would compute each output conclusion's origin as the join
of its input origins. Every commit step in `SimpleSolverState` would
thread the tag through.

A new mechanism — **bound projection** — would be added, plugged into
`InferCompiler.incrementallySolve` as a callback analogous to DRSINI's
default-arg step. Its logic:

1. Walk the rule set for `ResolveSR` rules whose param-list rune is
   known but whose return rune isn't.
2. For each such rule, check the origin of every piece of the param
   list.
3. If all pieces are `FromArg` (or transitively so), invoke
   `OverloadResolver.findFunction(name, paramCoords)` to look up the
   referenced function and learn its return type.
4. `commitStep` a conclusion for the return rune, tagged
   `FromBoundProjection` with the source origin.
5. Return `true` to resume solving.

Safety argument (as originally stated):

> Bound projection can consume values that the caller physically handed
> in. It cannot consume values the caller merely declared. So the
> HashMap regression cannot recur: if a caller writes
> `HashMap<str, int, MyHasher, MyEqr>()`, the K,V,H,E in that annotation
> are `FromAnnotation`-tagged, and projection refuses to run.

Implementation sketch:
- `SimpleSolverState` conclusion type gains a `ConclusionOrigin` field.
- ~15-20 existing rule handlers in `CompilerSolver.scala:600-700` get a
  mechanical change to compute output origin from input origins.
- New callback in `InferCompiler` calling `overloadResolver.findFunction`
  and injecting results via `commitStep` with the right tag.
- `ArrayCompiler`'s MSAE guard is removed; the postparser synthesizes an
  implicit element rune so there's always a target for projection.
- A new `Generics.md` section (provisional acronym `BRPZ` — Bound
  Return Projection) explaining the mechanism. The MSAE section
  redirects to it. The "...but not return types" section gets an
  amendment noting the narrow exception.

## Why this was rejected

The proposal works, in the narrow sense that it would make the failing
tests pass while preserving the HashMap regression's exclusion. Several
problems argue against shipping it anyway.

### 1. The metadata has one consumer

`ConclusionOrigin` is only meaningful to the bound-projection step. No
other rule handler cares. No downstream pass cares. The instantiator
doesn't care. The later-phase resolution check doesn't care. We'd be
adding a dimension to every fact in the solver because **one** new
consumer wants to distinguish two kinds of facts.

That's the shape of code that ages badly. A year from now someone reads
`ConclusionOrigin` and asks "why do we track this, what else uses it?"
The answer is "one piece of code does." That's the point at which
someone either rips the mechanism out (losing the safety property it was
protecting) or adds more consumers to justify it (making the solver
more procedural than it needs to be). Neither outcome is healthy.

### 2. The invariant it protects isn't really about origin

The HashMap regression isn't caused by facts being derived from
annotations. It's caused by **callers not declaring bound args they
were obligated to declare**. That's a property of the call site's
obligation structure, not of individual conclusions' histories.

Origin tagging is a *proxy* for the obligation structure. Proxies tend
to be equivalent to the property they're proxying under the conditions
the author tested, and drift from it as the codebase evolves. The
proxy here is plausible today because:
- every `InitialSend`-rooted value corresponds to a real argument the
  caller provided, and
- every `InitialKnown`-rooted value corresponds to something the caller
  wrote (either an annotation or a default).

Both claims are roughly true in today's rule-emission paths. Neither is
guaranteed by the solver's architecture. A future feature — contextual
return-type inference, expected-type propagation through blocks, a new
kind of inference hint — could easily create a value that's
`InitialSend`-rooted by the rules but corresponds to an annotation at
the language level, or vice versa. Any such change would invalidate
the proxy and silently weaken the safety property.

### 3. It introduces a distinction the solver's execution model doesn't reflect

The solver's discipline is that rules fire on known facts and produce
new known facts. There is no concept of "facts of different kinds."
Origin tracking would create a second, shadow type system — one that
the main rules don't participate in but bound projection does.

Type checkers get messy when they're partly declarative and partly
procedural. Origin tracking is a procedural memory (we remember where a
fact came from) grafted onto a declarative system (rules that reason
about facts as uniform). Historically this pattern accumulates:
- first you track origins,
- then you need to propagate them through new rule shapes,
- then handlers start having special cases ("if origin is X, do Y"),
- then debugging gets harder because the facts and their metadata are
  now two coupled things that evolve independently.

Vale's solver is already delicate. Adding a second layer it has to
keep consistent is a specific kind of technical debt.

### 4. "Scoped narrowly" was a euphemism

When I first described this mechanism as "scoped narrowly," I was
hiding three distinct claims:

- The rune is the return of a bound rule. (Structural, defensible.)
- The rune appears nowhere else. (A negative existential over the whole
  rule set — fragile, emergent, not structural.)
- Therefore inferring it is safe. (A claim about obligations, dressed
  up as a claim about rune topology.)

The "scoped narrowly" language collapsed these into one. That's
roughly the template for how special-case hacks hide in plain sight:
a real safety condition is stated, a weaker proxy is implemented, and
the prose conflates the two so nobody notices the gap.

### 5. There is a cleaner path

Evan's "Note from later" at `docs/Generics.md:541` is the clue:

> couldn't we leave it up to the call site to try to instantiate the
> return type? Then the definition could assume that everything exists
> and things will be fine. It seems that Rust allows a function
> definition to inherit bounds from its return value, so that hints
> that it might be fine.

The alternative that emerged in discussion: rather than patch the
solver with new metadata, **fix the underlying rule**. `ResolveSR`
currently fires in one direction (params+return → prototype). The
conceptually natural other direction (name+params → prototype+return,
via real overload lookup) is missing, and has been missing since the
templates→generics transition. The "two rules for the same bound"
machinery that emits both `ResolveSR` and `CallSiteFuncSR` was
specifically designed to allow rules to fire in whichever direction
becomes possible first. Adding the missing direction is, under this
framing, a completion of an already-bidirectional design, not a new
mechanism.

Under that approach, the HashMap safety comes from the existing
post-solve bound-arg verification (the "resolve later" half of SFWPRL).
If the caller didn't declare bound args covering the callee's bounds,
the later check fails — regardless of where individual facts in the
solver came from. That's the invariant stated in its natural form
("callers declare their bound obligations"), checked in the natural
place (post-solve resolution), with no new metadata.

This is a bigger change to the rule system than origin tracking, but
it's a **uniform** one. All call sites see the same, more-permissive
resolution. No distinction between "arg-origin" and "annotation-origin."
No new solver field. The safety is enforced where safety already lives,
not in a new shadow layer. A future reader would recognize it as a
type-system upgrade rather than a bolted-on guard.

---

## What remains usable from the proposal

Not everything here is wasted effort. Several observations from the
origin-tracking analysis carry over to the rule-relaxation approach:

- The `incrementallySolve` callback is the correct plug-in point. DRSINI
  (`InferCompiler.scala:755-782`) already uses this seam and it can host
  bound projection too — just without the origin-gating.
- Lambda body typing via `OverloadResolver.findFunction` already does
  what we need (types the body, returns a prototype, caches via
  `CompilerOutputs.signatureToFunction`). No new machinery.
- The lambda closure's `__call` generic is declared early (at expression
  evaluation time) and stamped lazily. Stamping it during outer-call
  inference is safe because the closure's captured env is already
  settled. No reentrancy concerns.
- Only two call sites in the entire stdlib emit MSAE-shape signatures
  (`arrays.vale:36` and `arrays.vale:52`). The blast radius of any fix
  is small.
- Every HashMap test currently passes with explicitly declared bounds.
  None rely on the dangerous regime. The stdlib HashMap (at
  `stdlib/src/collections/hashmap/HashMap.vale:26-29`) carries explicit
  `where func(&H, K)int, func(&E, K, K)bool, func drop(K)void` —
  confirming that the dangerous regime is genuinely absent from the
  current codebase, not just latent.

These observations are independent of which safety mechanism we choose.
They're preserved here so the next investigation can start from them.

---

## Summary

Origin tracking was proposed as a way to restore return-type inference
from bounds without re-enabling the HashMap regression. It would have
added `FromArg`/`FromAnnotation`/`FromBoundProjection` tags to every
solver conclusion, with bound projection gated on `FromArg`-rooted
inputs.

It was rejected because:
- The metadata has only one consumer.
- The invariant it protects is about call-site obligations, not fact
  provenance, and the origin-based check is a proxy for the real
  property.
- It introduces a procedural memory layer into a declarative solver.
- The proposal's "narrow scope" framing conflated a structural claim
  with a negative existential and with an obligation claim.
- A cleaner alternative — relax `ResolveSR` to fire with name+params
  known, rely on existing post-solve bound-arg verification for safety
  — was identified.

The rejected approach is documented here, not forgotten, so that a
future maintainer sees both the idea and the reasons it didn't pass
review.
