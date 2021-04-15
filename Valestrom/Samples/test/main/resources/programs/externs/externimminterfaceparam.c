#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "Spaceship.h"
#include "Seaship.h"
#include "IShip.h"

int64_t getShipFuel(IShip s) {
  int64_t result = 0;
  switch (s.type) {
    case IShip_Seaship: {
      Seaship* ship = (Seaship*)s.obj;
      result = ship->leftFuel + ship->rightFuel;
      break;
    }
    case IShip_Spaceship: {
      Spaceship* ship = (Spaceship*)s.obj;
      result = ship->fuel;
      break;
    }
    default:
      exit(1);
  }
  ValeReleaseMessage(s.obj);
  return result;
}
