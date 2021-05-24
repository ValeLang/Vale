#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "Spaceship.h"
#include "Seaship.h"
#include "IShip.h"
#include "expGetShipFuel.h"

extern int64_t expGetShipFuel(IShip s);

int64_t extGetShipFuel(IShip s) {
  return expGetShipFuel(s);
}
