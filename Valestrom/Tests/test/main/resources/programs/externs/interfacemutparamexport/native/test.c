#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "tmod/Spaceship.h"
#include "tmod/Seaship.h"
#include "tmod/IShip.h"
#include "tmod/valeGetShipFuel.h"
#include "tmod/cGetTripleShipFuel.h"

ValeInt tmod_cGetTripleShipFuel(tmod_IShipRef s) {
  return tmod_valeGetShipFuel(s) * 3;
}
