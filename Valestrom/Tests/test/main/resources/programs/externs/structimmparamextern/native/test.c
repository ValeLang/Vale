#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "vtest/Flamscrankle.h"

ValeInt vtest_extFunc(vtest_Flamscrankle* flam) {
  ValeInt result = flam->a + flam->c;
  free(flam);
  return result;
}
