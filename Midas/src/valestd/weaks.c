
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#include <string.h>

#define NO_FREE_WRCI 0xFFFFFFFFFFFFFFFF
#define WRC_LIVE_BIT 0x8000000000000000
#define WRC_INITIAL_VALUE WRC_LIVE_BIT

typedef struct {
  uint64_t capacity;
  uint64_t size;
  uint64_t firstFree;
  uint64_t* entries;
} WrcTable;

static WrcTable wrcTable = { 0, 0, NO_FREE_WRCI, NULL };

static void releaseWrc(uint64_t wrcIndex) {
  wrcTable.entries[wrcIndex] = wrcTable.firstFree;
  wrcTable.firstFree = wrcIndex;
  assert(wrcTable.size > 0);
  wrcTable.size--;
}

uint64_t __getNumWrcs() {
  return wrcTable.size;
}

uint64_t __allocWrc() {
  uint64_t resultWrci = 0;
  if (wrcTable.firstFree != NO_FREE_WRCI) {
    resultWrci = wrcTable.firstFree;
    wrcTable.firstFree = wrcTable.entries[resultWrci];
  } else {
    if (wrcTable.size == wrcTable.capacity) {
      uint64_t *oldEntries = wrcTable.entries;
      if (oldEntries) {
        int oldCapacity = wrcTable.capacity;
        int newCapacity = oldCapacity * 2;
        uint64_t *newEntries = malloc(sizeof(uint64_t) * newCapacity);
        memcpy(newEntries, oldEntries, sizeof(uint64_t) * oldCapacity);
        free(oldEntries);

        wrcTable.capacity = newCapacity;
        wrcTable.entries = newEntries;
      } else {
        int newCapacity = 32;
        uint64_t *newEntries = malloc(sizeof(uint64_t) * newCapacity);

        wrcTable.capacity = newCapacity;
        wrcTable.entries = newEntries;
      }
    }

    resultWrci = wrcTable.size++;
  }

  wrcTable.entries[resultWrci] = WRC_INITIAL_VALUE;
  return resultWrci;
}

int8_t __wrcIsLive(uint64_t wrcIndex) {
  assert(wrcIndex < wrcTable.size);
  int8_t alive = (wrcTable.entries[wrcIndex] & WRC_LIVE_BIT) != 0;
  return alive;
}

// Warning: can have false positives, where it says something's valid when it's not.
void __checkWrc(uint64_t wrcIndex) {
  assert(wrcIndex < wrcTable.size);
}

void __incrementWrc(uint64_t wrcIndex) {
  assert(wrcIndex < wrcTable.size);
  wrcTable.entries[wrcIndex]++;
  printf("incremented, now %d\n", wrcTable.entries[wrcIndex]);
}

void __decrementWrc(uint64_t wrcIndex) {
  assert(wrcIndex < wrcTable.size);
  wrcTable.entries[wrcIndex]--;
  printf("decremented, now %d\n", wrcTable.entries[wrcIndex]);
  if (wrcTable.entries[wrcIndex] == 0) {
    releaseWrc(wrcIndex);
  }
}

void __markWrcDead(uint64_t wrcIndex) {
  assert(wrcIndex < wrcTable.size);
  wrcTable.entries[wrcIndex] &= ~WRC_LIVE_BIT;
  if (wrcTable.entries[wrcIndex] == 0) {
    releaseWrc(wrcIndex);
  }
}
