#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "vtest/Engine.h"
#include "vtest/Spaceship.h"
#include "vtest/Seaship.h"
#include "vtest/IShip.h"
#include "vtest/cGetShipFuel.h"
#include "vtest/valeGetShipFuel.h"

extern ValeInt vtest_cGetShipFuel(vtest_IShip s) {
  return vtest_valeGetShipFuel(s);
}
