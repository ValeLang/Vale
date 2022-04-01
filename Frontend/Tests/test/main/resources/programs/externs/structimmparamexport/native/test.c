#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "vtest/Flamscrankle.h"
#include "vtest/expFunc.h"

ValeInt vtest_extFunc(vtest_Flamscrankle* flam) {
  ValeInt result = vtest_expFunc(flam);
  return result;
}
