rm -rf ../release-unix
mkdir -p ../release-unix
mkdir -p ../release-unix/samples
cp ../Valestrom/Valestrom.jar ../release-unix

cp -r ../Valestrom/Tests/test/main/resources/programs ../release-unix/samples
cp -r ../Midas/src/builtins ../release-unix/builtins
cp releaseREADME.txt ../release-unix/README.txt
cp valec-* ../release-unix
cp ../Midas/build/midas ../release-unix/midas
git clone https://github.com/ValeLang/stdlib ../release-unix/stdlib
cp -r helloworld ../release-unix/samples/helloworld
cp ../Driver/build/valec ../release-unix/valec

cd ../release-unix
zip -r ValeCompiler.zip *
