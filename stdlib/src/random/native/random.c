#include <time.h>
#include <math.h>
#include <stdlib.h>

#include "stdlib/randomize.h"
#include "stdlib/randomBool.h"
#include "stdlib/randomInt.h"
#include "stdlib/randomI64.h"
#include "stdlib/randomFloat.h"

void stdlib_randomize(void) {
   srand(time(0));
}

int8_t stdlib_randomBool() {
  return (rand() & 1);
}

int32_t stdlib_randomInt(int32_t lower, int32_t upper) {
  return ((rand() % (upper + 1 - lower)) + lower);
}

int64_t stdlib_randomI64(int64_t lower, int64_t upper) {
  return ((rand() % (upper + 1 - lower)) + lower);
}

double stdlib_randomFloat(double lower, double upper) {
  double random = ((double) rand()) / (double) RAND_MAX;
  float range = upper - lower;  
  return (random*range) + lower;
}
