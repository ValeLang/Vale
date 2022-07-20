#include <time.h>
#include <stdlib.h>

#include "stdlib/randomize.h"
#include "stdlib/random.h"

void stdlib_randomize(void) {
   srand(time(0));
}

int32_t stdlib_random(int32_t lower, int32_t upper) {
  return ((rand() % (upper + 1 - lower)) + lower);
}