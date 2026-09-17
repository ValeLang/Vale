#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "vtest/IShip.h"
#include "vtest/Firefly.h"
#include "vtest/cMakeShip.h"
#include "vtest/valeMakeShip.h"

extern vtest_IShip vtest_cMakeShip() {
  return vtest_valeMakeShip();
}
