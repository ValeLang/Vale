mkdir -p ../release
cp ../Valestrom/out/artifacts/Driver_jar/Driver.jar ../release/Driver.jar
cp -r ../Valestrom/Samples/test/main/resources/libraries ../release/vstl
cp -r ../Valestrom/Samples/test/main/resources/programs ../release/samples
cp -r ../benchmarks/BenchmarkRL/vale ../release/benchmark
cp -r ../Midas/src/valestd/ ../release/runtime
cp releaseREADME.txt ../release/README.txt
cp ../Midas/cmake-build-debug/valec ../release/valec
cp ../Midas/valec.py ../release/valec.py
