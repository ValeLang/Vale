
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#include <string.h>

#define WRC_LIVE_BIT 0x8000000000000000
#define WRC_INITIAL_VALUE WRC_LIVE_BIT

// would be nice to have a free bit, so we can progressively scan and sort the free list somehow.
// Maybe even with SIMD? Or maybe we can do it in small bursts every so often? We could use CPU
// prefetching to fetch N cache lines at a time in parallel, and run it every N uses.
// Or, we could just not add things to the free list, and let a rabbit pointer come and add things
// to the free list that it finds. Probably will hurt caching everywhere else though.

// Makes us not reuse old WRCIs, useful for debugging.
#define REUSE_RELEASED

uint64_t __wrc_capacity = 0;
uint64_t __wrc_firstFree = 0;
uint64_t* __wrc_entries = NULL;

uint64_t __getNumWrcs() {
  uint64_t numFrees = 0;
  for (uint64_t freeIndex = __wrc_firstFree; freeIndex != __wrc_capacity; freeIndex = __wrc_entries[freeIndex]) {
    numFrees++;
  }
  return __wrc_capacity - numFrees;
}

uint64_t __expandWrcTable() {
  uint64_t *oldEntries = __wrc_entries;
  int oldCapacity = __wrc_capacity;

  int newCapacity = 0;
  uint64_t *newEntries = NULL;

  if (oldCapacity > 0) {
    newCapacity = oldCapacity * 2;
  } else {
    newCapacity = 32;
  }

  newEntries = malloc(sizeof(uint64_t) * newCapacity);
  // Copy the old entries into the new array, exactly as they were.
  // If the list was empty, this will be a no-op.
  memcpy(newEntries, oldEntries, sizeof(uint64_t) * oldCapacity);

  // Make these new entries form a free list.
  for (int i = oldCapacity; i < newCapacity; i++) {
    // Make each one point at the next.
    // This will also make the last one point at the end of the array, which represents
    // the end of the free list.
    newEntries[i] = i + 1;
  }

  if (oldCapacity > 0) {
    // #ifdef REUSE_RELEASED
      // Then we know there's no free things, we can just make the free list point to the
      // beginning of the new entries.
      // We would:
      //   __wrc_firstFree = oldCapacity;
      // but it's fortunately already pointing there!
    // #else
      // There might be free things. Luckily, the last in that list will point at the old end,
      // which is fortunately the beginning of our new free list. So, we don't have to change
      // anything!
    // #endif
  } else {
    // There's nothing in the old list, so the firstFree will be zero, which means it's nicely
    // pointing at the beginning of our new free list.
  }

  __wrc_capacity = newCapacity;
  __wrc_entries = newEntries;

  if (oldCapacity > 0) {
    free(oldEntries);
  }
}
//
//uint64_t __allocWrc() {
//  if (__wrc_firstFree == __wrc_capacity) {
//    __expandWrcTable();
//  }
//
//  uint64_t resultWrci = __wrc_firstFree;
//  __wrc_firstFree = __wrc_entries[resultWrci];
//  __wrc_entries[resultWrci] = WRC_INITIAL_VALUE;
//  return resultWrci;
//}


// Warning: can have false positives, where it says something's valid when it's not.
void __checkWrc(uint64_t wrcIndex) {
  if (wrcIndex >= __wrc_capacity) {
    assert(0);
  }
}
