#include <stdint.h>
#include <string.h>
#include <assert.h>

#include "vtest/ImmIntArray.h"
#include "vtest/cMakeRSA.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

vtest_ImmIntArray* vtest_cMakeRSA() {
  int runNumber = incrementIntFile("myfile.bin");

  assert(vtest_ImmIntArray_SIZE == 3);
  vtest_ImmIntArray* arr = malloc(sizeof(vtest_ImmIntArray) + sizeof(ValeInt) * vtest_ImmIntArray_SIZE);
  arr->elements[0] = 13 * runNumber;
  arr->elements[1] = 14 * runNumber;
  arr->elements[2] = 15 * runNumber;
  return arr;
}
