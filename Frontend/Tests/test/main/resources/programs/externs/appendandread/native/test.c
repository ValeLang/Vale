#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "vtest/cAppend.h"

extern ValeInt vtest_cAppend(ValeInt s) {
  FILE* file = fopen("myfile.bin", "a");
  assert(file);

  int pos = ftell(file);
  // If this assert fails, then it means the file already existed, which
  // means this function is running in replay mode, which it shouldnt.
  assert(pos == 0);

  // Write some stuff to the file.
  int64_t toAppend = 1337;
  int writeResult = fwrite(&toAppend, sizeof(int64_t), 1, file);
  assert(writeResult);

  int closeResult = fclose(file);
  assert(closeResult == 0);

  return 42;
}
