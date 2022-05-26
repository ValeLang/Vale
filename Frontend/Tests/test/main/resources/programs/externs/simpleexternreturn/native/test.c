#include <stdint.h>
#include <stdio.h>

#include "vtest/cMake42.h"

int64_t incrementIntFile(const char* filename);

extern ValeInt vtest_cMake42() {
  // We use incrementIntFile to get some side effects to test replayability, see AASETR.
  return incrementIntFile("myfile.bin") * 42;
}
