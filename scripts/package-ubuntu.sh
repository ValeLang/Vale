rm -rf ../release-ubuntu
mkdir -p ../release-ubuntu
cp ../Valestrom/out/artifacts/Valestrom_jar/Valestrom.jar ../release-ubuntu/Valestrom.jar
cp -r ../Valestrom/Samples/test/main/resources/libraries ../release-ubuntu/vstl
cp -r ../Midas/vstl/* ../release-ubuntu/vstl
cp -r ../Valestrom/Samples/test/main/resources/programs ../release-ubuntu/samples
cp -r ../benchmarks/BenchmarkRL/vale ../release-ubuntu/benchmark
cp -r ../Midas/src/valestd/ ../release-ubuntu/runtime
cp releaseREADME.txt ../release-ubuntu/README.txt
cp ../Midas/cmake-build-debug/valec ../release-ubuntu/valec
cp ../Midas/valec.py ../release-ubuntu/valec.py
