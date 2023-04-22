#!/usr/bin/env bash

# This script builds the Vale compiler, runs some tests on it, and also packages up a release zip file.
# It assumes we've already ran prereqs-linux.sh, or otherwise installed all the dependencies.

set -eu

LLVM_DIR="$1"
if [ "$LLVM_DIR" == "" ]; then
  echo "Please supply the LLVM directory."
  echo "Example: ~/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10"
  exit 1
fi
shift;

LLVM_CMAKE_DIR="$LLVM_DIR/lib/cmake/llvm"
if [ ! -d "$LLVM_CMAKE_DIR" ]; then
  echo "Directory not found: $LLVM_CMAKE_DIR"
  echo "Are you sure you specified the right LLVM directory?"
  exit 1
fi

BOOTSTRAPPING_VALEC_DIR="$1"
if [ "$BOOTSTRAPPING_VALEC_DIR" == "" ]; then
  echo "Please supply the bootstrapping valec directory."
  echo "Example: ~/ValeCompiler-0.1.3.3-Ubuntu"
  exit 1
fi
shift;

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
  echo "Please specify the new version."
  exit 1
fi
shift;

cd Frontend

echo Compiling Frontend...
sbt assembly || { echo 'Frontend build failed.' ; exit 1; }

cd ../Backend

echo Generating Backend...
cmake -B build -D LLVM_DIR="$LLVM_CMAKE_DIR" || { echo 'Backend generate failed, aborting.' ; exit 1; }

echo Compiling Backend...
cmake --build build || { echo 'Backend build failed, aborting.' ; exit 1; }

cd ../Coordinator

echo Compiling Coordinator...
/usr/bin/java -cp $BOOTSTRAPPING_VALEC_DIR/Frontend.jar dev.vale.passmanager.PassManager build --output_dir build --sanity_check true stdlib=$BOOTSTRAPPING_VALEC_DIR/stdlib/src coordinator=/Outer/Vale/Coordinator/src valecutils=/Outer/Vale/Utils/src || { echo 'Coordinator build failed.' ; exit 1; }
$BOOTSTRAPPING_VALEC_DIR/backend --verify --output_dir build build/vast/stdlib.mtpid.vast build/vast/stdlib.path.vast build/vast/stdlib.command.vast build/vast/stdlib.vast build/vast/coordinator.vast build/vast/stdlib.stringutils.vast build/vast/__vale.vast build/vast/stdlib.flagger.vast build/vast/stdlib.resultutils.vast build/vast/stdlib.collections.list.vast build/vast/stdlib.os.vast build/vast/stdlib.math.vast --pic || { echo 'Coordinator build failed.' ; exit 1; }
clang -Ibuild/include -O3 -o build/valec -lm build/build.o $BOOTSTRAPPING_VALEC_DIR/builtins/strings.c $BOOTSTRAPPING_VALEC_DIR/builtins/math.c $BOOTSTRAPPING_VALEC_DIR/builtins/weaks.c $BOOTSTRAPPING_VALEC_DIR/builtins/stdio.c $BOOTSTRAPPING_VALEC_DIR/builtins/assert.c $BOOTSTRAPPING_VALEC_DIR/builtins/mainargs.c $BOOTSTRAPPING_VALEC_DIR/builtins/census.c $BOOTSTRAPPING_VALEC_DIR/stdlib/src/mtpid/native/mtpid.c $BOOTSTRAPPING_VALEC_DIR/stdlib/src/path/native/path.c $BOOTSTRAPPING_VALEC_DIR/stdlib/src/command/native/subprocess.c $BOOTSTRAPPING_VALEC_DIR/stdlib/src/os/native/os.c $BOOTSTRAPPING_VALEC_DIR/stdlib/src/math/native/mathnative.c build/abi/stdlib/MtpIdExtern.c build/abi/stdlib/close_stdin.c build/abi/stdlib/read_stdout.c build/abi/stdlib/write_stdin.c build/abi/stdlib/join.c build/abi/stdlib/get_env_var.c build/abi/stdlib/alive.c build/abi/stdlib/destroy.c build/abi/stdlib/launch_command.c build/abi/stdlib/read_stderr.c build/abi/stdlib/fsqrt.c build/abi/stdlib/lshift.c build/abi/stdlib/rshift.c build/abi/stdlib/xor.c build/abi/stdlib/i64.c build/abi/stdlib/IsWindows.c build/abi/stdlib/is_dir.c build/abi/stdlib/readFileAsString.c build/abi/stdlib/IsSymLinkExtern.c build/abi/stdlib/GetEnvPathSeparator.c build/abi/stdlib/writeStringToFile.c build/abi/stdlib/RemoveDirExtern.c build/abi/stdlib/GetTempDirExtern.c build/abi/stdlib/resolve.c build/abi/stdlib/is_file.c build/abi/stdlib/RemoveFileExtern.c build/abi/stdlib/RenameExtern.c build/abi/stdlib/CreateDirExtern.c build/abi/stdlib/AddToPathChildList.c build/abi/stdlib/iterdir.c build/abi/stdlib/exists.c build/abi/stdlib/makeDirectory.c build/abi/stdlib/GetPathSeparator.c build/abi/__vale/substring.c build/abi/__vale/strcmp.c build/abi/__vale/TruncateI64ToI32.c build/abi/__vale/castFloatStr.c build/abi/__vale/streq.c build/abi/__vale/strfromascii.c build/abi/__vale/numMainArgs.c build/abi/__vale/printstr.c build/abi/__vale/castI32Str.c build/abi/__vale/castFloatI32.c build/abi/__vale/getMainArg.c build/abi/__vale/castI32Float.c build/abi/__vale/addStr.c build/abi/__vale/strindexof.c build/abi/__vale/strtoascii.c build/abi/__vale/castI64Str.c || { echo 'Coordinator build failed.' ; exit 1; }


cd ../scripts

rm -rf ../release-ubuntu || { echo 'Error removing previous release-ubuntu dir.' ; exit 1; }
mkdir -p ../release-ubuntu || { echo 'Error making new release-ubuntu dir.' ; exit 1; }
# mkdir -p ../release-ubuntu/samples || { echo 'Error making new samples dir.' ; exit 1; }
cp ../Frontend/Frontend.jar ../release-ubuntu || { echo 'Error copying into release-ubuntu.' ; exit 1; }
# cp -r ../Frontend/Tests/test/main/resources/programs ../release-ubuntu/samples || { echo 'Error copying into release-ubuntu.' ; exit 1; }
cp -r ../Backend/builtins ../release-ubuntu/builtins || { echo 'Error copying into release-ubuntu.' ; exit 1; }
cp ../Backend/build/backend ../release-ubuntu/backend || { echo 'Error copying into release-ubuntu.' ; exit 1; }
cp -r ../stdlib ../release-ubuntu/stdlib || { echo 'Error copying into release-ubuntu.' ; exit 1; }
cp ../Coordinator/build/valec ../release-ubuntu/valec || { echo 'Error copying into release-ubuntu.' ; exit 1; }

cat all/README | sed s/\{valec_exe\}/.\\\/valec/g | sed s/\{sep\}/\\/\/g | sed s/\{valec_version\}/$VALEC_VERSION/g > ../release-ubuntu/README || { echo 'Error copying into release-ubuntu.' ; exit 1; }
cat all/valec-help-build.txt | sed s/\{valec_exe\}/.\\\/valec/g | sed s/\{sep\}/\\/\/g | sed s/\{valec_version\}/$VALEC_VERSION/g > ../release-ubuntu/valec-help-build.txt || { echo 'Error copying into release-ubuntu.' ; exit 1; }
cat all/valec-help.txt | sed s/\{valec_exe\}/.\\\/valec/g | sed s/\{sep\}/\\/\/g | sed s/\{valec_version\}/$VALEC_VERSION/g > ../release-ubuntu/valec-help.txt || { echo 'Error copying into release-ubuntu.' ; exit 1; }
cat all/valec-version.txt | sed s/\{valec_exe\}/.\\\/valec/g | sed s/\{sep\}/\\/\/g | sed s/\{valec_version\}/$VALEC_VERSION/g > ../release-ubuntu/valec-version.txt || { echo 'Error copying into release-ubuntu.' ; exit 1; }
# cp -r all/helloworld ../release-ubuntu/samples/helloworld || { echo 'Error copying into release-ubuntu.' ; exit 1; }

cd ../release-ubuntu || { echo 'Error copying into release-ubuntu.' ; exit 1; }
zip -r Vale-Ubuntu-0.zip * || { echo 'Error copying into release-ubuntu.' ; exit 1; }


if [ "$WHICH_TESTS" == "all" ]
then
  cd ../Tester

  rm -rf ./BuiltValeCompiler
  unzip ../release-ubuntu/Vale-Ubuntu-0.zip -d ./BuiltValeCompiler

  echo Compiling Tester...
  /usr/bin/java -cp $BOOTSTRAPPING_VALEC_DIR/Frontend.jar dev.vale.passmanager.PassManager build --output_dir build --sanity_check true stdlib=$BOOTSTRAPPING_VALEC_DIR/stdlib/src tester=/Outer/Vale/Tester/src valecutils=/Outer/Vale/Utils/src || { echo 'Tester build failed, aborting.' ; exit 1; }
  $BOOTSTRAPPING_VALEC_DIR/backend --verify --output_dir build build/vast/valecutils.command2.vast build/vast/stdlib.path.vast build/vast/stdlib.collections.hashmap.vast build/vast/stdlib.stringutils.vast build/vast/stdlib.vast build/vast/__vale.vast build/vast/stdlib.flagger.vast build/vast/stdlib.resultutils.vast build/vast/stdlib.optutils.vast build/vast/stdlib.collections.list.vast build/vast/stdlib.os.vast build/vast/stdlib.math.vast build/vast/tester.vast --pic || { echo 'Tester build failed, aborting.' ; exit 1; }
  /usr/bin/clang -Ibuild/include -O3 -o build/testvalec -lm build/build.o $BOOTSTRAPPING_VALEC_DIR/builtins/strings.c $BOOTSTRAPPING_VALEC_DIR/builtins/math.c $BOOTSTRAPPING_VALEC_DIR/builtins/weaks.c $BOOTSTRAPPING_VALEC_DIR/builtins/stdio.c $BOOTSTRAPPING_VALEC_DIR/builtins/assert.c $BOOTSTRAPPING_VALEC_DIR/builtins/mainargs.c $BOOTSTRAPPING_VALEC_DIR/builtins/census.c /Vale/Utils/src/command2/native/subprocess.c $BOOTSTRAPPING_VALEC_DIR/stdlib/src/path/native/path.c $BOOTSTRAPPING_VALEC_DIR/stdlib/src/os/native/os.c $BOOTSTRAPPING_VALEC_DIR/stdlib/src/math/native/mathnative.c build/abi/valecutils/close_stdin.c build/abi/valecutils/read_stdout.c build/abi/valecutils/write_stdin.c build/abi/valecutils/join.c build/abi/valecutils/get_env_var.c build/abi/valecutils/alive.c build/abi/valecutils/destroy.c build/abi/valecutils/launch_command.c build/abi/valecutils/read_stderr.c build/abi/stdlib/fsqrt.c build/abi/stdlib/lshift.c build/abi/stdlib/rshift.c build/abi/stdlib/xor.c build/abi/stdlib/i64.c build/abi/stdlib/IsWindows.c build/abi/stdlib/is_dir.c build/abi/stdlib/readFileAsString.c build/abi/stdlib/IsSymLinkExtern.c build/abi/stdlib/GetEnvPathSeparator.c build/abi/stdlib/writeStringToFile.c build/abi/stdlib/RemoveDirExtern.c build/abi/stdlib/GetTempDirExtern.c build/abi/stdlib/resolve.c build/abi/stdlib/is_file.c build/abi/stdlib/RemoveFileExtern.c build/abi/stdlib/RenameExtern.c build/abi/stdlib/CreateDirExtern.c build/abi/stdlib/AddToPathChildList.c build/abi/stdlib/iterdir.c build/abi/stdlib/exists.c build/abi/stdlib/makeDirectory.c build/abi/stdlib/GetPathSeparator.c build/abi/__vale/substring.c build/abi/__vale/strcmp.c build/abi/__vale/TruncateI64ToI32.c build/abi/__vale/castFloatStr.c build/abi/__vale/streq.c build/abi/__vale/strfromascii.c build/abi/__vale/numMainArgs.c build/abi/__vale/printstr.c build/abi/__vale/castI32Str.c build/abi/__vale/castFloatI32.c build/abi/__vale/getMainArg.c build/abi/__vale/castI32Float.c build/abi/__vale/addStr.c build/abi/__vale/strindexof.c build/abi/__vale/strtoascii.c build/abi/__vale/castI64Str.c || { echo 'Tester build failed, aborting.' ; exit 1; }

  echo Running Tester...
  ./build/testvalec --frontend_path ./BuiltValeCompiler/Frontend.jar --backend_path ./BuiltValeCompiler/backend --builtins_dir ./BuiltValeCompiler/builtins --valec_path ./BuiltValeCompiler/valec --backend_tests_dir ../Backend/test --frontend_tests_dir ../Frontend  --stdlib_dir ./BuiltValeCompiler/stdlib --concurrent 6 @resilient-v3 || { echo 'Tests failed, aborting.' ; exit 1; }
fi

cd ..
