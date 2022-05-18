

# Kind vs Coord, Implicit and Explicit (KVCIE)

## Approach A: Kinds Only When Explicitly Asked For

If we just default things to coords and require explicitly specifying kind, we run into problems here:
```
fn blah(ship &IShip) {
  ship.as<Firefly>();
}
```
because as<> expects a kind. So this would instead need to be like...
```
fn blah(ship &IShip) {
  ship.as<kind(Firefly)>();
}
```

We'd have a similar problem with impls:
```
impl<T> IShip<T> for Firefly<T>;
```
wouldn't work. We'd have to say:
```
impl<T> kind(IShip<T>) for kind(Firefly<T>);
```


## Approach B: Don't Assume Type For All Lookups

Let's say we have a rule that says `X = int`. We don't assume that X is a coord just because int is a kind, we wait until we know what the type of X is from other means.

In other words, we tell the solver that LookupSRs can't solve their own types with lookups.

Of course, that runs into some problems, for example when we say
```
X Ref = &Moo<int>
```
because we don't know the type of Moo. And, even knowing that the result is a coord, and the parameter is a coord, we still can't guess what Moo is because it could still be either a:

 * (Coord) -> Coord
 * (Coord) -> Kind

So we have kind of a deadlock.

So, this doesn't work.

Of course, this entire thing is silly, because whereas `X = int` is ambiguous, `X = Moo` is not, we already know Moo's type is a (Coord) -> Kind and that's never interpreted as anything else. Kinds can be interpreted as coords, but (Coord)->Kind is only ever interpreted as that.

## Approach C: Don't Assume Type For Kind Lookups

So instead, let's say we can only not solve kind lookups' types.

We'll peek at the type beforehand. If it's not a kind lookup, then we add that to the conclusions before the solver even runs.

We could bake that behavior into the solver later if we want, but for now this preprocessing works nicely.

**We'll go with C.**

Possible future improvements:

 * If there's a type that we still can't figure out, perhaps try default assuming it's a Coord.
 * Maybe combine KindTemplataType and CoordTemplataType, or perhaps equivalently, only deal with CoordTemplataType in the solvers... and a coord might have a list of modifiers to it, possibly none.


# Must Forward Declare Before Rules Evaluated (MFDBRE)

Let's say we have this struct:
```
struct MyList<Ref#T> imm {
  value: #T;
  next: MyOption<MyList<#T>>;
}
```
keep in mind, that's like this under the hood:
```
struct MyList<Ref#T> #M
where(#N = MyOption<MyList<#T>>, #M = imm)
{
  value: #T;
  next: #N;
}
```
which means that while we're evaluating `MyList<#T>`'s rules, we're trying to look up the templata for `MyList<#T>`. Recursion!

Luckily, the classic forward-declaring solution works here.

However, it used to be the case that when forward declaring, we would also declare the inner environment and the mutability, but we can't do that anymore, because we don't yet know the mutability (that's determined by rules), and the inner environment is only the "inner" environment because it has all the templatas for the runes for this particular struct.

Since those things can only happen once we have the runes, we must declare them after we have the runes.

It gets more complicated though. We might need the mutability of the incoming #T. In this case, we'll need the scout to predict it.


# Recursive Types Must Have Types Predicted (RTMHTP)

Previously: Recursive Types Must Have Types Predicted In Scout (RTMHTPS)

We ran into an infinite loop in this case: 
```
struct MyList<Ref#T> imm {
  value: #T;
  next: MyOption<MyList<#T>>;
}
```
...because while figuring out the type for MyList, it needed to check the type of MyList<#T> (struct or template?) against what MyOption expected (a kind).

To solve this, we're enforcing that if there's any recursive type shenanigans going on, we should know all the types ahead of time. Kind of like how scala requires a return type for recursive functions, we require the rune types for recursive structs.

This is done in the Scout, as part of the predictor.

Templar will use those types to disambiguate template arg templatas during calls.



# Do We Need Astronomer?

Even if we specify all the identifying runes, we likely still need astronomer because we need to figure out the intermediate runes.

```
struct Vec<N int, T ref>
where F Prot = Prot["__drop", Refs(&T), void] {
  values [#N]T;
}
```

there's some intermediate runes like the &T

```
func moo<T>(x Moo<int>) { ... }
```

We can't know what type int will be until we know what type moo is, but we don't know that yet.

what we can do however is get all the identifying runes to be typed, so that we dont reentrantly do any astronoming, its all just linear.

maybe we can do this:

 * just before the solver kicks in?
 * right before we add it to the overload index?
 * during the templar solver? (probably not, needless)

