Ultimate goal: to turn Metal (Vale\'s Intermediate Representation) into
a working .exe file.

Metal

Metal comes in three main parts:

-   High-level AST (Program, Struct, Interface, Function), defined in
    > [[ast.scala]{.underline}](https://github.com/Verdagon/Vale/blob/master/Metal/src/net/verdagon/vale/metal/ast.scala).

-   Types (Int, Str, Float, etc.), defined in
    > [[types.scala]{.underline}](https://github.com/Verdagon/Vale/blob/master/Metal/src/net/verdagon/vale/metal/types.scala).

-   Instructions (Call, If, Construct, etc.), defined in
    > [[instructions.scala]{.underline}](https://github.com/Verdagon/Vale/blob/master/Metal/src/net/verdagon/vale/metal/instructions.scala).

# High-Level AST

(Defined in
[[ast.scala]{.underline}](https://github.com/Verdagon/Vale/blob/master/Metal/src/net/verdagon/vale/metal/ast.scala))

## ProgramH

-   interfaces: List\<InterfaceDefinitionH\>

-   structs: List\<StructDefinitionH\>

-   externs: List\<PrototypeH\>\
    > The list of functions that the program expects will be available
    > at link-time.

-   functions: List\<FunctionH\>

## FullName and NamePart

A full name uniquely identifies a function, struct, or interface.

FullNameH

-   parts: List\<NamePartH\>

NamePartH

-   humanName: String

-   templateArgs: Option\<List\<ITemplataH\>\>

-   parameters: Option\<List\<Reference\>\>

-   codeLocation: Option\<CodeLocationH\>

For example, C++\'s std::map\<string, int\>::insert(const string& val)
would look like Vale\'s std:map\<Str, Int\>:insert(val Str) and have a
full name of:

> FullName(
>
> List(
>
> NamePart(\"std\", None, None, None),
>
> NamePart(
>
> \"map\",
>
> Some(
>
> List(
>
> ReferenceTemplata(Reference(Share, Str)),
>
> ReferenceTemplata(Reference(Share, Int)))),
>
> None,
>
> None),
>
> NamePart(
>
> \"insert\",
>
> None,
>
> Some(List(Reference(Share, Str))),
>
> None)))

One would think that the humanName and templateArgs are enough to
identify something, but we still need the parameters to disambiguate
overloads. Otherwise, the three different variants on
[[http://www.cplusplus.com/reference/map/map/insert/]{.underline}](http://www.cplusplus.com/reference/map/map/insert/)
would all have the same full name.

CodeLocationH

-   file: String

-   line: Int

-   char: Int

codeLocation is sometimes used for identifying lambdas, which all have
the same human name (\"\_\_lam\" or something).

## Function

FunctionH

-   prototype: PrototypeH\
    > All the information needed at a call-site to call the function.

-   block: BlockH\
    > Contains all the instructions

PrototypeH

-   fullName: FullNameH\
    > The full name that uniquely identifies the function.

-   params: List\<ReferenceH\>\
    > All the parameter types for the function. Note: This is slightly
    > redundant since the parameter types are also in the full name (to
    > disambiguate overloads) so maybe we should remove it? Not sure.

-   returnType: ReferenceH

## Struct and Interface

StructDefinitionH

-   fullName: FullNameH\
    > The full name that uniquely identifies the struct.

-   mutability: Mutability\
    > Whether the struct is mutable or not.\
    > Mutable structs will be eventually deallocated by a Destructure
    > instruction. Immutable structs are ref counted, though ones
    > smaller than 32b could eventually be inlined for a huge speedup.\
    > Immutable structs are deeply immutable; all their members are
    > immutable.\
    > Mutability is an enum, either Mutable or Immutable, see
    > [[types.scala]{.underline}](https://github.com/Verdagon/Vale/blob/master/Metal/src/net/verdagon/vale/metal/types.scala)
    > for more explanation.

-   edges: List\<EdgeH\>\
    > The list of interfaces that this struct implements, and the
    > vtables for all of them.

-   members: List\<StructMemberH\>

StructMemberH

-   name: String

-   variability: Variability\
    > Whether this field can be changed to point to something else.
    > Variability is an enum, either Varying or Final, see
    > [[types.scala]{.underline}](https://github.com/Verdagon/Vale/blob/master/Metal/src/net/verdagon/vale/metal/types.scala)
    > for some explanation.

-   type: ReferenceH

EdgeH

-   struct: FullNameH

-   interface: FullNameH

-   structPrototypesByInterfacePrototype: ListMap\<PrototypeH,
    > PrototypeH\>\
    > A map from the \"interface prototype\" to the \"struct
    > prototype\". In other words, a map from the abstract prototype to
    > the override prototype. In other other words, a map that would
    > look something like this:

    -   fn launch(**virtual this ISpaceship**, fuel Int) -\>\
        > fn launch(**this Firefly impl ISpaceship**, fuel Int)

    -   fn fire(**virtual this ISpaceship**, charges Int) -\>\
        > fn launch(**this Firefly impl ISpaceship**, charges Int)

> This is an ordered map (though, we could just get the order from the
> InterfaceDefinitionH\'s prototypes field).

An EdgeH defines the relationship between a struct and an interface.

InterfaceDefinitionH

-   fullName: FullNameH\
    > The full name that uniquely identifies the interface.

-   mutability: Mutability\
    > Mutability is an enum, either Mutable or Immutable, see
    > [[types.scala]{.underline}](https://github.com/Verdagon/Vale/blob/master/Metal/src/net/verdagon/vale/metal/types.scala)
    > for more explanation.

-   superInterfaces: List\<FullNameH\>\
    > All of the interfaces that this interface implements.\
    > Note: this will probably someday be changed to a List\<EdgeH\>
    > instead.

-   prototypes: List\<PrototypeH\>\
    > All of the functions for this interface.

# Types

See
[[types.scala]{.underline}](https://github.com/Verdagon/Vale/blob/master/Metal/src/net/verdagon/vale/metal/types.scala)
which is documented pretty thoroughly.

# Instructions

See
[[instructions.scala]{.underline}](https://github.com/Verdagon/Vale/blob/master/Metal/src/net/verdagon/vale/metal/instructions.scala)
which is documented pretty thoroughly. We\'ll focus here on the
translations from those to LLVM.

# Step 1: Hello Integer

This C++ program:

> int main() {
>
> return 42;
>
> }

in Vale would be:

> fn main() {
>
> 42
>
> }

which the vale compiler
([[vale.dev/valestrom0.0.1.jar]{.underline}](http://vale.dev/valestrom0.0.1.jar))
would compile to a file containing this Metal:
[[https://pastebin.com/raw/TKGpsQ9k]{.underline}](https://pastebin.com/raw/TKGpsQ9k)

which would result in llvm that looks like this:

> define i32 \@main() {
>
> ret i32 7
>
> }

The things like prototype and resultType are there to make the backend
easier, because they seem like they would be useful, and as an effort to
minimize the amount of logic needed in the backend. If there\'s an
information needed in the backend, we should consider having that
pre-computed by the compiler and supplied as part of the IR, because
other backends (JVM especially) will likely require the same
information.

# Step 2: Adding

This C++ program:

> int main() {
>
> return 4 + 5;
>
> }

in Vale would be:

> fn main() {
>
> 4 + 5
>
> }

which would produce this Metal:
[[https://pastebin.com/raw/kVsVss15]{.underline}](https://pastebin.com/raw/kVsVss15)

This program has a **Call**, which calls a function with the registers
it pulls from the stack in reverse order.

Vale assumes the backend provides a function named \_\_addIntInt which
will do addition for it. There will be many such functions in the future
(\_\_print, \_\_castFloatInt, etc). The backend can either provide that
function, or it can replace it on-the-fly with a LLVM add instruction
(we\'ll want that eventually, but it could be considered an optimization
so it can be deprioritized).

# Step 3+

By the time we finish step 2, the compiler will be producing cleaner
output which we can use to do the next steps.

To do that, the frontend needs to:

-   Do tree-shaking; not produce functions/structs/interfaces which
    > aren\'t used indirectly by main.

-   Stop producing so much noise before the actual Metal output.
