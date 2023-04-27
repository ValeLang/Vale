
echo Downloading LLVM...

mkdir "C:\llvm-tar-xz"
cd "C:\llvm-tar-xz"
powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/llvm/llvm-project/releases/download/llvmorg-16.0.0/llvm-project-16.0.0.src.tar.xz' -OutFile 'C:\llvm-tar-xz\llvm-project-16.0.0.src.tar.xz'"
dir "C:\llvm-tar-xz"

echo Unzipping LLVM...

mkdir "C:\llvm-tar"
cd "C:\llvm-tar"
7z x "C:\llvm-tar-xz\llvm-project-16.0.0.src.tar.xz" -oC:\llvm-tar
dir "C:\llvm-tar"

mkdir "C:\llvm-src"
cd "C:\llvm-src"
7z x "C:\llvm-tar\llvm-project-16.0.0.src.tar" -oC:\llvm-src
dir "C:\llvm-src"
dir "C:\llvm-src\llvm-project-16.0.0.src"
dir "C:\llvm-src\llvm-project-16.0.0.src\llvm"

echo Building LLVM...

mkdir "C:\llvm-src\llvm-project-16.0.0.src\llvm\build"
cd "C:\llvm-src\llvm-project-16.0.0.src\llvm\build"
cmake "C:\llvm-src\llvm-project-16.0.0.src\llvm" -G "Visual Studio 17 2022" -Thost=x64 -A x64 -D "CMAKE_INSTALL_PREFIX=%1" -D CMAKE_BUILD_TYPE=MinSizeRel -D LLVM_TARGETS_TO_BUILD="X86;WebAssembly" -DCMAKE_INSTALL_PREFIX=%1
dir "C:\llvm-src\llvm-project-16.0.0.src\llvm\build"

echo Installing LLVM...

mkdir %1
cmake --build . --target install
dir %1

echo Downloading bootstrapping Vale compiler...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/ValeLang/Vale/releases/download/v0.2.0/Vale-Windows-0.2.0.23.zip' -OutFile 'C:\BootstrappingValeCompiler.zip'"
mkdir %2
tar xf "C:\BootstrappingValeCompiler.zip" -C %2
