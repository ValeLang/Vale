#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "tmod/IShip.h"
#include "tmod/Firefly.h"
#include "tmod/cMakeShip.h"
#include "tmod/valeMakeShip.h"

extern tmod_IShip tmod_cMakeShip() {
  return tmod_valeMakeShip();
}
