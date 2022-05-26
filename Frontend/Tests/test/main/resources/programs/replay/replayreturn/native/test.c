#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "vtest/cAppend.h"

extern ValeInt vtest_cAppend() {
  return addToIntFile("myfile.bin", 42);
}
