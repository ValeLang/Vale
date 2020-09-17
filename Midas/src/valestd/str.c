#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

#define FALSE 0
#define TRUE 1

void __vinitStr(char* newStr, const char* chars) {
  for (int i = 0; ; i++) {
    newStr[i] = chars[i];
    // This is at the end because we want to copy the null terminating char too.
    if (chars[i] == 0) {
      break;
    }
  }
}

void __vaddStr(char* a, char* b, char* dest) {
  int i = 0;
  for (; a[i]; i++) {
    dest[i] = a[i];
  }
  int a_len = i;
  for (i = 0; b[i]; i++) {
    dest[i + a_len] = b[i];
  }
  int b_len = i;
  // Add a null terminating char for compatibility with C.
  // Midas should allocate an extra byte to accommodate this.
  dest[a_len + b_len] = 0;
}

uint8_t __veqStr(char* a, char* b) {
  for (int i = 0; ; i++) {
    if (a[i] != b[i]) {
      return FALSE;
    }
    if (a[i] == 0) {
      return TRUE;
    }
  }
}

void __vprintStr(char* a) {
  printf("%s", a);
}

void __vintToCStr(int n, char* dest, int destSize) {
  snprintf(dest, destSize, "%d", n);
}
