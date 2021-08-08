#include <stdint.h>
#include <string.h>

#include "tmod/Thing.h"
#include "tmod/makeThing.h"
#include "tmod/runExtCommand.h"

tmod_ThingRef tmod_runExtCommand() {
  return tmod_makeThing(ValeStrFrom("hello"), 37);
}
