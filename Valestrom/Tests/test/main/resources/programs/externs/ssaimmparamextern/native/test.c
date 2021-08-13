#include <stdint.h>
#include <stdio.h>
#include "vtest/ImmIntArray.h"
#include "vtest/cSumBytes.h"

ValeInt vtest_cSumBytes(vtest_ImmIntArray* arr) {
  ValeInt total = 0;
  for (int i = 0; i < vtest_ImmIntArray_SIZE; i++) {
    total += arr->elements[i];
  }
  return total;
}
