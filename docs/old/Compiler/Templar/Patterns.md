**Destructuring Shared Doesnt Compile To Destroy** (DSDCTD)

We also destroy shared things.

Anti-example: \`(x, y, z) = Vec3(3, 4, 5)\` will \*not\* evaluate to a
Destroy, it will evaluate to a bunch of aliasing. However, we \*do\*
have a Destroy2 instruction in every imm struct\'s destructor. That
asserts that there are no more references, and then deallocates it.
