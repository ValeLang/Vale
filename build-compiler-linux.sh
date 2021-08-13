# This script builds the Vale compiler, runs some tests on it, and also packages up a release zip file.
# It assumes we've already ran prereqs-linux.sh, or otherwise installed all the dependencies.

git clone --single-branch ${2:-https://github.com/ValeLang/stdlib} --branch ${3:-master}

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

echo Compiling Valestrom...
sbt assembly || { echo 'Valestrom build failed, aborting.' ; exit 1; }

cd ../Midas

cmake -B build -D LLVM_DIR="$LLVM_CMAKE_DIR"

cd build

echo Compiling Midas...
make

cd ../../Driver

echo Compiling Driver...
./build.sh || { echo 'Driver build failed, aborting.' ; exit 1; }

cd ../Tester

echo Compiling Tester...
./build.sh || { echo 'Tester build failed, aborting.' ; exit 1; }

echo Running Tester...
build/tester --valestrom_dir_override ../Valestrom --midas_dir_override ../Midas/build --builtins_dir_override ../Midas/src/builtins --valec_dir_override ../Driver/build --midas_tests_dir ../Midas/test --concurrent 6 @assist || { echo 'Tests failed, aborting.' ; exit 1; }

cd ../scripts

./package-unix.sh

cd ../release-unix

zip -r ValeCompiler.zip *
