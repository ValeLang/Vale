
echo Downloading minimal LLVM...
powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/llvm/llvm-project/releases/download/llvmorg-16.0.0/llvm-project-16.0.0.src.tar.xz' -OutFile '%temp%\llvm-project-16.0.0.src.tar.xz'"
mkdir "%temp%\LLVM"
cd "%temp%\LLVM"
tar xf "%temp%\llvm-project-16.0.0.src.tar.xz" -C "%temp%\LLVM" && exit /b 1
dir %1 && exit /b 1
cmake "%temp%\LLVM" -G "Visual Studio 17 2022" -Thost=x64 -A x64 -D "CMAKE_INSTALL_PREFIX=%temp%\LLVM" -D CMAKE_BUILD_TYPE=MinSizeRel -D LLVM_TARGETS_TO_BUILD="X86;WebAssembly" -DCMAKE_INSTALL_PREFIX=%1 && exit /b 1
cmake --build . --target install && exit /b 1

echo Downloading bootstrapping Vale compiler...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/ValeLang/Vale/releases/download/v0.2.0/Vale-Windows-0.2.0.23.zip' -OutFile '%temp%\BootstrappingValeCompiler.zip'"
mkdir %2
tar xf "%temp%\BootstrappingValeCompiler.zip" -C %2
