#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "vtest/myCFunc.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

extern ValeInt vtest_myCFunc(ValeInt x) {
  int runNumber = incrementIntFile("myfile.bin");
  return x * runNumber;
}
