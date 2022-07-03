## ReferenceMemberLookup Results In Member\'s Ownership

(RMLRMO)

When we do:

> struct Engine { fuel int; }
>
> struct Spaceship { engine Engine; }
>
> fn main() {
>
> ship = Spaceship(Engine(10));
>
> println(ship.engine);
>
> }

that ship.engine expression results in a &Engine.

So, we might think that a ReferenceMemberLookup will result in a
&Engine.

However, that doesnt work for this snippet:

> struct Engine { fuel int; }
>
> struct Spaceship { engine Engine; }
>
> fn main() {
>
> ship = Spaceship(Engine(10));
>
> mut ship.engine = Engine(15);
>
> }

because it\'s like we\'d be assigning a Engine into an &Engine
destination.

So instead, ReferenceMemberLookup should result in the member\'s type,
an Engine in both cases.

When we do println(ship.engine), we add a **SoftLoad** there, around the
ReferenceMemberLookup. It will turn the Engine into a &Engine.

## ReferenceMemberLookup Has TargetPermission

(RMLHTP)

When we have a readonly reference to a Ship, and we access its engine,
it too should be readonly.

> struct Engine { fuel int; }
>
> struct Spaceship { engine Engine; }
>
> fn printEngine(engine &Engine) {
>
> println(engine.fuel);
>
> }
>
> fn printShip(ship &Spaceship) {
>
> printEngine(ship.engine);
>
> }

To do this, we make it so ReferenceMemberLookup has a targetPermission
field.

This would seem inconsistent with RMLRMO, but its concerns don\'t apply
here, since we can\'t mutate anything readonly.

## Can Sometimes Have Read-Only Owning References

(CSHROOR)

Recall RMLHTP, if we have a &Ship and we load .engine from it, like:

> struct Engine { fuel int; }
>
> struct Spaceship { engine Engine; }
>
> fn printEngine(engine &Engine) {
>
> println(engine.fuel);
>
> }
>
> fn printShip(ship &Spaceship) {
>
> printEngine(ship.engine);
>
> }

then the ReferenceMemberLookup will return a read-only reference, but
one that is still an owning one.

This is the only known case of an owning read-only reference. This only
happens because it\'s an AddressExpression, btw.
