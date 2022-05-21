
echo Downloading minimal LLVM...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/Verdagon/LLVM13WinMinimal/releases/download/v1.2/LLVM13ForVale.zip' -OutFile '%temp%\LLVM13ForVale.zip'"
mkdir %1
tar xf "%temp%\LLVM13ForVale.zip" -C %1

echo Downloading bootstrapping Vale compiler...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/ValeLang/Vale/releases/download/v0.2.0/Vale-Windows-0.2.0.11.zip' -OutFile '%temp%\BootstrappingValeCompiler.zip'"
mkdir %2
tar xf "%temp%\BootstrappingValeCompiler.zip" -C %2
