#include <stdint.h>
#include <stdio.h>

#include "vtest/Spaceship.h"
#include "vtest/spaceshipGetA.h"
#include "vtest/spaceshipGetB.h"

extern ValeInt vtest_sumSpaceshipFields(vtest_SpaceshipRef s) {
  return vtest_spaceshipGetA(s) + vtest_spaceshipGetB(s);
}
