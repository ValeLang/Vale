#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "tmod/Spaceship.h"
#include "tmod/Seaship.h"
#include "tmod/IShip.h"
#include "tmod/cGetShipFuel.h"
#include "tmod/valeGetShipFuel.h"

extern ValeInt tmod_cGetShipFuel(tmod_IShip s) {
  return tmod_valeGetShipFuel(s);
}
