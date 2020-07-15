# Vale Compiler

Compiler for the Vale programming language.

## Vale

Vale is the fast, safe, and easy programming language. It uses single ownership with constraint references for memory safety without garbage collection, and an emphasis on modern, readable syntax.

See http://vale.dev/ for samples and more information on the language!

## Building and Running

### Linux

#### Linux Terminal

1: Change directory

```
$ cd Midas
```

2: Install LLVM 7.

```
$ sudo apt-get install llvm-7-dev
```

3: Generate the build files, and use it to build Midas:

```
$ cmake -B cmake-build-debug
$ cd cmake-build-debug
$ make
```

4: Run tests:

```
$ cd ../test
$ python3 -m unittest -f
```

5. Run compiler:
```
$ python3 valec.py test/tests/roguelike.vale
```

### OSX

#### OSX Terminal

1: Change directory

```
$ cd Midas
```

2: Install LLVM 7 and set LDFLAGS, CPPFLAGS, PATH env vars:

```
$ brew install llvm@7
$ export LDFLAGS="-L/usr/local/opt/llvm@7/lib -Wl,-rpath,/usr/local/opt/llvm@7/lib"
$ export CPPFLAGS="-I/usr/local/opt/llvm@7/include"
$ export PATH=/usr/local/opt/llvm@7/bin:$PATH
```

3: Generate the build files, and use it to build Midas:

```
$ cmake -B cmake-build-debug
$ cd cmake-build-debug
$ make
```

4: Run tests:

```
$ cd ../test
$ python3 -m unittest -f
```

5. Run compiler:
```
$ python3 valec.py test/tests/roguelike.vale
```

#### OSX CLion

1: Install LLVM 7.

```
$ brew install llvm@7
```

2: Apply default .idea configuration.
 
```
$ git merge --no-ff origin/idea_config
$ git reset HEAD~1
```

The CMake environment should point to an llvm-7 installation *similar* to the `export` commands in the Terminal variant instructions above. See Preferences -> Build, Execution, Deployment -> CMake for details or if you need to modify the environment. If you must clean the repo, you may prefer `git clean -xfd -e .idea` from the top-level

- Open CLion and open Midas through CMakeLists.txt

- Select the `valec|Debug` configuration (upper right toolbar) and build it by clicking the control that looks like a hammer (left of configuration dropdown)

- Select the `Unittests in tests` configuration and run it by clicking on the control that looks like a triangle/play button (right of configuration dropdown)

# Notes

Vale started in January 2013, and back then we called it "VLang", though there's now another language with that name. We then called it GelLLVM, in honor of Gel, the first language to offer constraint references. Since then, we've settled on the name "Vale". Note that Vale and Vala are two different languages.
