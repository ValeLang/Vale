Vale Compiler, version 0.1.3
http://vale.dev/

To run a program:
  python3 valec.py build [options] [root modules] [module directory mappings]

Run `python3 valec.py help build` for more.

Example:
  python3 valec.py build hello hello:./samples/helloworld stdlib:./stdlib/src
  ./a.out

This requires a python version of at least 3.8.
