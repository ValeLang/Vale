# Vale

Vale is a programming language whose goal is to show the world that **speed and safety can be easy!** Vale is:

 * *Fast:* Vale is an AOT compiled language that uses the new [generational references](https://verdagon.dev/blog/generational-references) technique, enabling memory-safe control over data layout.
 * *Fearless:* It is the [safest native language](https://vale.dev/fearless), using region isolation and "Fearless FFI" to keep extern code's bugs from affecting Vale objects.
 * *Flexible:* Its new take on [regions](/guide/regions) enables alternate memory management and allocation strategies, with the planned [region borrow checker](https://verdagon.dev/blog/zero-cost-refs-regions) enabling easy interop between them, and eliminating the vast majority of generational references' overhead.


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


# Thank you to our sponsors!

 * [Joseph Jaoudi](https://github.com/linkmonitor) ($25/mo)
 * [Sergey "Shnatsel" Davidoff](https://github.com/Shnatsel) ($12/mo)
 * [Ian (linuxy)](https://github.com/linuxy) ($12/mo)
 * [Ivo Balbaert](https://github.com/Ivo-Balbaert/) ($5/mo)
 * [Kevin Navero](https://github.com/solstice333/) ($5/mo)
 * Ilya Seletsky ($5/mo)
 * Jean Juang ($100)
 * [Posnet](https://github.com/Posnet) ($50)
 * Kim Shook ($20)


# Notes

Vale started in January 2013, and back then we called it "VLang", though there's now another language with that name. We then called it GelLLVM, in honor of Gel, the first language to offer constraint references. Since then, we've settled on the name "Vale". Note that Vale and Vala are two different languages.
