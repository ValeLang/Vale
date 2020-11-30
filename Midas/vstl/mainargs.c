#include <stdint.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>


// These are exposed by the compiled vale .obj/.o, they're
// the start of a Vale native API.
typedef struct ValeStr ValeStr;
ValeStr* vale_newstr(int64_t length);
char* vale_getstrchars(ValeStr* str);
int64_t vale_getstrnumbytes(ValeStr* str);


extern int64_t __main_num_args;
extern char** __main_args;
int64_t numMainArgs() {
  return __main_num_args;
}
ValeStr* getMainArg(int64_t i) {
  char* argCStr = __main_args[i];
  int64_t len = strlen(argCStr);
  ValeStr* vstr = vale_newstr(len);
  strncpy(vale_getstrchars(vstr), argCStr, len);
  return vstr;
}
