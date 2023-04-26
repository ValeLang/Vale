
echo Downloading minimal LLVM...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/Verdagon/LLVMWinMinimal/releases/download/v1.3/LLVM16.zip' -OutFile '%temp%\LLVM16.zip'"
mkdir %1
tar xf "%temp%\LLVM16.zip" -C %1
dir %1

echo Downloading bootstrapping Vale compiler...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/ValeLang/Vale/releases/download/v0.2.0/Vale-Windows-0.2.0.23.zip' -OutFile '%temp%\BootstrappingValeCompiler.zip'"
mkdir %2
tar xf "%temp%\BootstrappingValeCompiler.zip" -C %2
