#ifndef VALE_EXPORTS_IShip_H_
#define VALE_EXPORTS_IShip_H_
#include "ValeBuiltins.h"
#define vtest_IShip_Type_Seaship 0
#define vtest_IShip_Type_Spaceship 1
typedef struct vtest_IShip {
void* obj; uint64_t type;
} vtest_IShip;
#endif
