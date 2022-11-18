Remember, local environments can contain types! Best example is a
template parameter.

fn moo:T(t: T) { ... }

there\'s a type T in the local environment that isn\'t in the global
environment!

FunctionTemplar intentionally doesn\'t take an environment from the
caller, instead it will take a FunctionTemplata, which contains the
environment *the function was defined in*. This is also so that if
we\'re calling a closure that was declared in whoever called us, they
can access all the types and whatever in their environment.

When we generate a Function2, we have to:

2\. (Closure or Light layer)

\- Add any closure variables to the environment, and add \_\_Closure
closure struct ref to the env.

Here\'s where we make the function\'s \"near env\", the environment that
contains those things.

3\. (Ordinary or Template layer)

\- If we\'re spawning this function from a template from a call, then
don\'t just blindly assume the parameter types are the same as the
argument types. Coerce the parameters to fit both the argument types and
the constraints specified by the params and the template params.

\- Any types captured by the template params should be added to the
environment.

\- Evaluate the template rules and add them to the environment

this is where the InferTemplar is called.

Here\'s where we make the function\'s \"runed env\", the environment
that contains all the templatas from the runes in the prototype. We\'re
also going from the *function template\'s* environment to the actual
*function\'s* environment.

This layer needs to receive a FunctionEnvEntry, which is a function and
all of its parents, to feed into the inferer as needed, see NTKPRR.

4\. Middle layer

\- Now that we have all the knowledge of types and stuff, actually make
the function params and return type

\- now that we have params, make the actual function name and function
environment

\- See if we can grab existing things out of the temputs.

\- Declares the functions

\- after we\'re done making the new one, feed it to the virtualtemplar
to make ancestors and descendants.

Here\'s where we put the parameters into the runed env\'s name, and now
it completely and uniquely identifies the function. The env with this is
the \"named env\".

Below here, we sometimes wrap it in a box and call it the \"body env\",
or just \"env\" or \"fate\".

5\. Core

\- If we have a return type, figure it out.

\- If it\'s an abstract or extern function,

\- Temputs.declareFunctionSignature

\- Temputs.declareFunctionReturnType

\- Otherwise it has a body, proceed to body templar.

6\. Body

\- Temputs.declareFunctionSignature

\- As soon as can, Temputs.declareFunctionReturnType

\- (evaluate body)

\- VirtualTemplar.ensureFamiliesExistAndStampRelatives
