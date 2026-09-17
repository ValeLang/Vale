#include <stdint.h>
#include <stdio.h>
#include "vtest/MutIntArray.h"
#include "vtest/getMutIntArrayLen.h"
#include "vtest/getMutIntArrayElem.h"

ValeInt vtest_sumBytes(vtest_MutIntArrayRef arr) {
  ValeInt total = 0;
  ValeInt len = vtest_getMutIntArrayLen(arr);
  for (int i = 0; i < len; i++) {
    ValeInt elem = vtest_getMutIntArrayElem(arr, i);
    total += elem;
  }
  return total;
}
