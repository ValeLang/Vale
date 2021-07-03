#include <stdint.h>
#include <string.h>

#include "tmod/ImmIntArray.h"
#include "tmod/valeMakeRSA.h"
#include "tmod/cMakeRSA.h"

tmod_ImmIntArray* tmod_cMakeRSA() {
  return tmod_valeMakeRSA();
}
