#include <region/common/fatweaks/fatweaks.h>
#include "shared.h"
#include "utils/counters.h"
#include "weaks.h"

#include "translatetype.h"
#include "region/common/controlblock.h"
#include "utils/branch.h"

constexpr uint32_t WRC_ALIVE_BIT = 0x80000000;
constexpr uint32_t WRC_INITIAL_VALUE = WRC_ALIVE_BIT;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI = 0;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;
constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI = 1;

