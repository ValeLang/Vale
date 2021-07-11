#include <stdint.h>
#include <stdio.h>

#include "tmod/Spaceship.h"
#include "tmod/spaceshipGetA.h"
#include "tmod/spaceshipGetB.h"

extern ValeInt tmod_sumSpaceshipFields(tmod_SpaceshipRef* s) {
  printf("ref is: %lld, %lld, %d, %d\n", s->unused0, s->unused1, s->unused2, s->unused3);
  return tmod_spaceshipGetA(s) + tmod_spaceshipGetB(s);
}
