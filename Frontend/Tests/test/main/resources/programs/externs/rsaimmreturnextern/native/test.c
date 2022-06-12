#include <stdint.h>
#include <string.h>

#include "vtest/ImmIntArray.h"
#include "vtest/cMakeRSA.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

vtest_ImmIntArray* vtest_cMakeRSA() {
  int runNumber = incrementIntFile("myfile.bin");

  vtest_ImmIntArray* arr = malloc(sizeof(vtest_ImmIntArray) + sizeof(ValeInt) * 5);
  arr->length = 5;
  arr->elements[0] = 5 * runNumber;
  arr->elements[1] = 7 * runNumber;
  arr->elements[2] = 9 * runNumber;
  arr->elements[3] = 10 * runNumber;
  arr->elements[4] = 11 * runNumber;
  return arr;
}
