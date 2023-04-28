
# Unstackifieds Can Refer To Variables in Parent Environments (UCRTVPE)

We need to keep track of what variables have been unstackified. Theres two choices:

 * In the (possibly parent) environment where the local lives, we can have an unstackifieds list
 * Put it in the current environment

The first one doesnt work well, because if we unstackify from within an if-statement like so:

```
m = Marine(10);
if (something) {
  drop(m);
  return 7;
}
```

we don't want that unstackify to infect the parent environment. Really, we want the if-statement code to handle and propagate any relevant unstackifies.

Same with restackifies. If there's an unstackified local outside, and an if-statement restackifies like:

```
m = Marine(10);
[_] = m;
if (something) {
  set m = Marine(40);
  return m.hp;
}
```

then we don't want it to affect the parent environment.