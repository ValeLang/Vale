python3 ~/Vale/release-unix/valec.py build stdlib.collections.test stdlib:~/stdlib/src --output-dir build --add-exports-include-path

if [ "$1" == "run" ]; then
  build/a.out
fi
