#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include <stdlib.h>

const char attemptbadwritestr[] = "attemptbadwrite";
const char noattemptbadwritestr[] = "noattemptbadwrite";

int main(int argc, char** argv) {
  if (argc < 2) {
    fprintf(stderr, "Must specify attemptbadwrite or noattemptbadwrite for first arg!\n");
    return 1;
  }
  if (strncmp(argv[1], attemptbadwritestr, strlen(attemptbadwritestr)) != 0 &&
      strncmp(argv[1], noattemptbadwritestr, strlen(noattemptbadwritestr)) != 0) {
    fprintf(stderr, "Must specify attemptwrite or noattemptwrite for first arg!\n");
    return 1;
  }
  int attemptBadWrite = strncmp(argv[1], attemptbadwritestr, strlen(attemptbadwritestr)) == 0;


  size_t pagesize = getpagesize();

  printf("System page size: %zu bytes\n", pagesize);

  char *region = mmap(
    0,
    pagesize * 2,
    0,
    MAP_ANON | MAP_PRIVATE,
    0,
    0
  );

  if (region == MAP_FAILED) {
    perror("Could not mmap");
    return 1;
  }

  if (mprotect(region, pagesize, PROT_READ | PROT_WRITE)) {
    perror("Could not mprotect");
    return 1;
  }

  printf("Writing 42 to the end of the unprotected twin page!\n");
  *(long long*)&region[pagesize - sizeof(long long)] = 42LL;

  printf("Reading from the end of the unprotected twin page!\n");
  long long result = *(long long*)&region[pagesize - sizeof(long long)];
  printf("Got: %lld\n", result);

  if (attemptBadWrite) {
    printf("Writing to the beginning of the protected twin page, should crash!\n");
    region[pagesize] = 73LL;
  }

  int unmap_result = munmap(region, 1 << 10);
  if (unmap_result != 0) {
    perror("Could not munmap");
    return 1;
  }

  if (result == 42LL) {
    return 0;
  } else {
    return 1;
  }
}
