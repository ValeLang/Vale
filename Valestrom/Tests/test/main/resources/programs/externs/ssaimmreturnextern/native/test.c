#include <stdint.h>
#include <string.h>
#include <assert.h>

#include "tmod/ImmIntArray.h"
#include "tmod/cMakeRSA.h"

tmod_ImmIntArray* tmod_cMakeRSA() {
  assert(tmod_ImmIntArray_SIZE == 3);
  tmod_ImmIntArray* arr = malloc(sizeof(tmod_ImmIntArray) + sizeof(ValeInt) * tmod_ImmIntArray_SIZE);
  arr->elements[0] = 13;
  arr->elements[1] = 14;
  arr->elements[2] = 15;
  return arr;
}
