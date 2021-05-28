#include "ValeBuiltins.h"
#include <string.h>
#include <stdio.h>

ValeInt extStrLen(ValeStr* haystackContainerStr) {
  char* haystackContainerChars = haystackContainerStr->chars;
  ValeInt result = strlen(haystackContainerChars);
  return result;
}
