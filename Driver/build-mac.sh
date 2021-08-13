
java -cp ../Valestrom/Valestrom.jar net.verdagon.vale.driver.Driver build --output-dir build driver=src stdlib=../stdlib/src || { echo 'Valestrom build failed' ; exit 1; }
../Midas/build/midas build/vast/*.vast --output-dir build || { echo 'Midas build failed' ; exit 1; }
clang-11 ../Midas/src/builtins/*.c build/*.o build/abi/__vale/*.c build/abi/stdlib/*.c ../stdlib/src/path/native/*.c ../stdlib/src/command/native/*.c ../stdlib/src/os/native/*.c -Ibuild/include -o build/valec || { echo 'Clang build failed' ; exit 1; }
