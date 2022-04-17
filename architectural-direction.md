
# Architectural Direction

(See [Compiler Overview](/compiler-overview.md) for background)


## Frontend

The long-term goal for the Frontend is to migrate to a query-based architecture, to support IDE integration via LSP.

The phase separation (parser, post-parser, higher-typer, typer, simplifier) will disappear, and most of it will be consolidated into the the typer phase, which will become "the compiler".

We'll keep alive the AST from the previous compilation, receive source code changes from the user (via language server protocol or the command line interface) and reuse the previous AST where we can.

This is basically incremental compilation, but if we keep the previous AST in RAM, plus some indexes to assist in overload resolution, it will be a query-based architecture.

To get there, we'll also need to:

 * Switch from templates to generics.
    * The simplifying phase will then be responsible for monomporphizing the generics, to feed into the Backend.
 * Establish the Overload Index, keyed by arity, each parameters' struct/interface, permission, and so on.
    * Without this, we likely wouldn't be fast enough to serve autocomplete queries.


## Backend

The Backend is currently separated into different modules which each handle a different region kind. However, Vale's design has recently consolidated into one region kind, Hybrid-Generational Memory.

We currently have that ("resilient-v4") but we'll actually be starting over from the unsafe region kind. Before we do that, we'll make sure that the unsafe region kind compiles to roughly the same assembly as C, verifying the performance is the same, and adding some benchmarks to make sure it stays that way.

To get there, we'll also need to:

 * Add inline data support.


## Self-Hosting

After the above Frontend and Backend directions are complete, we'll be able to add IDE integration, and then Vale will be mature enough to self-host.
