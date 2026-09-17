#include <stdint.h>
#include <stdio.h>

#include "vtest/Spaceship.h"
#include "vtest/cGetFuel.h"
#include "vtest/valeGetFuel.h"

extern ValeInt vtest_cGetFuel(vtest_SpaceshipRef s) {
  return vtest_valeGetFuel(s);
}
