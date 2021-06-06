#include <stdint.h>
#include <math.h>

double __castI32Float(int32_t i) {
  return (double)i;
}

int32_t __castFloatI32(double i) {
  return (int32_t)i;
}

double __castI64Float(int64_t i) {
  return (double)i;
}

int64_t __castFloatI64(double i) {
  return (int64_t)i;
}
