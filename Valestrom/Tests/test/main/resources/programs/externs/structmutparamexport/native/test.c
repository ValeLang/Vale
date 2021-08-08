#include <stdint.h>
#include <stdio.h>

#include "tmod/Spaceship.h"
#include "tmod/spaceshipGetA.h"
#include "tmod/spaceshipGetB.h"

extern ValeInt tmod_sumSpaceshipFields(tmod_SpaceshipRef s) {
  return tmod_spaceshipGetA(s) + tmod_spaceshipGetB(s);
}
