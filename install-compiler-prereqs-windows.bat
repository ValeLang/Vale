
powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://firebasestorage.googleapis.com/v0/b/valesite.appspot.com/o/llvm-install-minimum.zip?alt=media&token=1022ffea-c43b-4fea-a5e9-696c6f0d0175' -OutFile '%temp%\llvm-install-minimum.zip'"
mkdir %1
tar xf "%temp%\llvm-install-minimum.zip" -C %1

powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://vale.dev/releases/ValeCompiler-0.1.3.4-Win.zip' -OutFile '%temp%\ValeCompiler-0.1.3.4-Win.zip'"
mkdir %2
tar xf "%temp%\ValeCompiler-0.1.3.4-Win.zip" -C %2

PATH=%PATH%;C:\Program Files\7-Zip
PATH=%PATH%;C:\Program Files\7-Zip\7z.exe
