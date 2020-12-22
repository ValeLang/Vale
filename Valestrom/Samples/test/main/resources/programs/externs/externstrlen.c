#include <stdint.h>
#include <string.h>

typedef struct ValeStr {
  uint64_t length;
  char* chars;
} ValeStr;
ValeStr* ValeStrNew(char* source, int64_t begin, int64_t length);

int64_t extStrLen(ValeStr* haystackContainerStr) {
  char* haystackContainerChars = haystackContainerStr->chars;
  return strlen(haystackContainerChars);
}
