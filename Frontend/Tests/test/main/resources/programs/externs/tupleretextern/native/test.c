#include <stdint.h>

typedef struct vtest_TwoInts {
  ValeInt a;
  ValeInt b;
} vtest_TwoInts;

TwoInts vtest_extMakeTuple(int i) {
  vtest_TwoInts result = { i * 2, i * 3 };
  return result;
}
