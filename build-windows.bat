
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

zip ValeCompiler.zip *
