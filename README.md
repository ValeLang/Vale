# Vale Compiler

Compiler for the Vale programming language.


## Vale

Vale is the fast, fearless, and flexible programming language. It aims to be as fast as Rust, as easy as Java, and as safe as Javascript.

See http://vale.dev/ for samples and more information on the language!

See also our [treasure trove of working examples](https://github.com/Ivo-Balbaert/Vale_Examples), grouped by functionality.


## Building a Vale Program

 1. Download the latest binary at http://vale.dev/download
 1. Unzip it into, for example, `~/Vale`, and `cd` into it.
 1. Compile a program: `python3 valec.py build hello hello:./samples/helloworld stdlib:./stdlib/src`
 1. Run the program: `./a.out`


## Learning Vale

See [the Guide](https://vale.dev/guide/introduction) for how to use Vale.


## Building the compiler itself

For instructions for building the compiler itself, see [Building the Compiler](build-compiler.md).


## Editor plugins

- [VSCode plugin](https://marketplace.visualstudio.com/items?itemName=pacifio.vale-lang): Syntax-highlighting and basic autocompletion.
- [Vim plugin](https://github.com/jfecher/vale.vim): Syntax-highlighting

# Notes

Vale started in January 2013, and back then we called it "VLang", though there's now another language with that name. We then called it GelLLVM, in honor of Gel, the first language to offer constraint references. Since then, we've settled on the name "Vale". Note that Vale and Vala are two different languages.
