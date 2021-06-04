#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "tmod/Spaceship.h"
#include "tmod/Seaship.h"
#include "tmod/IShip.h"
#include "tmod/expGetShipFuel.h"

extern int64_t tmod_expGetShipFuel(tmod_IShip s);

extern int64_t tmod_extGetShipFuel(tmod_IShip s) {
  return tmod_expGetShipFuel(s);
}
