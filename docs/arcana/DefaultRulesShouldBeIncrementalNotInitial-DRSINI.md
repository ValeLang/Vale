# Default Rules Should Be Incremental Not Initial (DRSINI)

Default generic parameter rules (`LiteralSR(_211, 5)` from `GenericParameterDefaultS.rules`)
must not be in the solver's initial rule set. They are added incrementally — only for runes
that remain unsolved after argument inference — via the `solveForResolving` and
`evaluateGenericFunctionFromCallForPrototype` incremental callbacks.

Per @ECSIIOSZ, this is part of the per-call-site setup contract: defaults are added to the
call-site's individual solver instance, not baked into the shared rule vector.

## Why

The postparser hoists an `EqualsSR(H, _211)` into the parent type's main rules, connecting
the generic param rune (H) to the default's result rune (_211). This is harmless alone — it
just aliases two unsolved runes. But if `LiteralSR(_211, 5)` is also in the initial rules,
the solver immediately concludes H=_211=5 before argument inference runs. When argument
inference later determines H to a different value, the solver conflicts.

Neither rule alone causes harm:

- `EqualsSR(H, _211)` in main rules — aliases H and _211. Both stay unsolved. Harmless.
- `LiteralSR(_211, 5)` from defaults — concludes _211=5. H untouched. Harmless.

Together in the initial rule set, they force H=5 before arguments are processed.

## Where the rules live

- `EqualsSR(H, _211)` — hoisted into parent's main rules by PostParser.scala (line 308+316).
  Flows into the solver via `assembleCallSiteRules`'s rule filter. Always present.
- `LiteralSR(_211, 5)` — stored in `GenericParameterDefaultS.rules`. Added incrementally
  by `solveForResolving` (InferCompiler.scala) and by the callback in
  `evaluateGenericFunctionFromCallForPrototype` (FunctionCompilerSolvingLayer.scala), only
  when the param rune is unsolved after argument inference.

## How incremental defaults work

1. The solver runs with initial rules (including the hoisted `EqualsSR(H, _211)` but NOT
   `LiteralSR(_211, 5)`). Argument inference determines what it can.
2. If the solve stalls with unsolved runes, the incremental callback fires.
3. The callback finds the first unsolved identifying rune with a default and commits
   `defaultRules.rules` (the `LiteralSR`) via `commitStep`.
4. The solver resumes. `_211=5` fires, then the existing `EqualsSR(H, _211)` fires,
   giving `H=5`.
5. If argument inference already determined H, the callback never fires for that rune.

## Affected paths

- **`assembleCallSiteRules`** (TemplataCompiler.scala) — just filters rules. Does NOT add
  defaults. `assembleCallSiteRules` used to eagerly add `x.rules` for defaulted params;
  this was removed.
- **`solveForResolving`** (InferCompiler.scala) — uses `incrementallySolve` with a default
  callback. Serves `resolveStruct` and `resolveInterface`.
- **`evaluateGenericFunctionFromCallForPrototype`** (FunctionCompilerSolvingLayer.scala) —
  has its own incremental callback that adds `defaultRules.rules` for unsolved runes.
- **`assemblePredictRules`** (TemplataCompiler.scala) — unaffected. Used for type prediction
  (`MyInterface<bool>` -> `MyInterface<bool, 5>`) where there's no argument inference to
  conflict with. It adds both `x.rules` and a dynamically-created connecting EqualsSR.
- **`solveForDefining`** — never includes defaults. Function bodies must work for any valid
  value, not just the default. Placeholders are used instead.
