#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "vtest/IShip.h"
#include "vtest/runExtCommand.h"
#include "vtest/makeFirefly.h"

vtest_IShipRef vtest_runExtCommand() {
  return vtest_makeFirefly(42);
}
