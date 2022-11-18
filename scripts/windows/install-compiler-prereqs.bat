
echo Downloading minimal LLVM...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/Verdagon/LLVM13WinMinimal/releases/download/14.0.6.0/llvm-project-llvmorg-14.0.6.zip' -OutFile '%temp%\llvm-project-llvmorg-14.0.6.zip'"
mkdir %1
tar xf "%temp%\llvm-project-llvmorg-14.0.6.zip" -C %1

echo Downloading bootstrapping Vale compiler...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/ValeLang/Vale/releases/download/v0.2.0/Vale-Windows-0.2.0.11.zip' -OutFile '%temp%\BootstrappingValeCompiler.zip'"
mkdir %2
tar xf "%temp%\BootstrappingValeCompiler.zip" -C %2
