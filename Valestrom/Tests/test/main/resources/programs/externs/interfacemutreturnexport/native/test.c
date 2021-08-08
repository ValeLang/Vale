#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "tmod/IShip.h"
#include "tmod/runExtCommand.h"
#include "tmod/makeFirefly.h"

tmod_IShipRef tmod_runExtCommand() {
  return tmod_makeFirefly(42);
}
