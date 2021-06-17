#include <stdint.h>

typedef struct tmod_TwoInts {
  ValeInt a;
  ValeInt b;
} tmod_TwoInts;

int tmod_extGetFromTuple(tmod_TwoInts ints) {
  return ints.b;
}
