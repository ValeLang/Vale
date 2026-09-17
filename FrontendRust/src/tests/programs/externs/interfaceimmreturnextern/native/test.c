#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "vtest/IShip.h"
#include "vtest/Firefly.h"
#include "vtest/cMakeShip.h"

vtest_IShip vtest_cMakeShip() {
  vtest_Firefly* firefly = (vtest_Firefly*)malloc(sizeof(vtest_Firefly));
  firefly->fuel = 42;

  // If the enum isnt 64 bits, we run into some undefined padding when there
  // are only 1 or 2 values in the enum.
  // Oddly, when we have 3 values in the enum, the problem disappears.
  // Anyway, we generate a vtest_IShip_Type_MAX_VALUE = 0x7FFFFFFFFFFFFFFF to
  // force this and fix it for good.
  // This assert is to check that it's 64 bits even though there's only one
  // entry in the enum.
  // Nevermind, it doesn't work for windows!

  vtest_IShip shipRef;
  shipRef.obj = firefly;
  shipRef.type = vtest_IShip_Type_Firefly;

  return shipRef;
}
