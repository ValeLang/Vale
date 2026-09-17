# SPDMX Addendum: Inline Pattern Consolidation in Tests

This extends the examples and clarifications in `Luz/shields/ScalaParityDuringMigration-SPDMX.md` with cases specific to `shouldHave` / `Collector.only` test patterns.

---

## Additional Examples

**DENY** — Scala packs all narrowings into one case pattern; Rust splits them into post-matches:
```rust
 let call: &FunctionCallTE = crate::collect_only_tnode!(
     NodeRefT::FunctionDefinition(bork),
+    NodeRefT::FunctionCall(c) => Some(c)
 );
+let prototype = call.callable;
+match prototype.id.local_name {
+    INameT::FunctionBound(fbn) => { assert_eq!(fbn.template.human_name.0, "drop"); ... }
+    _ => panic!(),
+}
+match prototype.return_type {
+    CoordT { ownership: OwnershipT::Share, kind: KindT::Void(_), .. } => {}
+    _ => panic!(),
+}
 /* bork.body shouldHave {
      case FunctionCallTE(PrototypeT(IdT(_, _, FunctionBoundNameT(...)), CoordT(ShareT,_, VoidT())), _, _) =>
    } */
```
The Scala case has both `FunctionBoundNameT(...)` and `CoordT(ShareT, _, VoidT())` inline in one pattern. Rust must not pull them into separate post-matches.

**ALLOW** — all narrowings packed into the `collect_only_tnode!` pattern, matching the Scala case exactly:
```rust
+crate::collect_only_tnode!(
+    NodeRefT::FunctionDefinition(bork),
+    NodeRefT::FunctionCall(FunctionCallTE {
+        callable: PrototypeT {
+            id: IdT {
+                local_name: INameT::FunctionBound(FunctionBoundNameT {
+                    template: FunctionBoundTemplateNameT { human_name: StrI("drop"), .. },
+                    template_args: &[],
+                    parameters: &[CoordT {
+                        ownership: OwnershipT::Own,
+                        kind: KindT::KindPlaceholder(KindPlaceholderT {
+                            id: IdT {
+                                init_steps: &[INameT::FunctionTemplate(FunctionTemplateNameT { human_name: StrI("bork"), .. })],
+                                local_name: INameT::KindPlaceholder(KindPlaceholderNameT {
+                                    template: KindPlaceholderTemplateNameT { index: 0, .. },
+                                }),
+                                ..
+                            },
+                        }),
+                        ..
+                    }],
+                    ..
+                }),
+                ..
+            },
+            return_type: CoordT { ownership: OwnershipT::Share, kind: KindT::Void(_), .. },
+        },
+        ..
+    }) => Some(())
+);
 /* bork.body shouldHave {
      case FunctionCallTE(PrototypeT(IdT(_, _, FunctionBoundNameT(...)), CoordT(ShareT,_, VoidT())), _, _) =>
    } */
```

---

**DENY** — Scala puts the variable check inside the `case` body; Rust extracts it as a post-match:
```rust
 let let_normal: &LetNormalTE = crate::collect_only_tnode!(
     NodeRefT::FunctionDefinition(main),
+    NodeRefT::LetNormal(l) => Some(l)
 );
+match let_normal.variable {
+    ILocalVariableT::Reference(ReferenceLocalVariableT { coord: CoordT { ..NeverT(false).. }, .. }) => {}
+    _ => panic!(),
+}
 /* main shouldHave {
      case LetNormalTE(ReferenceLocalVariableT(_,_,CoordT(ShareT,_,NeverT(false))), _) =>
    } */
```

**ALLOW** — field pattern is inline, matching the Scala `case LetNormalTE(ReferenceLocalVariableT(...), _)`:
```rust
+crate::collect_only_tnode!(
+    NodeRefT::FunctionDefinition(main),
+    NodeRefT::LetNormal(LetNormalTE {
+        variable: ILocalVariableT::Reference(ReferenceLocalVariableT {
+            coord: CoordT { ownership: OwnershipT::Share, kind: KindT::Never(NeverT { from_break: false }), .. },
+            ..
+        }),
+        ..
+    }) => Some(())
+);
 /* main shouldHave {
      case LetNormalTE(ReferenceLocalVariableT(_,_,CoordT(ShareT,_,NeverT(false))), _) =>
    } */
```

---

**DENY** — Scala nests a second `Collector.only` and a `match` inside the outer case body; Rust pulls them out as top-level post-matches:
```rust
 let upcast: &UpcastTE = crate::collect_only_tnode!(
     NodeRefT::FunctionDefinition(main),
+    NodeRefT::Upcast(u @ UpcastTE { target_super_kind: ISuperKindTT::Interface(.."Car"..), .. }) => Some(u)
 );
+match upcast.inner_expr.result().coord.kind {       // should be inside the arm body
+    KindT::Struct(stt) => { match stt.id.local_name { ... } }
+    other => panic!(),
+}
+match upcast.result().coord.kind {                  // should be inside the arm body
+    KindT::Interface(it) => { match it.id.local_name { ... } }
+    other => panic!(),
+}
 /* Collector.only(main, {
      case up @ UpcastTE(innerExpr, InterfaceTT(simpleNameT("Car")), _) => {
        Collector.only(innerExpr.result, { case StructTT(simpleNameT("Toyota")) => })
        up.result.coord.kind match { case InterfaceTT(IdT(x, Vector(), InterfaceNameT(...))) => vassert(x.isTest) }
      }
    }) */
```
The inner `Collector.only` and the `result.coord.kind match` are part of the outer case *body*, not separate statements.

**ALLOW** — inner checks live inside the arm body, and the nested match arms are flattened (per PSMONM):
```rust
+crate::collect_only_tnode!(
+    NodeRefT::FunctionDefinition(main),
+    NodeRefT::Upcast(u @ UpcastTE {
+        target_super_kind: ISuperKindTT::Interface(InterfaceTT {
+            id: IdT { local_name: INameT::Interface(InterfaceNameT {
+                template: InterfaceTemplateNameT { human_namee: StrI("Car"), .. }, ..
+            }), .. },
+            ..
+        }),
+        ..
+    }) => {
+        match u.inner_expr.result().coord.kind {
+            KindT::Struct(StructTT { id: IdT { local_name: INameT::Struct(StructNameT {
+                template: IStructTemplateNameT::StructTemplate(StructTemplateNameT { human_name: StrI("Toyota"), .. }),
+                ..
+            }), .. }, .. }) => {}
+            other => panic!("{:?}", other),
+        }
+        match u.result().coord.kind {
+            KindT::Interface(InterfaceTT { id: IdT {
+                package_coord: pc, init_steps: &[],
+                local_name: INameT::Interface(InterfaceNameT {
+                    template: InterfaceTemplateNameT { human_namee: StrI("Car"), .. },
+                    template_args: &[],
+                    ..
+                }),
+                ..
+            }, .. }) => { assert!(pc.is_test()); }
+            other => panic!("{:?}", other),
+        }
+        Some(())
+    }
+);
 /* Collector.only(main, {
      case up @ UpcastTE(innerExpr, InterfaceTT(simpleNameT("Car")), _) => {
        Collector.only(innerExpr.result, { case StructTT(simpleNameT("Toyota")) => })
        up.result.coord.kind match { case InterfaceTT(IdT(x, Vector(), InterfaceNameT(...))) => vassert(x.isTest) }
      }
    }) */
```

---

## Additional Clarifications

- **`shouldHave { case Pat => }` with no body maps to an inline `collect_only_tnode!` pattern.** All narrowings the Scala expresses as part of `Pat` — including deeply nested fields, slice lengths (`Vector()` → `&[]`, `Vector(x)` → `&[x]`), and integer literals — must live inside the `collect_only_tnode!` pattern, not in follow-up matches after it returns.

- **`shouldHave { case x @ Pat => { body } }` with a body maps to a block arm.** When the Scala case body contains additional checks (nested `Collector.only`, a follow-up `match`, `vassert`s), those go inside a `=> { ... Some(()) }` block, not outside the macro call. Everything that is inside the Scala `{ }` case body must be inside the Rust `=> { }` arm.

- **Rust slice patterns are the exact equivalent of Scala `Vector(...)` in pattern position.** `Vector()` → `&[]`, `Vector(x)` → `&[x]`, `Vector(x, y)` → `&[x, y]`. Using these inline avoids the need for post-`assert_eq!(len, N)` + index checks.

- **Match ergonomics handle `&'t T` field references automatically.** When destructuring a struct whose field is `&'t FooT`, writing `field: FooT { ... }` in the pattern works — no need for `field: &FooT { ... }`. This applies throughout the deeply-nested patterns above.
