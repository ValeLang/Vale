#include <stdint.h>
#include <stdio.h>
#include "tmod/ImmIntArray.h"
#include "tmod/cSumBytes.h"

ValeInt tmod_cSumBytes(tmod_ImmIntArray* arr) {
  ValeInt total = 0;
  for (int i = 0; i < tmod_ImmIntArray_SIZE; i++) {
    total += arr->elements[i];
  }
  return total;
}
