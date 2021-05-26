rm -rf ../release-unix
mkdir -p ../release-unix
mkdir -p ../release-unix/samples
cp ../Valestrom/Valestrom.jar ../release-unix

cp -r ../Valestrom/Tests/test/main/resources/programs ../release-unix/samples
cp -r ../benchmarks/BenchmarkRL/vale ../release-unix/BenchmarkRL
cp -r ../Midas/src/builtins ../release-unix/builtins
cp -r ../Midas/vstl ../release-unix/vstl
cp ../Midas/valec.py ../release-unix/valec.py
cp releaseREADME.txt ../release-unix/README.txt
cp valec-* ../release-unix
cp ../Midas/build/valec ../release-unix/valec
