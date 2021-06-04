#include <stdint.h>

#include "Spaceship.h"

//typedef struct {
//  uint64_t ignore_1; // generation. if 0xFF, this is the owning reference.
//  void* ignore_2; // ptr to obj.
//} SpaceshipRef;
extern int64_t tmod_spaceshipGetA(tmod_SpaceshipRef s);
extern int64_t tmod_spaceshipGetB(tmod_SpaceshipRef s);

extern int64_t tmod_sumSpaceshipFields(tmod_SpaceshipRef s) {
  return tmod_spaceshipGetA(s) + tmod_spaceshipGetB(s);
}
