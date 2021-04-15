#include <stdint.h>
#include <stdio.h>
#include "ImmIntArray.h"

int64_t sumBytes(ImmIntArray* arr) {
  int64_t total = 0;
  for (int i = 0; i < arr->length; i++) {
    total += arr->elements[i];
  }
  return total;
}
