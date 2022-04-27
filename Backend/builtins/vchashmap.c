#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#include <string.h>

// Note: This is not the fastest of hash sets.
// It would be nice to get a C implementation of swiss tables to use instead.
// optimize: replace this!

static const int64_t hashTableSizeList[] = {
  0, 3, 7, 13, 27, 53, 97, 193, 389, 769, 1543, 3079, 6151, 12289, 24593, 49157,
  98317, 196613, 393241, 786433, 1572869, 3145739, 6291469, 12582917, 25165843,
  50331653, 100663319, 201326611, 402653189, 805306457, 1610612741
};

typedef uint64_t (*VCHashSetHasher)(char* entry);
typedef char (*VCHashSetEquator)(char* entryA, char* entryB);

typedef struct {
  int32_t indexInSizeList;
  int32_t elementSize;
  int64_t capacity;
  int64_t size;
  // optimize: make this an array of bits, not an array of bytes.
  char* entryIndexToIsFilled;
  char* entriesBytes;
  VCHashSetHasher hasher;
  VCHashSetEquator equator;
} VCHashSet;

void VCHashSetInit(
    VCHashSet* hashset,
    int32_t elementSize,
    VCHashSetHasher hasher,
    VCHashSetEquator equator) {
  hashset->indexInSizeList = 0;
  hashset->elementSize = elementSize;
  hashset->capacity = 0;
  hashset->size = 0;
  hashset->entryIndexToIsFilled = NULL;
  hashset->entries = NULL;
  hashset->hasher = hasher;
  hashset->equator = equator;
}

static void* getEntryFromArray(char* entriesBytes, int32_t elementSize, int64_t index) {
  return entriesBytes[index * elementSize];
}
static void* getEntry(VCHashSet* hashet, int64_t index) {
  return getEntryFromArray(hashset->entriesBytes, hashset->elementSize, index);
}

// Returns -1 if not found.
static int64_t hashsetFindIndexOf(VCHashSet* hashset, void* obj) {
  assert(obj);
  if (!hashset->entries) {
    return -1;
  }
  int64_t startIndex = hashset->hasher(obj) % hashset->capacity;
  for (int64_t i = 0; i < hashset->capacity; i++) {
    int64_t indexInTable = (startIndex + i) % hashset->capacity;
    if (hashset->equator(getEntry(hashset, indexInTable), obj)) {
      return indexInTable;
    }
    if (!hashset->entryIndexToIsFilled[indexInTable]) {
      return -1;
    }
  }
  exit(1); // We shouldnt get here, it would mean the table is full.
}

int64_t VCHashSetContains(void* obj) {
  assert(obj);
  int64_t index = hashsetFindIndexOf(obj);
  return index != -1;
}

// Returns -1 if already present.
static int64_t hashsetFindOpenSpaceIndexFor(void* obj) {
  assert(obj);
  int64_t startIndex = hashset->hasher(obj) % hashset->capacity;
  for (int64_t i = 0; i < hashset->capacity; i++) {
    int64_t indexInTable = (startIndex + i) % hashset->capacity;
    if (hashset->equator(getEntry(hashset, indexInTable), obj)) {
      fprintf(stderr, "Found %p while looking for open hashset space, it's already present!\n", obj);
      assert(0);
    }
    if (!hashset->entryIndexToIsFilled[indexInTable]) {
      return indexInTable;
    }
  }
  fprintf(stderr, "%s:%d, unreachable!\n", __FILE__, __LINE__);
  exit(1); // We shouldnt get here, it would mean the table is full.
}

// Doesnt expand or increment size.
void hashsetInnerAdd(void* obj) {
  assert(obj);
  assert(hashset->size < hashset->capacity);
  int64_t index = hashsetFindOpenSpaceIndexFor(obj);
  assert(index != -1);
  assert(!hashset->entryIndexToIsFilled[index]);
  memcpy(getEntry(hashset, index), obj, hashset->elementSize);
  int64_t doublecheckIndex =  hashsetFindIndexOf(obj);
  assert(doublecheckIndex == index);
}

static void hashsetExpand() {
  int64_t oldNumEntries = hashset->capacity;
  char* oldEntries = hashset->entriesBytes;
  char* oldEntryIndexToIsFilled = hashset->entryIndexToIsFilled;

  //int64_t oldCapacity = hashset->capacity;

  hashset->indexInSizeList++;
  hashset->capacity = hashTableSizeList[hashset->indexInSizeList];
  hashset->entries = malloc(hashset->elementSize * hashset->capacity);
  memset(hashset->entries, 0, hashset->elementSize * hashset->capacity);
  hashset->entryIndexToIsFilled = malloc(1 * hashset->capacity);
  memset(hashset->entryIndexToIsFilled, 0, 1 * hashset->capacity);

  if (oldEntries) {
    for (int64_t i = 0; i < oldNumEntries; i++) {
      if (oldEntryIndexToIsFilled[i]) {
        hashsetInnerAdd(getEntryFromArray(oldEntries, hashset->elementSize, i));
      }
    }
    free(oldEntries);
    free(oldEntryIndexToIsFilled);
  }
}

void VCHashSetAdd(void* obj) {
  assert(obj);
  if (hashset->size >= hashset->capacity) {
    hashsetExpand();
  }
  if (VCHashSetContains(obj)) {
    fprintf(stderr, "Tried to add %p to hashset, but was already present!\n", obj);
    assert(0);
  }
  hashsetInnerAdd(obj);
  hashset->size++;
}

// Doesnt do any fixing of neighbors, or decrementing of size.
int64_t hashsetInnerRemove(void* obj) {
  assert(obj);
  int64_t originalIndex = hashsetFindIndexOf(obj);
  assert(originalIndex != -1);
  assert(hashset->entries[originalIndex].address == obj);
  hashset->entries[originalIndex].address = NULL;
  return originalIndex;
}

void VCHashSetRemove(void* obj) {
  assert(obj);
  int64_t originalIndex = hashsetInnerRemove(obj);
  hashset->size--;

  for (int64_t i = 1; i < hashset->capacity; i++) {
    int64_t neighborIndex = (originalIndex + i) % hashset->capacity;
    void* neighbor = hashset->entries[neighborIndex].address;
    if (neighbor != NULL) {
      hashset->entries[neighborIndex].address = NULL;
      hashsetInnerAdd(neighbor);
    } else {
      break;
    }
  }
}
