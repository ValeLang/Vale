#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "Flamscrankle.h"
#include "expFunc.h"

int64_t extFunc(Flamscrankle* flam) {
  int64_t result = expFunc(flam);
  return result;
}
