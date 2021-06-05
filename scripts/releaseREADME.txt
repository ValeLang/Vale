Vale Compiler, version 0.1.3
http://vale.dev/

To run a program:
  python3 valec.py build (vale files here)
  ./a.out

This requires a python version of at least 3.8.

To run the sample roguelike program:
  python3 valec.py build samples/programs/roguelike.vale vstl/hashmap.vale vstl/list.vale

To run benchmarks to see the differences between the various regions:
  cd benchmarks
  ./run_benchmark.sh
