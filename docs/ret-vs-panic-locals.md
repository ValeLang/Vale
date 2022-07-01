# Add Unreachable Moots After Panic (AUMAP)

UnreachableMoot is a wrapper instruction to signal to Hammer that something won't actually ever be run. We still compile and output some AST so that we can highlight any errors, and so the user can ctrl+click on an item to see its definition, even if a panic blocks it.

We have UnreachableMoots after return, break, and panic.

## Destroying Locals

ret and break will implicitly destroy every local in scope and then return.

However, a panic function won't.

So, we have to figure out... between these two functions:

```
fn main() {
  m = Marine(7);
  ret 6;
}
fn main() {
  m = Marine(7);
  panic();
}
```

do we unstackify 'm' at the end of the body?

Definitely not for the first one, because 'ret' already unstackified it.

But what about the second one?

It won't make a difference to Midas.

Reasons to unstackify:

1. It will instantiate the destructors (if theyre templated). That way, when we take out the 'panic', we wont suddenly get compiler errors about the destructors.
2. We can verify the VIR to make sure that everything was unstackified, and wont have to deal with exceptions about Nevers.

Reasons to not unstackify:

1. We don't want to deal with automatically called destructors yet, we're just trying to get something to compile just to see it work a little bit.

Theres also some considerations with if-statements:

```
fn main() {
  f = Firebat();
  m = Marine();
  if (i = inputInt()) {
    destructor(m, i);
  } else {
    panic();
  }
}
```

here, when we get to the end of the if-statement, we want m to be considered moved, and f to not be considered moved. So, at the very least, the panic **must** mark m moved/unstackified. The question after that is whether to actually emit instructions to actually unstackify.

To force them to have a 0-arg destructor for when we panic is kind of unreasonable. They might only have a destructor that takes some other argument (like above). So, A is unreasonable, and we can't **emit** instructions for them.

Decision:

- We'll have a special UnreachableMoot(x) which takes an argument by move, prints it out von-style, and halts the program. This should solve B.
- In the case of if-statement where one branch returns Never(), it will take all the variables that were moved by the other branch, and implicitly call that panic() on them, so VIR analyzers can see that they were moved.
- To be consistent with that, and because we can't force the user to make destructors just for panics (where they might otherwise just have special other-arg constructors), we'll hand things into panic for every block.

# Break and Return Can Only Be Statements (BRCOBS)

Break and return can only be statements because of this awkward situation:

a(b(), break);

We wouldn't know that we have to drop the b() that's hanging in limbo.

This also causes problems down in Midas, when Nevers are involved. After certain AST nodes, it adds extra instructions... for example, the making of the actual local in Stackify. If we have a:

a = return 3;

then we'll have instructions after the LLVM ret instruction, which LLVM very much dislikes (and also makes little sense to do anyway).

So we have checks occasionally to make sure we don't have any breaks or returns inside other expressions (except consecutors that are owned by blocks).

**Note from later:** We might not be able to avoid this problem, actually. If we ever have a bail operator, like:

a(b(), c()!);

then we'll need to call drop() on that b() result.

**Another note:** We run into this same problem with panics. The making of the actual local in Stackify will put things after a panic() even though nothing can happen.

**Possible solution:** Make Hammer guarantee that panics/breaks/returns will only be the last thing in a block, and not part of any other expression. This also means that if we have:

a(b(), panic());

then basically that a(...) call becomes a consecutor.

x = panic();

will remove the entire variable.

In fact, we could remove UnreachableMoot and have hammer intelligently do all of this removing.

Would JVM want all theses unstackifies in?

Hammer's Call, Stackify, Destroy, all of them disallow taking in Never params.

Templar's e.g. LetNormal can still take in Never things, they propagate freely throughout templar'd code, just like poison values will one day.

Should we drop things after a panic?

i think so, because we can use panics as eg vimpl.

also, we have this case:

`x = someFunc(3, panic());`

and we want to infer x's type correctly and proceed with the rest of the code, if possible.

This is also why we don't make a Function's result Never when any args are Never; we want to proceed with as normal type checking as possible.

re consecutor's result:

```
if true {
  whatever
} else {
  ret 4;
}
```

the `ret 4;` is followed by a void... but we still want to consider the whole block as a Never. so it would be good to say that a consecutor's result is Never if any inners are Never.

we also want that for the panic function:

```
fn panic() {
  __vbi_panic();
}
```

but do we ever not want that?

Consecutor With Never Will Make Temporaries (CWNWMT)

A never is the destroyer of worlds in hammer; it will stop evaluating anything after a never, and skip any instruction that depends on a never.

# Break Never And The Other Never (BNATON)

There are two kinds of nevers, because While needs to know where the never came from.

# Blocks Might Have Deferreds (BMHD)

// One would think that we would translate deferreds after each particular expression.

// Normally we do, except the last statement in a block might actually be returning something

// and has a defer, for example in:

// fn main() {

// println(Marine(4).hp);

// = [4, 5].0;

// }

// where we're deferring the destruction of that array.

// Instead, we do deferreds before any discards, since all the lines except the last in a block

// will be discarded.
