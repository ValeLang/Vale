#include <stdint.h>
#include <string.h>

#include "tmod/ImmIntArray.h"
#include "tmod/cMakeRSA.h"

tmod_ImmIntArray* tmod_cMakeRSA() {
  tmod_ImmIntArray* arr = malloc(sizeof(tmod_ImmIntArray) + sizeof(ValeInt) * 5);
  arr->length = 5;
  arr->elements[0] = 5;
  arr->elements[1] = 7;
  arr->elements[2] = 9;
  arr->elements[3] = 10;
  arr->elements[4] = 11;
  return arr;
}
