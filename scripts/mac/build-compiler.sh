# This script builds the Vale compiler, runs some tests on it, and also packages up a release zip file.
# It assumes we've already ran install-compiler-prereqs-mac.sh, or otherwise installed all the dependencies.

BOOTSTRAPPING_VALEC_DIR="$1"
if [ "$BOOTSTRAPPING_VALEC_DIR" == "" ]
then
  echo "Please supply the bootstrapping valec directory."
  echo "Example: ~/ValeCompiler-0.1.3.3-Ubuntu"
  exit 1
fi
shift;

LLVM_MAJOR_VER=16

WHICH_TESTS="$1"
if [ "$WHICH_TESTS" == "--test=none" ]
then
  WHICH_TESTS="none"
elif [ "$WHICH_TESTS" == "--test=smoke" ]
then
  WHICH_TESTS="smoke"
elif [ "$WHICH_TESTS" == "--test=all" ]
then
  WHICH_TESTS="all"
else
  echo "Please specify which tests to run afterward, either: --test=none, --test=smoke, --test=all"
  exit 1
fi
shift;


VALEC_VERSION=`cat "$1"`
if [ "$VALEC_VERSION" == "" ]
then
  echo "Please specify a file containing the new version."
  exit 1
fi
shift;

LLVM_OUTER_DIR=$1
if [ "$LLVM_OUTER_DIR" == "" ]
then
  LLVM_PREFIX=`brew --prefix llvm@$LLVM_MAJOR_VER`
  LLVM_DIR=`greadlink -f $LLVM_PREFIX`

  if [ ! -d "$LLVM_DIR" ]
  then
    echo "No LLVM override specified, and couldn't find brew cellar for LLVM."
    exit 1
  fi

  LLVM_CMAKE_DIR="$LLVM_DIR/lib/cmake/llvm"
  if [ ! -d "$LLVM_CMAKE_DIR" ]
  then
    echo "$LLVM_DIR doesn't contain ./lib/cmake/llvm!"
    exit 1
  fi
else
  LLVM_CMAKE_DIR="$LLVM_OUTER_DIR/lib/cmake/llvm"
  shift;
fi


if [ ! -d "$LLVM_CMAKE_DIR" ]
then
  echo "$LLVM_CMAKE_DIR doesn't exist!"
  exit 1
fi
echo "Using LLVM dir $LLVM_CMAKE_DIR"


touch ~/.zshrc
source ~/.zshrc

cd Frontend

echo Compiling Frontend...
sbt assembly || { echo 'Frontend build failed, aborting.' ; exit 1; }

cd ../Backend

echo Generating Backend...
echo cmake -B build -DLLVM_DIR="$LLVM_CMAKE_DIR"
cmake -B build -DLLVM_DIR="$LLVM_CMAKE_DIR" || { echo 'Backend generate failed, aborting.' ; exit 1; }

echo Compiling Backend...
cmake --build build || { echo 'Backend build failed, aborting.' ; exit 1; }

cd ../Coordinator

echo Compiling Coordinator...
./build.sh $BOOTSTRAPPING_VALEC_DIR || { echo 'Coordinator build failed, aborting.' ; exit 1; }

cd ../scripts


rm -rf ../release-mac || { echo 'Error removing previous release-mac dir.' ; exit 1; }
mkdir -p ../release-mac || { echo 'Error making new release-mac dir.' ; exit 1; }
# mkdir -p ../release-mac/samples || { echo 'Error making new samples dir.' ; exit 1; }
cp ../Frontend/Frontend.jar ../release-mac || { echo 'Error copying into release-mac.' ; exit 1; }
# cp -r ../Frontend/Tests/test/main/resources/programs ../release-mac/samples || { echo 'Error copying into release-mac.' ; exit 1; }
cp -r ../Backend/builtins ../release-mac/builtins || { echo 'Error copying into release-mac.' ; exit 1; }
cp ../Backend/build/backend ../release-mac/backend || { echo 'Error copying into release-mac.' ; exit 1; }
cp -r ../stdlib ../release-mac/stdlib || { echo 'Error copying into release-mac.' ; exit 1; }
cp ../Coordinator/build/valec ../release-mac/valec || { echo 'Error copying into release-mac.' ; exit 1; }

cat all/README | sed s/\{valec_exe\}/.\\\/valec/g | sed s/\{sep\}/\\/\/g | sed s/\{valec_version\}/$VALEC_VERSION/g > ../release-mac/README || { echo 'Error copying into release-mac.' ; exit 1; }
cat all/valec-help-build.txt | sed s/\{valec_exe\}/.\\\/valec/g | sed s/\{sep\}/\\/\/g | sed s/\{valec_version\}/$VALEC_VERSION/g > ../release-mac/valec-help-build.txt || { echo 'Error copying into release-mac.' ; exit 1; }
cat all/valec-help.txt | sed s/\{valec_exe\}/.\\\/valec/g | sed s/\{sep\}/\\/\/g | sed s/\{valec_version\}/$VALEC_VERSION/g > ../release-mac/valec-help.txt || { echo 'Error copying into release-mac.' ; exit 1; }
cat all/valec-version.txt | sed s/\{valec_exe\}/.\\\/valec/g | sed s/\{sep\}/\\/\/g | sed s/\{valec_version\}/$VALEC_VERSION/g > ../release-mac/valec-version.txt || { echo 'Error copying into release-mac.' ; exit 1; }
# cp -r all/helloworld ../release-mac/samples/helloworld || { echo 'Error copying into release-mac.' ; exit 1; }

cd ../release-mac || { echo 'Error copying into release-mac.' ; exit 1; }
zip -r Vale-Mac-0.zip * || { echo 'Error copying into release-mac.' ; exit 1; }

if [ "$WHICH_TESTS" == "all" ]
then
  cd ../Tester

  rm -rf ./BuiltValeCompiler
  unzip ../release-mac/Vale-Mac-0.zip -d ./BuiltValeCompiler

  echo Compiling Tester...
  ./build.sh $BOOTSTRAPPING_VALEC_DIR || { echo 'Tester build failed, aborting.' ; exit 1; }

  echo Running Tester...
  ./build/testvalec --frontend_path ./BuiltValeCompiler/Frontend.jar --backend_path ./BuiltValeCompiler/backend --builtins_dir ./BuiltValeCompiler/builtins --valec_path ./BuiltValeCompiler/valec --backend_tests_dir ../Backend/test --frontend_tests_dir ../Frontend --stdlib_dir ./BuiltValeCompiler/stdlib --concurrent 6 @resilient-v3 || { echo 'Tests failed, aborting.' ; exit 1; }
fi

cd ..
