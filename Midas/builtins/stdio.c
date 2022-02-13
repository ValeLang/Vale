#include <stdint.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>


void __vprintCStr(const char* str) {
  printf("%s", str);
}

void __vprintI64(int64_t x) {
  printf("%lld", x);
}

void __vprintBool(int8_t x) {
  if (x) {
    printf("true");
  } else {
    printf("false");
  }
}
