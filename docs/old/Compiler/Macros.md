Macros run in the pre stages of the compiler. We could run them in
astronomer, but right now we run them in templar.

Macros will mostly operate on astrouts, and produce more astrouts. They
don\'t seem to be super aware of the surrounding environment yet, just
producing stuff.

They can be recursive, with macros causing other macros to be invoked.
For example, there\'s a macro that runs for every interface which makes
an anonymous substruct, and then when we add that struct, a macro is
invoked which generates a constructor for that struct.

Right now some produce temputs, lets move away from that.
