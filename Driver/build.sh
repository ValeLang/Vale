java -cp ../Valestrom/Valestrom.jar net.verdagon.vale.driver.Driver build --output-dir build driver:src stdlib:/Users/verdagon/stdlib/src
../Midas/build/midas build/*.vast --output-dir build
clang-11 ../Midas/src/builtins/*.c build/*.c build/*.o build/stdlib/*.c /Users/verdagon/stdlib/src/path/native/*.c /Users/verdagon/stdlib/src/command/native/*.c /Users/verdagon/stdlib/src/os/native/*.c -Ibuild -o build/valec
