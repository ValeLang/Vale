#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#include "vtest/Bogglewoggle.h"
#include "vtest/Flamscrankle.h"

extern ValeInt vtest_expFunc(vtest_Flamscrankle* flam);

ValeInt vtest_extFunc(vtest_Flamscrankle* flam) {
  return vtest_expFunc(flam);
}
