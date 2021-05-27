#ifndef VALE_EXPORTS_ValeBuiltins_H_
#define VALE_EXPORTS_ValeBuiltins_H_

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define ValeReleaseMessage(msg) (free(*((void**)(msg) - 2)))

typedef struct { uint64_t length; char chars[0]; } ValeStr;
typedef int64_t ValeInt;
ValeStr* ValeStrNew(int64_t length);
ValeStr* ValeStrFrom(char* source);

#endif
