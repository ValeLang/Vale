#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "vtest/Spaceship.h"
#include "vtest/ImmSpaceshipArray.h"
#include "vtest/cSumFuel.h"

extern ValeInt vtest_cSumFuel(vtest_ImmSpaceshipArray* arr) {
  ValeInt total = 0;
  for (int i = 0; i < vtest_ImmSpaceshipArray_SIZE; i++) {
    total += arr->elements[i]->fuel;
  }
  return total;
}
