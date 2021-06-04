#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "Spaceship.h"
#include "Seaship.h"
#include "IShip.h"

int64_t tmod_getShipFuel(tmod_IShip s) {
  int64_t result = 0;
  switch (s.type) {
    case tmod_IShip_Type_Seaship: {
      tmod_Seaship* ship = (tmod_Seaship*)s.obj;
      result = ship->leftFuel + ship->rightFuel;
      break;
    }
    case tmod_IShip_Type_Spaceship: {
      tmod_Spaceship* ship = (tmod_Spaceship*)s.obj;
      result = ship->fuel;
      break;
    }
    default:
      exit(1);
  }
  ValeReleaseMessage(s.obj);
  return result;
}
