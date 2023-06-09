#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include "vtest/getAStr.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

ValeStr* vtest_runExtCommand() {
  int runNumber = incrementIntFile("myfile.bin");

  ValeStr* str = vtest_getAStr();

  printf("str length: %d\n", str->length);
  assert(str->length == 6);
  int diff = strncmp(str->chars, "hello!", 6);
  assert(diff == 0);

  // "hello!", "hello!hello!", etc.
  ValeStr* result = ValeStrNew(str->length * runNumber);
  for (int i = 0; i < runNumber; i++) {
    strncpy(result->chars + strlen("hello!") * runNumber, "hello!", strlen("hello!"));
  }

  free(str);

  return result;
}
