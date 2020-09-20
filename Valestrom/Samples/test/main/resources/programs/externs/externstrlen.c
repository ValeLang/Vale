#include <stdint.h>
#include <string.h>

typedef struct ValeStr ValeStr;
char* vale_getstrchars(ValeStr* str);

int64_t extStrLen(ValeStr* haystackContainerStr) {
  char* haystackContainerChars = vale_getstrchars(haystackContainerStr);
  return strlen(haystackContainerChars);
}
