#include <stdint.h>
#include <string.h>

#include "vtest/ImmIntArray.h"
#include "vtest/cMakeRSA.h"

vtest_ImmIntArray* vtest_cMakeRSA() {
  vtest_ImmIntArray* arr = malloc(sizeof(vtest_ImmIntArray) + sizeof(ValeInt) * 5);
  arr->length = 5;
  arr->elements[0] = 5;
  arr->elements[1] = 7;
  arr->elements[2] = 9;
  arr->elements[3] = 10;
  arr->elements[4] = 11;
  return arr;
}
