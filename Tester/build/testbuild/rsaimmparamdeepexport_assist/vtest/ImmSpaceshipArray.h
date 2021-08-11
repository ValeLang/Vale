#ifndef VALE_EXPORTS_ImmSpaceshipArray_H_
#define VALE_EXPORTS_ImmSpaceshipArray_H_
#include "ValeBuiltins.h"
typedef struct vtest_Spaceship vtest_Spaceship;
typedef struct vtest_ImmSpaceshipArray {
  uint32_t length;
  vtest_Spaceship* elements[0];
} vtest_ImmSpaceshipArray;
#endif
