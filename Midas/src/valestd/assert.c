#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

void __vassert(char value) {
  if (!value) {
    printf("Assertion failed!\n");
    exit(255);
  }
}

void __vassertI64Eq(int64_t expected, int64_t actual) {
  if (expected != actual) {
    printf("Assertion failed! Expected %d but was %d.\n", expected, actual);
    exit(255);
  }
}

void __vflare_i64(int64_t color, int64_t x) {
  printf("Flare %d: %d\n", color, x);
}
