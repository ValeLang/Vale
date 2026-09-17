# Plan: Postparser Environments — Clone-Heavy to Rc

## Problem

The postparser environments (`EnvironmentS`, `FunctionEnvironmentS`, `IEnvironmentS`, `StackFrame`) are currently plain owned structs with `#[derive(Clone)]`. They are cloned **~70+ times** across the scouting pass — every call to `scout_expression`, `translate_rulex`, `translate_templex`, `scout_block`, etc. clones the environment or stack frame.

These clones are deep: `StackFrame` contains `FunctionEnvironmentS` (with a `Vec<IRuneS>`) and `Option<Box<StackFrame>>` (a recursive parent chain). Each clone copies the entire chain.

The environments are **never mutated after construction**. They follow a strict build-then-freeze pattern:
1. Construct the env with all runes, parent chain, etc.
2. Pass it around (currently via clone) to all scouting functions
3. Discard it when the scouting pass is done — environments are NOT stored in output AST nodes (`FunctionS`, `StructS`, `InterfaceS` have no env field)

`StackFrame` has a `plus()` method that returns a new `StackFrame` with additional locals, but this is a functional update — it creates a new value, never mutates the old one.

## Solution: Wrap in `Rc`

Since environments are immutable after construction, we can wrap them in `Rc` to make "cloning" a cheap refcount bump.

### Step 1: Change the types

**Before:**
```rust
#[derive(Clone, Debug, PartialEq)]
pub struct EnvironmentS<'s> {
  pub file: &'s FileCoordinate<'s>,
  pub parent_env: Option<Box<EnvironmentS<'s>>>,
  pub name: INameS<'s>,
  pub user_declared_runes: Vec<IRuneS<'s>>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct FunctionEnvironmentS<'s> {
  pub file: &'s FileCoordinate<'s>,
  pub name: IFunctionDeclarationNameS<'s>,
  pub parent_env: Option<Box<IEnvironmentS<'s>>>,
  pub declared_runes: Vec<IRuneS<'s>>,
  pub num_explicit_params: i32,
  pub is_interface_internal_method: bool,
}

#[derive(Clone, Debug, PartialEq)]
pub enum IEnvironmentS<'s> {
  Environment(EnvironmentS<'s>),
  FunctionEnvironment(FunctionEnvironmentS<'s>),
}

#[derive(Clone, Debug, PartialEq)]
pub struct StackFrame<'s> {
  pub file: &'s FileCoordinate<'s>,
  pub name: IFunctionDeclarationNameS<'s>,
  pub parent_env: FunctionEnvironmentS<'s>,
  pub maybe_parent: Option<Box<StackFrame<'s>>>,
  pub context_region: IRuneS<'s>,
  pub pure_height: i32,
  pub locals: VariableDeclarations<'s>,
}
```

**After:**
```rust
#[derive(Clone, Debug, PartialEq)]
pub struct EnvironmentS<'s> {
  pub file: &'s FileCoordinate<'s>,
  pub parent_env: Option<Rc<EnvironmentS<'s>>>,
  pub name: INameS<'s>,
  pub user_declared_runes: Vec<IRuneS<'s>>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct FunctionEnvironmentS<'s> {
  pub file: &'s FileCoordinate<'s>,
  pub name: IFunctionDeclarationNameS<'s>,
  pub parent_env: Option<Rc<IEnvironmentS<'s>>>,
  pub declared_runes: Vec<IRuneS<'s>>,
  pub num_explicit_params: i32,
  pub is_interface_internal_method: bool,
}

#[derive(Clone, Debug, PartialEq)]
pub enum IEnvironmentS<'s> {
  Environment(EnvironmentS<'s>),
  FunctionEnvironment(FunctionEnvironmentS<'s>),
}

#[derive(Clone, Debug)]
pub struct StackFrame<'s> {
  pub file: &'s FileCoordinate<'s>,
  pub name: IFunctionDeclarationNameS<'s>,
  pub parent_env: Rc<FunctionEnvironmentS<'s>>,
  pub maybe_parent: Option<Rc<StackFrame<'s>>>,
  pub context_region: IRuneS<'s>,
  pub pure_height: i32,
  pub locals: VariableDeclarations<'s>,
}
```

Key changes:
- `EnvironmentS.parent_env`: `Option<Box<...>>` → `Option<Rc<...>>`
- `FunctionEnvironmentS.parent_env`: `Option<Box<...>>` → `Option<Rc<...>>`
- `StackFrame.parent_env`: owned `FunctionEnvironmentS` → `Rc<FunctionEnvironmentS>`
- `StackFrame.maybe_parent`: `Option<Box<StackFrame>>` → `Option<Rc<StackFrame>>`

Note: `StackFrame` loses `PartialEq` since `Rc` compares by value (which is fine — equality is never used on `StackFrame` anywhere in the codebase).

### Step 2: Change construction sites

Construction sites that create parent chains need to wrap in `Rc` instead of `Box`:

**`EnvironmentS` parent chain — `post_parser.rs`:**
Where struct/interface envs are created with `parent_env: Some(Box::new(parent))`, change to `parent_env: Some(Rc::new(parent))`.

**`FunctionEnvironmentS` parent chain — `function_scout.rs`:**
Where function envs are created with `parent_env: Some(Box::new(IEnvironmentS::...))`, change to `parent_env: Some(Rc::new(IEnvironmentS::...))`.

**`StackFrame` creation — `function_scout.rs`, `expression_scout.rs`:**
Where `StackFrame` is created with `parent_env: function_env.clone()`, change to `parent_env: Rc::new(function_env)` (at the point where the function env is finalized).
Where `maybe_parent: Some(Box::new(parent_stack_frame))`, change to `maybe_parent: Some(Rc::new(parent_stack_frame))` — but only if the parent is no longer needed by the caller. If the caller still uses the parent after creating the child, the `Rc::clone()` is cheap.

### Step 3: Change clone sites (the big win)

All ~70+ `.clone()` calls on environments and stack frames become **cheap `Rc::clone()` calls** (just bumps a refcount). The call sites don't change syntax — `stack_frame.clone()` still works, but now it clones the `Rc` (refcount bump) instead of deep-copying the entire struct chain.

The only call sites that need actual changes are ones that access the inner data through the `Rc`:
- `stack_frame.parent_env.declared_runes` stays the same (auto-deref through `Rc`)
- `stack_frame.parent_env.clone()` becomes cheap (cloning the `Rc`)

### Step 4: Change `plus()`

`StackFrame::plus()` currently clones all fields to return a new `StackFrame` with updated locals. With `Rc`:

```rust
pub fn plus(&self, new_vars: &VariableDeclarations<'s>) -> StackFrame<'s> {
  StackFrame {
    file: self.file,
    name: self.name.clone(),           // IFunctionDeclarationNameS is small (enum of &'srefs)
    parent_env: self.parent_env.clone(), // Rc clone = refcount bump
    maybe_parent: self.maybe_parent.clone(), // Rc clone = refcount bump
    context_region: self.context_region.clone(), // IRuneS is small
    pure_height: self.pure_height,
    locals: self.locals.plus_plus(new_vars),
  }
}
```

The expensive clones (parent env, parent stack frame chain) become refcount bumps.

### Step 5: Change `new_block` and child stack frame creation

In `expression_scout.rs`, `new_block` creates child stack frames. Currently:
```rust
let maybe_parent = parent_stack_frame.clone().map(Box::new);
```

With Rc:
```rust
let maybe_parent = parent_stack_frame.map(Rc::new);
// or if parent_stack_frame is already Rc:
let maybe_parent = parent_stack_frame.clone(); // if it's Option<Rc<StackFrame>>
```

The exact change depends on whether `new_block` receives the parent as an owned `StackFrame` or `Rc<StackFrame>`. Since `new_block` is typically called from `scout_block` which receives the stack frame and wants to keep using it, the cleanest approach is:
- `scout_block` receives `stack_frame: StackFrame<'s>` (owned)
- Wraps it in `Rc` at the start: `let stack_frame = Rc::new(stack_frame);`
- Passes `Rc::clone(&stack_frame)` to all sub-calls
- `new_block` receives `parent_stack_frame: Option<Rc<StackFrame<'s>>>`

Alternatively, keep passing stack frames by value into `scout_block` and only wrap in `Rc` for child stack frames. Either approach works.

## Files to change

1. **`post_parser.rs`** — `EnvironmentS`, `FunctionEnvironmentS`, `IEnvironmentS`, `StackFrame` struct definitions. `EnvironmentS::child()` method. All construction sites for struct/interface/impl envs.
2. **`function_scout.rs`** — `FunctionEnvironmentS` construction, `StackFrame` construction, `scout_lambda` parent chain.
3. **`expression_scout.rs`** — `new_block` signature, child stack frame creation, all `stack_frame.clone()` calls (these just become cheap).
4. **`loop_post_parser.rs`** — `parent_env.clone()` calls (become cheap).
5. **`rules/rule_scout.rs`** — `env.clone()` calls (become cheap).
6. **`rules/templex_scout.rs`** — `env.clone()` calls (become cheap).
7. **`patterns/pattern_scout.rs`** — `stack_frame.clone()` calls (become cheap).

## What NOT to change

- `VariableDeclarations` — this is small and only contains a `Vec` of variable names. Keep it owned/cloned.
- `IRuneS`, `INameS`, `IFunctionDeclarationNameS` — these are small enum wrappers around `&'a` arena refs. Cloning is already cheap (copies a tagged pointer).
- The output AST types (`FunctionS`, `StructS`, etc.) — environments don't appear in these at all.

## Verification

After the change:
- `cargo build --lib` must pass
- All existing tests must pass
- No behavior changes — this is a pure performance optimization
- Environments are never mutated through an `Rc` (no `Rc::get_mut` or `Rc::make_mut` needed)
