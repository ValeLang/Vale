# Lambdas Are Generic Templates Not Generics (LAGTNGZ)

Top-level functions and lambdas are specialized by different mechanisms. Top-level functions are **generic**: compile the body once against abstract placeholders, have the Instantiator stamp concrete monomorphizations later from the call graph. Lambdas are **templates**: the typing pass solves each call site fresh with concrete arg types, and bakes those arg types into the resulting function's template name — so each distinct arg-tuple produces a separately-named `LambdaCallFunctionNameT` entry in `CompilerOutputs.functions`.

The lambda-vs-top-level choice is made at overload resolution on `FunctionA.isLambda()`; inside the templated path, whether to set up a closure struct is a secondary branch on `FunctionA.isLight()`, but both light and non-light lambdas go through the same per-call-site expansion. A lambda without captures is still a lambda — it takes the templated path, not the generic path. `NameTranslator` reflects this: its lambda name builder bakes the call-site arg coords into `LambdaCallFunctionTemplateNameT`, and its generic name builder refuses lambdas outright.

**Why the postparser still publishes `genericParams` for lambdas.** The postparser synthesizes a coord rune for each untyped lambda param (`(_) =>`, `(a, b) =>`) and lifts it into `FunctionS.genericParams`, so `(a, b) => a == b` looks shape-equivalent to `func foo<T, U>(a T, b U)` from later passes' perspective. The `_` vs `<T>` syntactic distinction stays contained inside the postparser; downstream code walks `genericParams` uniformly without branching on origin. The typing pass's LAGTNGZ specialization is unaffected — arg types still drive `LambdaCallFunctionTemplateNameT.paramTypes` at each call site. The `genericParams` values additionally appear in the resulting `LambdaCallFunctionNameT.templateArgs`: redundant relative to `paramTypes`, but preserving the generic-function contract that a function's identifying-rune values are recoverable from its name.

**Why this split exists.** Lambdas capture variables from enclosing scope. For a pure-generic model to handle them, the captured-variable types would need to be published on the enclosing function's interface as bounds, so the body could type-check against abstract placeholders for the captures. The template model sidesteps this: wait until a call site supplies concrete arg types (and by then the closure's captured-variable types are concrete too), then expand per distinct arg tuple.

**Example: one call site.**

```vale
func takeInt<F>(f F) int where func __call(&F, int)int { f(5) }
exported func main() int {
  takeInt((x) => { x })
}
```

`(x) => { x }` is scouted with one generic parameter for `x`'s type. When `takeInt` is typed, its `__call` bound resolves to the lambda with `params = [int]`. The typing pass produces one `LambdaCallFunctionNameT(template = LambdaCallFunctionTemplateNameT(loc, [int]), parameters = [int])` in `CompilerOutputs.functions`.

**Example: one lambda, two call sites with different types.**

```vale
exported func main() {
  lam = (x) => { x };
  (lam)(5);
  (lam)("hi");
}
```

`lam` is one closure struct — exactly one `LambdaCitizenNameT` is produced (closure structs aren't parameterized; `LambdaCitizenTemplateNameT.makeStructName` asserts `templateArgs.isEmpty`). But `CompilerOutputs.functions` contains **two** `__call` entries:

```
LambdaCallFunctionNameT(LambdaCallFunctionTemplateNameT(loc, [int]), ..., [int])
LambdaCallFunctionNameT(LambdaCallFunctionTemplateNameT(loc, [str]), ..., [str])
```

One closure struct, two instantiations of its call function — separately type-checked, separately named, both pointing at the same closure. This is the direct consequence of arg types being encoded in the template name: different tuple, different name, different entry.

**Interaction with ECSIIOSZ.** Per-call-site solves naturally mesh with per-call-site template expansion: each solve independently reaches the lambda dispatcher with its own `argTypes`, so independent solves produce independent `LambdaCallFunctionTemplateNameT`s without sharing state.

## See also

- `docs/Generics.md` — the generics system that top-level functions use.
- `docs/arcana/EachCallSiteIsItsOwnSolve-ECSIIOSZ.md` — the per-call-site solve model.
- `docs/arcana/ByDefaultPullFromWhereDeclared-BDPFWDZ.md` — pull-by-default is what makes per-call-site lambda specialization work without copying lambda bodies anywhere; the body stays in its lambda env and each call-site solve reaches it via the standard env chain.
