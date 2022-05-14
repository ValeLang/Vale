
# Purity Across the FFI Boundary (PAFFIB)

One might think that we can just pass purity through the context word, in a bit.

So if we call a pure function which receives the outside region, we can set that bit to 1. For example:

```vale

func main() {
  ...
  mySecondPureFunction(&mainShip);
}

pure func mySecondPureFunction(mainShip &Ship) {
  ...
}
```

We would have an implicit parameter, the context word for the main region, which might look like 0x99999991. The last four bits are 0b0010, that 1 means we're in a pure function.

This is nice, because if we ever want to do anything that violates that pureness, we can just check that bit.

However, this doesn't exactly work, because of alien references, like we give to C. See this example:

```vale
struct Ship {
  fuel! int;
}

func main() {
  mainShip = Ship(11);

  // Not a pure call, giving C a reference to something mutable.
  cFunctionFromMain(&mainShip);

  mySecondPureFunction();
}

pure func mySecondPureFunction() {
  secondShip = Ship(11);

  cFunctionFromSecond(&secondShip);
}

extern func cFunctionFromMain();

extern func cFunctionFromSecond();

exported func myThirdFunction(mainShip &Ship) {
  set mainShip.x = 7; // Uh oh!
}
```

```c
ShipRef mainShipGlobal;
ShipRef secondShipGlobal;

void cFunctionFromMain(ShipRef mainShip) {
  mainShipGlobal = mainShip;
}

void cFunctionFromSecond(ShipRef secondShip) {
  secondShipGlobal = secondShip;

  myThirdFunction(mainShipGlobal);
}
```

There's nothing stopping us from calling `myThirdFunction` which adopts `mainShip`'s region... _mutably._

We can't check the context word because we don't have it, we lost it when we called `cFunctionFromSecond`.

## Solution

There are a lot of approaches to this, see AFPAFFIB. Below is what we went with.

Let's say a Vale function calls an extern function, and it has some context words for that function's open regions. For example, `mySecondPureFunction` above.

When C calls back into an export function, it will only be allowed to open the regions that were open in the original Vale function. In the above example, `myThirdFunction` will only be allowed to open the regions that were open in `mySecondPureFunction`. `mySecondPureFunction` didn't have main's region open, so it will halt right there.

To work around this, the user needs to carry these region annotations all the way down the stack:

```vale
pure func mySecondPureFunction<'m>() {
  secondShip = Ship(11);

  cFunctionFromSecond(&secondShip);
}
```

Note that `'m` which seems unused, but its actually stored when we do the FFI to `cFunctionFromSecond`, so that `myThirdFunction` can use it. Of course, in this example, `myThirdFunction` will see that the context word says that region is immutable, and halt there, but that's a good thing.

That workaround is the one downside; the user has to carry those region annotations all the way down the stack. However, they'd have to do this anyway if FFI wasn't involved, so it doesn't seem to terrible honestly.

An extra benefit to this is that we can do some static analysis, and just skip the bit-or into the context word if we see it doesn't do any FFI or virtual calls. Most functions don't, after all.

Another thing that works well here is that if the user never uses regions, this is never a problem. Opt-in complexity!
