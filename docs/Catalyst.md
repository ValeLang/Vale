# Catalyst

List could be defined like this:

```
struct List<E> l'{
  arr ''[]E;
}
```

In other words, it's a cell'd array of elements. We name the List's own region as l'.

In main, if we have something like this:

```
struct Ship { hp int; }
func main() {
  list = List<Ship>();
  list.add(Ship(42));
  list.add(Ship(43));
  println(list.get(0).hp);
}
```

Then it's possible we can eliminate a lot of the generation checks here because of that cell.

First, we make it so list's constructor returns an iso. Not sure if this could be automatic.

Next, have static analysis determine that `list` is actually an isolate all the way throughout the function, because it only talks to well-behaved functions (this might be tricky).

Now imagine add was defined like this:

```
inline func add<l', E>(list &l'List<E>, x E) {
  arr = list.arr.lock();
  // now we know arr is a unique reference to that array
  ...
}
```

Since it's inline, it's merged into the parent function and can use its knowledge. Since it knows that List is an iso, it knows that it now has the only reachable reference to that `list.arr`, and it can make the `lock` a no-op.

It then also knows that it has the only reference to that entire array. At that point, it can access that array without generation checks.

In a way, with static analysis we're transitively applying the List's isolated-ness to the contained cell, since we know that nothing else in the world can have a reference to that cell.

If we do this right, it could eliminate a lot of gen checks for at least simpler structures.

We could alternately have a uni& which we can get from opening a cell'd thing.