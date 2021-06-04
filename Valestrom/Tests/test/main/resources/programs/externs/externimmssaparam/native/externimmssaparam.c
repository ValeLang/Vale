#include <stdint.h>
#include <stdio.h>
#include "tmod/ImmIntArray.h"

int64_t sumBytes(tmod_ImmIntArray* arr) {
  int64_t total = 0;
  for (int i = 0; i < tmod_ImmIntArray_LENGTH; i++) {
    total += arr->elements[i];
  }
  return total;
}
