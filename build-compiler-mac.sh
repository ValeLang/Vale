# This script builds the Vale compiler, runs some tests on it, and also packages up a release zip file.
# It assumes we've already ran install-compiler-prereqs-mac.sh, or otherwise installed all the dependencies.

cd Valestrom

sbt assembly

cd ../Midas

LLVM_CMAKE_DIR="/usr/local/Cellar/llvm@11/`ls /usr/local/Cellar/llvm@11`/lib/cmake/llvm"

cmake -B build -D LLVM_DIR="$LLVM_CMAKE_DIR"

cd build

make

cd ..

python3 -m unittest -f -k assist
if [ ! $? -eq 0 ]; then
  echo "Running tests failed, aborting!"
  exit 1
fi

cd ../scripts

./package-unix.sh

cd ../release-unix

zip -r ValeCompiler.zip *

