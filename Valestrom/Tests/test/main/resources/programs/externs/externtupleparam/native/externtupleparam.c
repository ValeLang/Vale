#include <stdint.h>

typedef struct tmod_TwoInts {
  int64_t a;
  int64_t b;
} tmod_TwoInts;

int tmod_extGetFromTuple(tmod_TwoInts ints) {
  return ints.b;
}
