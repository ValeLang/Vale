#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>

// These are exposed by the compiled vale .obj/.o, they're
// the start of a Vale native API.
typedef struct ValeStr ValeStr;
ValeStr* vale_newstr(int64_t length);
char* vale_getstrchars(ValeStr* str);


#define TRUE 1
#define FALSE 0

int64_t vstr_indexOf(
    ValeStr* haystackContainerStr, int64_t haystackBegin, int64_t haystackEnd,
    ValeStr* needleContainerStr, int64_t needleBegin, int64_t needleEnd) {
  char* haystackContainerChars = vale_getstrchars(haystackContainerStr);
  char* haystack = haystackContainerChars + haystackBegin;
  int64_t haystackLen = haystackEnd - haystackBegin;

  char* needleContainerChars = vale_getstrchars(needleContainerStr);
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
  char* sourceChars = vale_getstrchars(sourceStr);

  assert(begin >= 0);
  assert(length >= 0);

  ValeStr* result = vale_newstr(length);
  char* resultChars = vale_getstrchars(result);
  strncpy(resultChars, sourceChars, length);
  return result;
}

char vstr_eq(
    ValeStr* aContainerStr,
    int64_t aBegin,
    int64_t aEnd,
    ValeStr* bContainerStr,
    int64_t bBegin,
    int64_t bEnd) {
  char* aContainerChars = vale_getstrchars(aContainerStr);
  char* a = aContainerChars + aBegin;
  int64_t aLen = aEnd - aBegin;

  char* bContainerChars = vale_getstrchars(bContainerStr);
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
  char* aContainerChars = vale_getstrchars(aContainerStr);
  char* a = aContainerChars + aBegin;
  int64_t aLen = aEnd - aBegin;

  char* bContainerChars = vale_getstrchars(bContainerStr);
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
  char* a = vale_getstrchars(aStr);
  char* b = vale_getstrchars(bStr);

  ValeStr* result = vale_newstr(aLength + bLength);
  char* dest = vale_getstrchars(result);

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
  int charsWritten = snprintf(tempBuffer, 150, "%d", n);
  ValeStr* result = vale_newstr(charsWritten);
  char* resultChars = vale_getstrchars(result);
  strncpy(resultChars, tempBuffer, charsWritten);
  return result;
}

void __vprintStr(ValeStr* s, int start, int length) {
  char* chars = vale_getstrchars(s);
  fwrite(chars + start, 1, length, stdout);
}

int vstr_toascii(ValeStr* s, int begin, int end) {
  assert(begin + 1 <= end);
  char* chars = vale_getstrchars(s);
  return (int)*(chars + begin);
}
