# Building the Compiler

This page describes how to build the Vale compiler.

Note that the below instructions don't build LLVM from source, but we highly recommend it. Building LLVM from source will enable its debug-only checks, which help immensely when modifying the compiler.


## Ubuntu

```sh
sudo apt install -y git
git clone --single-branch --branch master https://github.com/ValeLang/Vale
# Add the -j and -s flags to the below command to also install Java and SBT from external APT repositories
Vale/scripts/ubuntu/install-compiler-prereqs.sh -l LLVMForVale -b BootstrappingValeCompiler
cd Vale
./scripts/ubuntu/build-compiler.sh "$PWD/../LLVMForVale/clang+llvm-16.0.0-x86_64-linux-gnu-ubuntu-18.04" "$PWD/../BootstrappingValeCompiler" --test=all ./scripts/VERSION

```


## Mac

```sh
git clone --single-branch --branch master https://github.com/ValeLang/Vale
Vale/scripts/mac/install-compiler-prereqs.sh ~/BootstrappingValeCompiler
source ~/.zshrc
cd Vale
./scripts/mac/build-compiler.sh ~/BootstrappingValeCompiler --test=all ./scripts/VERSION
```


## Windows

One *must* build LLVM from source, because [the Windows LLVM release is broken](https://bugs.llvm.org/show_bug.cgi?id=28677).


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

If you want to skip this, you can download and extract [this file](https://github.com/Verdagon/LLVMWinMinimal/releases/download/14.0.6.0/llvm-project-llvmorg-14.0.6.zip) to `C:\llvm`. **Disclaimer**: Download at your own risk; we made this .zip file by building it, stripping it down, and merging the include files to fix the problems with the [regular LLVM windows release](https://bugs.llvm.org/show_bug.cgi?id=28677).

Ensure your machine (or VM) has sufficient resources: 5 cores, 10gb ram, 200gb disk. You'll be building all of LLVM, which is quite resource intensive.

Download [LLVM sources](https://github.com/llvm/llvm-project/releases).

Unzip to e.g. `C:\llvm-project-llvmorg-11.0.1`.

Depending on where visual studio is:

 * If in Program Files (x86), use the program "x86_64 Cross Tools Command Prompt for VS 2019"
 * If in Program Files, use the program "Developer Command Prompt for VS 2019"

`cd C:\llvm-project-llvmorg-13.0.1`

`mkdir build`

`cd build`

For when building a distributable (such as for CI) LLVM release: the built LLVM (in the `build` directory here) **will reference things in the original source directory** (the `C:\llvm-project-llvmorg-13.0.1` here). This is why we make a `build` subdirectory under the source directory; we can then package it all up together and distribute it as one archive.

`cmake "C:\llvm-project-llvmorg-13.0.1\llvm" -D "CMAKE_INSTALL_PREFIX=C:\llvm-project-llvmorg-13.0.1\build" -D CMAKE_BUILD_TYPE=Release -G "Visual Studio 17 2022" -Thost=x64 -A x64`

`cmake --build .`

`cmake --build . --target install`


### Build the Compiler

Once youve done the above steps and installed LLVM, run the below commands:

```sh
git clone https://github.com/ValeLang/Vale --single-branch --branch master
cd Vale
.\scripts\windows\build-compiler.bat C:\llvm\llvm-project-llvmorg-13.0.1 C:\OldValeCompiler --test=all ./scripts/VERSION
```

If you get an error "fatal error LNK1112: module machine type 'x86' conflicts with target machine type 'x64'" and you're running in the shell "x64_x86 Cross Tools Command Prompt for VS 2022", try instead running in the shell "x64 Native Tools Command Prompt for VS 2022".


## For development

If working on the Vale compiler, it's best to:

 * Build LLVM from scratch, in debug mode.
 * Use CLion.
    * [Build with a profile](https://www.jetbrains.com/help/clion/cmake-profile.html#CMakeProfileSwitcher), with an environment variable `LLVM_DIR=(llvm build dir)`. [Verify in the CMake log](https://stackoverflow.com/a/34772936/1424454) looking for "Using LLVMConfig.make in (llvm build dir)".
