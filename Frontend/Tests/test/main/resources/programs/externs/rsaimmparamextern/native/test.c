#include <stdint.h>
#include <stdio.h>
#include "vtest/ImmIntArray.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

ValeInt vtest_sumBytes(vtest_ImmIntArray* arr) {
  int runNumber = incrementIntFile("myfile.bin");

  ValeInt total = 0;
  for (int i = 0; i < arr->length; i++) {
    total += arr->elements[i];
  }
  free(arr);
  return total * runNumber;
}
