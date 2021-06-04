#include <stdint.h>
#include <string.h>

#include "tmod/Thing.h"

extern tmod_ThingRef tmod_makeThing(ValeStr* a, ValeInt b);

tmod_ThingRef tmod_runExtCommand() {
  return tmod_makeThing(ValeStrFrom("hello"), 37);
}
