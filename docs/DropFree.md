
# Discard Is Not Special, It's Everywhere

(DINSIE)

The Discard instruction has some interesting semantics:

 * It can drop a constraint ref. This just decrements the target's ref count.
 * If can drop a shared primitive ref. This doesn't really do anything.
 * It can drop a shared ref to a struct or an interface.
     * This will call the destructor on the object if the count hits zero.

However, this isn't special. All sorts of instructions have to do this
exact same thing.

For example, MemberLoad takes a struct reference, grabs the member out
of it, and then discards the struct reference. When it discards the
struct reference, it has to do all of the above anyway.

Lots of instructions do this. So, it's not special.

Once, we tried splitting it into:

 * DiscardConstraintReference

 * DiscardSharedReference

 * DiscardSharedPrimitiveReference

because DiscardSharedReference would also carry the destructor prototype
we'd want to call if we hit zero. However, lots of instructions also
need that destructor prototype. So, best get it from elsewhere.

**Note from later:** Just tried it again, even after the above warning.
Past me was totally right. Look at how many parts of vivem call
discard() on something. We'd need to add a destructor prototype to
*every one of those*, not just the Discard instruction.

