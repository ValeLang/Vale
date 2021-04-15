#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#include "Bogglewoggle.h"
#include "Flamscrankle.h"

extern int64_t expFunc(Flamscrankle* flam);

int64_t extFunc(Flamscrankle* flam) {
  return expFunc(flam);
}
