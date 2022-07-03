Afaik they don\'t do anything special compilation wise unless you use
IL2CPP (though that may change once the burst compiler drops). They do
heavily leverage calling across the Managed/Native boundary via mono\'s
\_internal_call mechanism. Also seen some \[UsedByNativeCode\]
attributes in places so my guess is there\'s some Native -\> Managed
calls going on as well. No idea about how they handle Update but it
likely using the type system to resolve the methods.

Not exactly easy to integrate with lol. Closest I\'ve gotten that
doesn\'t cause a ton of headaches for the end user is calling into a C
interface to the foreign language from c# via P/Invoke for whatever I
needed in there. (Usually c++/Rust for extra functionality or
performance)

thats some great info, you\'ve given me a gold mine of things to search
for and look into

ive never seen \[UsedByNativeCode\], definitely going to look into that
first

i\'ve gotten some rather simple P/Invoke stuff to work, but it was\...
rather nightmarish

and i couldn\'t figure out how to call back into C# from the native
plugin, so that was kind of a non-starter, sigh

could you elaborate on what you mean by it using the type system to
resolve the methods?

would they be doing that through a mono API, or something different do
you think?

Probably mono. Sadly all I can find is speculation about how they do it.
I remember a forum post where they said they do not use reflection but
that\'s about all the concrete info I can find.

Probably still uses the metadata for the type but beyond that it\'s
anyone\'s guess
