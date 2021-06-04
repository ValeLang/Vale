#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#include "Bogglewoggle.h"
#include "Flamscrankle.h"

extern int64_t tmod_expFunc(tmod_Flamscrankle* flam);

int64_t tmod_extFunc(tmod_Flamscrankle* flam) {
  return tmod_expFunc(flam);
}
