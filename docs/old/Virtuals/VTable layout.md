-   Tracing table, if enabled

-   Components

-   Edges containing the methods

-   Pointer to tracing table

-   Pointer to component table

-   (pointer here)

-   ETable

anything the ETable points to must start with a pointer, thats how we do
instanceof

the tracing table seems like it would be easy... just put it in the
ETable, and instead of pointing at something with

v.ITracingInfo will be a table that looks like this:

-   Pointer to a IInfo for v.ITracingInfo

-   Number of references

-   (repeated) Offset in struct for each immutable

We need a way to check if a given struct contains a given component (so
we can subscribe to certain ECS).

We'll never be checking if a different struct implements a given
component, will we? i suppose we'll have to... so a struct can subscribe
to a certain system.

so, every component should have a accompanying interface ID.

The ETable will map from that interface ID to an ITable representing how
this struct does indeed have that component.

The ITable will look like this:

-   Pointer to the IInfo for that component's interface (or perhaps we
    > can make it point to the struct? what would be more useful?)

-   Offset in the parent struct of this component.

every object\'s vtable index 0 is the destructor, vtable index 1 is the
tracer.
