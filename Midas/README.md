# Vale

Compiler for the Vale programming language - http://vale.dev/

## Building and Running Vale in Terminal (OSX)

- get llvm 7 and set LDFLAGS, CPPFLAGS, PATH env vars:

```
$ brew install llvm@7
$ export LDFLAGS="-L/usr/local/opt/llvm@7/lib -Wl,-rpath,/usr/local/opt/llvm@7/lib"
$ export CPPFLAGS="-I/usr/local/opt/llvm@7/include"
$ export PATH=/usr/local/opt/llvm@7/bin:$PATH
```

- generate the build files, and use it to build valec:

```
$ cmake -B cmake-build-debug
$ cd cmake-build-debug
$ make
```

- run functional tests:

```
$ cd ../test
$ python3 -m unittest -f
```

## Building and Running Vale in CLion (OSX)

- get llvm 7
```
$ brew install llvm@7
```

- apply default .idea configuration. 
```
$ git merge --no-ff origin/idea_config
$ git reset HEAD~1
```
The CMake environment should point to an llvm-7 installation *similar* to the `export` commands in the Terminal variant instructions above. See Preferences -> Build, Execution, Deployment -> CMake for details or if you need to modify the environment. If you must clean the repo, you may prefer `git clean -xfd -e .idea` from the top-level

- open CLion and open Midas through CMakeLists.txt

- Select the `valec|Debug` configuration (upper right toolbar) and build it by clicking the control that looks like a hammer (left of configuration dropdown)

- Select the `Unittests in tests` configuration and run it by clicking on the control that looks like a triangle/play button (right of configuration dropdown)

## Emitting Source To LLVM Assembly

```
$ clang -c -emit-llvm foo.cpp -o foo.bc && llvm-dis -o foo.ll foo.bc 
```
