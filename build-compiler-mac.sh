# This script builds the Vale compiler, runs some tests on it, and also packages up a release zip file.
# It assumes we've already ran install-compiler-prereqs-mac.sh, or otherwise installed all the dependencies.

git clone --single-branch ${1:-https://github.com/ValeLang/stdlib} --branch ${2:-master}

cd Valestrom

echo Compiling Valestrom...
sbt assembly || { echo 'Valestrom build failed, aborting.' ; exit 1; }

cd ../Midas

LLVM_CMAKE_DIR="/usr/local/Cellar/llvm@11/`ls /usr/local/Cellar/llvm@11`/lib/cmake/llvm"

cmake -B build -D LLVM_DIR="$LLVM_CMAKE_DIR" || { echo 'Midas build failed, aborting.' ; exit 1; }

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
build/tester build --valestrom_dir_override ../Valestrom --midas_dir_override ../Midas/build --builtins_dir_override ../Midas/src/builtins --valec_dir_override ../Driver/build --midas_tests_dir ../Midas/test --concurrent 6 @assist || { echo 'Tests failed, aborting.' ; exit 1; }

cd ../scripts

./package-unix.sh

cd ../release-unix

zip -r ValeCompiler.zip *

