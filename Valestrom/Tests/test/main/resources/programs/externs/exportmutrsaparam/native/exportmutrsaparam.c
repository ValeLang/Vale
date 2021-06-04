#include <stdint.h>
#include <stdio.h>
#include "MutIntArray.h"
#include "getMutIntArrayLen.h"
#include "getMutIntArrayElem.h"

int64_t tmod_sumBytes(tmod_MutIntArrayRef arr) {
  int64_t total = 0;
  int64_t len = tmod_getMutIntArrayLen(arr);
  for (int i = 0; i < len; i++) {
    int64_t elem = tmod_getMutIntArrayElem(arr, i);
    total += elem;
  }
  return total;
}
