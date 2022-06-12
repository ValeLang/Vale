#include <stdint.h>
#include <stdio.h>
#include "vtest/ImmIntArray.h"
#include "vtest/expSumBytes.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

ValeInt vtest_extSumBytes(vtest_ImmIntArray* arr) {
  int runNumber = incrementIntFile("myfile.bin");

  return vtest_expSumBytes(arr) * runNumber;
}
