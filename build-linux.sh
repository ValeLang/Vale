## Building the Vale Compiler

cd Valestrom

sbt assembly

cd ../Midas

cmake -D CMAKE_CXX_COMPILER=clang++-11 -B build

cd build

make

cd ..

python3 -m unittest -f -k assist

cd ../scripts

./package-unix.sh

cd ../release-unix

zip -r ValeCompiler.zip *

