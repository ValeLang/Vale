#include <stdint.h>
#include <string.h>
#include <stdio.h>

#include "vtest/Flamscrankle.h"
#include "vtest/cMakeStruct.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

vtest_Flamscrankle* vtest_cMakeStruct() {
  int runNumber = incrementIntFile("myfile.bin");

  printf("run number: %d\n", runNumber);
  vtest_Flamscrankle* flam = (vtest_Flamscrankle*)malloc(sizeof(vtest_Flamscrankle));
  flam->a = 37 * runNumber;
  flam->c = 5 * runNumber;
  printf("returning a flam: %p, a: %p\n", flam, &flam->a);
  return flam;
}
