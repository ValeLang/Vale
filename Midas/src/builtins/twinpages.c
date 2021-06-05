#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#ifdef _WIN32
#else
#include <sys/mman.h>
#include <unistd.h>
#endif

char* __vale_initTwinPages() {
#ifdef _WIN32
  return malloc(4096*2);
#else
  size_t pageSize = getpagesize();
  char *region = mmap(0, pageSize * 2, 0, MAP_ANON | MAP_PRIVATE, 0, 0);
  if (region == MAP_FAILED) {
    perror("Could not mmap twin pages!");
    exit(1);
  }
  if (mprotect(region, pageSize, PROT_READ | PROT_WRITE)) {
    perror("Could not mprotect twin pages!");
    exit(1);
  }
  // Just do a couple writes, just to be sure.
  *(long long*)&region[pageSize - sizeof(long long)] = 42LL;
  long long result = *(long long*)&region[pageSize - sizeof(long long)];
  return region + pageSize;
#endif
}
