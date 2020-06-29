/** Timer Handling
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include <stdint.h>
#include <stdio.h>
#include "timer.h"

size_t timerCurrent = TimerCount;
uint64_t timerStamp = 0;
uint64_t timers[TimerCount];

#if defined(WIN32) || defined(_WIN32) || defined(__WIN32) && !defined(__CYGWIN__)
#include <Windows.h>
uint64_t timerGet() {
    LARGE_INTEGER captureTime;
    QueryPerformanceCounter(&captureTime);
    return (uint64_t)captureTime.QuadPart;
}
uint64_t timerTick() {
    LARGE_INTEGER captureFreq;
    QueryPerformanceFrequency(&captureFreq);
    return (uint64_t)captureFreq.QuadPart;
}
#else
#include <time.h>
uint64_t timerGet() {
    struct timespec tp;
//    clock_gettime(CLOCK_REALTIME, &tp);
    return (uint64_t)tp.tv_nsec;
}
uint64_t timerTick() {
    return 1000000000;
}
#endif

void timerBegin(size_t aTimer) {
    uint64_t timer = timerGet();
    if (timerCurrent < TimerCount)
        timers[timerCurrent] += timer - timerStamp;
    timerStamp = timer;
    timerCurrent = aTimer;
}

uint64_t timerGetTicks(size_t aTimer) {
    return timers[aTimer];
}

double timerGetSecs(size_t aTimer) {
    return (double)timers[aTimer] / timerTick();
}

double timerSummary() {
    uint64_t total = 0;
    for (int i = 0; i < TimerCount; ++i)
        total += timers[i];
    return (double)total / timerTick();
}

void timerPrint() {
    printf("Compile stage timing benchmarks (secs):\n");
    printf("  LLVM setup: %.6g\n", timerGetSecs(SetupTimer));
    printf("  Load:       %.6g\n", timerGetSecs(LoadTimer));
    printf("  Lexer:      %.6g\n", timerGetSecs(LexTimer));
    printf("  Parse:      %.6g\n", timerGetSecs(ParseTimer));
    printf("  Analysis    %.6g\n", timerGetSecs(SemTimer));
    printf("  Gen:        %.6g\n", timerGetSecs(GenTimer));
    printf("  Verify:     %.6g\n", timerGetSecs(VerifyTimer));
    printf("  Optimize:   %.6g\n", timerGetSecs(OptTimer));
    printf("  Codegen:    %.6g\n", timerGetSecs(CodeGenTimer));
    puts("");
}
