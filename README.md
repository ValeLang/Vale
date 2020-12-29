# Vale Compiler

Compiler for the Vale programming language.

## Vale

Vale is the fast, safe, and easy programming language. It uses single ownership with constraint references for memory safety without garbage collection, and an emphasis on modern, readable syntax.

See http://vale.dev/ for samples and more information on the language!

## Building and Running

If you just want to build and run Vale programs, go to the [downloads](https://vale.dev/downloads) page. Simply unzip the zip-file. You get the Vale compiler script valec.py, together with the rest of the Vale platform.

Here is a first Hello World program to get you started:
Create a text file hello.vale with as content:

```
fn main() export {
  println("Hello world from Vale!");
}
```

Compile this with:
```
$ python3 valec.py hello.vale
```

The result is a main.exe on Windows or a a.out file on Linux/OS X. Run this with main or ./a.out to see the output:

```
Hello world from Vale!
```

(On Windows, be sure to use a Python3.8 (or higher) 32-bit version to execute the compiler script valec.py)

### Building from source on Linux and OSX

These are the instructions for building the compiler itself.

1: Getting the source code:
    git clone https://github.com/ValeLang/Vale

    This will be placed in a `~/Vale` folder.

2: Download LLVM 11 from their [releases page](https://releases.llvm.org/download.html),
    for example: clang+llvm-11.0.0-x86_64-linux-gnu-ubuntu-20.04.tar.xz

3: Unzip it into any directory, for example `~/llvm11`.

4: Extract the LLVM 11 file with tar:
    $ sudo apt install xz-utils
    $ cd `~/llvm11`
	$ tar -xf clang+llvm-11.0.0-x86_64-linux-gnu-ubuntu-20.04.tar.xz

5: Set LDFLAGS, CPPFLAGS, PATH env vars:

```
$ export LDFLAGS="-L~/llvm11/lib -Wl,-rpath,~/llvm11/lib"
$ export CPPFLAGS="-I~/llvm11/include"
$ export PATH=~/llvm11/bin:$PATH
```

6: Change directory:

```
$ cd Vale/Midas
```

7: Generate the build files, and use it to build Midas:
  (If necessary: install cmake: $ sudo apt install cmake )

```
$ cmake -B cmake-build-debug
$ cd cmake-build-debug
$ make
```

8: Run tests:

In folder `~Vale/Midas`:

```
$ cp valec.py test 
$ cd ..
$ cp Valestrom.jar Midas
$ cd Midas
$ python3 -m unittest -f
```
You should see an output like:

Using valec from .. Set GENPATH env var if this is incorrect
......................................................................................................................................................................................................................................................................................................................................................................
----------------------------------------------------------------------
Ran 358 tests in 974.673s

### Building from source on Windows

We recommend using Mac or Linux instead, because they don't require building all of LLVM.
Compiling on Windows is quite involved, come by the discord and we can walk you through it.


# Notes

Vale started in January 2013, and back then we called it "VLang", though there's now another language with that name. We then called it GelLLVM, in honor of Gel, the first language to offer constraint references. Since then, we've settled on the name "Vale". Note that Vale and Vala are two different languages.
