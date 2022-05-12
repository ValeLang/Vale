#include <stdint.h>
#include <stdio.h>

#include "vtest/cMake42.h"

extern ValeInt vtest_cMake42() {
  printf("In cMake42!\n");
  return 42;
}
