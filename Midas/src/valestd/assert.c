#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

void __vassert(char value, const char* message) {
  if (!value) {
    printf("%s\n", message);
    exit(255);
  }
}

void __vassertI64Eq(int64_t expected, int64_t actual, const char* message) {
  if (expected != actual) {
    printf("%s Expected %ld but was %ld.\n", message, expected, actual);
    exit(255);
  }
}

void __vflare_i64(int64_t color, int64_t x) {
  printf("Flare %ld: %ld\n", color, x);
}
