#include <stdint.h>
#include <stdio.h>
#include "tmod/ImmIntArray.h"

ValeInt sumBytes(tmod_ImmIntArray* arr) {
  ValeInt total = 0;
  for (int i = 0; i < tmod_ImmIntArray_LENGTH; i++) {
    total += arr->elements[i];
  }
  return total;
}
