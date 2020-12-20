#include <stdint.h>

#include "Spaceship.h"

//typedef struct {
//  uint64_t ignore_1; // generation. if 0xFF, this is the owning reference.
//  void* ignore_2; // ptr to obj.
//} SpaceshipRef;
extern int64_t spaceshipGetA(SpaceshipRef s);
extern int64_t spaceshipGetB(SpaceshipRef s);

int64_t sumSpaceshipFields(SpaceshipRef s) {
  return spaceshipGetA(s) + spaceshipGetB(s);
}
