#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "vtest/runExtCommand.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

ValeInt vtest_runExtCommand() {
  int runNumber = incrementIntFile("myfile.bin");

  return 42 * runNumber;
}
