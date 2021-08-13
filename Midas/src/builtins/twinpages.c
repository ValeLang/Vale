#include <stdlib.h>
#include <stdlib.h>
#include <signal.h>
#include <string.h>
#include <stdio.h>

#ifdef _WIN32
#include <windows.h>
#include <tchar.h>
#else
#include <sys/mman.h>
#include <unistd.h>
#endif

void SignalHandler(int signal) {
  printf("Memory violation! Signal %d\n", signal);
  exit(11);
}

int getOSPageSize() {
#ifdef _WIN32
  SYSTEM_INFO sSysInfo;         // useful information about the system
  GetSystemInfo(&sSysInfo);     // initialize the structure
  return sSysInfo.dwPageSize;
#else
  return getpagesize();
#endif
}

char* __vale_initTwinPages() {
  // Set up the signal to catch the seg fault.
  // This is especially nice for windows, which otherwise just silently fails.
  //typedef void (*SignalHandlerPointer)(int);
  //SignalHandlerPointer previousSigsegvHandler =
      signal(SIGSEGV, SignalHandler);
  // Mac can have bus faults, not seg faults, when we do bad accesses
#ifndef _WIN32
  //SignalHandlerPointer previousBusErrorHandler =
      signal(SIGBUS, SignalHandler);
#endif

  size_t pageSize = getOSPageSize();

#ifdef _WIN32
  char* allocationPtr =
      VirtualAlloc(
          NULL, pageSize * 2, MEM_RESERVE | MEM_COMMIT, PAGE_NOACCESS);
  if(allocationPtr == NULL) {
    _tprintf(TEXT("VirtualAlloc failed. Error: %ld\n"), GetLastError());
    exit(1);
  }
  DWORD oldFlags = 0;
  int virtualProtectSuccess =
      VirtualProtect(
          allocationPtr, pageSize, PAGE_READWRITE, &oldFlags);
  if (!virtualProtectSuccess) {
    _tprintf(TEXT("VirtualProtect failed. Error: %ld\n"), GetLastError());
    exit(1);
  }
#else
  char *allocationPtr = mmap(0, pageSize * 2, 0, MAP_ANON | MAP_PRIVATE, 0, 0);
  if (allocationPtr == MAP_FAILED) {
    perror("Could not mmap twin pages!");
    exit(1);
  }
  if (mprotect(allocationPtr, pageSize, PROT_READ | PROT_WRITE)) {
    perror("Could not mprotect twin pages!");
    exit(1);
  }
#endif
  // Do a write just to be sure.
  *(long long*)&allocationPtr[pageSize - sizeof(long long)] = 42LL;
//  long long result =
//      *(long long*)&allocationPtr[pageSize - sizeof(long long)];
  return allocationPtr + pageSize;
}
