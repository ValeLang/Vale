
cd Valestrom

call sbt assembly

cd ..

cd Midas

cmake -B build -D LLVM_DIR="C:\llvm-install-minimum\lib\cmake\llvm"

cd build

cmake --build .

cd ..

copy C:\llvm-install-minimum\bin\LLVM-C.dll .

python -m unittest -f -k assist

cd ..\scripts

call package-windows.bat

cd ..\release-windows

PATH=%PATH%;C:\Program Files\7-Zip\7z.exe
7z a ValeCompiler.zip *
