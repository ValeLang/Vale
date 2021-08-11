#ifndef VALE_EXPORTS_ImmSpaceshipArray_H_
#define VALE_EXPORTS_ImmSpaceshipArray_H_
#include "ValeBuiltins.h"
typedef struct vtest_Spaceship vtest_Spaceship;
#define vtest_ImmSpaceshipArray_SIZE 3
typedef struct vtest_ImmSpaceshipArray {
  vtest_Spaceship* elements[3];
} vtest_ImmSpaceshipArray;
#endif
