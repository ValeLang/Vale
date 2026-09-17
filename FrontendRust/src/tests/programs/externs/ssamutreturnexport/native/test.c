#include <stdint.h>
#include <string.h>

#include "vtest/MutIntArray.h"
#include "vtest/valeMakeSSA.h"
#include "vtest/cMakeSSA.h"

vtest_MutIntArrayRef vtest_cMakeSSA() {
  return vtest_valeMakeSSA();
}
