#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "vtest/Spaceship.h"
#include "vtest/ImmSpaceshipArray.h"
#include "vtest/cSumFuel.h"
#include "vtest/valeSumFuel.h"

extern ValeInt vtest_cSumFuel(vtest_ImmSpaceshipArray* arr) {
  return vtest_valeSumFuel(arr);
}
