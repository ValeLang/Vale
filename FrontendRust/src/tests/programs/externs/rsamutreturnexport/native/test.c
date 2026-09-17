#include <stdint.h>
#include <string.h>

#include "vtest/MutIntArray.h"
#include "vtest/valeMakeRSA.h"
#include "vtest/cMakeRSA.h"

vtest_MutIntArrayRef vtest_cMakeRSA() {
  return vtest_valeMakeRSA();
}
