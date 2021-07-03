#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "tmod/ImmIntArray.h"
#include "tmod/cSumFuel.h"
#include "tmod/valeSumFuel.h"

extern ValeInt tmod_cSumFuel(tmod_ImmIntArray* arr) {
  return tmod_valeSumFuel(arr);
}
