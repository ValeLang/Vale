#include <stdint.h>

typedef struct TwoInts {
  int64_t a;
  int64_t b;
} TwoInts;

int extGetFromTuple(TwoInts ints) {
  return ints.b;
}
