#include <stdint.h>
#include <stdio.h>
#include "tmod/ImmIntArray.h"

ValeInt tmod_sumBytes(tmod_ImmIntArray* arr) {
  ValeInt total = 0;
  for (int i = 0; i < arr->length; i++) {
    total += arr->elements[i];
  }
  ValeReleaseMessage(arr);
  return total;
}
