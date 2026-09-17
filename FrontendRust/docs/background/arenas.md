# Arena Allocation

The compiler frontend uses three bump arenas (`bumpalo::Bump`), each self-contained with its own lifetime and interning maps.

## `'p` — Parser Arena

Owned by `ParseArena<'p>`. Contains interned strings (`StrI<'p>`), package/file coordinates, and all parser AST nodes (`FileP`, `FunctionP`, `IExpressionPE`, `ITemplexPT`, etc.).

Access: `parse_arena.intern_str(...)`, `parse_arena.intern_package_coordinate(...)`, `parse_arena.bump()`.

The postparser reads `'p` data as input but allocates into `'s` (except synthetic parser nodes — see @PPSPASTNZ).

## `'s` — Scout Arena

Owned by `ScoutArena<'s>`. Contains all postparser output (`StructS`, `FunctionS`, `IExpressionSE`, `IRulexSR`, etc.), all higher typing output (`StructA`, `FunctionA`, `InterfaceA`, etc.), and interned names/runes (`INameS<'s>`, `IRuneS<'s>`, `IImpreciseNameS<'s>`).

Access: `scout_arena.intern_str(...)`, `scout_arena.intern_rune(...)`, `scout_arena.intern_name(...)`, `scout_arena.bump()`.

## `'t` — Typing Arena

Owned by `TypingInterner<'s, 't>` (note: two lifetime parameters — interned values transitively hold both scout and typing refs). Contains all typing-pass output: interned names (`INameT<'s, 't>`, `IdT<'s, 't>`, concrete name structs), interned Kind payloads (`StructTT`, `InterfaceTT`, etc.), interned templata payloads, `PrototypeT`/`SignatureT`, environment structs, heavy-templata payloads, expression nodes (from Slab 5 onward), and `HinputsT`.

Access: `typing_interner.intern_name(...)`, `typing_interner.intern_id(...)`, `typing_interner.intern_struct_tt(...)` etc. (6 family-level methods + ~84 per-concrete wrappers), plus `alloc(...)` / `alloc_slice_copy(...)` / `alloc_slice_from_vec(...)` for non-interned arena allocation.

The typing arena's lifetime ordering: `'s` outlives `'t` (`where 's: 't` on every typing-pass type). Scout arena must not drop until the typing arena (and anything holding its `&'t` refs — e.g. `HinputsT` consumed by the instantiator) is done.

See `docs/architecture/typing-pass-design-v3.md` Part 1 for the typing pass's arena architecture.

## Data Flow

```
Source code
    |
    v
Parser --- allocates into 'p arena ---> FileP, FunctionP, IExpressionPE, ...
    |                                    (StrI<'p>, PackageCoordinate<'p>)
    v
PostParser --- allocates into 's arena -> StructS, FunctionS, IExpressionSE, ...
    |                                      (re-interns StrI<'p> -> StrI<'s>)
    v
HigherTyping --- allocates into 's arena -> StructA, FunctionA, InterfaceA, ...
    |                                        (same 's arena as postparser)
    v
Typing --- allocates into 't arena -----> INameT, IdT, KindT payloads, templatas,
                                          PrototypeT, SignatureT, envs,
                                          expressions, FunctionDefinitionT, HinputsT
                                          (holds &'s refs to FunctionA/StructA
                                          directly — no re-interning)
```

At the parser->postparser boundary, `StrI<'p>` values are re-interned into `'s` via `scout_arena.intern_str(name_p.as_str())`. Coordinates are similarly re-interned. At the scout->typing boundary there is **no re-interning** — typing holds `&'s` references directly and adds its own interned `'t` data alongside.

## Key Invariant

**Nothing in an arena is ever mutated.** Arenas are append-only-immutable: once a value is allocated, no field, slice element, or interior cell ever changes again. This is absolute, not a guideline.

If a value needs to mutate, it does *not* go in the arena. Three alternatives, depending on the lifecycle:

- **Box pattern.** Vec-backed mutation buffer outside the arena, with `snapshot(interner)` to freeze the current state into the arena as a fresh `&'t T`. Used by `NodeEnvironmentBox` and `TemplatasStoreBuilder`.
- **Non-arena container.** Owns its own heap collections, lives outside the arena entirely, dies at pass end. Used by `CompilerOutputs`.
- **By-value.** Owned `T` on the stack, mutated freely, then moved into final position (often as the input to an arena-allocating call).

Data is built using one of these mutable forms, then frozen into the arena. See `docs/usage/arenas.md` for the pattern.

## Transient vs Permanent

Interned types (runes, names, imprecise names) have two forms: a **transient Val** used to check "does this already exist?" and a **permanent** arena-allocated canonical form. Transient Vals live on the stack and are discarded on an intern hit, or promoted to permanent on a miss. A Val struct defines an **internable unit** — the boundary around everything that gets interned together. Slices and collections inside a Val are parts of that unit, not independently interned. When a transient Val contains a slice, the slice borrows from a stack temporary (lifetime `'tmp`) so that no arena space is wasted on hits. See @DSAUIMZ for the full pattern.
