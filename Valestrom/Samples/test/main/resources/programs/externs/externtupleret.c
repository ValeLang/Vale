#include <stdint.h>

typedef struct TwoInts {
  int64_t a;
  int64_t b;
} TwoInts;

TwoInts extMakeTuple(int i) {
  TwoInts result = { i * 2, i * 3 };
  return result;
}
