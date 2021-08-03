# This script builds the Vale compiler, runs some tests on it, and also packages up a release zip file.
# It assumes we've already ran prereqs-linux.sh, or otherwise installed all the dependencies.

LLVM_DIR="$1"
if [ "$LLVM_DIR" == "" ]; then
  echo "Please supply the LLVM directory."
  echo "Example: $0 ~/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10"
  exit
fi

LLVM_CMAKE_DIR="$LLVM_DIR/lib/cmake/llvm"
if [ ! -d "$LLVM_CMAKE_DIR" ]; then
  echo "Directory not found: $LLVM_CMAKE_DIR"
  echo "Are you sure you specified the right LLVM directory?"
  exit
fi


cd Valestrom

sbt assembly

cd ../Midas

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

