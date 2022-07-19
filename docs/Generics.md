
# Some Rules Only Apply to Call Site or Definition (SROACSD)

This snippet is using a function bound:

```
func moo<X>(x X)
where func foo(int)void {
  ...
}
```

When we do a `func foo(int)void` rule, that's actually making three rules under the hood.

The first one is a `DefinitionFunc` rule, and it just generates a `Prototype` for its result rune, and puts it in the env. That way, the body can call that function. **This rule is used when compiling the function itself, it's not used at the call site.**

In the call site, instead we have a **Resolve** rule which looks in the current environment and grabs the actual `Prototype` that matches, and we have a **CallSiteFunc** rule which checks that its params and returns match what we expect. **These rules are used at the call site, but not used when compiling the function itself.**

In other words, some rules only apply to the call site, and some rules only apply when compiling the definition. There's some filtering that makes this happen.


# Some Rules are Hoisted Out of Default Param (SRHODP)

This snippet is using a **function bound generic parameter**:

```
func moo<X, func foo(int)void>(x X)
where  {
  ...
}
```

It's particularly nice because the call-site can hand in something that's not called `foo`.

This `func foo(int)void` generates three rules (see SROACSD):

 * DefinitionFunc, used when compiling definition, creates a prototype that can later be called.
 * Resolve, which looks in the current environment for a matching function.
 * CallSiteFunc, used when compiling call site, which makes sure the given prototype has the right parameters and returns.

If the call-site wants to pass in their own prototype, then they *don't* want that Resolve rule in there. So, we need Resolve only be a *default* expression, only run when the user doesn't specify something.

But we don't want the other two rules (DefinitionFunc, CallSiteFunc) to be defaults, we want those to always be there. So, we'll hoist them out of the generic parameter's "default rules" and into the function's main rules.



# Call Site Solving Needs Caller Env (CSSNCE)

We have functions like:

```
func xoo<X>(x X) void
where func drop(X)void
{
  zoo(x);
}

func zoo<Z>(z Z) void
where func drop(Z)void
{
  ...
}
```

We're trying to solve `zoo(x)`. `zoo` requires a `func drop(Z)void` but that only exists in `xoo`'s environment.

So, the callee needs access to the caller's environment.



# Default Parameters Can Only Depend on Other Default Parameters (DPCODODP)

We had:

```
struct Functor1<F Prot = func(P1)R> imm
where P1 Ref, R Ref { }
```

But when defining it it had no idea what to do. It should have generated a DefinitionCallSR which would produce the right prototype, but it didn't know what coords to use for its param and return.

I believe this means that we should have had some placeholders for the P1 and R.

That then means that P1 and R should have been generic params themselves.

So, it should be like this:

```
struct Functor1<P1 Ref, R Ref, F Prot = func(P1)R> imm { }
```

And then we should make placeholders for P1 and R, and let the 3rd param's DefinitionCallSR create a prototype using those two. Then things would work.



# Don't Add Placeholders for Generic Params with Defaults (DAPGPD)

Having a default parameter implies that the call site has enough information such that with this default value there will be enough to solve the entire function. So for any information the call site can provide, we just need an equivalent at definition time.

If a default param says to look up a func(int)void, then in definition time instead of populating a placeholder we should populate a rule saying that it's a func(int)void.





im also thinking DefinitionCallSR should be in the default argument spot, and CallSiteCallSR should just always be present.


theres three possibilities if we have a default argument:

- calling, but supplied no argument:
   - use ResolveSR if its there
   - hope that it can be figured out from other things, such as the normal arguments
- calling, supplied an argument: want to use CallSiteFuncSR
- defining: want to use DefinitionFuncSR




# Require Rune for Function Bound?

In this example:

```
func moo(i int, b bool) str { return "hello"; }

exported func main() str
where func moo(int, bool)str
{
  return moo(5, true);
}
```

It's ambiguous which moo we're referring to. Which one should we use?

We could say that since it's ambiguous, they should stuff it into a rune and then call the rune directly... but this feels like it would be fragile.








Variable SSA vs Final SSA

It might be difficult to have parameterized variability.

when we make SSAs, they need to have a variability, right there in the kind.

we could have a PlaceholderVariability? and then not allow sets on it.


we'll likely run into the same problem with the size. StaticSizedArrayT holds an integer, not really a placeholder.


we could have an ITemplata[+ITemplataType] perhaps?
nope. theres more than just 9 templata types, theyre infinite because pack and template.
cant constrain something like that.
well, we can at least constrain on those top 9 types. should be good enough.


but wait, we have a problem now. coords need to be able to have placeholder kinds in them.
 * could we make locals contain itemplatas?
 * struct fields too maybe?
 * function parameters?
anything.
at this point, theres not much difference between KindT and a Placeholder[KindTemplataType].

the basic problem is that basically templatas can be (and contain) coords and kinds, and kinds can contain templatas now.

well they arent really templatas. theyre Variability|Placeholder, Ownership|Placeholder, Coord|Placeholder. also, structs have a list of templatas already.

we need to associate envs with certain templatas then. wtf?



# A: PlaceholderKind for kind and coord, PlaceholderOr[ITemplata] for all else

Locals, members, and parameters would need to be this.

perhaps this could be a good stepping stone to full rules?

yes, and coord and kind happen to already kind of do this on their own, which is nice.

this is a good stepping stone to full rules. after this, we:

 * replace all of these with IRuneT, and get rid of PlaceholderKind.
 * have a table of values, basically ITemplata. look up that IRuneT in that table. if its not present, its a placeholder. if its there, its a value


# B: ITemplata[+T <: ITemplataType]

Locals, members, parameters would need to change to this. everything would.

it could be a stepping stone to full rules. we'd do the same thing as A.

this is just more intensive right now.


# C: PlaceholderKind for kind and coord, ITemplata[+T <: ITemplataType] for all else

This is honestly equivalent to A.

Not a fan because there's kind of an overlap, `ITemplata[CoordTemplataType]` and `ITemplata[KindTemplataType]` don't make sense.


# All-in on Runes

we could have everything be a rune, and all constants would be in the env.
in fact, that would make things closer to the rule system.
thats an interesting thought.

oddly enough, we're already kind of there. post-scout, everything is already runes. presumably iexpression would become a bunch of rules? i suppose it doesnt really need to.

pre-solving would basically just try and figure enough out to resolve all overloads and... what else


but itd be nice to not have to embark on this particular journey yet. perhaps there's an easy first step that will unblock regions?



steps:
 * ??
 * Make structref itemplatas, interfaceref itemplatas, locals, members, parameters, returns all be runes. the current ITemplata will live on in hammer definitely


an itemplata should be like a rune, that may or may not have a value assigned.
so, every itemplata should be either a placeholder or an ITemplataValue or something.

could ITemplataValues have runes? yes.


what does an env follow? it seems they can follow placeholders. no, it shouldnt be associated with a particular usage of the type, should be associated with the type itself. in fact, not really the type, but instead the template.

we dont really need the env to follow the placeholder, because those functions will be in scope anyway.

in the future, we would like to resolve overloads with only partial data. so there should be some notion of a coord whose type isnt really known.

sounds like instead of coords flying around we want ITemplata[CoordTemplataType]s flying around.

but when we figure out a rune, wed have to go in and update all the rune itemplatas to be value templatas. that would be annoying. the reason this works for placeholders is that we know they wont be resolved. so we need a table eventually. but perhaps not while we're just doing placeholders.


# Hardcode Zero, empty string, Final, mutable?

hardcoding mutable might be weird.

it feels like this will head straight into trouble.


# IntTemplata contains Placeholder|int, BoolTemplata contains Placeholder|bool etc.

Just like we're doing with kind and coord and stuff.

perhaps this could be a good stepping stone to full rules?

after this, an ITemplata would basically become an IRuneS. it might point to a rule that has a constant or not. after pre-solving, it might have an inference or not.

this would evolve into IntTemplata becoming IntRuneT, BoolTemplata becoming BoolRuneT, etc.


# PlaceholderInt, PlaceholderKind, etc. all subclasses of ITemplata

Locals, members, and parameters would need to be ITemplata


# IGenericData: VariableGenericData or LiteralGenericData

Locals, members, and parameters would need to be this.


# PlaceholderT kind, ITemplata/PlaceholderTemplata subclasses for everything else?

means locals, members, parameters wouldnt need to do anything different.

but StaticSizedArrayT would need to contain e.g. ITemplata[OwnershipTemplata]

