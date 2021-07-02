#include <stdint.h>
#include <string.h>

#include "tmod/MutIntArray.h"
#include "tmod/valeMakeSSA.h"
#include "tmod/cMakeSSA.h"

void tmod_cMakeSSA(tmod_MutIntArrayRef* result) {
  tmod_valeMakeSSA(result);
}
