#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "tmod/Spaceship.h"
#include "tmod/ImmSpaceshipArray.h"
#include "tmod/cSumFuel.h"
#include "tmod/valeSumFuel.h"

extern ValeInt tmod_cSumFuel(tmod_ImmSpaceshipArray* arr) {
  return tmod_valeSumFuel(arr);
}
