#ifdef _WIN32
#include <windows.h>
#include <stdlib.h>
#include <tchar.h>
#include <signal.h>
#else
#include <sys/mman.h>
#include <unistd.h>
#include <stdlib.h>
#endif
#include <string.h>
#include <stdio.h>


int pageSize() {
#ifdef _WIN32
  DWORD dwPageSize;             // amount of memory to allocate.
  SYSTEM_INFO sSysInfo;         // useful information about the system
  GetSystemInfo(&sSysInfo);     // initialize the structure
  return sSysInfo.dwPageSize;
#else
  return getpagesize();
#endif
}


void SignalHandler(int signal)
{
    printf("Memory violation! Signal %d\n", signal);
}

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

  // Set up the signal to catch the seg fault.
  // This is nice for windows, which otherwise just silently fails.
  typedef void (*SignalHandlerPointer)(int);
  SignalHandlerPointer previousHandler;
  previousHandler = signal(SIGSEGV , SignalHandler);


  size_t pagesize = pageSize();

  printf("System page size: %zu bytes\n", pagesize);

#ifdef _WIN32
  char* allocationPtr =
      VirtualAlloc(
          NULL, pagesize * 2, MEM_RESERVE | MEM_COMMIT, PAGE_NOACCESS);
  if(allocationPtr == NULL) {
    _tprintf(TEXT("VirtualAlloc failed. Error: %ld\n"), GetLastError());
    return 1;
  }
#else
  char *allocationPtr =
      mmap(0, pagesize * 2, 0, MAP_ANON | MAP_PRIVATE, 0, 0);
  if (allocationPtr == MAP_FAILED) {
    perror("Could not mmap");
    return 1;
  }
#endif

  printf("Successfully allocated two pages, starting at %p\n", allocationPtr);

#ifdef _WIN32
  DWORD oldFlags = 0;
  int virtualProtectSuccess =
      VirtualProtect(
          allocationPtr, pagesize, PAGE_READWRITE, &oldFlags);
  if (!virtualProtectSuccess) {
    _tprintf(TEXT("VirtualProtect failed. Error: %ld\n"), GetLastError());
    exit(1);
  }
#else
  if (mprotect(allocationPtr, pagesize, PROT_READ | PROT_WRITE)) {
    perror("Could not mprotect");
    return 1;
  }
#endif

  printf("Writing 42 to the end of the unprotected twin page!\n");
  *(long long*)&allocationPtr[pagesize - sizeof(long long)] = 42LL;

  printf("Reading from the end of the unprotected twin page!\n");
  long long result = *(long long*)&allocationPtr[pagesize - sizeof(long long)];
  printf("Got: %lld\n", result);

  if (attemptBadWrite) {
    printf("Writing to the beginning of the protected twin page, should crash!\n");
    allocationPtr[pagesize] = 73LL;
  }

#ifdef _WIN32
  int virtualFreeSuccess = VirtualFree(allocationPtr, 0, MEM_RELEASE);
  if (!virtualFreeSuccess) {
    _tprintf(TEXT("VirtualFree failed. Error: %ld\n"), GetLastError());
    exit(1);
  }
#else
  int unmap_result = munmap(allocationPtr, pagesize * 2);
  if (unmap_result != 0) {
    perror("Could not munmap");
    return 1;
  }
#endif

  if (result == 42LL) {
    printf("Success!\n");
    return 0;
  } else {
    return 1;
  }
}