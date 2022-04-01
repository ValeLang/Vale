# Vale

Vale is a programming language whose goal is to show the world that **speed and safety can be easy!** Vale is:

 * *Fast:* Vale is an AOT compiled language that uses an entirely new approach to memory management: [generational references](https://verdagon.dev/blog/generational-references), which have zero aliasing costs and no garbage collection pauses.
 * *Fearless:* It is the [safest native language](/fearless): zero `unsafe`, region isolation, extern boundaries, and dependency extern whitelisting.
 * *Flexible:* Its new take on [regions](/guide/regions) enables alternate memory management and allocation strategies, with the [region borrow checker](https://verdagon.dev/blog/zero-cost-refs-regions) enabling seamless, fast, and _easy_ interop between them.


Vale is part of the [Vale Language Project](https://vale.dev/project), which explores, discovers, and publishes new programming language mechanisms that enable speed, safety, and ease of use. 


See http://vale.dev/ for more information on the language, and please consider [sponsoring us](https://github.com/sponsors/ValeLang) so that we can work on this even more!


See also our [treasure trove of working examples](https://github.com/Ivo-Balbaert/Vale_Examples), grouped by functionality.


## Building a Vale Program

 1. Download the latest binary at http://vale.dev/download
 1. Unzip it into, for example, `~/Vale`, and `cd` into it.
 1. Compile a program: `valec build hello=./samples/helloworld --output_dir target`
 1. Run the program: `target/main`


## Learning Vale

See [the Guide](https://vale.dev/guide/introduction) for how to use Vale.


## Building the compiler itself

For instructions for building the compiler itself, see [Building the Compiler](build-compiler.md).


For an overview of the project structure, see [Compiler Overview](compiler-overview.md).


## Editor plugins

- [VSCode plugin](https://marketplace.visualstudio.com/items?itemName=pacifio.vale-lang): Syntax-highlighting and basic autocompletion.
- [Vim plugin](https://github.com/jfecher/vale.vim): Syntax-highlighting


# Notes

Vale started in January 2013, and back then we called it "VLang", though there's now another language with that name. We then called it GelLLVM, in honor of Gel, the first language to offer constraint references. Since then, we've settled on the name "Vale". Note that Vale and Vala are two different languages.
