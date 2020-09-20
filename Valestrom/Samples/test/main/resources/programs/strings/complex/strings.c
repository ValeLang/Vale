#include <stdint.h>
#include <string.h>
#include <assert.h>

// These are exposed by the compiled vale .obj/.o, they're
// the start of a Vale native API.
typedef struct ValeStr ValeStr;
ValeStr* vale_newstr(char* source, int64_t begin, int64_t length);
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

  for (int64_t i = 0; i < haystackLen - needleLen; i++) {
    if (strncmp(needle, haystack + i, needleLen) == 0) {
      return i;
    }
  }
  return -1;
}


ValeStr* vstr_substring(
    ValeStr* haystackContainerStr,
    int64_t haystackBegin,
    int64_t haystackEnd,
    int64_t beginInHaystack,
    int64_t endInHaystack) {
  // printf("calling getstrchars\n");
  char* haystackContainerChars = vale_getstrchars(haystackContainerStr);

  // printf("in substring, %d %d %d %d\n", haystackBegin, haystackEnd, beginInHaystack, endInHaystack);

  assert(haystackBegin >= 0);
  assert(haystackEnd >= 0);
  assert(beginInHaystack >= 0);
  assert(endInHaystack >= 0);

  int64_t haystackLen = haystackEnd - haystackBegin;
  assert(beginInHaystack < haystackLen);
  assert(endInHaystack <= haystackLen);
  assert(beginInHaystack + haystackBegin < haystackEnd);

  return vale_newstr(haystackContainerChars, haystackBegin + beginInHaystack, endInHaystack - beginInHaystack);
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
