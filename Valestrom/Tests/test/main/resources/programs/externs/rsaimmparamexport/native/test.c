#include <stdint.h>
#include <stdio.h>
#include "tmod/ImmIntArray.h"

extern ValeInt tmod_expSumBytes(tmod_ImmIntArray* arr);

ValeInt tmod_extSumBytes(tmod_ImmIntArray* arr) {
  return tmod_expSumBytes(arr);
}
