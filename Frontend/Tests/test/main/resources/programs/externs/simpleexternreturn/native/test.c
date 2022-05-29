#include <stdint.h>
#include <stdio.h>

#include "vtest/cMake42.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

extern ValeInt vtest_cMake42() {
  int runNumber = incrementIntFile("myfile.bin");
  return 42 * runNumber;
}
