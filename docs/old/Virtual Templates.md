A virtual template is a function in an interface that has template
parameters, for example:

> interface IntOption {
>
> fn map\<#T, #F\>(this, func: &#F \< func(Int)#T) #T;
>
> }

Virtual templates are the underlying machinery for the mat expression,
the map function, the flatMap function, and others.

See Scala\'s Option\'s
[[map]{.underline}](https://www.scala-lang.org/api/current/scala/Option.html#map%5BB%5D(f:A=%3EB):Option%5BB%5D)
function, it\'s a virtual template. Rust\'s Option\'s
[[map]{.underline}](https://doc.rust-lang.org/std/option/enum.Option.html#method.map)
function uses a match statement under the hood, which itself is doing a
sort of virtual template under the hood, just not in a way exposed to
the user.

[[Background]{.underline}](#background)

[[Virtual Templates]{.underline}](#virtual-templates)

[[Optimizing: Inline Virtuals]{.underline}](#optimizing-inline-virtuals)

[[Optimizing: Interface-Bounded Template
Parameter]{.underline}](#optimizing-interface-bounded-template-parameter)

[[More Sorcery]{.underline}](#more-sorcery)

[[Another Example: map]{.underline}](#another-example-map)

## Background

Let\'s say we had a sealed interface with some subclasses:

> sealed interface IUnit { }
>
> struct Marine { name: Str; }
>
> struct Firebat { fuel: Int; armor: Int; }

Since it\'s sealed, we can use the mat statement on it:

> fn main() {
>
> let unit: IUnit = \...;
>
> let message =
>
> mat &unit {
>
> :Marine(name) = \"You found a marine named: \" + name;
>
> :Firebat = \"You found a firebat!\";
>
> };
>
> \...
>
> }

under the hood, this is because the sealed keyword generated **visitor**
code for IUnit and its subclasses, shown below. Ignore for now the fact
that visit and runIfMarine and runIfFirebat all return Str, that will be
corrected shortly.

> interface IUnit {
>
> fn visit(this, visitor: &IUnitVisitor) Str;
>
> }
>
> interface IUnitVisitor {
>
> fn runIfMarine(this, marine: &Marine) Str;
>
> fn runIfFirebat(this, firebat: &Firebat) Str;
>
> }
>
> struct Marine {
>
> name: Str;
>
> impl for IUnit {
>
> fn visit(this overrides, visitor: &IUnitVisitor) Str {
>
> visitor.runIfMarine(this)
>
> }
>
> }
>
> }
>
> struct Firebat {
>
> fuel: Int;
>
> armor: Int;
>
> impl for IUnit {
>
> fn visit(this overrides, visitor: &IUnitVisitor) Str {
>
> visitor.runIfFirebat(this)
>
> }
>
> }
>
> }

and our match statement was transformed into a visitor which called it:

> fn main() {
>
> let unit: IUnit = \...;
>
> let message =
>
> IUnitVisitor{
>
> fn runIfMarine(this overrides, :Marine(name)) {
>
> \"You found a marine named: \" + name
>
> }
>
> fn runIfFirebat(this overrides, :Firebat(name)) {
>
> \"You found a firebat!\"
>
> }
>
> }.visit(unit);
>
> \...
>
> }

## Virtual Templates

Now, let\'s talk about how these functions are returning Str. That may
work here, but what if someone makes a mat statement that returns an
Int? Or a Bool?

The answer is to make our virtual visit method a template; to make it a
**virtual template**:

> interface IUnit {
>
> fn visit\<#T\>(this, visitor: &IUnitVisitor\<#T\>) #T;
>
> }
>
> interface IUnitVisitor\<#T\> {
>
> fn runIfMarine(this, marine: &Marine) #T;
>
> fn runIfFirebat(this, firebat: &Firebat) #T;
>
> }

This means that every time someone calls visit with a new type, under
the hood, *another* method is added to IUnit\'s itable! That in turn
means that every subclass of IUnit, in this case Marine and Firebat,
must both make sure that they have overrides for those new itable
entries. To do this, they can make their overrides templates as well:

> struct Marine {
>
> name: Str;
>
> impl for IUnit {
>
> fn visit\<#T\>(this overrides, visitor: &IUnitVisitor\<#T\>) #T {
>
> visitor.runIfMarine(this)
>
> }
>
> }
>
> }

## Optimizing: Inline Virtuals

By moving all the mat expression\'s cases into an anonymous IUnitVisitor
subclass like that, we\'ve introduced a virtual dispatch, which can\'t
be inlined and optimized, and since the instructions aren\'t in main
anymore, they aren\'t prefetched into the instruction cache.

We can fix this by inlining the function. This can only be done for
sealed classes, so we\'ll also add sealed.

> sealed interface IUnit {
>
> inline fn visit\<#T\>(this, visitor: &IUnitVisitor\<#T\>) #T;
>
> }

When a method of a sealed interface is inlined, that means there is no
entry added to the ITable. Instead, the code that\'s generated looks
something like this:

> inline fn visit\<#T\>(this, visitor: &IUnitVisitor\<#T\>) #T {
>
> = if (this isa Marine) {
>
> (this asa Marine).visit(visitor)
>
> } else if (this isa Firebat) {
>
> (this asa Firebat).visit(visitor)
>
> } else { panic() }
>
> }

Now, no entry is added to IUnit\'s itable, and the compiler can inline
this new version of the method easily.

## Optimizing: Interface-Bounded Template Parameter

We can make the visit method inline, and make the visitor\'s type a
template parameter:

> interface IUnit {
>
> inline fn visit\<#T, #V\>(this, visitor: &#V \< IUnitVisitor\<#T\>)
> #T;
>
> }
>
> struct Marine {
>
> name: Str;
>
> impl for IUnit {
>
> inline fn visit\<#T, #V\>(
>
> this overrides,
>
> visitor: &#V \< IUnitVisitor\<#T\>) #T {
>
> visitor.runIfMarine(this)
>
> }
>
> }
>
> }

Now, the compiler can inline everything, and Vale\'s mat expression
becomes just syntactical sugar.

## More Sorcery

It\'s odd that the generated code contains functions named runIfMarine
and runIfFirebat:

> interface IUnitVisitor\<#T\> {
>
> fn runIfMarine(this, marine: &Marine) #T;
>
> fn runIfFirebat(this, firebat: &Firebat) #T;
>
> }

This was a simplification; we actually use more virtual templates under
the hood. The IUnitVisitor interface actually looks like this:

> interface IUnitVisitor\<#T\> {
>
> fn runIf\<#C\>(this, subclass: &#C) #T;
>
> }

and the Marine subclass looks like this:

> struct Marine {
>
> name: Str;
>
> impl for IUnit {
>
> inline fn visit\<#T, #V\>(
>
> this overrides,
>
> visitor: &#V \< IUnitVisitor\<#T\>) #T {
>
> visitor.runIf\<Marine\>(this)
>
> }
>
> }
>
> }

and the visitor subclass looks like this:

> fn main() {
>
> let unit: IUnit = \...;
>
> let message =
>
> IUnitVisitor{
>
> fn runIf(this impl, :Marine(name)) {
>
> \"You found a marine named: \" + name
>
> }
>
> fn runIf(this impl, :Firebat(name)) {
>
> \"You found a firebat!\"
>
> }
>
> }.visit(unit);
>
> \...
>
> }

This shows how virtual templates are quite powerful and flexible.

## Another Example: map

Option\'s map is a virtual template function. For clarity, we\'ll first
illustrate with a simple IntOption class:

> sealed interface IntOption {
>
> fn map\<#B\>(func: fn(Int)#B) IOption\<#B\>;
>
> }
>
> struct IntNone {
>
> impl for IntOption {
>
> fn map\<#B\>(this overrides, func: fn(Int)#B) IOption\<#B\> {
>
> IntNone();
>
> }
>
> }
>
> }
>
> struct IntSome {
>
> val: Int;
>
> impl for IntOption {
>
> fn map\<#B\>(this overrides, func: fn(Int)#B) IOption\<#B\> {
>
> IntSome\<B\>(func(this.val));
>
> }
>
> }
>
> }

Now, for the IOption class, and with the above optimizations:

> sealed interface IOption\<#T\> {
>
> inline fn map(func: &#F \< fn(T)#Y) IOption\<#Y\>;
>
> }
>
> struct None\<#T\> {
>
> impl for IOption\<#T\> {
>
> inline fn map(this overrides, func: &#F \< fn(T)#Y) IOption\<#Y\> {
>
> None\<#Y\>();
>
> }
>
> }
>
> }
>
> struct Some\<#T\> {
>
> val: #T;
>
> impl for IOption\<#T\> {
>
> inline fn map(this overrides, func: &#F \< fn(T)#Y) IOption\<#Y\> {
>
> IntSome\<Y\>(func(this.val));
>
> }
>
> }
>
> }
