#include <stdint.h>

#include "exports.h"

//typedef struct {
//  uint64_t ignore_1; // generation. if 0xFF, this is the owning reference.
//  void* ignore_2; // ptr to obj.
//} SpaceshipRef;
//extern int32_t Spaceship_get_a(Spaceship* s);
//extern int32_t Spaceship_get_b(Spaceship* s);

int64_t sumSpaceshipFields(SpaceshipRef s) {
  return Spaceship_get_a(s) + Spaceship_get_b(s);
}
