#include <stdint.h>
#include <string.h>

#include "tmod/Thing.h"
#include "tmod/makeThing.h"
#include "tmod/runExtCommand.h"

extern void tmod_makeThing(tmod_ThingRef* result, ValeStr* a, ValeInt b);

void tmod_runExtCommand(tmod_ThingRef* result) {
  tmod_makeThing(result, ValeStrFrom("hello"), 37);
}
