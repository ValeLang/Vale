#include <stdint.h>
#include <math.h>

double __castIntFloat(int64_t i) {
  return (double)i;
}

int64_t __castFloatInt(double i) {
  printf("got float: %lf\n", i);
  return (int64_t)i;
}
