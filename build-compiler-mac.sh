# This script builds the Vale compiler, runs some tests on it, and also packages up a release zip file.
# It assumes we've already ran install-compiler-prereqs-mac.sh, or otherwise installed all the dependencies.

BOOTSTRAPPING_VALEC_DIR="$1"
if [ "$BOOTSTRAPPING_VALEC_DIR" == "" ]; then
  echo "Please supply the bootstrapping valec directory."
  echo "Example: ~/ValeCompiler-0.1.3.3-Ubuntu"
  exit
fi

source ~/.zshrc

cd Valestrom

echo Compiling Valestrom...
sbt assembly || { echo 'Valestrom build failed, aborting.' ; exit 1; }

cd ../Midas

echo Generating Midas...
LLVM_CMAKE_DIR="/usr/local/Cellar/llvm@11/`ls /usr/local/Cellar/llvm@11`/lib/cmake/llvm"
cmake -B build -D LLVM_DIR="$LLVM_CMAKE_DIR" || { echo 'Midas build failed, aborting.' ; exit 1; }

cd build

echo Compiling Midas...
make

cd ../../Driver

echo Compiling Driver...
./build.sh $BOOTSTRAPPING_VALEC_DIR || { echo 'Driver build failed, aborting.' ; exit 1; }

cd ../Tester

echo Compiling Tester...
./build.sh $BOOTSTRAPPING_VALEC_DIR || { echo 'Tester build failed, aborting.' ; exit 1; }

cd ../scripts

./package-unix.sh

cd ..

unzip ./release-unix/ValeCompiler.zip -d ./BuiltValeCompiler

echo Running Tester...
Tester/build/testvalec --valestrom_dir_override ./BuiltValeCompiler --midas_dir_override ./BuiltValeCompiler --builtins_dir_override ./BuiltValeCompiler/builtins --valec_dir_override ./BuiltValeCompiler --midas_tests_dir ../Midas/test --concurrent 6 @assist || { echo 'Tests failed, aborting.' ; exit 1; }
