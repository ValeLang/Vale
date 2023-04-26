
echo Downloading minimal LLVM...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/llvm/llvm-project/releases/download/llvmorg-16.0.0/LLVM-16.0.0-win64.exe' -OutFile '%temp%\LLVM-16.0.0-win64.exe'"
mkdir %1
Rem See https://stackoverflow.com/questions/16087674/how-can-i-make-my-nsis-silent-installer-block-until-complete
Rem This command might hang indefinitely if there's a problem.
start "" /WAIT runas.exe /savecred /user:administrator "'%temp%\LLVM-16.0.0-win64.exe' /S /D \"%1\""
dir %1

echo Downloading bootstrapping Vale compiler...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://github.com/ValeLang/Vale/releases/download/v0.2.0/Vale-Windows-0.2.0.26.zip' -OutFile '%temp%\BootstrappingValeCompiler.zip'"
mkdir %2
tar xf "%temp%\BootstrappingValeCompiler.zip" -C %2
