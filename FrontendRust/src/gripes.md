# Enums are weird

It's weird that I cant easily cast an enum to its contained struct.
```
enum Something {
    Thing { x: u64, y: u64 }
}
```
I can't do this:
`let thing = my_something.as<Thing>()`
Similarly, it makes it hard to have a macro that deeply searches for a Thing.
So instead I have ESCCD.

# Patterns Suck

I can't match StrI inline.
