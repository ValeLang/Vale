#include <stdint.h>
#include <stdio.h>
#include "tmod/ImmIntArray.h"

int64_t tmod_sumBytes(tmod_ImmIntArray* arr) {
  int64_t total = 0;
  for (int i = 0; i < arr->length; i++) {
    total += arr->elements[i];
  }
  return total;
}
