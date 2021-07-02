#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "tmod/Spaceship.h"
#include "tmod/ImmSpaceshipArray.h"
#include "tmod/cSumFuel.h"

extern ValeInt tmod_cSumFuel(tmod_ImmSpaceshipArray* arr) {
  ValeInt total = 0;
  for (int i = 0; i < tmod_ImmSpaceshipArray_SIZE; i++) {
    total += arr->elements[i]->fuel;
  }
  return total;
}
