# This script builds the Vale compiler, runs some tests on it, and also packages up a release zip file.
# It assumes we've already ran prereqs-linux.sh, or otherwise installed all the dependencies.

LLVM_DIR="$1"
if [ "$LLVM_DIR" == "" ]; then
  echo "Please supply the LLVM directory."
  echo "Example: ~/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10"
  exit
fi

LLVM_CMAKE_DIR="$LLVM_DIR/lib/cmake/llvm"
if [ ! -d "$LLVM_CMAKE_DIR" ]; then
  echo "Directory not found: $LLVM_CMAKE_DIR"
  echo "Are you sure you specified the right LLVM directory?"
  exit
fi

BOOTSTRAPPING_VALEC_DIR="$2"
if [ "$BOOTSTRAPPING_VALEC_DIR" == "" ]; then
  echo "Please supply the bootstrapping valec directory."
  echo "Example: ~/ValeCompiler-0.1.3.3-Ubuntu"
  exit
fi


cd Valestrom

echo Compiling Valestrom...
sbt assembly || { echo 'Valestrom build failed.' ; exit 1; }

cd ../Midas

echo Generating Midas...
cmake -B build -D LLVM_DIR="$LLVM_CMAKE_DIR"

cd build

echo Compiling Midas...
make

cd ../../Driver

echo Compiling Driver...
./build.sh $BOOTSTRAPPING_VALEC_DIR || { echo 'Driver build failed.' ; exit 1; }

cd ../scripts


rm -rf ../release-unix || { echo 'Error removing previous release-unix dir.' ; exit 1; }
mkdir -p ../release-unix || { echo 'Error making new release-unix dir.' ; exit 1; }
mkdir -p ../release-unix/samples || { echo 'Error making new samples dir.' ; exit 1; }
cp ../Valestrom/Valestrom.jar ../release-unix || { echo 'Error copying into release-unix.' ; exit 1; }
cp -r ../Valestrom/Tests/test/main/resources/programs ../release-unix/samples || { echo 'Error copying into release-unix.' ; exit 1; }
cp -r ../Midas/src/builtins ../release-unix/builtins || { echo 'Error copying into release-unix.' ; exit 1; }
cp releaseREADME.txt ../release-unix/README.txt || { echo 'Error copying into release-unix.' ; exit 1; }
cp valec-* ../release-unix || { echo 'Error copying into release-unix.' ; exit 1; }
cp ../Midas/build/midas ../release-unix/midas || { echo 'Error copying into release-unix.' ; exit 1; }
cp -r ../stdlib ../release-unix/stdlib || { echo 'Error copying into release-unix.' ; exit 1; }
cp -r helloworld ../release-unix/samples/helloworld || { echo 'Error copying into release-unix.' ; exit 1; }
cp ../Driver/build/valec ../release-unix/valec || { echo 'Error copying into release-unix.' ; exit 1; }
cd ../release-unix || { echo 'Error copying into release-unix.' ; exit 1; }
zip -r ValeCompiler.zip * || { echo 'Error copying into release-unix.' ; exit 1; }


cd ../Tester

rm -rf ./BuiltValeCompiler
unzip ../release-unix/ValeCompiler.zip -d ./BuiltValeCompiler

echo Compiling Tester...
./build.sh $BOOTSTRAPPING_VALEC_DIR || { echo 'Tester build failed.' ; exit 1; }

echo Running Tester...
./build/testvalec --valestrom_path ./BuiltValeCompiler/Valestrom.jar --midas_path ./BuiltValeCompiler/midas --builtins_dir ./BuiltValeCompiler/builtins --valec_path ./BuiltValeCompiler/valec --midas_tests_dir ../Midas/test --valestrom_tests_dir ../Valestrom --concurrent 6 @assist || { echo 'Tests failed.' ; exit 1; }

cd ..
