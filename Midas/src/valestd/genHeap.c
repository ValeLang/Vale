#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#include <string.h>

typedef struct {
  // Vale's control block should also have gen right here.
  uint32_t generationOrRc;
  // Just for now, for naive RC. After that, this is unused.
  uint32_t size;
  // Only used when free.
  void* nextFree;
} __Heap_Entry;

static __Heap_Entry* __gen_16B_heap_free_head = NULL;
static __Heap_Entry* __gen_24B_heap_free_head = NULL;
static __Heap_Entry* __gen_32B_heap_free_head = NULL;
static __Heap_Entry* __gen_40B_heap_free_head = NULL;
static __Heap_Entry* __gen_48B_heap_free_head = NULL;
static __Heap_Entry* __gen_56B_heap_free_head = NULL;
static __Heap_Entry* __gen_64B_heap_free_head = NULL;
static __Heap_Entry* __gen_80B_heap_free_head = NULL;
static __Heap_Entry* __gen_96B_heap_free_head = NULL;
static __Heap_Entry* __gen_128B_heap_free_head = NULL;
static __Heap_Entry* __gen_large_heap_free_head = NULL;

static __Heap_Entry** __gen_small_heaps_by_8b_multiple[] = {
    &__gen_16B_heap_free_head, // 0
    &__gen_16B_heap_free_head, // 8
    &__gen_16B_heap_free_head, // 16
    &__gen_24B_heap_free_head, // 24
    &__gen_32B_heap_free_head, // 32
    &__gen_40B_heap_free_head, // 40
    &__gen_48B_heap_free_head, // 48
    &__gen_56B_heap_free_head, // 56
    &__gen_64B_heap_free_head, // 64
    &__gen_80B_heap_free_head, // 72
    &__gen_80B_heap_free_head, // 80
    &__gen_96B_heap_free_head, // 88
    &__gen_96B_heap_free_head, // 96
    &__gen_128B_heap_free_head, // 104
    &__gen_128B_heap_free_head, // 112
    &__gen_128B_heap_free_head, // 120
    &__gen_128B_heap_free_head, // 128
};

static inline void* mallocAndZeroGen(int bytes) {
  __Heap_Entry* newAllocation = malloc(bytes);
  newAllocation->generationOrRc = 0;
  newAllocation->size = bytes;
  return newAllocation;
}

static inline void incrementGenAndAddToFreeList(__Heap_Entry* entry, __Heap_Entry** head) {
  // This doesnt make much sense for RCs, but thats fine since itll be overwritten to zero when
  // its allocated.
  entry->generationOrRc++;

  entry->size = 0;
  entry->nextFree = *head;
  *head = entry;
}

static inline __Heap_Entry* popFromFreeList(__Heap_Entry** head) {
  __Heap_Entry* entry = *head;
  *head = entry->nextFree;
  return entry;
}


// we assume bytes is a multiple of 8
void* __genMalloc(int bytes) {
  __Heap_Entry** freeListHead = NULL;
  if (bytes <= 128) {
    freeListHead = __gen_small_heaps_by_8b_multiple[bytes / 8];
  } else {
    freeListHead = &__gen_large_heap_free_head;
  }
  __Heap_Entry* result = NULL;
  if (*freeListHead) {
    result = popFromFreeList(freeListHead);
  } else {
    result = mallocAndZeroGen(bytes);
  }
  // Dont change the generation
  result->size = bytes;
  return result;
}

// we assume bytes is a multiple of 8
void* __genFree(void* allocationVoidPtr) {
  __Heap_Entry* allocation = (__Heap_Entry*)allocationVoidPtr;
  __Heap_Entry** freeListHead = NULL;
  if (allocation->size <= 128) {
    freeListHead = __gen_small_heaps_by_8b_multiple[allocation->size / 8];
  } else {
    freeListHead = &__gen_large_heap_free_head;
  }
  incrementGenAndAddToFreeList((__Heap_Entry*)allocation, freeListHead);
}
