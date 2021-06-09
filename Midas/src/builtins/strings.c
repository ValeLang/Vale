#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include "ValeBuiltins.h"

#define TRUE 1
#define FALSE 0

ValeStr* ValeStrNew(ValeInt length) {
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

ValeInt vstr_indexOf(
    ValeStr* haystackContainerStr, ValeInt haystackBegin, ValeInt haystackEnd,
    ValeStr* needleContainerStr, ValeInt needleBegin, ValeInt needleEnd) {
  char* haystackContainerChars = haystackContainerStr->chars;
  char* haystack = haystackContainerChars + haystackBegin;
  ValeInt haystackLen = haystackEnd - haystackBegin;

  char* needleContainerChars = needleContainerStr->chars;
  char* needle = needleContainerChars + needleBegin;
  ValeInt needleLen = needleEnd - needleBegin;

  for (ValeInt i = 0; i <= haystackLen - needleLen; i++) {
    if (strncmp(needle, haystack + i, needleLen) == 0) {
      ValeReleaseMessage(haystackContainerStr);
      ValeReleaseMessage(needleContainerStr);
      return i;
    }
  }
  ValeReleaseMessage(haystackContainerStr);
  ValeReleaseMessage(needleContainerStr);
  return -1;
}


ValeStr* vstr_substring(
    ValeStr* sourceStr,
    ValeInt begin,
    ValeInt length) {
  char* sourceChars = sourceStr->chars;

  assert(begin >= 0);
  assert(length >= 0);

  ValeStr* result = ValeStrNew(length);
  char* resultChars = result->chars;
  strncpy(resultChars, sourceChars + begin, length);
  ValeReleaseMessage(sourceStr);
  return result;
}

char vstr_eq(
    ValeStr* aStr,
    ValeInt aBegin,
    ValeInt aEnd,
    ValeStr* bStr,
    ValeInt bBegin,
    ValeInt bEnd) {
  char* aContainerChars = aStr->chars;
  char* a = aContainerChars + aBegin;
  ValeInt aLen = aEnd - aBegin;

  char* bContainerChars = bStr->chars;
  char* b = bContainerChars + bBegin;
  ValeInt bLen = bEnd - bBegin;

  if (aLen != bLen) {
    ValeReleaseMessage(aStr);
    ValeReleaseMessage(bStr);
    return FALSE;
  }
  ValeInt len = aLen;

  for (int i = 0; i < len; i++) {
    if (a[i] != b[i]) {
      ValeReleaseMessage(aStr);
      ValeReleaseMessage(bStr);
      return FALSE;
    }
  }

  ValeReleaseMessage(aStr);
  ValeReleaseMessage(bStr);
  return TRUE;
}

ValeInt vstr_cmp(
    ValeStr* aStr,
    ValeInt aBegin,
    ValeInt aEnd,
    ValeStr* bStr,
    ValeInt bBegin,
    ValeInt bEnd) {
  char* aContainerChars = aStr->chars;
  char* a = aContainerChars + aBegin;
  ValeInt aLen = aEnd - aBegin;

  char* bContainerChars = bStr->chars;
  char* b = bContainerChars + bBegin;
  ValeInt bLen = bEnd - bBegin;

  for (int i = 0; ; i++) {
    if (i >= aLen && i >= bLen) {
      break;
    }
    if (i >= aLen && i < bLen) {
      ValeReleaseMessage(aStr);
      ValeReleaseMessage(bStr);
      return -1;
    }
    if (i < aLen && i >= bLen) {
      ValeReleaseMessage(aStr);
      ValeReleaseMessage(bStr);
      return 1;
    }
    if (a[i] < b[i]) {
      ValeReleaseMessage(aStr);
      ValeReleaseMessage(bStr);
      return -1;
    }
    if (a[i] > b[i]) {
      ValeReleaseMessage(aStr);
      ValeReleaseMessage(bStr);
      return 1;
    }
  }
  ValeReleaseMessage(aStr);
  ValeReleaseMessage(bStr);
  return 0;
}

ValeStr* __vaddStr(
    ValeStr* aStr, ValeInt aBegin, ValeInt aLength,
    ValeStr* bStr, ValeInt bBegin, ValeInt bLength) {
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

  ValeReleaseMessage(aStr);
  ValeReleaseMessage(bStr);
  return result;
}

extern ValeStr* __castI64Str(int64_t n) {
  char tempBuffer[100] = { 0 };
  int charsWritten = snprintf(tempBuffer, 100, "%d", n);
  ValeStr* result = ValeStrNew(charsWritten);
  char* resultChars = result->chars;
  strncpy(resultChars, tempBuffer, charsWritten);
  return result;
}

extern ValeStr* __castI32Str(int32_t n) {
  return __castI64Str((int64_t)n);
}

extern ValeStr* __castFloatStr(double f) {
  char tempBuffer[100] = { 0 };
  int charsWritten = snprintf(tempBuffer, 100, "%lf", f);
  ValeStr* result = ValeStrNew(charsWritten);
  char* resultChars = result->chars;
  strncpy(resultChars, tempBuffer, charsWritten);
  return result;
}

void __vprintStr(ValeStr* s, ValeInt start, ValeInt length) {
  char* chars = s->chars;
  fwrite(chars + start, 1, length, stdout);
  ValeReleaseMessage(s);
}

ValeInt vstr_toascii(ValeStr* s, ValeInt begin, ValeInt end) {
  assert(begin + 1 <= end);
  char* chars = s->chars;
  ValeInt result = (ValeInt)*(chars + begin);
  ValeReleaseMessage(s);
  return result;
}

ValeStr* vstr_fromascii(ValeInt code) {
  ValeStr* result = ValeStrNew(1);
  char* dest = result->chars;
  *dest = code;
  return result;
}
