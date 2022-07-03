what

pure functions and isolates seem to interact in weird ways.

if we have two isolates:

myStuff = List\<Input\>();

\...

myShipyard = iso Shipyard(\...);

myGalaxy = iso Galaxy(\...);

and a pure function:

fn myThing\<\'a ro, \'b, \'c\>(

myStuff \'a &List\<Input\>,

shipyard \'b &Shipyard,

shipyard \'c &Galaxy) pure {

\...

}

note how \'b and \'c arent ro. this is doable with a pure function.

we can safely modify \'a and \'b.

wtf is goin on here?

some random observations:

-   for **at least** pure functions, we can open up a local isolate via
    > the region borrow checker, **and do mutable aliasing**

    -   this is the colin gordon principle i believe

-   when we have an iso thats a field, we can swap it out into a local
    > temporarily, **and do mutable aliasing.**

    -   nice workaround to use colin gordon more

-   when we have an iso thats a field, we can use a pure function and
    > **immutably alias it**. this is so we can be sure nobody else
    > changes it.

-   if we have an **at least** pure function that we\'re *only* handing
    > field isolates to, we can **open them up mutably.** because we
    > can\'t reach them any other way, you see.

    -   is this what rust\'s borrow checker is doing?

-   but if we\'re handing anything else to it, through which we might
    > reach those same iso\'s, weird things happen. theyre not really
    > immutable.

so:

-   a pure function lets us freely immutably alias everything and
    > isolates

-   a pure function that only takes in field isolates will let us mutate
    > them

-   a pure function that takes in local isolates will let us mutate them

  ---------------------------------------------------------------------------------
  whats in                            what can we do  what can we do  what can we
  the args?                           with the        with the field  do with the
                                      aliased data?   isos?           local isos?
  ----------- ----------- ----------- --------------- --------------- -------------
  aliased     field iso   local iso   **immutable**   **immutable**   **mutable**
  data in     data in     data in                                     
  args        args        args                                        

                          no local    **immutable**   **immutable**   n/a
                          iso data in                                 
                          args                                        

              no field    local iso   **immutable**   n/a             **mutable**
              iso data in data in                                     
              args        args                                        

                          no local    **immutable**   n/a             n/a
                          iso data in                                 
                          args                                        

  no aliased  field iso   local iso   n/a             **mutable**     **mutable**
  data in     data in     data in                                     
  args        args        args                                        

                          no local    n/a             **mutable**     n/a
                          iso data in                                 
                          args                                        

              no field    local iso   n/a             n/a             **mutable**
              iso data in data in                                     
              args        args                                        

                          no local    n/a             n/a             n/a
                          iso data in                                 
                          args                                        
  ---------------------------------------------------------------------------------

can we have an isolate that\'s pointing outside, that has un-deref-able
data?

you know, there\'s another un-deref-able data we\'ve seen: generics\'
placeholders, like \<T\>.

so maybe we could have a List\<T\> be an isolate, lol

**option 1: keep it how it is**

globals can have refs into other regions, we have to annotate functions
as pure, cant access the globals from pure funcs.

**option 2: globals are isos**

perhaps we can make every function effectively pure, by enforcing that
globals be isolates. i mean, they kind of are, since they must be in
mutexes? perhaps we just need to really *realize* that.

so, they cant have refs into any other region.

in which case, kind of like the table above, they dont affect anyone
else, so we can open them up however we want, and everything\'s pure.

**option 3: globals can have alien refs, like C does**

we can have alien refs (like the kind we encrypt and give to C) and open
them up immutably if the region is immutable\... maybe?

and we can open up an alien ref mutably, if the region is currently
mutably open? maybe?

like, we can merge the usage of the global with the usage of this
region. maybe.
