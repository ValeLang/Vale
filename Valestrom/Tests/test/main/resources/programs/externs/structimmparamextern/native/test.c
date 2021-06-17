#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "tmod/Flamscrankle.h"

ValeInt tmod_extFunc(tmod_Flamscrankle* flam) {
  ValeInt result = flam->a + flam->c;
  ValeReleaseMessage(flam);
  return result;
}
