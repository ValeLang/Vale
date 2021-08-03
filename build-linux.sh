
cd Valestrom

sbt assembly

cd ../Midas

cmake -B build -D LLVM_DIR="~/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10/lib/cmake/llvm"

cd build

make

cd ..

python3 -m unittest -f -k assist

cd ../scripts

./package-unix.sh

cd ../release-unix

zip -r ValeCompiler.zip *

