cd C:\Users\Valerian

git clone https://github.com/ValeLang/Vale

cd Vale/Valestrom

sbt assembly

cd ..

cd Midas

cmake -B build -D LLVM_DIR="C:\Users\Valerian\llvm11\lib\cmake\llvm"

cd build

cmake --build .

cd ..

copy C:\Users\Valerian\llvm11\Debug\bin\LLVM-C.dll .

python -m unittest -f -k assist

cd ..\scripts

package-windows.bat

cd ..\release-windows

zip ValeCompiler.zip *
