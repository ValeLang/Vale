#include <stdint.h>
#include <math.h>

double __vale_castI32Float(int32_t i) {
  return (double)i;
}

int32_t __vale_castFloatI32(double i) {
  return (int32_t)i;
}

double __vale_castI64Float(int64_t i) {
  return (double)i;
}

int64_t __vale_castFloatI64(double i) {
  return (int64_t)i;
}

int32_t __vale_TruncateI64ToI32(int64_t i) {
  return (int32_t)i;
}
