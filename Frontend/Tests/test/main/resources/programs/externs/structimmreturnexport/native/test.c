#include <stdint.h>
#include <string.h>

#include "vtest/Flamscrankle.h"
#include "vtest/valeMakeStruct.h"
#include "vtest/cMakeStruct.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

vtest_Flamscrankle* vtest_cMakeStruct() {
  int runNumber = incrementIntFile("myfile.bin");

  vtest_Flamscrankle* x = vtest_valeMakeStruct();
  x->a *= runNumber;
  x->c *= runNumber;
  return x;
}
