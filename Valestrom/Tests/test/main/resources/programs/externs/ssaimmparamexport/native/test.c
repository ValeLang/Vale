#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "vtest/ImmIntArray.h"
#include "vtest/cSumFuel.h"
#include "vtest/valeSumFuel.h"

extern ValeInt vtest_cSumFuel(vtest_ImmIntArray* arr) {
  return vtest_valeSumFuel(arr);
}
