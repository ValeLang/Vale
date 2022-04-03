#include <stdint.h>
#include <string.h>
#include <assert.h>

#include "vtest/ImmIntArray.h"
#include "vtest/cMakeRSA.h"

vtest_ImmIntArray* vtest_cMakeRSA() {
  assert(vtest_ImmIntArray_SIZE == 3);
  vtest_ImmIntArray* arr = malloc(sizeof(vtest_ImmIntArray) + sizeof(ValeInt) * vtest_ImmIntArray_SIZE);
  arr->elements[0] = 13;
  arr->elements[1] = 14;
  arr->elements[2] = 15;
  return arr;
}
