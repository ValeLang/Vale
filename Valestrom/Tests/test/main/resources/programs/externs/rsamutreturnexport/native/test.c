#include <stdint.h>
#include <string.h>

#include "tmod/MutIntArray.h"
#include "tmod/valeMakeRSA.h"
#include "tmod/cMakeRSA.h"

void tmod_cMakeRSA(tmod_MutIntArrayRef* result) {
  tmod_valeMakeRSA(result);
}
