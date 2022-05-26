#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "vtest/myCFunc.h"

int64_t incrementIntFile(const char* filename);

extern ValeInt vtest_myCFunc(ValeInt x) {
  return incrementIntFile("myfile.bin") * x;
}
