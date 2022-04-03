#ifdef _WIN32
#include <windows.h>
#include <processthreadsapi.h>
#else
#include <sys/time.h>
#include <unistd.h>
#endif

#include "stdlib/MtpIdExtern.h"

extern int64_t stdlib_MtpIdExtern() {
  #ifdef _WIN32
    unsigned __int64 freq;
    QueryPerformanceFrequency((LARGE_INTEGER*)&freq);
    double timerFrequency = (1.0/freq);

    unsigned __int64 startTime;
    QueryPerformanceCounter((LARGE_INTEGER *)&startTime);

    int64_t microtime = startTime * timerFrequency;
  #else
    struct timeval time;
    gettimeofday(&time, NULL); // This actually returns a struct that has microsecond precision.
    int64_t microtime = ((unsigned long long)time.tv_sec * 1000000) + time.tv_usec;
  #endif


  #ifdef _WIN32
    int32_t processId = GetCurrentProcessId();
  #else
    int32_t processId = getpid();
  #endif

  return (int64_t)(((uint64_t)(uint32_t)processId << 32) | (uint64_t)(uint32_t)(microtime & 0xFFFFFFFF));
}
