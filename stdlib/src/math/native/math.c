#include <math.h>

#include "stdlib/fsqrt.h"
#include "stdlib/lshift.h"
#include "stdlib/rshift.h"
#include "stdlib/xor.h"

extern double stdlib_fsqrt(double x) {
  return sqrt(x);
}

extern int64_t stdlib_lshift(int64_t x, int32_t by) {
  return x << by;
}
extern int64_t stdlib_rshift(int64_t x, int32_t by) {
  return x >> by;
}
extern int64_t stdlib_xor(int64_t a, int64_t b) {
  return a ^ b;
}
extern int64_t stdlib_i64(int32_t x) {
  return x;
}
