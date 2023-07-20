# Vale

Vale is a programming language that's aims to be not only **fast** and **memory-safe**, but also **easy and flexible**.

* **Fast:** Vale is AOT compiled to LLVM, statically-typed, and uses the new [generational references](https://verdagon.dev/blog/generational-references) technique for memory safety with speed and flexibility, and will soon have [region borrow checking](https://verdagon.dev/blog/zero-cost-refs-regions) to make it even faster.
 * **Safe:** It is the [safest native language](https://vale.dev/memory-safe), thanks to [generational references](https://verdagon.dev/blog/generational-references) and [Fearless FFI](https://verdagon.dev/blog/fearless-ffi).
 * **Easy:** Vale has memory-safe single ownership without garbage collection or a borrow checker, which makes it easy to write safe, fast code.


See http://vale.dev/ for more information on the language. We're also open for [sponsorship](https://github.com/sponsors/ValeLang), thank you to all our sponsors for enabling our ambitious endeavor!


See also our [treasure trove of working examples](https://github.com/Ivo-Balbaert/Vale_Examples), grouped by functionality.


## Building a Vale Program

 1. Download the latest binary from https://github.com/ValeLang/Vale/releases
 1. Unzip it into, for example, `~/Vale`, and `cd` into it.
 1. Make a file `hello.vale` containing some vale code, like `exported func main() { println("Hello world!"); }`
 1. Compile a program: `./valec build mymod=hello.vale --output_dir target`
 1. Run the program: `target/main`, and you should see the output `Hello world!`


## Learning Vale

See [the Guide](https://vale.dev/guide/introduction) for how to use Vale.


## Building the compiler itself

For instructions for building the compiler itself, see [Building the Compiler](build-compiler.md).


For an overview of the project structure, see [Compiler Overview](compiler-overview.md).


## Editor plugins

- [VSCode plugin](https://marketplace.visualstudio.com/items?itemName=pacifio.vale-lang): Syntax-highlighting and basic autocompletion.
- [Vim plugin](https://github.com/jfecher/vale.vim): Syntax-highlighting


# Thank you to all of our sponsors!

 * [Arthur Weagel](https://github.com/aweagel)
 * [Kiril Mihaylov](https://github.com/KirilMihaylov)
 * [Radek Miček](https://github.com/radekm)
 * [Geomitron](https://github.com/Geomitron)
 * [Chiuzon](https://github.com/chiuzon)
 * [Felix Scholz](https://github.com/soupertonic)
 * [Joseph Jaoudi](https://github.com/linkmonitor)
 * [Luke Puchner-Hardman](https://github.com/lupuchard)
 * [Jonathan Zielinski](https://github.com/tootoobeepbeep)
 * [Albin Kocheril Chacko](https://github.com/albinkc)
 * [Enrico Zschemisch](https://github.com/ezschemi)
 * [Svintooo](https://github.com/Svintooo)
 * [Tim Stack](https://github.com/tstack)
 * [Alon Zakai](https://github.com/kripken)
 * [Alec Newman](https://github.com/rovaughn)
 * [Sergey Davidoff](https://github.com/Shnatsel)
 * [Ian (linuxy)](https://github.com/linuxy)
 * [Ivo Balbaert](https://github.com/Ivo-Balbaert/)
 * [Pierre Curto](https://github.com/pierrec)
 * [Love Jesus](https://github.com/loveJesus)
 * [J. Ryan Stinnett](https://github.com/jryans)
 * [Cristian Dinu](https://github.com/cdinu)
 * [Florian Plattner](https://github.com/lasernoises)

# Notes

Vale started in January 2013, and back then we called it "VLang", though there's now another language with that name. We then called it GelLLVM, in honor of Gel, the first language to offer constraint references. Since then, we've settled on the name "Vale". Note that Vale and Vala are two different languages.
