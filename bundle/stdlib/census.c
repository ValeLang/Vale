#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#include <string.h>

const int64_t hashTableSizeList[] = {
  0, 3, 7, 13, 27, 53, 97, 193, 389, 769, 1543, 3079, 6151, 12289, 24593, 49157,
  98317, 196613, 393241, 786433, 1572869, 3145739, 6291469, 12582917, 25165843,
  50331653, 100663319, 201326611, 402653189, 805306457, 1610612741
};

typedef struct {
  void* address;
} CensusEntry;

typedef struct {
  int64_t indexInSizeList;
  int64_t capacity;
  int64_t size;
  CensusEntry* entries;
} Census;

static Census census = { 0, 0, 0, NULL };

// Returns -1 if not found.
static int64_t censusFindIndexOf(void* obj) {
  assert(obj);
  assert(census.entries);
  int64_t startIndex = ((uint64_t)obj) % census.capacity;
  for (int64_t i = 0; i < census.capacity; i++) {
    int64_t indexInTable = (startIndex + i) % census.capacity;
    if (census.entries[indexInTable].address == obj) {
      return indexInTable;
    }
    if (census.entries[indexInTable].address == NULL) {
      return -1;
    }
  }
  assert(0); // We shouldnt get here, it would mean the table is full.
}

int64_t __vcensusContains(void* obj) {
  assert(obj);
  int64_t index = censusFindIndexOf(obj);
  return index != -1;
}

// Returns -1 if already present.
static int64_t censusFindOpenSpaceIndexFor(void* obj) {
  assert(obj);
  int64_t startIndex = ((uint64_t)obj) % census.capacity;
  for (int64_t i = 0; i < census.capacity; i++) {
    int64_t indexInTable = (startIndex + i) % census.capacity;
    if (census.entries[indexInTable].address == obj) {
      fprintf(stderr, "Found %p while looking for open census space, it's already present!\n", obj);
      assert(0);
    }
    if (census.entries[indexInTable].address == NULL) {
      return indexInTable;
    }
  }
  assert(0); // We shouldnt get here, it would mean the table is full.
}

// Doesnt expand or increment size.
void censusInnerAdd(void* obj) {
  assert(obj);
  assert(census.size < census.capacity);
  int64_t index = censusFindOpenSpaceIndexFor(obj);
  assert(index != -1);
  assert(census.entries[index].address == NULL);
  census.entries[index].address = obj;
  int64_t doublecheckIndex =  censusFindIndexOf(obj);
  assert(doublecheckIndex == index);
}

static void censusExpand() {
  int64_t oldNumEntries = census.capacity;
  CensusEntry* oldEntries = census.entries;

  int64_t oldCapacity = census.capacity;

  census.indexInSizeList++;
  census.capacity = hashTableSizeList[census.indexInSizeList];
  census.entries = malloc(sizeof(CensusEntry) * census.capacity);
  memset(census.entries, 0, sizeof(CensusEntry) * census.capacity);

  if (oldEntries) {
    for (int64_t i = 0; i < oldNumEntries; i++) {
      if (oldEntries[i].address) {
        censusInnerAdd(oldEntries[i].address);
      }
    }
    free(oldEntries);
  }
}

void __vcensusAdd(void* obj) {
  assert(obj);
  if (census.size >= census.capacity) {
    censusExpand();
  }
  if (__vcensusContains(obj)) {
    fprintf(stderr, "Tried to add %p to census, but was already present!\n", obj);
    assert(0);
  }
  censusInnerAdd(obj);
  census.size++;
}

// Doesnt do any fixing of neighbors, or decrementing of size.
int64_t censusInnerRemove(void* obj) {
  assert(obj);
  int64_t originalIndex = censusFindIndexOf(obj);
  assert(originalIndex != -1);
  assert(census.entries[originalIndex].address == obj);
  census.entries[originalIndex].address = NULL;
  return originalIndex;
}

void __vcensusRemove(void* obj) {
  assert(obj);
  int64_t originalIndex = censusInnerRemove(obj);
  census.size--;

  for (int64_t i = 1; i < census.capacity; i++) {
    int64_t neighborIndex = (originalIndex + i) % census.capacity;
    void* neighbor = census.entries[neighborIndex].address;
    if (neighbor != NULL) {
      census.entries[neighborIndex].address = NULL;
      censusInnerAdd(neighbor);
    } else {
      break;
    }
  }
}
