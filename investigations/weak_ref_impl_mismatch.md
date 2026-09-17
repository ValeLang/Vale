# Investigation: Weak reference impl mismatch tests

## Bug summary

Tests: `dev.vale.typing.AfterRegionsErrorTests`
- "Cant make non-weakable extend a weakable" — non-weakable `Muta` impls weakable `IUnit`
- "Cant make weakable extend a non-weakable" — weakable `Muta` impls non-weakable `IUnit`
- "Cant make weak ref to non-weakable" — `&&Muta` where `Muta` is not weakable

Expected: First two throw `WeakableImplingMismatch(structWeakable, interfaceWeakable)`. Third throws `TookWeakRefOfNonWeakableError`.

## Root cause

### Tests 1 & 2: Missing weakable check in ImplCompiler

`WeakableImplingMismatch` was defined in `StructCompiler.scala` (line 27) but never thrown anywhere. `ImplCompiler.compileImpl` resolves the sub-citizen and super-interface but never compared their `weakable` flags.

Both `StructDefinitionT` and `InterfaceDefinitionT` have a `weakable: Boolean` field (populated from the `weakable` keyword in source). The check was simply missing.

### Test 3: `lock()` builtin not available in test context

The test code calls `lock(weakMuta)` on a `&&Muta` weak reference, but `lock` is a builtin function that isn't available in the unit test compilation context. The compiler fails with "Couldn't find a suitable function lock(&&Muta)" before reaching the `weakAlias` method where `TookWeakRefOfNonWeakableError` is checked.

The `TookWeakRefOfNonWeakableError` check in `ExpressionCompiler.weakAlias` (line 1718) is correct — it fires when creating a weak reference via `&&` in expression position. But the test never reaches that point because the function containing the weak ref (`getHp`) fails to compile due to the missing `lock` builtin.

Fixing this test requires either: (a) making `lock` available in the test compilation, (b) simplifying the test to trigger `weakAlias` without calling `lock`, or (c) checking weakable during parameter type resolution rather than at weak-ref creation time.

## Resolution

### Fix for tests 1 & 2 (ImplCompiler.scala)

Added weakable compatibility check after sub-citizen and super-interface are resolved (~line 254):

```scala
val subCitizenWeakable =
  coutputs.lookupCitizen(subCitizen) match {
    case s: StructDefinitionT => s.weakable
    case i: InterfaceDefinitionT => i.weakable
  }
val superInterfaceWeakable = coutputs.lookupInterface(superInterface).weakable
if (subCitizenWeakable != superInterfaceWeakable) {
  throw WeakableImplingMismatch(subCitizenWeakable, superInterfaceWeakable)
}
```

Also added `StructDefinitionT` to the imports.

### Test 3: "Cant make weak ref to non-weakable" — missing weakable check on local load path

Investigated via collapsed call tree. Found three issues, resolved all.

**Issue 1: `lock()` not found in original test.** The test used `CompilerTestCompilation.test` which loads builtins via `getModulizedCodeMap` (builtins in `v.builtins.*` packages). The test code had no `import v.builtins.weak.*;`, so `lock` wasn't visible.

**Issue 2: Missing weakable check on local variable load path.** When creating `&&m` where `m` is a local, the code path is `LocalLoadSE` → `evaluateLookupForLoad` → `softLoad`, which directly produces `SoftLoadTE(a, WeakT)` without any weakable check. The `weakAlias` method (which HAS the check) is only reached via a different path — augmented expression results (`OwnershippedSE`), not local variable loads.

**Issue 3: Two existing tests missing `weakable`.** "Lock weak member" (`CompilerTests.scala:1510`) and "Weak yonder member" (`WeakTests.scala:175`) both used `&&Base` where `Base` lacked the `weakable` keyword. All integration tests in `Tests/test/main/resources/programs/weaks/` correctly use `weakable` on every struct that has `&&` taken on it.

### Collapsed call tree

```
- ExpressionCompiler#evaluate(LocalLoadSE "m", LoadAsWeakP):
  - evaluateLookupForLoad(targetOwnership=LoadAsWeakP):
    - evaluateAddressibleLookup: finds local "m", addr ownership=OwnT
    BEFORE FIX:
    - softLoad(OwnT, LoadAsWeakP) → SoftLoadTE(a, WeakT) — NO CHECK
    AFTER FIX:
    - LoadAsWeakP + addr ownership != WeakT → route through weakAlias:
      - softLoad(OwnT, LoadAsBorrowP) → SoftLoadTE(a, BorrowT)
      - weakAlias(borrowExpr):
        - vcheck(structDef.weakable, TookWeakRefOfNonWeakableError) → THROWS ✓
```

### Fix

1. **`evaluateLookupForLoad`** (`ExpressionCompiler.scala`): When `targetOwnership` is `LoadAsWeakP` and the address has non-weak ownership, load as borrow first, then call `weakAlias` (which checks weakable). When the address already has `WeakT` (loading existing weak ref members), use `softLoad` normally.

2. **Simplified the test** to `w = &&m;` — no `lock()` needed.

3. **Added `weakable` to `Base`** in "Lock weak member" and "Weak yonder member" tests.

## Score after fix

1055 passed, 31 failed (was 1052/34). All three weak ref tests fixed.
