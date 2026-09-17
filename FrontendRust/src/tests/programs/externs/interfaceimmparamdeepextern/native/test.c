#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "vtest/Engine.h"
#include "vtest/Spaceship.h"
#include "vtest/Seaship.h"
#include "vtest/IShip.h"
#include "vtest/cGetShipFuel.h"

ValeInt vtest_cGetShipFuel(vtest_IShip s) {
  ValeInt result = 0;
  switch (s.type) {
    case vtest_IShip_Type_Seaship: {
      vtest_Seaship* ship = (vtest_Seaship*)s.obj;
      result = ship->engine->leftFuel + ship->engine->rightFuel;
      break;
    }
    case vtest_IShip_Type_Spaceship: {
      vtest_Spaceship* ship = (vtest_Spaceship*)s.obj;
      result = ship->fuel;
      break;
    }
    default:
      exit(1);
  }
  free(s.obj);
  return result;
}
