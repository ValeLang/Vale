#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#include "tmod/Bogglewoggle.h"
#include "tmod/Flamscrankle.h"

extern ValeInt tmod_expFunc(tmod_Flamscrankle* flam);

ValeInt tmod_extFunc(tmod_Flamscrankle* flam) {
  return tmod_expFunc(flam);
}
