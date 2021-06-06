#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "tmod/Flamscrankle.h"
#include "tmod/expFunc.h"

ValeInt tmod_extFunc(tmod_Flamscrankle* flam) {
  ValeInt result = tmod_expFunc(flam);
  return result;
}
