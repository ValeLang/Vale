#ifndef VALE_EXPORTS_StrArray_H_
#define VALE_EXPORTS_StrArray_H_
#include "ValeBuiltins.h"
typedef struct stdlib_StrArray {
  uint32_t length;
  ValeStr* elements[0];
} stdlib_StrArray;
#endif
