#include <stdint.h>
#include <stdio.h>
#include "ImmIntArray.h"

extern int64_t expSumBytes(ImmIntArray* arr);

int64_t extSumBytes(ImmIntArray* arr) {
  return expSumBytes(arr);
}
