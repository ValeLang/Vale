#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "Flamscrankle.h"
#include "expFunc.h"

int64_t tmod_extFunc(tmod_Flamscrankle* flam) {
  int64_t result = tmod_expFunc(flam);
  return result;
}
