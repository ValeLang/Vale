# Building the Vale Compiler (Windows)

It's much easier to build on Mac or Linux, because [the Windows LLVM release is broken](https://bugs.llvm.org/show_bug.cgi?id=28677) and must be built from scratch. This takes hours, so we recommend using Mac or Linux instead.

If you still want to compile the compiler on Windows, keep reading.


## Prerequisites

### Dependencies

 * Install Visual Studio.
 * Install python 3, remember to check the box to add it to the path
 * [Install git](https://git-scm.com/download/win).
 * [Install sbt](https://github.com/sbt/sbt/releases) (look for the .msi on that page)
 * [Install java](https://adoptopenjdk.net/)


### Build LLVM

Ensure your machine (or VM) has sufficient resources: 5 cores, 10gb ram, 200gb disk. You'll be building all of LLVM, which is quite resource intensive.

Download [LLVM sources](https://github.com/llvm/llvm-project/releases).

Unzip to e.g. C:\Users\Valerian\Downloads\llvm-project-llvmorg-11.0.1

Depending on where visual studio is:

 * If in Program Files (x86), use the program "x86_64 Cross Tools Command Prompt for VS 2019"
 * If in Program Files, use the program "Developer Command Prompt for VS 2019"

`mkdir C:\Users\Valerian\llvm11`

`cd C:\Users\Valerian\llvm11`

`cmake "C:\Users\Valerian\Downloads\llvm-project-llvmorg-11.0.1\llvm" -D "CMAKE_INSTALL_PATH=C:\Users\Valerian\llvm11" -D CMAKE_BUILD_TYPE=Release -G "Visual Studio 16 2019" -Thost=x64 -A x64`

`cmake --build .`

`cmake --build . --target install`


## Vale Compiler

cd C:\Users\Valerian

git clone https://github.com/ValeLang/Vale

cd Vale/Valestrom

sbt assembly

cd ..

cd Midas

cmake -B build -D LLVM_DIR="C:\Users\Valerian\llvm11\lib\cmake\llvm"

cd build

cmake --build .

cd ..

copy C:\Users\Valerian\llvm11\Debug\bin\LLVM-C.dll .

python -m unittest -f -k assist

cd ..\scripts

package-windows.bat

cd ..\release-windows

zip ValeCompiler.zip *
