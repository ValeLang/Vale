rm -rf ../release-unix
mkdir -p ../release-unix
cp ../Valestrom/out/artifacts/Valestrom_jar/Valestrom.jar ../release-unix
cp ../Valestrom/lib/lift-json_2.12-3.3.0-RC1.jar ../release-unix
cp -r ../Valestrom/Samples/test/main/resources/programs ../release-unix/samples
cp -r ../Valestrom/Samples/test/main/resources/libraries ../release-unix/samples/libraries
cp -r ../benchmarks/BenchmarkRL/vale ../release-unix/BenchmarkRL
cp -r ../Midas/src/builtins ../release-unix/builtins
cp -r ../Midas/vstl ../release-unix/vstl
cp releaseREADME.txt ../release-unix/README.txt
cp ../Midas/cmake-build-debug/valec ../release-unix/valec
cp ../Midas/valec.py ../release-unix/valec.py
