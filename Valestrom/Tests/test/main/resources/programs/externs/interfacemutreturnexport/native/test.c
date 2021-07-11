#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "tmod/IShip.h"
#include "tmod/runExtCommand.h"
#include "tmod/makeFirefly.h"

void tmod_runExtCommand(tmod_IShipRef* resultShipRefPtr) {
  tmod_IShipRef shipRef = { 0 };
  tmod_makeFirefly(&shipRef, 42);

  *resultShipRefPtr = shipRef;
}
