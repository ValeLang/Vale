# Vale Compiler

Compiler for the Vale programming language.

## Vale

Vale is the fast, safe, and easy programming language. It uses single ownership with constraint references for memory safety without garbage collection, and an emphasis on modern, readable syntax.

See http://vale.dev/ for samples and more information on the language!

## Building and Running

### Linux and OSX

1: Download LLVM 11 from their [releases page](https://releases.llvm.org/download.html).

2: Unzip it into any directory, for example `~/llvm11`.

3: Set LDFLAGS, CPPFLAGS, PATH env vars:

```
$ export LDFLAGS="-L~/llvm11/lib -Wl,-rpath,~/llvm11/lib"
$ export CPPFLAGS="-I~/llvm11/include"
$ export PATH=~/llvm11/bin:$PATH
```

4: Change directory:

```
$ cd Midas
```

5: Generate the build files, and use it to build Midas:

```
$ cmake -B cmake-build-debug
$ cd cmake-build-debug
$ make
```

6: Run tests:

```
$ cd ../test
$ python3 -m unittest -f
```

5. Run compiler:

```
$ python3 valec.py test/tests/roguelike.vale
```

### Windows

Compiling on windows is quite involved, come by the discord and we can walk you through it.


# Notes

Vale started in January 2013, and back then we called it "VLang", though there's now another language with that name. We then called it GelLLVM, in honor of Gel, the first language to offer constraint references. Since then, we've settled on the name "Vale". Note that Vale and Vala are two different languages.
