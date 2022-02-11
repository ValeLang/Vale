
echo Downloading minimal LLVM...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://firebasestorage.googleapis.com/v0/b/valesite.appspot.com/o/llvm-install-minimal.zip?alt=media&token=7e73117d-5319-4a78-bfba-04f8d3b91060' -OutFile '%temp%\llvm-install-minimal.zip'"
mkdir %1
tar xf "%temp%\llvm-install-minimal.zip" -C %1

echo Downloading bootstrapping Vale compiler...

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://vale.dev/releases/ValeCompiler-0.1.3.4-Win.zip' -OutFile '%temp%\ValeCompiler-0.1.3.4-Win.zip'"
mkdir %2
tar xf "%temp%\ValeCompiler-0.1.3.4-Win.zip" -C %2

echo Downloading stdlib...

git clone https://github.com/ValeLang/stdlib %3
