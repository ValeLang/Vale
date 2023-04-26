
#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <inttypes.h>

void __vassert(char value, const char* message) {
  if (!value) {
    printf("%s\n", message);
    exit((unsigned char)1);
  }
}

void __vassertI64Eq(int64_t expected, int64_t actual, const char* message) {
  if (expected != actual) {
    printf("%s Expected %"PRId64" but was %"PRId64".\n", message, expected, actual);
    exit((unsigned char)1);
  }
}
