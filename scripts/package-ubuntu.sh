rm -rf ../release-ubuntu
mkdir -p ../release-ubuntu
cp ../Valestrom/out/artifacts/Valestrom_jar/Valestrom.jar ../release-ubuntu
cp ../Valestrom/lib/lift-json_2.12-3.3.0-RC1.jar ../release-ubuntu
cp -r ../Valestrom/Samples/test/main/resources/programs ../release-ubuntu/samples
cp -r ../Valestrom/Samples/test/main/resources/libraries ../release-ubuntu/samples/libraries
cp -r ../benchmarks/BenchmarkRL/vale ../release-ubuntu/BenchmarkRL
cp -r ../Midas/src/builtins ../release-ubuntu/builtins
cp -r ../Midas/vstl ../release-ubuntu/vstl
cp releaseREADME.txt ../release-ubuntu/README.txt
cp ../Midas/cmake-build-debug/valec ../release-ubuntu/valec
cp ../Midas/valec.py ../release-ubuntu/valec.py
