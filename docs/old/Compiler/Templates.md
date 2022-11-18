Templates can be thought of as doing two things:

-   Constraints

-   Capturing

This is quite familiar: it\'s pattern matching!

Let\'s bring in some concepts from pattern matching:

-   If Let, which takes in something and requires it be a certain shape,
    > and also gives us the opportunity to capture some variables

-   If, which requires a certain arbitrary condition

We\'ll also have extractors built-in:

-   callable: takes something callable (closures, function pointers, and
    > our std::function-ish thing). extracts the return type and
    > parameters.

-   template: requires the thing be a templated citizen. extracts the
    > template parameters

-   coord: requires the thing be a coord. extracts the ownership and
    > referend.

Given that, there are five parts of a Function1 involved in templating:

-   templateDeclarations: Just a Set\[String\], which has all the names
    > of everything that is figured out by the template system. If a
    > name isn\'t in here, it will be looked up in the environment.

-   templateParams: Things that can be specified in code. In
    > forEach:println(myList), we\'re specifying println. They\'re also
    > extracted by the \"template\" extractor.

-   templateIfLets: Constrain and capture things.

-   templateIfs: Just constrain things.

-   function params: All have Coord rules.

fn forEach

templateDeclarations(F, T, C)

templateParams(F) // things specifiable by call, and able to be
extracted by iflet

templateIfLets(

if let callable(T):Void = F,

if let template(T) = C)

templateIfs(

if C:T \<: ImmIterable:T)

params(list: &C:T, func: F)

fn forEach

templateDeclarations(T)

templateParams(F) // things specifiable by call, and able to be
extracted by iflet

params(list: &List:T, func: (T):Void)

fn idestructor

templateDeclarations(S, I)

templateParams()

templateIfLets(

if let coord(ownership = (Own\|Shared), referend = Struct)

if let Interface = I)

(this: S for I)

# Interface Methods Can Be Templates (IMCBT)

This interface is templated:

> interface IFunction1\<M, P1, R\> {
>
> fn \_\_call(virtual self &!IFunction1\<M, P1, R\>, p1 P1) R;
>
> }

M, P1, and R are all runes.

This is because we might want to call \_\_call from the outside with an
e.g. IFunction1\<mut, int, int\> and we want it to solve for M, P1, and
R.

So, \_\_call is a template.

Sometimes, we might still eagerly evaluate these, outside of a call. For
example, if we just instantiated an IFunction1\<mut, int, int\>, we\'ll
be eagerly evaluating the \_\_call for it.

In that moment, we know what M, P1, and R are, and just want to evaluate
the method. We can look up M, P1, and R in the environment.
