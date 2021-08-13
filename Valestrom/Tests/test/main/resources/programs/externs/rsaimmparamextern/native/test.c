#include <stdint.h>
#include <stdio.h>
#include "vtest/ImmIntArray.h"

ValeInt vtest_sumBytes(vtest_ImmIntArray* arr) {
  ValeInt total = 0;
  for (int i = 0; i < arr->length; i++) {
    total += arr->elements[i];
  }
  free(arr);
  return total;
}
