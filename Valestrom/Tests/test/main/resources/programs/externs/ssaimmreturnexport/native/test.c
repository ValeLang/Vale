#include <stdint.h>
#include <string.h>

#include "vtest/ImmIntArray.h"
#include "vtest/valeMakeSSA.h"
#include "vtest/cMakeSSA.h"

vtest_ImmIntArray* vtest_cMakeSSA() {
  return vtest_valeMakeSSA();
}
