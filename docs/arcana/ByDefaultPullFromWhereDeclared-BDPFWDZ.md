# By Default Pull From Where Declared (BDPFWDZ)

Vale's environments are pull-style. A declaration lives in the scope that introduced it; consumers reach it by walking parent chains and following declared links (impls, parameter types, citizen relationships). We try not to eagerly copy things from one scope into another.

Two pillars:

- **Introduced together.** A thing and its callable surface are introduced together, in the same scope. For a struct, that scope is the package. For a placeholder introduced by `<T>`, that scope is the function's near-env. A `where` clause is just the syntax for declaring callable surface inside a function near-env — morally equivalent to a free function declared next to a struct in a package.

- **Stay-in-place.** Declarations stay in their introducing scope. They don't propagate downward into child scopes or sideways into consumer scopes. Lookups walk to find them.

A consumer asking "what's callable on T?" walks the env chain — parent envs, parameter-type envs of the containing function, and super-interface envs reached through impl bounds. The walk pays a per-lookup cost; the alternative (push/harvest) trades identity, privacy, and freshness for a flatter near-env.

**Why pull.** Identity is preserved (every found candidate carries its original declaration site, useful for diagnostics). Privacy is tight (a private import in file Y doesn't transitivize when X imports Y, because nothing was copied). Single source of truth (no syncing because no copies). Uniform reasoning (every "where is this reachable?" question has the same answer shape: trace the chain).

**This is a leaning.** This is not a solid rule, and we might encounter reasons to go the other way later on. Tentatively though, this is the way we think.

A `where` clause is not an import. Imports carry copy-semantics in most languages, which steers designers toward harvest-style implementations. In Vale, a `where` clause declares reachability; the resolver follows the declared link to wherever the surface lives.

**Currently-cataloged push exceptions.** The following sites copy declarations downward and are technical debt to refactor toward pull. New push sites must be added here with justification, or refactored before landing.

- `addRunedDataToNearEnv` harvests `FunctionBoundNameT` prototypes from citizen-typed parameters' inner envs into the calling function's near-env (NBIFP). The bounds belong in the citizen's inner env per the introduced-together pillar; harvesting them down violates stay-in-place. The pull replacement is for `OverloadResolver` to walk the calling function's parameter envs at lookup time.

**Interactions with @ECSIIOSZ, @BRRZ.** These arcana are pull-aligned. ECSIIOSZ works because impl bounds are pulled from the calling env at solve time, not pre-pushed. BRRZ's mid-solve real lookup is itself a pull operation reaching across to the callee's definition env. Solution C for impl bounds (`OverloadResolver.getPlaceholderImplBoundEnvs`) is a positive instance: bounds are walked from the function's calling env and methods stay in the interface where declared.

## See also

- [`EachCallSiteIsItsOwnSolve-ECSIIOSZ.md`](./EachCallSiteIsItsOwnSolve-ECSIIOSZ.md) — per-call-site solve substrate; pulls bounds at solve time.
- [`BoundReturnResolution-BRRZ.md`](./BoundReturnResolution-BRRZ.md) — mid-solve real lookup, a pull operation across def-time/callsite boundaries.
- [`LambdasAreGenericTemplatesNotGenerics-LAGTNGZ.md`](./LambdasAreGenericTemplatesNotGenerics-LAGTNGZ.md) — per-call-site lambda specialization; lambda bodies stay in their lambda env.
