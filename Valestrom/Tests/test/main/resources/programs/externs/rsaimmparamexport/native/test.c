#include <stdint.h>
#include <stdio.h>
#include "vtest/ImmIntArray.h"

extern ValeInt vtest_expSumBytes(vtest_ImmIntArray* arr);

ValeInt vtest_extSumBytes(vtest_ImmIntArray* arr) {
  return vtest_expSumBytes(arr);
}
