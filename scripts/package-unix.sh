rm -rf ../release-unix
mkdir -p ../release-unix
mkdir -p ../release-unix/samples
cp ../Valestrom/Valestrom.jar ../release-unix

cp -r ../Valestrom/Samples/test/main/resources/programs ../release-unix/samples
cp -r ../Valestrom/Samples/test/main/resources/libraries ../release-unix/samples/libraries
cp -r ../benchmarks/BenchmarkRL/vale ../release-unix/BenchmarkRL
cp -r ../Midas/src/builtins ../release-unix/builtins
cp -r ../Midas/vstl ../release-unix/vstl
cp ../Midas/valec.py ../release-unix/valec.py
cp releaseREADME.txt ../release-unix/README.txt
cp valec-help* ../release-unix
cp ../Midas/cmake-build-debug/valec ../release-unix/valec
