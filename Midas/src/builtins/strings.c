#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#define TRUE 1
#define FALSE 0

// These are exposed by the compiled vale .obj/.o, they're
// the start of a Vale native API.
typedef struct ValeStr {
  uint64_t length;
  char chars[0];
} ValeStr;

ValeStr* ValeStrNew(int64_t length) {
  ValeStr* result = (ValeStr*)malloc(sizeof(ValeStr) + length + 1);
  result->length = length;
  result->chars[0] = 0;
  result->chars[length] = 0;
  return result;
}

ValeStr* ValeStrFrom(char* source) {
  int length = strlen(source);
  ValeStr* result = ValeStrNew(length);
  strncpy(result->chars, source, length);
  result->chars[length] = 0;
  return result;
}

int64_t vstr_indexOf(
    ValeStr* haystackContainerStr, int64_t haystackBegin, int64_t haystackEnd,
    ValeStr* needleContainerStr, int64_t needleBegin, int64_t needleEnd) {
  char* haystackContainerChars = haystackContainerStr->chars;
  char* haystack = haystackContainerChars + haystackBegin;
  int64_t haystackLen = haystackEnd - haystackBegin;

  char* needleContainerChars = needleContainerStr->chars;
  char* needle = needleContainerChars + needleBegin;
  int64_t needleLen = needleEnd - needleBegin;

  for (int64_t i = 0; i <= haystackLen - needleLen; i++) {
    if (strncmp(needle, haystack + i, needleLen) == 0) {
      return i;
    }
  }
  return -1;
}


ValeStr* vstr_substring(
    ValeStr* sourceStr,
    int64_t begin,
    int64_t length) {
  char* sourceChars = sourceStr->chars;

  assert(begin >= 0);
  assert(length >= 0);

  ValeStr* result = ValeStrNew(length);
  char* resultChars = result->chars;
  strncpy(resultChars, sourceChars + begin, length);
  return result;
}

char vstr_eq(
    ValeStr* aContainerStr,
    int64_t aBegin,
    int64_t aEnd,
    ValeStr* bContainerStr,
    int64_t bBegin,
    int64_t bEnd) {
  char* aContainerChars = aContainerStr->chars;
  char* a = aContainerChars + aBegin;
  int64_t aLen = aEnd - aBegin;

  char* bContainerChars = bContainerStr->chars;
  char* b = bContainerChars + bBegin;
  int64_t bLen = bEnd - bBegin;

  if (aLen != bLen) {
    return FALSE;
  }
  int64_t len = aLen;

  for (int i = 0; i < len; i++) {
    if (a[i] != b[i]) {
      return FALSE;
    }
  }
  return TRUE;
}

int64_t vstr_cmp(
    ValeStr* aContainerStr,
    int64_t aBegin,
    int64_t aEnd,
    ValeStr* bContainerStr,
    int64_t bBegin,
    int64_t bEnd) {
  char* aContainerChars = aContainerStr->chars;
  char* a = aContainerChars + aBegin;
  int64_t aLen = aEnd - aBegin;

  char* bContainerChars = bContainerStr->chars;
  char* b = bContainerChars + bBegin;
  int64_t bLen = bEnd - bBegin;

  for (int i = 0; ; i++) {
    if (i >= aLen && i >= bLen) {
      break;
    }
    if (i >= aLen && i < bLen) {
      return -1;
    }
    if (i < aLen && i >= bLen) {
      return 1;
    }
    if (a[i] < b[i]) {
      return -1;
    }
    if (a[i] > b[i]) {
      return 1;
    }
  }
  return 0;
}

ValeStr* __vaddStr(
    ValeStr* aStr, int aBegin, int aLength,
    ValeStr* bStr, int bBegin, int bLength) {
  char* a = aStr->chars;
  char* b = bStr->chars;

  ValeStr* result = ValeStrNew(aLength + bLength);
  char* dest = result->chars;

  for (int i = 0; i < aLength; i++) {
    dest[i] = a[aBegin + i];
  }
  for (int i = 0; i < bLength; i++) {
    dest[i + aLength] = b[bBegin + i];
  }
  // Add a null terminating char for compatibility with C.
  // Midas should allocate an extra byte to accommodate this.
  // (Midas also adds this in case we didn't do it here)
  dest[aLength + bLength] = 0;

  return result;
}

ValeStr* __castIntStr(int n) {
  char tempBuffer[100] = { 0 };
  int charsWritten = snprintf(tempBuffer, 100, "%d", n);
  ValeStr* result = ValeStrNew(charsWritten);
  char* resultChars = result->chars;
  strncpy(resultChars, tempBuffer, charsWritten);
  return result;
}

void __vprintStr(ValeStr* s, int start, int length) {
  char* chars = s->chars;
  fwrite(chars + start, 1, length, stdout);
}

int vstr_toascii(ValeStr* s, int begin, int end) {
  assert(begin + 1 <= end);
  char* chars = s->chars;
  return (int)*(chars + begin);
}

ValeStr* vstr_fromascii(int code) {
  ValeStr* result = ValeStrNew(1);
  char* dest = result->chars;
  *dest = code;
  return result;
}
