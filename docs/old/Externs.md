if:

\- export interface

\- struct

\- impl

then we\'ll make sure the interface is included in the output.

if the interface is included in the output, its probably because some
function in the core upcasts to it.

do we need to include all implementing structs in the output?

no. only the ones that are reachable.

if:

\- interface

\- export struct

\- impl

then the struct is in the output.

do we need to include the interface and the impls in the output?

no. only the ones reachable.

if:

\- export interface

\- export struct

\- impl

both are in output.

do we need to include the impl in the output?

yes, if its reachable; if we ever cast from that struct to that
interface.

will the client ever upcast?

sure.

ok, so we need to always include it then.

should public fields be exported?

for now just say yes all fields should be public. consider it an error
if we dont export any field types of an exported struct or func or
interface.

when exporting, theres really no such thing as impls\.... unless
exporting to rust?

when exporting templates, what happens?

well, we gotta export the template args\...
