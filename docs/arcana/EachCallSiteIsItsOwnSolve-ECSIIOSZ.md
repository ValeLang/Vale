# Each Call-Site Is Its Own Solve (ECSIIOSZ)

Every call-site in source code — every `Some<T>(x)`, every `[]&E(size, callable)`, every `add(list, 7)` — is lowered by the postparser into its own self-contained vector of solver rules, and the typing pass spins up a fresh `InferCompiler` solver instance per call-site to resolve them.

Call-site solves don't share state. Each gets its own rule vector, its own rune-to-type map, its own initial-knowns, and its own conclusion map.

For example, `Some<T>(x)` inside `moo<T>`'s body is scouted into roughly four rules: a lookup for `Some`, a `RuneParentEnvLookupSR` for `T` (per MKRFA, since `T` comes from moo's enclosing template), a `CallSR` applying `Some` to `T`, and a `ResolveSR` finding a prototype matching the argument type.

When compiling moo's body, the typing pass hits this call-site, builds a fresh solver over these four rules, seeds `T` as an initial known from moo's env, solves — producing `Some<moo$T>` — and discards the solver.

**Why per call-site:** call-sites have contextual knowledge that definitions don't — argument types, explicit template args, the surrounding environment — and each call-site's knowledge is different.

A per-call-site solver lets that knowledge be consumed as initial-knowns without polluting a shared definition solve or requiring the solver to partition its conclusion map by call-site.

It also maps cleanly to Vale's two solve modes, `solveForDefining` vs `solveForResolving` (see DBDAR), each of which runs over an appropriately-filtered rule vector per SROACSD.

**What this demands of call-site setup code:** every site that instantiates a solver (`ArrayCompiler`, `OverloadResolver`, `ImplCompiler`, `StructCompilerGenericArgsLayer`, `FunctionCompilerSolvingLayer`) is individually responsible for the full setup contract.

The rule vector is self-contained *except* for "portal" rules that only make sense relative to the caller. `RuneParentEnvLookupSR` must be preprocessed out into initial-knowns (MKRFA). Default generic-param rules must be added incrementally rather than up front (DRSINI). Site-specific rules (`ResolveSR`, `CallSiteFuncSR`, `DefinitionFuncSR`) must be filtered for the right solve mode (SROACSD). The calling function's env must be threaded through as `InferEnv.callingEnv` (CSSNCE).

**⚠ MKRFA is unenforced and leaky — refactor soon.** The MKRFA preprocessing obligation is the least robust part of this contract. It's a prose cross-reference with no type-level or runtime enforcement, and the value solver's `RuneParentEnvLookupSR` handler at `CompilerSolver.scala:852` is a silent no-op that conceals violations. Three `ArrayCompiler` expression entry points violated MKRFA undetected from 2022 until April 2026. Every new expression-scoped solver caller is a candidate to repeat this bug. See `docs/refactor-thoughts/mkrfa-protocol-leak.md` for the full write-up and queued remediation (extract the fold into an `InferCompiler` helper; replace the no-op with `vwat()`). Declaration-scoped callers (`FunctionCompilerSolvingLayer`, `StructCompilerGenericArgsLayer`, `ImplCompiler`) are safe *by accident*: their rule sources happen never to emit `RuneParentEnvLookupSR`.

**Interaction with LAGTNGZ.** Per-call-site solving is the substrate on which LAGTNGZ operates: because lambdas are template-expanded per call site (not stamped once by the Instantiator), each fresh solve independently produces its own `LambdaCallFunctionTemplateNameT` with its own baked-in argTypes, and no state leaks between expansions. Two call sites passing a lambda with different concrete types yield two independent solves and therefore two independent function entries.

**Interaction with BDPFWDZ.** Per-call-site solving is fundamentally pull-shaped: each solve reaches into the calling env for whatever bounds, conclusions, and impls it needs at solve time, rather than depending on something pre-pushed into a shared store. Solution C's impl-bound walk and BRRZ's mid-solve real lookup both ride on this substrate.

## See also

- `docs/arcana/ByDefaultPullFromWhereDeclared-BDPFWDZ.md` — the broader pull-by-default principle that per-call-site solving is one expression of.
- `docs/arcana/LambdasAreGenericTemplatesNotGenerics-LAGTNGZ.md` — the per-call-site lambda specialization that rides on this substrate.
- `docs/arcana/BoundReturnResolution-BRRZ.md` — mid-solve real lookup operates within a per-call-site solve.
- `docs/arcana/DefaultRulesShouldBeIncrementalNotInitial-DRSINI.md` — defaults are added incrementally per call site, not pre-baked into the shared rule vector.
