#include <stdint.h>
#include <string.h>

#include "vtest/ImmIntArray.h"
#include "vtest/valeMakeRSA.h"
#include "vtest/cMakeRSA.h"

vtest_ImmIntArray* vtest_cMakeRSA() {
  return vtest_valeMakeRSA();
}
