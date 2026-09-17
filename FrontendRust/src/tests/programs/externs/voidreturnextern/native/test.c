#include <stdint.h>

#include "vtest/MyBox.h"
#include "vtest/changeInBox.h"

void vtest_runExtCommand(vtest_MyBoxRef b) {
  vtest_changeInBox(b);
}
