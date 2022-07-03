simplest approach:

-   **MyThing**: owning reference. it can modify things.

-   **&MyThing**: borrow which can't modify. c++'s const

-   **&!MyThing**: borrow which can modify.

-   **&&** can be used wherever there's a & above, it means weak
    > pointer.

quite simple.

more complete approach:

-   **MyThing**: owning reference. it can modify things.

-   **&MyThing**: borrow which can't modify, and is guaranteed that
    > nobody else can modify. basically this is a lock.

-   **&!MyThing**: borrow which can't modify, but someone else might
    > modify. c++'s const.

-   **!&MyThing**: borrow which can modify, and nobody else can modify.

-   **!&!MyThing**: borrow which can modify, and everyone else can too.

-   **&&** can be used wherever there's a & above, it means weak
    > pointer.

however, the deep lock (the lone &) is an interesting creature, because
of these questions:

-   if we follow an owning reference, is the referend locked too?

-   if we follow a strong/weak reference, is the referend locked too?

three possibilities: yes/yes, yes/no, no/no.

we'd sometimes want yes/yes like in a superstructure where the entire
world in there is locked.

we'd probably sometimes want yes/no if a component and all of its
children are locked.

is there a way to make this some sort of custom logic thing? we can
return some sort of special reference type?

if we had custom reference types, we could make this work.

fn derefForMut:(O, M, F)(owner: &O, member:

fn derefForRead

fn &:(F, M)(base: &Base, member: &M) {

return Snapshot

}

fn myBorrowingFunc(marine: &Marine) { ... }

let m = Marine()

myBorrowingFunc(m)

the above calls like a move, but the function takes in a borrow. this
should be legal, and the call site should destroy it after the call is
complete.

however, of course, doing a lend into a function that takes in an owning
is a compiler error.

### Nullable

? means nullable.

let x! = 4;

let x!: ?Int = 4;

let x!: ?Int = null;

(Int\|Null) is a superclass of Int which is why that works.

Until then, we can use Some and None, those work nicely too.

### Struct References

TheThing - \"immutable\" - nobody can modify it

TheThing! - \"owning\" - modifiable, i own, i can modify it

&TheThing - \"const borrow\" - modifiable, i borrow, i cannot modify it

&TheThing! - modifiable, i borrow, i can modify it \"mutable borrow\"

&&TheThing - modifiable, i weak borrow it, i cannot modify it "const
weak"

&&TheThing! - modifiable, i weak borrow it, i can modify it "mutable
weak"

& and ! only ever apply to structs really, because everything else is
immutable.

let myBlark! = SomeThing!();

let myFoo = TheThing!();

if you want to pass a modifiable mutable reference to something:

doAllThings(&!myBlark!);

doAnyThing(&!myThing);

only ever makes sense to send myThing, &myThing, or &!myThing.

Alternative:

TheThing - \"immutable\" - nobody can modify it

!TheThing - \"owning\" - modifiable, i own, i can modify it

&TheThing - \"const borrow\" - modifiable, i borrow, i cannot modify it

&!TheThing - modifiable, i borrow, i can modify it \"mutable borrow\"

&&TheThing - modifiable, i weak borrow it, i cannot modify it "const
weak"

&&!TheThing - modifiable, i weak borrow it, i can modify it "mutable
weak"

let thing = MyClass!();
