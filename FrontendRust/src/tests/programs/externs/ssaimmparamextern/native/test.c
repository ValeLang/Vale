#include <stdint.h>
#include <stdio.h>
#include "vtest/ImmIntArray.h"
#include "vtest/cSumBytes.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

ValeInt vtest_cSumBytes(vtest_ImmIntArray* arr) {
  int runNumber = incrementIntFile("myfile.bin");

  ValeInt total = 0;
  for (int i = 0; i < vtest_ImmIntArray_SIZE; i++) {
    total += arr->elements[i];
  }
  return total * runNumber;
}
