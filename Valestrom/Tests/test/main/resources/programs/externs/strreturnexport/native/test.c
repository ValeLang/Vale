#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "tmod/getAStr.h"

ValeStr* tmod_runExtCommand() {
  ValeStr* str = tmod_getAStr();

  assert(str->length == 6);
  int diff = strncmp(str->chars, "hello!", 6);
  assert(diff == 0);

  ValeStr* result = ValeStrFrom(str->chars);

  ValeReleaseMessage(str);

  return result;
}
