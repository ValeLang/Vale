#include <stdint.h>

typedef struct tmod_TwoInts {
  ValeInt a;
  ValeInt b;
} tmod_TwoInts;

TwoInts tmod_extMakeTuple(int i) {
  tmod_TwoInts result = { i * 2, i * 3 };
  return result;
}
