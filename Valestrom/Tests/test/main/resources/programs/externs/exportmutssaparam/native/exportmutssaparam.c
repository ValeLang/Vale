#include <stdint.h>
#include <stdio.h>
#include "tmod/MutIntArray.h"
#include "tmod/getMutIntArrayLen.h"
#include "tmod/getMutIntArrayElem.h"

ValeInt tmod_sumBytes(tmod_MutIntArrayRef arr) {
  ValeInt total = 0;
  ValeInt len = tmod_getMutIntArrayLen(arr);
  for (int i = 0; i < len; i++) {
    ValeInt elem = tmod_getMutIntArrayElem(arr, i);
    total += elem;
  }
  return total;
}
