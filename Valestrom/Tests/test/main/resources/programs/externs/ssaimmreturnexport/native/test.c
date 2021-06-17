#include <stdint.h>
#include <string.h>

#include "tmod/ImmIntArray.h"
#include "tmod/valeMakeSSA.h"
#include "tmod/cMakeSSA.h"

tmod_ImmIntArray* tmod_cMakeSSA() {
  return tmod_valeMakeSSA();
}
