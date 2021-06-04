#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "Spaceship.h"
#include "Seaship.h"
#include "IShip.h"
#include "expGetShipFuel.h"

extern int64_t tmod_expGetShipFuel(tmod_IShip s);

extern int64_t tmod_extGetShipFuel(tmod_IShip s) {
  return tmod_expGetShipFuel(s);
}
