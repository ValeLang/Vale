#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "vtest/cAppend.h"

int64_t addToIntFile(const char* filename, int64_t addend) {
  FILE* file = fopen("myfile.bin", "a+");
  assert(file);

  int64_t num = 0;

  int pos = ftell(file);
  if (pos != 0) {
    // If we get here, the file already existed.

    // Read the int that was already there.
    int seekResult = fseek(file, 0, SEEK_SET);
    assert(seekResult == 0);
    int readResult = fread(&num, sizeof(int64_t), 1, file);
    assert(readResult);
  }

  num += addend;

  // Write added number to the file.
  int seekResult = fseek(file, 0, SEEK_SET);
  assert(seekResult == 0);
  int writeResult = fwrite(&num, sizeof(int64_t), 1, file);
  assert(writeResult);

  int closeResult = fclose(file);
  assert(closeResult == 0);

  return num;
}

extern ValeInt vtest_cAppend() {
  return addToIntFile("myfile.bin", 42);
}
