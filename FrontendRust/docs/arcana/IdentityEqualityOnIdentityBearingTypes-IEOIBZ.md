# Identity Equality On Identity-Bearing Types (IEOIBZ)

Types that have identity — arena-allocated, accessed by reference, where two distinct allocations are distinct things — implement their own `PartialEq`/`Eq`/`Hash` directly via `std::ptr::eq` and `std::ptr::hash` on `&self`. Wrappers that hold references to those types just `#[derive(PartialEq, Eq, Hash)]` — the derived impl deref-calls the inner type's pointer-equality impl, with the same semantics and no copy-paste.

This pushes the "this type has identity" knowledge to the type that actually has identity. A new wrapper holding `&'t IEnvironmentT` doesn't need to know about identity — it derives, and identity propagates correctly. The alternative — manual ptr-eq impls on every wrapper — forces every new wrapper to repeat the dance and leaves the inner type silent about its own identity.

**How this affects authoring code:** when adding a new identity-bearing type (anything Arena-allocated per @TFITCX where two distinct allocations should compare unequal), write three small impls:

```rust
impl<...> PartialEq for FooT { fn eq(&self, other: &Self) -> bool { std::ptr::eq(self, other) } }
impl<...> Eq for FooT {}
impl<...> std::hash::Hash for FooT {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) { std::ptr::hash(self, state) }
}
```

When adding a wrapper that holds `&'t FooT` (or `&'s FooA`), just `#[derive(PartialEq, Eq, Hash)]`. Don't write a manual `std::ptr::eq(self.foo, other.foo)` impl on the wrapper.

**Why deriving on the wrapper recurses correctly:** for `x, y: &T`, `*x == *y` desugars to `<T as PartialEq>::eq(&*x, &*y)`. Auto-reborrow makes `&*x` the same pointer as `x`. So the derived call lands in the inner type's `eq` with the same pointers as the outer wrapper had — exactly where we want ptr-eq to happen.

**Where:**
- Identity types with manual ptr-eq impls: `IEnvironmentT`, `FunctionA`, `StructA`, `InterfaceA`, `ImplA`, `FunctionHeaderT`. (`IdT` is the slight outlier — it ptr-eq's the `init_steps` slice, not the whole struct, because `IdT` is Copy and lives by value, not by reference.)
- Wrappers that just derive: `FunctionTemplataT`, `StructDefinitionTemplataT`, `InterfaceDefinitionTemplataT`, `ImplDefinitionTemplataT`, `ContainerInterface`/`Struct`/`Function`/`Impl`, `ExternFunctionTemplataT`, plus most TFITCX Value-types in `templata.rs`.
- Variant env types (`PackageEnvironmentT`, `FunctionEnvironmentT`, etc.) use `self.id == other.id` rather than ptr-eq. That's correct given `IdT` is sealed/canonical, and these are usually compared through `&'t IEnvironmentT` (which is ptr-eq) anyway.

**Interactions with @SICZ:** SICZ is what makes `IdT`'s existing slice-pointer-equality sound. Without sealing, two un-interned `IdT`s could have different `init_steps` pointers and compare unequal despite being conceptually identical. SICZ + IEOIBZ together: identity types declare ptr-eq, sealing guarantees the pointers are canonical, so ptr-eq is correct everywhere.

**Exception — equality opt-outs (vcurious mirror):** some Arena-allocated types intentionally have **no equality of any kind** in Rust, mirroring Scala's `override def equals(obj: Any): Boolean = vcurious()` pattern (which panics if anyone tries to `==` them). The canonical example is the typing pass's expression hierarchy: `ReferenceExpressionTE`, `AddressExpressionTE`, `ExpressionTE`, and the ~50 per-variant struct types (`UnletTE`, `BlockTE`, `ConstantIntTE`, etc.) — Scala has 52 `vcurious` overrides on these in `ast/expressions.scala`. Rust mirrors this by **not deriving or impl-ing** `PartialEq`/`Hash` at all. Misuse fails at compile time, which is strictly stronger than Scala's runtime panic.

These types are still `/// Arena-allocated` per @TFITCX — they're stored in the arena for memory/lifetime reasons (large, deeply nested expression trees holding `&'t` child pointers). They just don't carry identity semantics: two distinct allocations of the same expression are neither `==` (no impl) nor distinguishable by identity (nothing compares them). The opt-out applies to the type itself and propagates: a wrapper holding `&'t ReferenceExpressionTE` (e.g. `ExpressionTE`) also can't `derive(PartialEq)` because the derive would try to call the inner's missing eq.

**When to opt out:** check the Scala counterpart. If every case class in the hierarchy has `vcurious()` equals/hashCode overrides, mirror it as no-impl in Rust. Otherwise apply the standard ptr-eq impl from above.
