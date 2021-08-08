#include <stdint.h>
#include <string.h>

#include "tmod/MutIntArray.h"
#include "tmod/valeMakeSSA.h"
#include "tmod/cMakeSSA.h"

tmod_MutIntArrayRef tmod_cMakeSSA() {
  return tmod_valeMakeSSA();
}
