# This script builds the Vale compiler, runs some tests on it, and also packages up a release zip file.
# It assumes we've already ran install-compiler-prereqs-mac.sh, or otherwise installed all the dependencies.

cd Valestrom

sbt assembly

cd ../Midas

cmake -B build

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

