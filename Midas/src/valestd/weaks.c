
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

// Makes us not reuse old values, and also memsets to zero after mallocing.
#define REUSE_RELEASED

typedef struct {
  uint64_t capacity;
  // Looks like we only track this for the assertion at the end, perhaps we can optimize it out?
  uint64_t size;
  // If this is pointing at the end of the table, then there's no more free things.
  uint64_t firstFree;
  uint64_t* entries;
} WrcTable;

static WrcTable wrcTable = { 0, 0, 0, NULL };

static void releaseWrc(uint64_t wrcIndex) {
#ifdef REUSE_RELEASED
  wrcTable.entries[wrcIndex] = wrcTable.firstFree;
  wrcTable.firstFree = wrcIndex;
#else
  // Do nothing, dont add to the freelist. Freelist will just contain all the unused free spots
  // later in the array.
#endif
  assert(wrcTable.size > 0);
  wrcTable.size--;
}

uint64_t __getNumWrcs() {
  return wrcTable.size;
}

uint64_t __allocWrc() {
  uint64_t resultWrci = 0;
  if (wrcTable.firstFree == wrcTable.capacity) {
    uint64_t *oldEntries = wrcTable.entries;
    int oldCapacity = wrcTable.capacity;

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
#ifdef REUSE_RELEASED
      // Then we know there's no free things, we can just make the free list point to the
      // beginning of the new entries.
      // We would:
      //   wrcTable.firstFree = oldCapacity;
      // but it's fortunately already pointing there!
#else
      // There might be free things. Luckily, the last in that list will point at the old end,
      // which is fortunately the beginning of our new free list. So, we don't have to change
      // anything!
#endif
    } else {
      // There's nothing in the old list, so the firstFree will be zero, which means it's nicely
      // pointing at the beginning of our new free list.
    }

    wrcTable.capacity = newCapacity;
    wrcTable.entries = newEntries;

    if (oldCapacity > 0) {
      free(oldEntries);
    }
  }

  resultWrci = wrcTable.firstFree;
  printf("Allocated wrci %d from free list\n", resultWrci);
  wrcTable.firstFree = wrcTable.entries[resultWrci];

  wrcTable.size++;

  wrcTable.entries[resultWrci] = WRC_INITIAL_VALUE;
  return resultWrci;
}

int8_t __wrcIsLive(uint64_t wrcIndex) {
  if (wrcIndex >= wrcTable.capacity) {
    printf("Bad wrci: %d\n", wrcIndex);
    assert(0);
  }
  int8_t alive = (wrcTable.entries[wrcIndex] & WRC_LIVE_BIT) != 0;
  return alive;
}

// Warning: can have false positives, where it says something's valid when it's not.
void __checkWrc(uint64_t wrcIndex) {
  if (wrcIndex >= wrcTable.capacity) {
    printf("Bad wrci: %d\n", wrcIndex);
    assert(0);
  }
}

void __incrementWrc(uint64_t wrcIndex) {
  if (wrcIndex >= wrcTable.capacity) {
    printf("Bad wrci: %d\n", wrcIndex);
    assert(0);
  }
  wrcTable.entries[wrcIndex]++;
  printf("incremented %d, now %d\n", wrcIndex, wrcTable.entries[wrcIndex]);
}

void __decrementWrc(uint64_t wrcIndex) {
  if (wrcIndex >= wrcTable.capacity) {
    printf("Bad wrci: %d\n", wrcIndex);
    assert(0);
  }
  wrcTable.entries[wrcIndex]--;
  printf("decremented %d, now %d\n", wrcIndex, wrcTable.entries[wrcIndex]);
  if (wrcTable.entries[wrcIndex] == 0) {
    printf("releasing! %d\n", wrcIndex);
    releaseWrc(wrcIndex);
  }
}

void __markWrcDead(uint64_t wrcIndex) {
  if (wrcIndex >= wrcTable.capacity) {
    printf("Bad wrci: %d\n", wrcIndex);
    assert(0);
  }
  wrcTable.entries[wrcIndex] &= ~WRC_LIVE_BIT;
  printf("marking %d dead!\n", wrcIndex);
  if (wrcTable.entries[wrcIndex] == 0) {
    printf("releasing! %d\n", wrcIndex);
    releaseWrc(wrcIndex);
  }
}
