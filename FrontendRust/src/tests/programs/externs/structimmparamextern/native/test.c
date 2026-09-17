#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "vtest/Flamscrankle.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

ValeInt vtest_extFunc(vtest_Flamscrankle* flam) {
  int runNumber = incrementIntFile("myfile.bin");
  ValeInt result = (flam->a + flam->c) * runNumber;
  free(flam);
  return result;
}
