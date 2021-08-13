#include <stdint.h>
#include <string.h>

#include "vtest/Thing.h"
#include "vtest/makeThing.h"
#include "vtest/runExtCommand.h"

vtest_ThingRef vtest_runExtCommand() {
  return vtest_makeThing(ValeStrFrom("hello"), 37);
}
