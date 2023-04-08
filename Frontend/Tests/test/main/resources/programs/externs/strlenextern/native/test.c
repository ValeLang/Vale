#include "ValeBuiltins.h"
#include <string.h>
#include <stdio.h>

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

ValeInt vtest_extStrLen(ValeStr* haystackContainerStr) {
  int runNumber = incrementIntFile("myfile.bin");

  printf("extstrlen run number %d\n", runNumber);

  char* haystackContainerChars = haystackContainerStr->chars;
  ValeInt result = strlen(haystackContainerChars);
  return result * runNumber;
}
