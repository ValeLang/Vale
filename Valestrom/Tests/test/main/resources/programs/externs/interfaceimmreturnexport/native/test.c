#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "tmod/IShip.h"
#include "tmod/Firefly.h"
#include "tmod/cMakeShip.h"
#include "tmod/valeMakeShip.h"

void tmod_cMakeShip(tmod_IShip* resultShipRefPtr) {
  tmod_IShip myRef = { 0 };
  tmod_valeMakeShip(&myRef);

  *resultShipRefPtr = myRef;
}
