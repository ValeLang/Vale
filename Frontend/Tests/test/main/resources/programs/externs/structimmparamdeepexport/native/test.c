#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#include "vtest/Bogglewoggle.h"
#include "vtest/Flamscrankle.h"

extern ValeInt vtest_expFunc(vtest_Flamscrankle* flam);

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

ValeInt vtest_extFunc(vtest_Flamscrankle* flam) {
  int runNumber = incrementIntFile("myfile.bin");

  return vtest_expFunc(flam) * runNumber;
}
