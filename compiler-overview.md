# Compiler Overview

The compiler is separated into various parts:

 * **Frontend** has most of the logic for the semantics of the Vale language. This is made of various stages, and produces the "Final AST".
 * **Backend** feeds Vale's Final AST to LLVM, which produces a .o (or .obj on windows).
 * **Coordinator** serves as the command line interface (`valec`), and invokes the frontend, feeds the Final AST to the backend, and then feeds the resulting .o/.obj to clang.


## Frontend

The frontend has most of the logic for the semantics of the Vale language. This is made of various stages, and produces the "Final AST".

The frontend is written in a very simple, imperative kind of Scala, closer to Java or Kotlin than anything else.

It contains multiple passes:

 * **ParsingPass** parses the code that the user wrote.
 * **PostParsingPass** does a brief glance at the parser's outputs, and notes what variables are later modified or used from closures.
 * **HigherTypingPass** looks at all generic parameters (such as the `T` in `List<T>`) and determines whether they're values, references, integers, or booleans.
 * **TypingPass** is the largest pass. It does everything the other passes don't.
 * **SimplifyingPass** simplifies the AST and lowers it to concepts easily understood by the backend.
 * **PassManager** which invokes all of the above.


Frontend is gradually moving away from these "passes" and switching to a more query-based architecture, for better incremental compilation and IDE support.


The Frontend has a few other useful libraries:

 * **TestVM** interprets the Final AST directly, which helps us isolate problems to just the Frontend or Backend.
 * **Highlighter** is a syntax highlighter, which uses the results of the ParsingPass.
 * **Solver** is a general constraint solver, used during the HigherTypingPass and TypingPass.
 * **CompilerServer** is a cloud function that invokes the compiler on input received from the web.


The Frontend has almost a thousand tests, mostly under the IntegrationTests/ directory.


## Backend

The backend is written in C++. Its main purpose is to feed the Final AST to LLVM, and produces a .o (or .obj on windows).


## Coordinator

Coordinator has the code for the command line interface (`valec`), and invokes the frontend, feeds the Final AST to the backend, and then feeds the resulting .o/.obj to clang.

The coordinator is written in Vale itself.


## Long-Term Direction

See [Architectural Direction](/architectural-direction.md) for where we're heading with these designs.


## More Details

Let us know if you'd like more details! We're keeping this page light for now, but aim to migrate our internal documentation over to here once it becomes interesting to others.

