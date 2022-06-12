#include <stdint.h>
#include <string.h>

#include "vtest/ImmIntArray.h"
#include "vtest/valeMakeSSA.h"
#include "vtest/cMakeSSA.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

vtest_ImmIntArray* vtest_cMakeSSA() {
  int runNumber = incrementIntFile("myfile.bin");

  vtest_ImmIntArray* arr = vtest_valeMakeSSA();

  for (int i = 0; i < vtest_ImmIntArray_SIZE; i++) {
    arr->elements[i] *= runNumber;
  }

  return arr;
}
