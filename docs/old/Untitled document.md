Everyone knows of the major 3 memory management strategies: reference
counting, tracing garbage collection, and Single ownership. you might be
surprised to know, however, that there are a lot more ways to manage
memory.

The first one is an **arena,** where we pull allocations from a single
slab of memory. it\'s extremely fast. It\'s great for when we have some
temporary memory we need to use oh, and we don\'t necessarily need it to
live outside the scope of the Arena. we generally don\'t use it as a
general-purpose memory management mechanism because it uses so much
memory. But there are some programs that are very short-lived and use
this strategy to be very fast. Some programs use it for all of their
temporary memory usage. I think that if a language could rey nicely
harness arenas, it could unlock a lot of speed.

fn main() export {

game = makeGame(\...);

// Call findPath, make it use a pool.

path = \'pool findPath(&game, Location(1, 2), Location(8, 9));

println(path);

}

fn findPath\<\'b, \'r\>(game \'r &Game, from \'r Location, to \'r
Location) List\<Location\> \'b { «annotations»

explored = Set\<Location\>();

// \...

}

The next one is array, where we refer to instances by index. This one is
cool because it avoids calls to Malik and free, but the downsides are
that we incur bounds checking costs and expansion costs. it also means
we can\'t have pointers to them, because every time we expanded the
array, these objects move to somewhere else in memory. Referring to
things by index is irksome because we can\'t dereference them directly,
we have to pass in the entire array, or store it in the a member.
Passing in the entire array does not play well with polymorphism, and
storing it in a member seems like it violates encapsulation.

The next one is a pool where are we reuse allocations of the same type.
usually we use it for a specific type. For example, if we have a lot of
spaceships, we might have a spaceship array with a spaceship . this lets
us avoid calling Malik and free which are expensive. we normally don\'t
see this 4 multiple types at the same time. It is possible though. If a
language could make it easy to do this for multiple types, then it could
be really cool.This is particularly cool because these allegations are
stable, they won\'t move around like they would in an array. this means
we can avoid bounds checking costs.

The next one is Basil\'s crazy stack-based thing. basically every value
owns a bunch of other values in line. whenever we return a bunch of data
from a function it copies onlay that data and automatically lets go of
the rest. This is a pretty cool technique because it means we don\'t
have to call malachor free.

we can combine single ownership with something called scoop tethering.
when we tether a object, we are extending its lifetime for the duration
of the scope tether. It\'s similar to reference counting, but it only
uses one bit. however, we need to be sure that the object still exist
before we get it tether.

The last one I want to mention is in line allocation. this is where we
put an object inside another object memory. when taken to the extreme we
end up with something like Fortran, where we designate all memory up
front. The benefit of this is that we never call Malik or free, but in
real-world applications we will probably be storing things in a raise
because we don\'t know how many of them there are or something. This is
more often used in conjunction with another strategy. how we do this
depends on the particular strategy.

Now let\'s talk about memory safety.

One new method is generational references. with generational references,
we can check if an object is still alive. We compare the generation from
the reference to the generation stored in the object. This is also
similar to memory tagging, where we store a tag for every 16 bytes and
before we D reference by pointer we check that the pointers tag still
matches the 16 bites tag.

the next method is constraint references. It uses a single ownership.
Not owning references are constraint references. if we try to delete the
owning reference while any constraint references are still valid, we
will halt the program. The nice thing about this, is that it can be
disabled for release builds. for example if we are writing a triple A
game, then we might want this into bug mode, to make sure that are
Pointers don\'t become dangling, and then disable A Time release mode so
we don\'t have any performance costs.

Now let\'s imagine something like reference counting or Garbage
Collection or single ownership, but where every allocation is protected
by a rough cell. we can now have inline objects. One language
experimenting with this style is cone.

1.  basil\'s thing (instance stable)

2.  arenas (instance stable)

3.  pools (type stable)

4.  single ownership, arrays, no pointers, manual weak refs (fortran,
    > rust, c++) (type stable)

5.  rc/gc (shared ownership) (unstable)

6.  single ownership, constraint references (vale, inko) (unstable)

7.  single ownership, weak refs (vale) (unstable)

-   except for basil + arenas, these:

    -   can have inline structs.

    -   though, inline enums would have to be unique.

    -   RC would need fat pointer, a ptr to count (or at least an
        > offset)

    -   gen refs would need a fat ptr, to generation (or at least an
        > offset)

    -   maybe not even a fat pointer, maybe just encode it in the top
        > byte.

-   none of these can do \"hierarchy borrowing\" even with final,
    > because of the array problem.

    -   constraint refs could help here. just have a c-ref to the next
        > highest varying (or element).

so far, actually pretty good. inline enums are small.

so how do we enable temporary refs to inline enums? adding something
above the varying.

-   a separate readwrite and readonly counter. basically, rust.

-   a constraint ref counter, if we want things in the heap to
    > temporarily reference them.

-   a generation.

these are nice, but what about escaping references? respectively:

-   if a reference escapes, it locks up the entire program.

-   allowed, and they will lock the object.

-   these are naturally weak, so its fine.

what if we dont want to lock the object?

-   we have to rearchitect our program so that we can ID them.

-   a bit awkward, we\'d need a separate heap-allocated WRC, kind of
    > defeats the point. or we can reference the containing thing, plus
    > an indicator.

-   these are naturally weak, so its fine.

gen refs are lookin pretty good so far.

how do we enable hierarchy borrowing?

-   a separate readwrite and readonly counter: if we *must* go through
    > the parent, we can ensure everything on the way here is readonly.

-   a constraint ref counter: if we check it on set, we can be sure
    > nothing will change.

-   no can do with just gen refs.

-   scope tethers! if we check on set, we can be sure nothing will
    > change.

lets add region borrow checking.

-   if we lock the entire world, we know its alllll good.
