#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "vtest/Spaceship.h"
#include "vtest/Seaship.h"
#include "vtest/IShip.h"
#include "vtest/valeGetShipFuel.h"
#include "vtest/cGetTripleShipFuel.h"

ValeInt vtest_cGetTripleShipFuel(vtest_IShipRef s) {
  return vtest_valeGetShipFuel(s) * 3;
}
