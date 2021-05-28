#include <stdint.h>
#include <string.h>

#include "Thing.h"

extern ThingRef makeThing(ValeStr* a, ValeInt b);

ThingRef runExtCommand() {
  return makeThing(ValeStrFrom("hello"), 37);
}
