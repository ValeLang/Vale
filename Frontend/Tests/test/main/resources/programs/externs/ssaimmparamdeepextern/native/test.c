#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "vtest/Spaceship.h"
#include "vtest/ImmSpaceshipArray.h"
#include "vtest/cSumFuel.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

extern ValeInt vtest_cSumFuel(vtest_ImmSpaceshipArray* arr) {
  int runNumber = incrementIntFile("myfile.bin");

  ValeInt total = 0;
  for (int i = 0; i < vtest_ImmSpaceshipArray_SIZE; i++) {
    total += arr->elements[i]->fuel;
  }
  return total * runNumber;
}
