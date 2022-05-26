#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

int64_t incrementIntFile(const char* filename) {
  FILE* file = fopen(filename, "a+");
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

  num++;

  // Write added number to the file.
  int seekResult = fseek(file, 0, SEEK_SET);
  assert(seekResult == 0);
  int writeResult = fwrite(&num, sizeof(int64_t), 1, file);
  assert(writeResult);

  int closeResult = fclose(file);
  assert(closeResult == 0);

  return num;
}
