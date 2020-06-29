/** Timer handling
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef timer_h
#define timer_h

#include <stdint.h>
#include <stddef.h>

enum Timers {
    LoadTimer,
    LexTimer,
    ParseTimer,
    SemTimer,
    GenTimer,
    VerifyTimer,
    OptTimer,
    CodeGenTimer,
    SetupTimer,
    TimerCount
};

// Start timing ticks for a specific timer
void timerBegin(size_t aTimer);

// Get the tick count for a timer
uint64_t timerGetTicks(size_t aTimer);

// Get a specific timer in seconds
double timerGetSecs(size_t aTimer);

// Get the summary of all timers in seconds
double timerSummary();

// Print out all timers
void timerPrint();

#endif
