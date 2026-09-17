#include <stdint.h>
#include <string.h>
#include <assert.h>

#include "ValeBuiltins.h"

#define TRUE 1
#define FALSE 0

ValeInt vstr_indexOf(
    ValeStr* haystackContainerStr, ValeInt haystackBegin, ValeInt haystackEnd,
    ValeStr* needleContainerStr, ValeInt needleBegin, ValeInt needleEnd) {
  char* haystackContainerChars = haystackContainerStr->chars;
  char* haystack = haystackContainerChars + haystackBegin;
  ValeInt haystackLen = haystackEnd - haystackBegin;

  char* needleContainerChars = needleContainerStr->chars;
  char* needle = needleContainerChars + needleBegin;
  ValeInt needleLen = needleEnd - needleBegin;

  for (ValeInt i = 0; i < haystackLen - needleLen; i++) {
    if (strncmp(needle, haystack + i, needleLen) == 0) {
      return i;
    }
  }
  return -1;
}


ValeStr* vstr_substring(
    ValeStr* str,
    ValeInt begin,
    ValeInt length) {
  // printf("calling getstrchars\n");
  char* strChars = str->chars;

  // printf("in substring, %d %d %d %d\n", haystackBegin, haystackEnd, beginInHaystack, endInHaystack);

  vassert(begin >= 0);
  vassert(length >= 0);

  return ValeStrNew(strChars, begin, length);
}

char streq(
    ValeStr* aContainerStr,
    ValeInt aBegin,
    ValeInt aEnd,
    ValeStr* bContainerStr,
    ValeInt bBegin,
    ValeInt bEnd) {
  char* aContainerChars = aContainerStr->chars;
  char* a = aContainerChars + aBegin;
  ValeInt aLen = aEnd - aBegin;

  char* bContainerChars = bContainerStr->chars;
  char* b = bContainerChars + bBegin;
  ValeInt bLen = bEnd - bBegin;

  if (aLen != bLen) {
    return FALSE;
  }
  ValeInt len = aLen;

  for (int i = 0; i < len; i++) {
    if (a[i] != b[i]) {
      return FALSE;
    }
  }
  return TRUE;
}

ValeInt vstr_cmp(
    ValeStr* aContainerStr,
    ValeInt aBegin,
    ValeInt aEnd,
    ValeStr* bContainerStr,
    ValeInt bBegin,
    ValeInt bEnd) {
  char* aContainerChars = aContainerStr->chars;
  char* a = aContainerChars + aBegin;
  ValeInt aLen = aEnd - aBegin;

  char* bContainerChars = bContainerStr->chars;
  char* b = bContainerChars + bBegin;
  ValeInt bLen = bEnd - bBegin;

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
