#include <stdint.h>
#include <string.h>

#include "tmod/MutIntArray.h"
#include "tmod/valeMakeRSA.h"
#include "tmod/cMakeRSA.h"

tmod_MutIntArrayRef tmod_cMakeRSA() {
  return tmod_valeMakeRSA();
}
