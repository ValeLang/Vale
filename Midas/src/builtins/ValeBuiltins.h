#ifndef VALE_EXPORTS_ValeBuiltins_H_
#define VALE_EXPORTS_ValeBuiltins_H_

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define ValeReleaseMessage(msg) (free(*((void**)(msg) - 2)))

typedef int32_t ValeInt;
typedef struct { ValeInt length; char chars[0]; } ValeStr;
ValeStr* ValeStrNew(int64_t length);
ValeStr* ValeStrFrom(char* source);

#endif
