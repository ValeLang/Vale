#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

#define FALSE 0
#define TRUE 1

// Note that this is NOT __Str_rc, which is what Midas usually handles. This
// lives inside __Str_rc.
typedef struct {
  int64_t length;
  uint8_t chars[];
} __Str;

void __vinitStr(__Str * restrict newStr, const char* chars) {
  for (int i = 0; ; i++) {
    newStr->chars[i] = chars[i];
    // This is at the end because we want to copy the null terminating char too.
    if (chars[i] == 0) {
      break;
    }
  }
}

void __vaddStr(__Str * restrict a, __Str * restrict b, __Str * restrict dest) {
  int a_len = a->length;
  int b_len = b->length;
  dest->length = a_len + b_len;
  for (int i = 0; i < a_len; i++) {
    dest->chars[i] = a->chars[i];
  }
  for (int i = 0; i < b_len; i++) {
    dest->chars[i + a_len] = b->chars[i];
  }
  // Add a null terminating char for compatibility with C.
  // Midas should allocate an extra byte to accommodate this.
  dest->chars[dest->length] = 0;
}

uint8_t __veqStr(__Str * restrict a, __Str * restrict b) {
  int a_len = a->length;
  int b_len = b->length;
  if (a_len != b_len) {
    return FALSE;
  }
  for (int i = 0; i < a_len; i++) {
    if (a->chars[i] != b->chars[i]) {
      return FALSE;
    }
  }
  return TRUE;
}

void __vprintStr(__Str * restrict a) {
  printf("%s", a->chars);
}

void __vintToCStr(int n, char* dest, int destSize) {
  snprintf(dest, destSize, "%d", n);
}
