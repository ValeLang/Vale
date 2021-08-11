#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "vtest/getAStr.h"

ValeStr* vtest_runExtCommand() {
  ValeStr* str = vtest_getAStr();

  assert(str->length == 6);
  int diff = strncmp(str->chars, "hello!", 6);
  assert(diff == 0);

  ValeStr* result = ValeStrFrom(str->chars);

  free(str);

  return result;
}
