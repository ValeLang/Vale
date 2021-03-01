#include <stdint.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>


typedef struct ValeStr {
  uint64_t length;
  char chars[0];
} ValeStr;

extern ValeStr* ValeStrNew(int64_t length);


extern int64_t __main_num_args;
extern char** __main_args;
int64_t numMainArgs() {
  return __main_num_args;
}
ValeStr* getMainArg(int64_t i) {
  char* argCStr = __main_args[i];
  int64_t len = strlen(argCStr);
  ValeStr* vstr = ValeStrNew(len);
  strncpy(vstr->chars, argCStr, len);
  return vstr;
}
