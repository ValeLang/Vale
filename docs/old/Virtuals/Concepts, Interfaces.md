class HashMap\<KeyThing K, ValueThing V\> {

Array\<\[KeyThing, ValueThing\]\> arr;

}

interface ToStringable {

fn toString() : Str;

}

ts is a fat pointer. the caller can conjure up that itable whenever it
wants. the ability to conjure an itable at runtime is another form of
polymorphism, another way to call generic functions, without needing a
template instantiation. that's cool af.

print(ts :&ToStringable) {

let str = ts.toString();

str.chars().foldLeft(stdout, {\_.output(\_)});

}

struct MyStringyThing {

implements ToStringable;

fn toString() : Str = "i am stringy";

}

struct MyWeirdThing {

fn toString() : Str = "i am weird";

}

let myStringyThing = MyStringyThing();

print(&myStringyThing);

let myWeirdThing = MyWeirdThing();

print(conform &myWeirdThing);

for now, lets accomplish this ability this way:

-   there's no distinction between a concept, trait, interface... all
    > just one thing. all just an interface.

-   a fat pointer has two things: the value, and the itable. fat
    > pointers can be used for optimization, or they can be used for
    > this new ability, keep reading.

-   if the argument implements the interface, it just goes in cleanly,
    > such as print(&myStringyThing) above, thats a no-op.

-   if the argument doesnt implement the interface, we can use the
    > 'conform' keyword (actually a compiler hook function) which:

    -   allocates a statement-scope itable.

    -   fills it in with all the necessary function pointers.

    -   puts the itable, and the value pointer, into a fat pointer

    -   passes the fat pointer in.

-   any time a function takes in an interface, it actually takes in a
    > fat pointer.

so far, the above is only possible on the stack, because that's where
the itable lives. otherwise, we'd have to malloc an itable and have the
fat pointer own it. mallocs are quite expensive.

shoot, if we don't know if the value implements the interface, that
means we can't convert it back to a thin pointer.

there's a cost: since we don't know at compile time what the values of
this itable are, we have to populate it every time we call that function
