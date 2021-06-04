#include <stdint.h>

typedef struct tmod_TwoInts {
  int64_t a;
  int64_t b;
} tmod_TwoInts;

TwoInts tmod_extMakeTuple(int i) {
  tmod_TwoInts result = { i * 2, i * 3 };
  return result;
}
