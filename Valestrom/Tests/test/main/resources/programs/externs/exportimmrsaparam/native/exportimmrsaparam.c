#include <stdint.h>
#include <stdio.h>
#include "tmod/ImmIntArray.h"

extern int64_t tmod_expSumBytes(tmod_ImmIntArray* arr);

int64_t tmod_extSumBytes(tmod_ImmIntArray* arr) {
  return tmod_expSumBytes(arr);
}
