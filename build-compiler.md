# Building the Compiler

Here's how to build the Vale compiler.


## Ubuntu

```sh
sudo apt install -y git
git clone --single-branch --branch master https://github.com/ValeLang/Vale
Vale/install-compiler-prereqs-linux.sh ~/LLVMForVale ~/BootstrappingValeCompiler ~/stdlib
cd Vale
./build-compiler-linux.sh ~/LLVMForVale/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10 ~/BootstrappingValeCompiler ~/stdlib
```


## Mac

```sh
git clone --single-branch --branch master https://github.com/ValeLang/Vale
Vale/install-compiler-prereqs-mac.sh ~/BootstrappingValeCompiler ~/stdlib
source ~/.zshrc
cd Vale
./build-compiler-mac.sh ~/BootstrappingValeCompiler ~/stdlib
```


## Windows

It's much easier to build on Mac or Linux, because [the Windows LLVM release is broken](https://bugs.llvm.org/show_bug.cgi?id=28677) and must be built from scratch. This takes hours, so we recommend using Mac or Linux instead.

If you still want to compile the compiler on Windows, keep reading.


### Dependencies

 1. Install Visual Studio
 1. Install python 3, remember to check the box to add it to the path
 1. Install git: https://git-scm.com/download/win
 1. Install sbt: https://github.com/sbt/sbt/releases (look for the .msi on that page)
 1. Install java: https://adoptopenjdk.net/
 1. Install 7-zip: https://www.7-zip.org/download.html
 1. Install the previous version of the vale compiler: https://vale.dev/download
 1. Build LLVM, see next section.
 1. Build the compiler, which is the section after next.


### Build LLVM

If you want to skip this, you can download and extract [this file](https://firebasestorage.googleapis.com/v0/b/valesite.appspot.com/o/llvm-install-minimum.zip?alt=media&token=1022ffea-c43b-4fea-a5e9-696c6f0d0175) to `C:\llvm`. **Disclaimer**: Download at your own risk, this is a stripped down and zipped up version of what we got from the below steps.

Ensure your machine (or VM) has sufficient resources: 5 cores, 10gb ram, 200gb disk. You'll be building all of LLVM, which is quite resource intensive.

Download [LLVM sources](https://github.com/llvm/llvm-project/releases).

Unzip to e.g. `C:\llvm-project-llvmorg-11.0.1`.

Depending on where visual studio is:

 * If in Program Files (x86), use the program "x86_64 Cross Tools Command Prompt for VS 2019"
 * If in Program Files, use the program "Developer Command Prompt for VS 2019"

`mkdir C:\llvm`

`cd C:\llvm`

`cmake "C:\llvm-project-llvmorg-11.0.1\llvm" -D "CMAKE_INSTALL_PATH=C:\llvm" -D CMAKE_BUILD_TYPE=Release -G "Visual Studio 16 2019" -Thost=x64 -A x64`

`cmake --build .`

`cmake --build . --target install`


### Build the Compiler

Once youve done the above steps and installed LLVM, run the below commands:

```sh
git clone https://github.com/ValeLang/Vale --single-branch --branch master
git clone https://github.com/ValeLang/stdlib --single-branch --branch master
cd Vale
.\\build-compiler-windows.bat C:\\llvm C:\\OldValeCompiler C:\\stdlib
```
