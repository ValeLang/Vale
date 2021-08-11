#ifndef VALE_EXPORTS_ImmIntArray_H_
#define VALE_EXPORTS_ImmIntArray_H_
#include "ValeBuiltins.h"
typedef struct vtest_ImmIntArray {
  uint32_t length;
  int32_t elements[0];
} vtest_ImmIntArray;
#endif
