#include <stdint.h>

typedef struct vtest_TwoInts {
  ValeInt a;
  ValeInt b;
} vtest_TwoInts;

int vtest_extGetFromTuple(vtest_TwoInts ints) {
  return ints.b;
}
