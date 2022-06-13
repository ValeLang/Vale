#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

#include "vtest/Spaceship.h"
#include "vtest/Seaship.h"
#include "vtest/IShip.h"
#include "vtest/cGetShipFuel.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

ValeInt vtest_cGetShipFuel(vtest_IShip s) {
  int runNumber = incrementIntFile("myfile.bin");
  ValeInt result = 0;
  switch (s.type) {
    case vtest_IShip_Type_Seaship: {
      vtest_Seaship* ship = (vtest_Seaship*)s.obj;
      result = (ship->leftFuel + ship->rightFuel) * runNumber;
      break;
    }
    case vtest_IShip_Type_Spaceship: {
      vtest_Spaceship* ship = (vtest_Spaceship*)s.obj;
      result = ship->fuel * runNumber;
      break;
    }
    default:
      exit(1);
  }
  free(s.obj);
  return result;
}
